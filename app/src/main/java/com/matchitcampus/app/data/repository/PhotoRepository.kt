package com.matchitcampus.app.data.repository

import android.net.Uri
import android.util.Log
import com.matchitcampus.app.data.local.dao.ItemDao
import com.matchitcampus.app.data.photo.PhotoStore
import com.matchitcampus.app.data.prefs.SyncPreferences
import com.matchitcampus.app.data.remote.PhotoRemoteDataSource
import com.matchitcampus.app.domain.model.Item
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "MatchIt/PhotoRepository"

/**
 * Item photos: local files ([PhotoStore]) plus the copy in Firestore ([PhotoRemoteDataSource]).
 *
 * **Upload queue.** A photo can only be uploaded after its item document exists in Firestore
 * (the security rules check that), and uploading it again on every status change would waste
 * bandwidth and writes. So photos to upload are kept as a set of item ids in DataStore
 * ([pendingUploads]), separately from the item's own sync state. `SyncManager` uploads them
 * once their item is synced, and removes them from the set. DataStore survives restarts, so a
 * photo taken offline is still uploaded later.
 *
 * **Download on demand.** Other people's photos are downloaded only when their item is opened
 * ([ensureLocalCopy]), never for the feed, to keep Firestore reads (Spark quota) low.
 */
class PhotoRepository(
    private val photoStore: PhotoStore,
    private val photoRemote: PhotoRemoteDataSource,
    private val itemDao: ItemDao,
    private val syncPreferences: SyncPreferences,
) {

    /** Ids of items whose photo still has to be uploaded. */
    val pendingUploads: Flow<Set<String>> = syncPreferences.pendingPhotoUploads

    /** See [PhotoStore.createDraft]. */
    suspend fun createDraft(uri: Uri): String = photoStore.createDraft(uri)

    suspend fun discardDraft(path: String) = photoStore.deleteFile(path)

    /** Makes a draft the item's photo file. Call before saving the item with the returned path. */
    suspend fun commitDraft(draftPath: String, itemId: String): String = photoStore.commitDraft(draftPath, itemId)

    /**
     * Queues the item's photo for upload. Call **after** the item is saved, so `SyncManager`
     * never sees a queued photo for an item that isn't in Room yet.
     */
    suspend fun queueUpload(itemId: String) = syncPreferences.addPendingPhoto(itemId)

    /** Drops an item's photo from the upload queue (it was removed, or the item was deleted). */
    suspend fun unqueue(itemId: String) = syncPreferences.removePendingPhoto(itemId)

    /** The owner removed the photo while editing: delete the local files and any queued upload. */
    suspend fun removeLocalPhoto(itemId: String) {
        photoStore.deleteAllFor(itemId)
        unqueue(itemId)
    }

    /**
     * Uploads one queued photo, then takes it off the queue. Network errors are thrown, so
     * `SyncManager` can log them and keep the photo queued for the next try.
     */
    suspend fun upload(itemId: String, ownerId: String, localPath: String) {
        val jpeg = photoStore.read(localPath)
        if (jpeg == null) {
            Log.w(TAG, "Photo file for item $itemId is gone; nothing to upload")
        } else {
            photoRemote.upload(itemId, ownerId, jpeg)
            Log.d(TAG, "Uploaded photo for item $itemId (${jpeg.size / 1024} KB)")
        }
        unqueue(itemId)
    }

    /**
     * After the owner's item delete has reached Firestore: delete the photo document too
     * (best effort: a failure is only logged, the photo is then just orphaned) and the local files.
     */
    suspend fun deleteEverywhere(itemId: String) {
        try {
            photoRemote.delete(itemId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // For example the photo was never uploaded, so the rules have no document to check.
            Log.w(TAG, "Couldn't delete the remote photo of item $itemId", e)
        }
        removeLocalPhoto(itemId)
    }

    /**
     * Makes sure this phone has [item]'s photo, downloading it if needed. On success the new
     * path is written to Room, and every screen showing the item updates by itself.
     *
     * @return true if the photo is available locally (or the item has none), false if the
     *   download failed (offline, or no photo document yet).
     */
    suspend fun ensureLocalCopy(item: Item): Boolean {
        if (!item.hasPhoto) return true
        val path = item.localPhotoPath
        if (path != null && photoStore.exists(path)) return true

        val jpeg = try {
            withTimeoutOrNull(DOWNLOAD_TIMEOUT_MS) { photoRemote.download(item.id) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Photo download failed for item ${item.id}", e)
            null
        }
        if (jpeg == null) {
            Log.w(TAG, "No photo available for item ${item.id} yet")
            return false
        }
        val newPath = photoStore.saveDownloaded(item.id, jpeg)
        itemDao.updateLocalPhotoPath(item.id, newPath)
        Log.d(TAG, "Downloaded photo for item ${item.id} (${jpeg.size / 1024} KB)")
        return true
    }

    private companion object {
        const val DOWNLOAD_TIMEOUT_MS = 15_000L
    }
}
