package com.matchitcampus.app.domain.matching

/**
 * Word-overlap similarity: the fallback when an item has no embedding (for example if the
 * model couldn't be loaded on this phone).
 *
 * It's much weaker than embeddings: "earbuds" and "earphones" share no word, so they score 0
 * here. That's why results computed with it are flagged `usedFallback`.
 */
object KeywordSimilarity {

    /**
     * Jaccard similarity of the two texts' word sets: shared words / all distinct words.
     * 1.0 means the same words, 0.0 means none in common. Two texts with no meaningful words
     * score 0, because there's no evidence they match.
     */
    fun jaccard(a: String, b: String): Double {
        val tokensA = TextNormalizer.tokens(a)
        val tokensB = TextNormalizer.tokens(b)
        if (tokensA.isEmpty() || tokensB.isEmpty()) return 0.0
        val shared = tokensA.intersect(tokensB).size
        val all = tokensA.union(tokensB).size
        return shared.toDouble() / all
    }

    /**
     * How much of a search [query] appears in [text]: the share of the query's words found in
     * the text. Used for search instead of [jaccard], because a two-word query against a long
     * description would always get a tiny Jaccard score even when both words are present.
     */
    fun queryCoverage(query: String, text: String): Double {
        val queryTokens = TextNormalizer.tokens(query)
        if (queryTokens.isEmpty()) return 0.0
        val textTokens = TextNormalizer.tokens(text)
        return queryTokens.count { it in textTokens }.toDouble() / queryTokens.size
    }
}
