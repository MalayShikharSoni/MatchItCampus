package com.matchitcampus.app.data.repository

import android.util.Log
import com.matchitcampus.app.data.local.dao.ItemDao
import com.matchitcampus.app.data.local.mapper.toDomain
import com.matchitcampus.app.data.local.mapper.toEntity
import com.matchitcampus.app.domain.model.Item
import com.matchitcampus.app.domain.model.ItemStatus
import com.matchitcampus.app.domain.model.ItemType
import com.matchitcampus.app.domain.model.SyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val TAG = "MatchIt/ItemRepository"

/**
 * The one place the rest of the app goes for lost and found items.
 *
 * **Repository pattern:** ViewModels call this class and never touch the DAO or (from Phase 3)
 * Firestore directly. It hides where data comes from and hands out domain [Item]s, not
 * Room entities.
 *
 * **Single source of truth:** every read is a Flow from Room. Writes go to Room first and are
 * marked [SyncState.PENDING_UPLOAD] or [SyncState.PENDING_DELETE]. In Phase 3, `SyncManager`
 * pushes those rows to Firestore and marks them SYNCED. Because the UI observes Room, it
 * shows a change immediately, even offline.
 *
 * @param clock returns the current time in epoch millis. Passed in so tests can fix the time.
 */
class ItemRepository(
    private val itemDao: ItemDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** One feed tab (Lost or Found), newest first. */
    fun observeFeed(type: ItemType): Flow<List<Item>> =
        itemDao.observeByType(type).map { rows -> rows.map { it.toDomain() } }

    /** One item, or null once it's gone or deleted. */
    fun observeItem(id: String): Flow<Item?> =
        itemDao.observeById(id).map { it?.toDomain() }

    fun observeMyItems(ownerId: String): Flow<List<Item>> =
        itemDao.observeByOwner(ownerId).map { rows -> rows.map { it.toDomain() } }

    /** Open items of both types, for the map. */
    fun observeOpenItems(): Flow<List<Item>> =
        itemDao.observeOpen().map { rows -> rows.map { it.toDomain() } }

    /** Number of items with changes not yet in Firestore (for the logout warning). */
    fun observeUnsyncedCount(): Flow<Int> = itemDao.observeUnsyncedCount()

    /**
     * Creates or updates an item.
     *
     * The caller supplies the id (a new UUID for a new post). That lets the post screen name
     * the photo file after the item before the item is saved.
     *
     * The stored embedding is cleared, because a create or edit may change the text it was
     * computed from. Phase 4 recomputes missing embeddings.
     */
    suspend fun saveItem(item: Item) {
        val toSave = item.copy(updatedAt = clock(), syncState = SyncState.PENDING_UPLOAD)
        itemDao.upsert(toSave.toEntity())
        Log.d(TAG, "Saved item ${item.id} (${item.type}) as PENDING_UPLOAD")
    }

    /**
     * Marks an item returned by hand (from "My posts").
     *
     * @return false if no item has this id.
     */
    suspend fun markReturned(id: String): Boolean = updateStatus(id, ItemStatus.RETURNED)

    /**
     * Changes an item's status. Only the status columns are written, so the embedding and the
     * rest of the row are left alone.
     *
     * @return false if no item has this id.
     */
    suspend fun updateStatus(id: String, status: ItemStatus): Boolean {
        val changed = itemDao.updateStatus(id, status, SyncState.PENDING_UPLOAD, clock()) > 0
        if (changed) Log.d(TAG, "Item $id is now $status") else Log.w(TAG, "updateStatus: no item $id")
        return changed
    }

    /**
     * Deletes an item. The row is kept as [SyncState.PENDING_DELETE] (hidden from every screen)
     * until Phase 3's SyncManager has deleted it from Firestore, and only then removed. If the
     * row were removed now, an offline delete would be lost, and the next sync would bring the
     * item back.
     *
     * @return false if no item has this id.
     */
    suspend fun deleteItem(id: String): Boolean {
        val changed = itemDao.updateSyncState(id, SyncState.PENDING_DELETE, clock()) > 0
        if (changed) Log.d(TAG, "Item $id marked PENDING_DELETE") else Log.w(TAG, "deleteItem: no item $id")
        return changed
    }
}
