package com.matchitcampus.app.ml

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import com.matchitcampus.app.domain.matching.Embedder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val TAG = "MatchIt/TextEmbedderHelper"

/**
 * Runs the bundled Universal Sentence Encoder model with MediaPipe's Text Embedder.
 *
 * **The only class that imports MediaPipe.** Everything else sees the small [Embedder]
 * interface, so the matching code stays plain Kotlin and is tested on the JVM.
 *
 * - **Lazy:** the model (about 6 MB, `assets/universal_sentence_encoder.tflite`) is loaded
 *   on the first [embed] call, not at app start, so it doesn't slow down launching.
 * - **L2 normalisation on:** every vector comes back with length 1, so cosine similarity is a
 *   plain dot product and vectors from different texts are directly comparable.
 * - **Quantisation off:** we keep full 32-bit floats. Quantised (8-bit) vectors are smaller but
 *   less precise, and ours are only 100 floats (400 bytes) per item anyway.
 * - **Off the main thread:** running the model takes a few milliseconds of CPU, so it runs on
 *   [Dispatchers.Default], the dispatcher meant for CPU work.
 * - **One call at a time:** a [Mutex] serialises calls. MediaPipe doesn't promise that one
 *   embedder can be used from several threads at once, and matching never needs to.
 *
 * If the model can't be loaded (a missing or broken file), the failure is logged once and
 * every call returns null. Matching then uses its keyword fallback instead of crashing.
 */
class TextEmbedderHelper(private val context: Context) : Embedder {

    override val version: Int = MODEL_VERSION

    private val mutex = Mutex()
    private var embedder: TextEmbedder? = null
    private var loadFailed = false

    override suspend fun embed(text: String): FloatArray? {
        if (text.isBlank()) return null
        return withContext(Dispatchers.Default) {
            mutex.withLock {
                val model = getOrCreate() ?: return@withLock null
                try {
                    model.embed(text).embeddingResult().embeddings().firstOrNull()?.floatEmbedding()
                } catch (e: Exception) {
                    Log.e(TAG, "Embedding failed for \"${text.take(40)}\"", e)
                    null
                }
            }
        }
    }

    /** Frees the model's native memory. Not needed in the app (it lives as long as the process), but tests call it. */
    suspend fun close() = mutex.withLock {
        embedder?.close()
        embedder = null
    }

    /** Called with [mutex] held. */
    private fun getOrCreate(): TextEmbedder? {
        embedder?.let { return it }
        if (loadFailed) return null
        return try {
            val options = TextEmbedder.TextEmbedderOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL_ASSET).build())
                .setL2Normalize(true)
                .setQuantize(false)
                .build()
            TextEmbedder.createFromOptions(context, options).also {
                embedder = it
                Log.d(TAG, "Loaded $MODEL_ASSET")
            }
        } catch (e: Exception) {
            loadFailed = true
            Log.e(TAG, "Could not load $MODEL_ASSET; matching will use keyword similarity", e)
            null
        }
    }

    companion object {
        const val MODEL_ASSET = "universal_sentence_encoder.tflite"
        /** Raise this if [MODEL_ASSET] is replaced: stored embeddings are then recomputed. */
        const val MODEL_VERSION = 1
    }
}
