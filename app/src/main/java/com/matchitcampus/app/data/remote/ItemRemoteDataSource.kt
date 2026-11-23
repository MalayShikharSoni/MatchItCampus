package com.matchitcampus.app.data.remote

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.matchitcampus.app.data.remote.dto.ItemDto
import com.matchitcampus.app.data.remote.mapper.Fields
import com.matchitcampus.app.data.remote.mapper.itemDtoFromMap
import com.matchitcampus.app.data.remote.mapper.toFirestoreMap
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Date

private const val TAG = "MatchIt/ItemRemote"

/**
 * Reads and writes the `items` collection in Firestore.
 *
 * Only `SyncManager` uses this class. The UI never reads Firestore: remote changes reach
 * the screens by being written into Room first.
 *
 * **Quota awareness (Spark plan: 50,000 reads and 20,000 writes per day).** Each document a
 * query returns costs one read. So we never re-download the whole collection. Instead we
 * only ask for documents whose server `updatedAt` is newer than the last one we saw
 * ([observeChangesSince]), and in pages of at most [PAGE_SIZE].
 */
class ItemRemoteDataSource(private val firestore: FirebaseFirestore) {

    private val items get() = firestore.collection(COLLECTION)

    /**
     * Uploads the whole item, creating or replacing `items/{id}`.
     *
     * `updatedAt` is set to `FieldValue.serverTimestamp()`, so Firestore fills in its own
     * clock when the write arrives. The security rules check that it really is the server time.
     */
    suspend fun upsert(item: ItemDto) {
        val data = item.toFirestoreMap() + (Fields.UPDATED_AT to FieldValue.serverTimestamp())
        items.document(item.id).set(data).await()
    }

    /**
     * Marks an item deleted (a "tombstone") instead of deleting the document, so other phones
     * that already have the item find out about the deletion through their usual
     * "changed since" query.
     *
     * @return false if the document doesn't exist, meaning the item was never uploaded and
     *   there's nothing to delete remotely.
     */
    suspend fun markDeleted(id: String, updatedAtMillis: Long): Boolean {
        return try {
            items.document(id).update(
                mapOf(
                    Fields.DELETED to true,
                    Fields.UPDATED_AT_MILLIS to updatedAtMillis,
                    Fields.UPDATED_AT to FieldValue.serverTimestamp(),
                ),
            ).await()
            true
        } catch (e: FirebaseFirestoreException) {
            // update() fails with NOT_FOUND when the document isn't there. Any other error
            // (no permission, network) is a real failure, so it's passed on.
            if (e.code == FirebaseFirestoreException.Code.NOT_FOUND) false else throw e
        }
    }

    /**
     * A live listener for items changed after [sinceServerMillis], oldest change first, at
     * most [PAGE_SIZE] at a time.
     *
     * `callbackFlow` turns Firestore's callback listener into a Flow. The listener is
     * attached when the Flow is collected and removed in `awaitClose` when collection stops,
     * so a cancelled coroutine can never leave a listener running (and costing reads).
     *
     * If a page comes back full, there may be more changes than fit in one page. The caller
     * then collects again from the newest timestamp it has seen (see `SyncManager`).
     */
    fun observeChangesSince(sinceServerMillis: Long): Flow<RemoteItemPage> = callbackFlow {
        val registration = changesSinceQuery(sinceServerMillis).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Item listener failed", error)
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) trySend(snapshot.toPage())
        }
        Log.d(TAG, "Item listener attached (changes after $sinceServerMillis)")
        awaitClose {
            registration.remove()
            Log.d(TAG, "Item listener removed")
        }
    }

    /** The same query as [observeChangesSince], read once (used by "sync now"). */
    suspend fun fetchChangesSince(sinceServerMillis: Long): RemoteItemPage =
        changesSinceQuery(sinceServerMillis).get().await().toPage()

    private fun changesSinceQuery(sinceServerMillis: Long): Query =
        items
            .whereGreaterThan(Fields.UPDATED_AT, Timestamp(Date(sinceServerMillis)))
            .orderBy(Fields.UPDATED_AT, Query.Direction.ASCENDING)
            .limit(PAGE_SIZE)

    private fun QuerySnapshot.toPage(): RemoteItemPage {
        val parsed = documents.mapNotNull { it.toItemDtoOrNull() }
        // Only documents the server has confirmed count towards "last synced". Our own
        // writes show up here first with only an estimated timestamp.
        val newestConfirmed = documents
            .filterNot { it.metadata.hasPendingWrites() }
            .mapNotNull { it.getTimestamp(Fields.UPDATED_AT)?.toDate()?.time }
            .maxOrNull()
        return RemoteItemPage(
            items = parsed,
            isFullPage = size() >= PAGE_SIZE,
            newestServerMillis = newestConfirmed,
        )
    }

    private fun DocumentSnapshot.toItemDtoOrNull(): ItemDto? {
        val data = data ?: return null
        // ESTIMATE: for our own not-yet-confirmed writes, use the local estimate instead of null.
        val serverMillis = getTimestamp(Fields.UPDATED_AT, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            ?.toDate()?.time
        return itemDtoFromMap(id, data, serverMillis)
            ?: null.also { Log.w(TAG, "Skipping malformed item document $id") }
    }

    companion object {
        const val COLLECTION = "items"

        /** Most documents one query returns. Bounds the reads a single sync can cost. */
        const val PAGE_SIZE = 200L
    }
}

/**
 * One delivery from the items listener.
 *
 * @property isFullPage true if the query hit [ItemRemoteDataSource.PAGE_SIZE], so more
 *   changes may be waiting.
 * @property newestServerMillis the newest server-confirmed `updatedAt` in this page; the
 *   next sync starts after it. Null if the page has no confirmed documents.
 */
data class RemoteItemPage(
    val items: List<ItemDto>,
    val isFullPage: Boolean,
    val newestServerMillis: Long?,
)
