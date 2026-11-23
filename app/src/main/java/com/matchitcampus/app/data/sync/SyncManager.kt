package com.matchitcampus.app.data.sync

import android.util.Log
import androidx.room.withTransaction
import com.matchitcampus.app.data.local.AppDatabase
import com.matchitcampus.app.data.prefs.SyncPreferences
import com.matchitcampus.app.data.remote.ClaimRemoteDataSource
import com.matchitcampus.app.data.remote.ItemRemoteDataSource
import com.matchitcampus.app.data.remote.RemoteItemPage
import com.matchitcampus.app.data.remote.dto.ClaimDto
import com.matchitcampus.app.data.remote.mapper.toDto
import com.matchitcampus.app.data.remote.mapper.toEntity
import com.matchitcampus.app.data.repository.ClaimRepository
import com.matchitcampus.app.data.repository.ItemRepository
import com.matchitcampus.app.domain.model.AuthUser
import com.matchitcampus.app.domain.model.ClaimStatus
import com.matchitcampus.app.domain.model.ItemStatus
import com.matchitcampus.app.domain.model.SyncState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "MatchIt/SyncManager"

/**
 * Keeps Room and Firestore in step. Room stays the single source of truth: the UI only
 * observes Room, and this class is the only thing that copies data between the two.
 *
 * **Local → remote (push).** Repositories write to Room and mark rows `PENDING_UPLOAD` or
 * `PENDING_DELETE`. This class watches Room's count of unsynced rows and the network, and
 * pushes whenever there's something to send and we're online. So the repositories never
 * need to know that sync exists. After a successful upload a row is marked `SYNCED`.
 *
 * **Remote → local (listeners).** While a user is signed in **and** the app is in the
 * foreground, Firestore listeners deliver:
 * - items changed since the last sync (an incremental query, see [ItemRemoteDataSource]),
 * - the user's claims, as claimant and as finder.
 *
 * Each change is merged into Room with last-write-wins ([SyncMerge]).
 *
 * **Lifecycle.** [start] combines the auth state with the app's foreground state and uses
 * `collectLatest`. When either changes, the previous sync session coroutine is cancelled,
 * which removes its listeners (their `awaitClose` runs), and a new one starts if needed.
 * Nothing keeps listening after logout or while the app is in the background.
 *
 * @param appInForeground true while the app is visible (from `ProcessLifecycleOwner`).
 * @param scope the app-wide scope. Sync outlives any one screen.
 */
