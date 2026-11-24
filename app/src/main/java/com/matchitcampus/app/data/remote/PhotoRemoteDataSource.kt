package com.matchitcampus.app.data.remote

import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.matchitcampus.app.data.remote.mapper.Fields
import kotlinx.coroutines.tasks.await

/**
 * Reads and writes `itemPhotos/{itemId}`: one compressed JPEG per item, stored as a Firestore
 * Blob (bytes inside the document).
 *
 * **Why Firestore and not Cloud Storage:** Cloud Storage for Firebase needs the paid Blaze plan.
 * A photo shrunk to ~150 KB fits easily in a Firestore document (limit 1 MiB).
 *
 * **Why a separate collection:** the feed's item queries must never download image bytes.
 * Keeping photos apart means a photo is only read (one document read) when someone opens the
 * item.
 *
 * The security rules only allow the item's owner to write the photo, and only once the item
 * document exists, so `SyncManager` uploads the item first and the photo after it.
 */
class PhotoRemoteDataSource(private val firestore: FirebaseFirestore) {

    private val photos get() = firestore.collection(COLLECTION)

    /** Creates or replaces the photo. `updatedAt` is the server time (the rules check it). */
    suspend fun upload(itemId: String, ownerId: String, jpeg: ByteArray) {
        photos.document(itemId).set(
            mapOf(
                Fields.OWNER_ID to ownerId,
                DATA to Blob.fromBytes(jpeg),
                Fields.UPDATED_AT to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    /** The photo's bytes, or null if there's no photo document. */
    suspend fun download(itemId: String): ByteArray? =
        photos.document(itemId).get().await().getBlob(DATA)?.toBytes()

    suspend fun delete(itemId: String) {
        photos.document(itemId).delete().await()
    }

    private companion object {
        const val COLLECTION = "itemPhotos"
        const val DATA = "data"
    }
}
