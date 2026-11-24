package com.matchitcampus.app.data.photo

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The size rules for item photos, as plain arithmetic (unit-tested in `PhotoSizingTest`).
 *
 * Photos are stored in Firestore as a Blob inside a document, and a Firestore document can't
 * be bigger than 1 MiB. Phone cameras take 3–10 MB pictures, so every photo is shrunk to at
 * most [MAX_SIDE_PX] on its longest side and saved as a JPEG, aiming for under [TARGET_BYTES].
 * At 800 px a photo still shows the details that identify an item (a sticker, a scratch).
 */
object PhotoSizing {

    const val MAX_SIDE_PX = 800

    /** What we aim for: small enough that downloading a photo is quick on mobile data. */
    const val TARGET_BYTES = 150 * 1024

    /** Hard limit, comfortably below Firestore's 1 MiB document limit. The rules check it too. */
    const val MAX_BYTES = 900 * 1024

    /** JPEG qualities tried in order until the file is under [TARGET_BYTES]. */
    val QUALITY_STEPS = listOf(70, 60, 50, 40)

    /**
     * The width and height that fit inside [maxSide] × [maxSide] with the same shape.
     * Never enlarges a photo that is already small enough.
     */
    fun scaledSize(width: Int, height: Int, maxSide: Int = MAX_SIDE_PX): Pair<Int, Int> {
        require(width > 0 && height > 0) { "Image size must be positive, was ${width}x$height" }
        val longest = max(width, height)
        if (longest <= maxSide) return width to height
        val scale = maxSide.toDouble() / longest
        return (width * scale).roundToInt().coerceAtLeast(1) to (height * scale).roundToInt().coerceAtLeast(1)
    }

    /**
     * For the old decoder on Android 8 (API 26–27): `BitmapFactory` can skip pixels while
     * decoding, but only by powers of two (`inSampleSize`). This returns the largest power of two
     * that still leaves the longest side at least [maxSide], so the big original is never fully
     * loaded into memory, and the final resize only has to shrink a little more.
     */
    fun sampleSize(width: Int, height: Int, maxSide: Int = MAX_SIDE_PX): Int {
        val longest = max(width, height)
        var sample = 1
        while (longest / (sample * 2) >= maxSide) sample *= 2
        return sample
    }
}
