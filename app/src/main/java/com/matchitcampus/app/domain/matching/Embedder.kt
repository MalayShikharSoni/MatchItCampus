package com.matchitcampus.app.domain.matching

/**
 * Something that turns text into an embedding (a vector that captures its meaning).
 *
 * The real one is `ml.TextEmbedderHelper` (MediaPipe). It's behind this small interface so
 * that `MatchRepository` doesn't depend on MediaPipe, and tests can pass a fake that returns
 * fixed vectors without loading a model.
 */
interface Embedder {

    /**
     * Identifies the model that produced a vector. Stored next to each embedding, so that if
     * the bundled model is ever replaced (and this number raised), old vectors, which aren't
     * comparable with new ones, are recomputed.
     */
    val version: Int

    /** The embedding of [text], or null if it couldn't be computed (the error is logged). */
    suspend fun embed(text: String): FloatArray?
}
