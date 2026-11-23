package com.matchitcampus.app.domain.matching

import org.junit.Assert.assertEquals
import org.junit.Test

class VectorMathTest {

    @Test
    fun identicalVectors_scoreOne() {
        val v = floatArrayOf(0.3f, -0.4f, 0.5f)
        assertEquals(1.0, VectorMath.cosine(v, v), 1e-6)
    }

    @Test
    fun sameDirectionDifferentLength_scoreOne() {
        assertEquals(1.0, VectorMath.cosine(floatArrayOf(1f, 2f), floatArrayOf(2f, 4f)), 1e-6)
    }

    @Test
    fun orthogonalVectors_scoreZero() {
        assertEquals(0.0, VectorMath.cosine(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)), 1e-6)
    }

    @Test
    fun oppositeVectors_scoreMinusOne() {
        assertEquals(-1.0, VectorMath.cosine(floatArrayOf(1f, 2f), floatArrayOf(-1f, -2f)), 1e-6)
    }

    @Test
    fun zeroVector_scoresZeroInsteadOfNaN() {
        assertEquals(0.0, VectorMath.cosine(floatArrayOf(0f, 0f), floatArrayOf(1f, 1f)), 0.0)
        assertEquals(0.0, VectorMath.cosine(floatArrayOf(0f, 0f), floatArrayOf(0f, 0f)), 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun differentSizes_throw() {
        VectorMath.cosine(floatArrayOf(1f), floatArrayOf(1f, 2f))
    }
}