class SyncManager(
    private val authState: StateFlow<AuthUser?>,
    private val database: AppDatabase,
    private val itemRepository: ItemRepository,
    private val claimRepository: ClaimRepository,
    private val itemRemote: ItemRemoteDataSource,
    private val claimRemote: ClaimRemoteDataSource,
    private val syncPreferences: SyncPreferences,
    private val connectivity: ConnectivityObserver,
    private val appInForeground: Flow<Boolean>,
    private val scope: CoroutineScope,
) {
    private val itemDao = database.itemDao()
    private val claimDao = database.claimDao()

    /** Only one push runs at a time, so a row is never uploaded twice in parallel. */
    private val pushMutex = Mutex()

    private val _events = MutableSharedFlow<SyncEvent>(extraBufferCapacity = 16)

    /**
     * One-shot events for the UI, such as "a new claim arrived on your item".
     *
     * A SharedFlow, not a StateFlow, because each event should be shown once (as a snackbar)
     * rather than held as state that is shown again after rotation.
     */
    val events: SharedFlow<SyncEvent> = _events.asSharedFlow()

    private var started = false

    /** Called once, from `MatchItApp.onCreate`. */
    fun start() {
        check(!started) { "SyncManager already started" }
        started = true
        scope.launch {
            combine(
                authState.map { it?.uid }.distinctUntilChanged(),
                appInForeground,
            ) { uid, inForeground -> if (inForeground) uid else null }
                .distinctUntilChanged()
                .collectLatest { uid ->
                    if (uid == null) Log.d(TAG, "Sync paused (signed out or in background)") else runSession(uid)
                }
        }
    }

    /**
     * Everything that runs while [uid] is signed in and the app is visible.
     *
     * `coroutineScope` makes the three jobs children of this call (structured concurrency):
     * when `collectLatest` cancels the session, all three stop together, and none can outlive it.
     */
    private suspend fun runSession(uid: String) {
        Log.d(TAG, "Sync session started for $uid")
        try {
            coroutineScope {
                launch { pushWhenNeeded() }
                launch { listenToItems() }
                launch { listenToClaims(uid) }
            }
        } finally {
            Log.d(TAG, "Sync session stopped for $uid")
        }
    }

    /**
     * Pushes whenever Room has unsynced rows and the network is up.
     *
     * `combine` re-evaluates when either count or the connection changes. `debounce` waits for
     * a short quiet period, so a burst of local writes (for example accepting a claim, which
     * changes several rows) becomes one push instead of several.
     */
    @OptIn(FlowPreview::class)
    private suspend fun pushWhenNeeded() {
        combine(
            itemDao.observeUnsyncedCount(),
            claimDao.observeUnsyncedCount(),
            connectivity.isOnline,
        ) { items, claims, online -> online && items + claims > 0 }
            .debounce(PUSH_DEBOUNCE_MS)
            .filter { it }
            .collect { pushPending() }
    }

    /**
     * Uploads every unsynced row that belongs to the signed-in user.
     *
     * Each row is handled on its own: one failure (for example a rule rejection) is logged and
     * the row stays pending, but the others still go through. Rows owned by another account
     * (left over from someone who logged out with unsynced changes) are skipped, because the
     * security rules would reject them anyway.
     */
    suspend fun pushPending() = pushMutex.withLock {
        val uid = authState.value?.uid ?: return@withLock
        if (!connectivity.isCurrentlyOnline()) {
            Log.d(TAG, "Offline; push postponed until the network is back")
            return@withLock
        }
        var sent = 0
        var failed = 0

        for (item in itemDao.getBySyncState(SyncState.PENDING_UPLOAD)) {
            if (item.ownerId != uid) continue
            if (tryRemote("upload item ${item.id}") { itemRemote.upsert(item.toDto()) }) {
                itemDao.markSynced(item.id, item.updatedAt)
                sent++
            } else {
                failed++
            }
        }

        for (item in itemDao.getBySyncState(SyncState.PENDING_DELETE)) {
            if (item.ownerId != uid) continue
            var existedRemotely = false
            if (tryRemote("delete item ${item.id}") { existedRemotely = itemRemote.markDeleted(item.id, item.updatedAt) }) {
                // Either way the row can go: the tombstone is written, or there was nothing remote.
                itemDao.deleteById(item.id)
                if (!existedRemotely) Log.d(TAG, "Item ${item.id} was never uploaded; deleted locally only")
                sent++
            } else {
                failed++
            }
        }

        for (claim in claimDao.getBySyncState(SyncState.PENDING_UPLOAD)) {
            if (claim.claimantId != uid && claim.finderId != uid) continue
            if (tryRemote("upload claim ${claim.id}") { claimRemote.upsert(claim.toDto()) }) {
                claimDao.markSynced(claim.id, claim.updatedAt)
                sent++
            } else {
                failed++
            }
        }

        if (sent + failed > 0) Log.d(TAG, "Push finished: $sent sent, $failed failed")
    }

    /**
     * "Sync now" (pull-to-refresh and the debug screen): push, then fetch changes once.
     * The live listeners normally make this unnecessary, but it's a useful manual retry.
     */
    suspend fun syncNow() {
        pushPending()
        if (authState.value == null) return
        val since = syncPreferences.lastItemSyncMillis.first()
        tryRemote("fetch item changes") {
            applyItemPage(itemRemote.fetchChangesSince(since))
        }
    }

    /**
     * Listens for item changes, starting after the last sync point.
     *
     * Each page is merged into Room as it arrives. If a page is full (the query hit its
     * limit), more changes may be waiting, so the listener is re-attached from the new sync
     * point. If the listener fails (for example no network at the start, or rules not yet
     * deployed), it waits and tries again.
     */
    private suspend fun listenToItems() {
        while (currentCoroutineContext().isActive) {
            val since = syncPreferences.lastItemSyncMillis.first()
            try {
                itemRemote.observeChangesSince(since)
                    .onEach { page -> applyItemPage(page) }
                    .first { page -> page.isFullPage }
                Log.d(TAG, "Item page was full; continuing from the new sync point")
                // Safety net: if the sync point didn't move, don't spin.
                if (syncPreferences.lastItemSyncMillis.first() <= since) delay(LISTENER_RETRY_MS)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Item listener stopped; retrying in ${LISTENER_RETRY_MS / 1000}s", e)
                delay(LISTENER_RETRY_MS)
            }
        }
    }

    /** Merges one page of remote items into Room in a single transaction, then moves the sync point. */
    private suspend fun applyItemPage(page: RemoteItemPage) {
        if (page.items.isNotEmpty()) {
            var written = 0
            var deleted = 0
            var skipped = 0
            database.withTransaction {
                for (remote in page.items) {
                    when (val decision = SyncMerge.decide(itemDao.getById(remote.id), remote)) {
                        is SyncMerge.ItemDecision.Write -> { itemDao.upsert(decision.entity); written++ }
                        SyncMerge.ItemDecision.Delete -> { itemDao.deleteById(remote.id); deleted++ }
                        SyncMerge.ItemDecision.Skip -> skipped++
                    }
                }
            }
            Log.d(TAG, "Items from Firestore: $written written, $deleted deleted, $skipped kept local")
        }
        page.newestServerMillis?.let { syncPreferences.advanceLastItemSync(it) }
    }

    /** Listens to the user's claims (as claimant and as finder), retrying after failures. */
    private suspend fun listenToClaims(uid: String) {
        while (currentCoroutineContext().isActive) {
            try {
                var firstSnapshot = true
                claimRemote.observeClaimsFor(uid).collect { claims ->
                    // The first snapshot is the existing backlog, not news, so no snackbars for it.
                    applyClaims(uid, claims, announceNew = !firstSnapshot)
                    firstSnapshot = false
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Claims listener stopped; retrying in ${LISTENER_RETRY_MS / 1000}s", e)
                delay(LISTENER_RETRY_MS)
            }
        }
    }

    /**
     * Merges remote claims into Room, then reacts to them. Each user only writes their own
     * documents, so each side's phone does its own part:
     * - **Finder:** a new pending claim moves the found item to CLAIM_PENDING (and back to
     *   OPEN when no pending claims are left).
     * - **Claimant:** an ACCEPTED claim marks the claimant's linked lost item RETURNED.
     */
    private suspend fun applyClaims(uid: String, remoteClaims: List<ClaimDto>, announceNew: Boolean) {
        val newIncoming = mutableListOf<ClaimDto>()
        database.withTransaction {
            // If the user signed out while this snapshot was on its way, don't write their
            // private claims back into the database that logout just cleared.
            if (authState.value?.uid != uid) return@withTransaction
            for (remote in remoteClaims) {
                val local = claimDao.getById(remote.id)
                if (!SyncMerge.remoteWins(local?.updatedAt, remote.updatedAtMillis)) continue
                if (local == null && remote.finderId == uid && remote.status == ClaimStatus.PENDING) {
                    newIncoming += remote
                }
                claimDao.upsert(remote.toEntity())
            }
        }
        if (authState.value?.uid != uid) return

        remoteClaims.filter { it.finderId == uid }
            .map { it.foundItemId }
            .distinct()
            .forEach { claimRepository.refreshFoundItemStatus(it, ownerUid = uid) }

        remoteClaims.filter { it.claimantId == uid && it.status == ClaimStatus.ACCEPTED }
            .mapNotNull { it.lostItemId }
            .forEach { lostItemId -> markLostItemReturned(lostItemId, uid) }

        if (announceNew) {
            newIncoming.forEach { claim ->
                Log.d(TAG, "New claim ${claim.id} on item ${claim.foundItemId}")
                _events.tryEmit(SyncEvent.NewIncomingClaim(claim.id, claim.foundItemId, claim.claimantName))
            }
        }
    }

    private suspend fun markLostItemReturned(lostItemId: String, uid: String) {
        val lost = itemDao.getById(lostItemId) ?: return
        if (lost.ownerId == uid && lost.status != ItemStatus.RETURNED) {
            Log.d(TAG, "Claim accepted: marking my lost item $lostItemId RETURNED")
            itemRepository.updateStatus(lostItemId, ItemStatus.RETURNED)
        }
    }

    /**
     * Runs one Firestore call with a timeout, turning any failure into `false` (logged).
     *
     * `withTimeoutOrNull` matters because a Firestore write's Task doesn't fail when the
     * server is unreachable: it just waits. The timeout gives up on waiting, so the push
     * loop can move on; the row stays pending and is retried later. Cancellation is re-thrown
     * so that stopping the session really stops the work.
     */
    private suspend fun tryRemote(what: String, block: suspend () -> Unit): Boolean = try {
        withTimeoutOrNull(REMOTE_TIMEOUT_MS) { block(); true }
            ?: false.also { Log.w(TAG, "Timed out: $what") }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Failed: $what", e)
        false
    }

    private companion object {
        const val PUSH_DEBOUNCE_MS = 500L
        const val REMOTE_TIMEOUT_MS = 15_000L
        const val LISTENER_RETRY_MS = 30_000L
    }
}

/** One-shot things the UI may want to tell the user about. */
sealed interface SyncEvent {
    data class NewIncomingClaim(val claimId: String, val foundItemId: String, val claimantName: String) : SyncEvent
}
