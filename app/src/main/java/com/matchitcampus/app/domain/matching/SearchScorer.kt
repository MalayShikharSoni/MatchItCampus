package com.matchitcampus.app.domain.matching

/** One search result: an item id and how relevant it is to the query (higher is better). */
data class SearchHit(val itemId: String, val relevance: Double)

/**
 * Ranks items against a search query typed in the feed.
 *
 * Relevance = meaning + exact-text bonus:
 * - **meaning**: cosine similarity between the query's embedding and the item's, clamped to
 *   0..1, so "earphones" finds "JBL earbuds". Without embeddings, the share of the query's
 *   words that appear in the item ([KeywordSimilarity.queryCoverage]).
 * - **exact-text bonus** ([SUBSTRING_BOOST]) when the item's text contains the query as typed.
 *   Embeddings are fuzzy, and a user who types "JBL" expects JBL items first.
 *
 * Every item is returned, most relevant first; nothing is cut off. The real model's cosine
 * values are high even for unrelated texts (see `MatchWeights`), so a fixed minimum would
 * either hide real results or keep everything anyway. The order is what's reliable.
 *
 * The flow side (debounce, mapLatest) belongs to the feed's ViewModel (Phase 5); this class
 * is the pure, testable part.
 */
object SearchScorer {

    /** Added when the item's text contains the query word for word. */
    const val SUBSTRING_BOOST = 0.3

    /** Relevance of one item. [itemText] should come from [TextNormalizer.forEmbedding]. */
    fun relevance(query: String, queryEmbedding: FloatArray?, itemText: String, itemEmbedding: FloatArray?): Double {
        val meaning = if (queryEmbedding != null && itemEmbedding != null && queryEmbedding.size == itemEmbedding.size) {
            VectorMath.cosine(queryEmbedding, itemEmbedding).coerceIn(0.0, 1.0)
        } else {
            KeywordSimilarity.queryCoverage(query, itemText)
        }
        val normalizedQuery = TextNormalizer.normalize(query)
        val boost = if (normalizedQuery.isNotEmpty() && normalizedQuery in TextNormalizer.normalize(itemText)) {
            SUBSTRING_BOOST
        } else {
            0.0
        }
        return meaning + boost
    }

    /** Every item, most relevant to [query] first. */
    fun rank(query: String, queryEmbedding: FloatArray?, items: List<ItemForMatching>): List<SearchHit> =
        items
            .map { SearchHit(it.item.id, relevance(query, queryEmbedding, TextNormalizer.forEmbedding(it.item), it.embedding)) }
            .sortedByDescending { it.relevance }
}
