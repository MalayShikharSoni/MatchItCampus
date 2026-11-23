package com.matchitcampus.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.matchitcampus.app.data.local.entity.ClaimEntity
import com.matchitcampus.app.data.local.relation.ClaimWithItems
import com.matchitcampus.app.domain.model.ClaimStatus
import com.matchitcampus.app.domain.model.SyncState
import kotlinx.coroutines.flow.Flow

/**
 * Database access for the `claims` table. Same conventions as [ItemDao]: `Flow` for observed
 * queries, `suspend` for one-off reads and writes, `@Transaction` where `@Relation` is used.
 */
@Dao
interface ClaimDao {

    @Upsert
    suspend fun upsert(claim: ClaimEntity)

    @Upsert
    suspend fun upsertAll(claims: List<ClaimEntity>)

    /** Claims other people made on my found items (Claims → Incoming). */
    @Transaction
    @Query("SELECT * FROM claims WHERE finderId = :finderId ORDER BY createdAt DESC")
    fun observeIncoming(finderId: String): Flow<List<ClaimWithItems>>

    /** Claims I made (Claims → Outgoing). */
    @Transaction
    @Query("SELECT * FROM claims WHERE claimantId = :claimantId ORDER BY createdAt DESC")
    fun observeOutgoing(claimantId: String): Flow<List<ClaimWithItems>>

    /** All claims on one found item, for the finder's view of the detail screen. */
    @Transaction
    @Query("SELECT * FROM claims WHERE foundItemId = :foundItemId ORDER BY createdAt DESC")
    fun observeForFoundItem(foundItemId: String): Flow<List<ClaimWithItems>>

    /** Number of claims waiting for my decision: the badge on the Claims tab. */
    @Query("SELECT COUNT(*) FROM claims WHERE finderId = :finderId AND status = 'PENDING'")
    fun observePendingIncomingCount(finderId: String): Flow<Int>

    @Query("SELECT * FROM claims WHERE id = :id")
    suspend fun getById(id: String): ClaimEntity?

    /** Open claims on one item. Accepting one claim rejects the others. */
    @Query("SELECT * FROM claims WHERE foundItemId = :foundItemId AND status = 'PENDING'")
    suspend fun getPendingForFoundItem(foundItemId: String): List<ClaimEntity>

    @Query("SELECT * FROM claims WHERE syncState = :syncState")
    suspend fun getBySyncState(syncState: SyncState): List<ClaimEntity>

    /** Changes only the status and bookkeeping columns. @return the number of rows changed. */
    @Query("UPDATE claims SET status = :status, syncState = :syncState, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: ClaimStatus, syncState: SyncState, updatedAt: Long): Int

    /** Same idea as [ItemDao.markSynced]: only marks the row if it didn't change during the upload. */
    @Query(
        """
        UPDATE claims SET syncState = 'SYNCED'
        WHERE id = :id AND updatedAt = :updatedAt AND syncState = 'PENDING_UPLOAD'
        """,
    )
    suspend fun markSynced(id: String, updatedAt: Long): Int

    @Query("SELECT COUNT(*) FROM claims WHERE syncState != 'SYNCED'")
    fun observeUnsyncedCount(): Flow<Int>

    /** Claims are private to the claimant and finder, so logout removes them all. */
    @Query("DELETE FROM claims")
    suspend fun deleteAll()
}
