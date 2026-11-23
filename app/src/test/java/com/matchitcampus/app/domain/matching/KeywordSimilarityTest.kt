package com.matchitcampus.app.domain.matching

import org.junit.Assert.assertEquals
import org.junit.Test

class KeywordSimilarityTest {

    @Test
    fun jaccard_sameWords_isOne() {
        assertEquals(1.0, KeywordSimilarity.jaccard("Black wallet", "wallet, BLACK"), 0.0)
    }

    @Test
    fun jaccard_isSharedOverAllDistinctWords() {
        // {black, wallet} vs {brown, wallet}: 1 shared of 3 distinct.
        assertEquals(1.0 / 3, KeywordSimilarity.jaccard("black wallet", "brown wallet"), 1e-9)
    }

    @Test
    fun jaccard_ignoresStopWords() {
        // Without stop-word removal "my", "in", "the" would make these look related.
        assertEquals(0.0, KeywordSimilarity.jaccard("my keys in the hostel", "my bottle in the library"), 0.0)
    }

    @Test
    fun jaccard_differentWordsForTheSameThing_scoreZero() {
        // This is exactly why embeddings are the main signal and keywords only the fallback.
        assertEquals(0.0, KeywordSimilarity.jaccard("earbuds", "earphones"), 0.0)
    }

    @Test
    fun jaccard_emptyText_isZero() {
        assertEquals(0.0, KeywordSimilarity.jaccard("", "wallet"), 0.0)
        assertEquals(0.0, KeywordSimilarity.jaccard("the a of", "the a of"), 0.0)
    }

    @Test
    fun queryCoverage_isShareOfQueryWordsFound() {
        assertEquals(1.0, KeywordSimilarity.queryCoverage("JBL case", "black jbl earbuds case with scratch"), 0.0)
        assertEquals(0.5, KeywordSimilarity.queryCoverage("JBL bottle", "black jbl earbuds case"), 0.0)
        assertEquals(0.0, KeywordSimilarity.queryCoverage("the", "the case"), 0.0)
    }
}
