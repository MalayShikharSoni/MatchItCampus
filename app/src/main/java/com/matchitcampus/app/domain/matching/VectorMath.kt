package com.matchitcampus.app.domain.matching

import kotlin.math.sqrt

/**
 * Vector maths for embeddings, in plain Kotlin.
 *
 * MediaPipe has its own `TextEmbedder.cosineSimilarity`, but calling it would tie the domain
 * code to an Android library, so it couldn't be unit-tested on the JVM. The formula is short
 * enough to write and test ourselves.
 */
object VectorMath {

    /**
     * Cosine similarity: `a·b / (|a| |b|)`, the cosine of the angle between the two vectors.
     * 1 means same direction (same meaning), 0 unrelated, -1 opposite.
     *
     * The embedder returns L2-normalised vectors (length 1), so this is really just the dot
     * product, but dividing by the lengths keeps it correct for any input.
     *
     * @return 0.0 if either vector is all zeros, since the angle is undefined (and dividing by 0
     *   would give NaN, which would poison every score it touches).
     * @throws IllegalArgumentException if the sizes differ (vectors from different models).
     */
    fun cosine(a: FloatArray, b: FloatArray): Double {
        require(a.size == b.size) { "Vectors have different sizes: ${a.size} and ${b.size}" }
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        if (normA == 0.0 || normB == 0.0) return 0.0
        return dot / (sqrt(normA) * sqrt(normB))
    }
}
