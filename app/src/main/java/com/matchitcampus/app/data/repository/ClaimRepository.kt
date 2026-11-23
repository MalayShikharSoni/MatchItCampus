package com.matchitcampus.app.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.matchitcampus.app.data.local.AppDatabase
import com.matchitcampus.app.data.local.mapper.toDomain
import com.matchitcampus.app.data.local.mapper.toEntity
import com.matchitcampus.app.domain.model.Claim
import com.matchitcampus.app.domain.model.ClaimStatus
import com.matchitcampus.app.domain.model.ItemStatus
import com.matchitcampus.app.domain.model.SyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val TAG = "MatchIt/ClaimRepository"

/**
 * Claims on found items. Same pattern as [ItemRepository]: reads are Flows from Room, and
 * writes go to Room as PENDING_UPLOAD for Phase 3's SyncManager to push.
 *
 * It takes the whole [AppDatabase] rather than only the claim DAO, because accepting a claim
 * changes the `claims` and `items` tables together, in one transaction ([acceptClaim]).
 *
 * @param clock returns the current time in epoch millis. Passed in so tests can fix the time.
 */
class ClaimRepository(
    private val database: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val claimDao = database.claimDao()
    private val itemDao = database.itemDao()

    /** Claims on my found items. */
    fun observeIncoming(finderId: String): Flow<List<Claim>> =
        claimDao.observeIncoming(finderId).map { rows -> rows.map { it.toDomain() } }

    /** Claims I made. */
    fun observeOutgoing(claimantId: String): Flow<List<Claim>> =
        claimDao.observeOutgoing(claimantId).map { rows -> rows.map { it.toDomain() } }

    fun observeForFoundItem(foundItemId: String): Flow<List<Claim>> =
        claimDao.observeForFoundItem(foundItemId).map { rows -> rows.map { it.toDomain() } }

    /** For the badge on the Claims tab. */
    fun observePendingIncomingCount(finderId: String): Flow<Int> =
        claimDao.observePendingIncomingCount(finderId)

    fun observeUnsyncedCount(): Flow<Int> = claimDao.observeUnsyncedCount()

    /**
     * Saves a new claim as PENDING.
     *
     * It does not change the found item: that item belongs to the finder, and each user only
     * writes their own documents (the Firestore rules enforce this from Phase 3). The finder's
     * phone moves the item to CLAIM_PENDING when it sees the claim.
     *
     * @throws IllegalArgumentException if the claim isn't PENDING, or the claimant is the finder.
     */
    suspend fun createClaim(claim: Claim) {
        require(claim.status == ClaimStatus.PENDING) { "A new claim must be PENDING" }
        require(claim.claimantId != claim.finderId) { "You can't claim your own item" }
        val now = clock()
        claimDao.upsert(
            claim.copy(createdAt = now, updatedAt = now, syncState = SyncState.PENDING_UPLOAD).toEntity(),
        )
        Log.d(TAG, "Created claim ${claim.id} on item ${claim.foundItemId}")
    }

    /**
     * The finder accepts a claim. In one transaction:
     * 1. the claim becomes ACCEPTED,
     * 2. the found item becomes RETURNED,
     * 3. every other PENDING claim on that item becomes REJECTED.
     *
     * The transaction means no screen can observe a half-done state, such as an accepted
     * claim on an item that is still OPEN. If any step throws, nothing is written.
     *
     * The claimant's linked lost item is not touched here. It's the claimant's document, so
     * their own phone marks it RETURNED when it sees the accepted claim (Phase 3/5).
     *
     * @return false if the claim doesn't exist or is no longer PENDING.
     */
    suspend fun acceptClaim(claimId: String): Boolean = database.withTransaction {
        val claim = claimDao.getById(claimId)
        if (claim == null || claim.status != ClaimStatus.PENDING) {
            Log.w(TAG, "acceptClaim: claim $claimId is missing or not pending (${claim?.status})")
            return@withTransaction false
        }
        val now = clock()
        claimDao.updateStatus(claimId, ClaimStatus.ACCEPTED, SyncState.PENDING_UPLOAD, now)
        itemDao.updateStatus(claim.foundItemId, ItemStatus.RETURNED, SyncState.PENDING_UPLOAD, now)
        val others = claimDao.getPendingForFoundItem(claim.foundItemId)
        for (other in others) {
            claimDao.updateStatus(other.id, ClaimStatus.REJECTED, SyncState.PENDING_UPLOAD, now)
        }
        Log.d(TAG, "Accepted claim $claimId, rejected ${others.size} other claim(s)")
        true
    }

    /**
     * The finder rejects a claim.
     *
     * @return false if the claim doesn't exist or is no longer PENDING.
     */
    suspend fun rejectClaim(claimId: String): Boolean = database.withTransaction {
        val rejected = closePendingClaim(claimId, ClaimStatus.REJECTED)
        if (rejected) {
            // Only the finder rejects, so the finder is the item's owner here.
            val claim = claimDao.getById(claimId)
            if (claim != null) refreshFoundItemStatus(claim.foundItemId, ownerUid = claim.finderId)
        }
        rejected
    }

    /**
     * Keeps a found item's status in step with its claims. Runs on the **finder's** phone,
     * because only the owner may write the item (Phase 3 security rules):
     * - OPEN with at least one PENDING claim → CLAIM_PENDING
     * - CLAIM_PENDING with no PENDING claims left (all rejected or cancelled) → OPEN
     * - RETURNED is final and never changes here.
     *
     * `SyncManager` calls it when claims arrive from Firestore, and [rejectClaim] calls it
     * after a rejection.
     *
     * @param ownerUid the signed-in user. Nothing changes unless they own the item.
     * @return true if the item's status changed.
     */
    suspend fun refreshFoundItemStatus(foundItemId: String, ownerUid: String): Boolean =
        database.withTransaction {
            val item = itemDao.getById(foundItemId)
            if (item == null || item.ownerId != ownerUid) return@withTransaction false
            val hasPending = claimDao.getPendingForFoundItem(foundItemId).isNotEmpty()
            val newStatus = when {
                item.status == ItemStatus.OPEN && hasPending -> ItemStatus.CLAIM_PENDING
                item.status == ItemStatus.CLAIM_PENDING && !hasPending -> ItemStatus.OPEN
                else -> return@withTransaction false
            }
            itemDao.updateStatus(foundItemId, newStatus, SyncState.PENDING_UPLOAD, clock())
            Log.d(TAG, "Found item $foundItemId: ${item.status} -> $newStatus")
            true
        }

    /**
     * The claimant withdraws their claim.
     *
     * @return false if the claim doesn't exist or is no longer PENDING.
     */
    suspend fun cancelClaim(claimId: String): Boolean = closePendingClaim(claimId, ClaimStatus.CANCELLED)

    /** Removes every claim from this phone. Called on logout, because claims are private. */
    suspend fun clearAll() {
        claimDao.deleteAll()
        Log.d(TAG, "Cleared all local claims")
    }

    private suspend fun closePendingClaim(claimId: String, newStatus: ClaimStatus): Boolean =
        database.withTransaction {
            val claim = claimDao.getById(claimId)
            if (claim == null || claim.status != ClaimStatus.PENDING) {
                Log.w(TAG, "Can't set claim $claimId to $newStatus: it is ${claim?.status ?: "missing"}")
                return@withTransaction false
            }
            claimDao.updateStatus(claimId, newStatus, SyncState.PENDING_UPLOAD, clock())
            Log.d(TAG, "Claim $claimId is now $newStatus")
            true
        }
}
