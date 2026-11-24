package com.matchitcampus.app.data.photo

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID

private const val TAG = "MatchIt/PhotoStore"

/** The picked image is still too big after the strongest compression we allow. */
class PhotoTooLargeException(sizeBytes: Int) : IOException("Photo is $sizeBytes bytes after compression")

/**
 * Item photos on this phone: compressing a picked image, and the files in `filesDir/photos/`.
 *
 * **Files.** A photo is saved as `photos/{itemId}_{time}.jpg`. The time in the name changes
 * whenever the photo is replaced, so the path stored in Room (`localPhotoPath`) changes too, and
 * Coil can't keep showing an old picture from its memory cache (Coil 3 keys file images by
 * path only). Older files of the same item are deleted when a new one is saved.
 *
 * **Drafts.** While the Post screen is open, the compressed photo lives in `photos/drafts/`.
 * Its path is kept in the screen's `SavedStateHandle`, so the photo survives rotation and even
 * the app being killed in the background. Posting moves the draft to the item's photo file.
 *
 * `filesDir` is private to the app, so no storage permission is needed, and Android removes
 * the files when the app is uninstalled.
 */
class PhotoStore(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val contentResolver: ContentResolver = context.applicationContext.contentResolver
    private val photosDir = File(context.applicationContext.filesDir, "photos")
    private val draftsDir = File(photosDir, "drafts")

    /**
     * Shrinks and compresses the image at [uri] (from the Photo Picker) and saves it as a draft.
     *
     * Decoding and compressing are CPU work, so they run on `Dispatchers.Default`; writing the
     * file is I/O, so it runs on `Dispatchers.IO`.
     *
     * @return the draft file's path.
     * @throws PhotoTooLargeException if even the lowest quality is over [PhotoSizing.MAX_BYTES].
     * @throws IOException if the image can't be read or decoded.
     */
    suspend fun createDraft(uri: Uri): String {
        val jpeg = withContext(Dispatchers.Default) { compress(uri) }
        return withContext(Dispatchers.IO) {
            draftsDir.mkdirs()
            val file = File(draftsDir, "${UUID.randomUUID()}.jpg")
            file.writeBytes(jpeg)
            Log.d(TAG, "Draft photo saved: ${jpeg.size / 1024} KB")
            file.absolutePath
        }
    }

    /**
     * Moves a draft to become [itemId]'s photo, and deletes the item's older photo files.
     *
     * @return the new photo's path, to store as the item's `localPhotoPath`.
     */
    suspend fun commitDraft(draftPath: String, itemId: String): String = withContext(Dispatchers.IO) {
        val draft = File(draftPath)
        if (!draft.exists()) throw IOException("Draft photo $draftPath is gone")
        val target = newPhotoFile(itemId)
        // Both files are in filesDir, so renameTo is a cheap move. Copy as a fallback.
        if (!draft.renameTo(target)) {
            draft.copyTo(target, overwrite = true)
            draft.delete()
        }
        deleteOtherPhotosOf(itemId, keep = target)
        target.absolutePath
    }

    /** Saves a photo downloaded from Firestore. @return its path. */
    suspend fun saveDownloaded(itemId: String, jpeg: ByteArray): String = withContext(Dispatchers.IO) {
        val target = newPhotoFile(itemId)
        target.writeBytes(jpeg)
        deleteOtherPhotosOf(itemId, keep = target)
        target.absolutePath
    }

    /** The bytes of a photo file, or null if it no longer exists. */
    suspend fun read(path: String): ByteArray? = withContext(Dispatchers.IO) {
        File(path).takeIf { it.exists() }?.readBytes()
    }

    suspend fun exists(path: String): Boolean = withContext(Dispatchers.IO) { File(path).exists() }

    /** Deletes one file (e.g. an abandoned draft). Missing files are fine. */
    suspend fun deleteFile(path: String) {
        withContext(Dispatchers.IO) { File(path).delete() }
    }

    /** Deletes every photo file of an item. */
    suspend fun deleteAllFor(itemId: String) {
        withContext(Dispatchers.IO) { deleteOtherPhotosOf(itemId, keep = null) }
    }

    private fun newPhotoFile(itemId: String): File {
        photosDir.mkdirs()
        return File(photosDir, "${itemId}_${clock()}.jpg")
    }

    private fun deleteOtherPhotosOf(itemId: String, keep: File?) {
        photosDir.listFiles { file -> file.isFile && file.name.startsWith("${itemId}_") && file != keep }
            ?.forEach { it.delete() }
    }

    /**
     * Decodes at roughly the final size, then tries JPEG qualities from [PhotoSizing.QUALITY_STEPS]
     * until the result is under the target size.
     */
    private fun compress(uri: Uri): ByteArray {
        val bitmap = decodeScaled(uri)
        try {
            var jpeg = ByteArray(0)
            for (quality in PhotoSizing.QUALITY_STEPS) {
                val out = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                jpeg = out.toByteArray()
                if (jpeg.size <= PhotoSizing.TARGET_BYTES) break
            }
            if (jpeg.size > PhotoSizing.MAX_BYTES) throw PhotoTooLargeException(jpeg.size)
            return jpeg
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Android 9+ (API 28): `ImageDecoder` decodes straight to the target size and applies the
     * photo's EXIF rotation, so portrait photos stay upright. The software allocator is needed
     * because a hardware bitmap can't be compressed to JPEG.
     *
     * Android 8 (API 26–27): `BitmapFactory` reads the size first, decodes with `inSampleSize`,
     * then scales. It doesn't apply EXIF rotation, so a few photos may appear sideways there
     * (a known limitation, see HANDOFF).
     */
    private fun decodeScaled(uri: Uri): Bitmap =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { decoder, info, _ ->
                val (width, height) = PhotoSizing.scaledSize(info.size.width, info.size.height)
                decoder.setTargetSize(width, height)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            decodeScaledLegacy(uri)
        }

    private fun decodeScaledLegacy(uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Not an image: $uri")

        val options = BitmapFactory.Options().apply { inSampleSize = PhotoSizing.sampleSize(bounds.outWidth, bounds.outHeight) }
        val sampled = openStream(uri).use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("Couldn't decode $uri")
        val (width, height) = PhotoSizing.scaledSize(sampled.width, sampled.height)
        if (width == sampled.width && height == sampled.height) return sampled
        return Bitmap.createScaledBitmap(sampled, width, height, true).also { sampled.recycle() }
    }

    private fun openStream(uri: Uri) =
        contentResolver.openInputStream(uri) ?: throw IOException("Can't open $uri")
}
