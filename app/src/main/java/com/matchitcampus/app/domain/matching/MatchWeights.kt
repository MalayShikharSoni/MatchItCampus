package com.matchitcampus.app.domain.matching

/**
 * The tuning knobs of matching, kept in one place so they're easy to find, explain and change.
 *
 * `score = text·textScore + category·categoryScore + location·locationScore + date·dateScore`
 *
 * **How the weights were chosen.** They add up to 1, so the total stays in 0..1.
 * - **Text 0.60.** The description is the only thing that tells *what* the item is. A black
 *   JBL case and a steel bottle can be in the same place on the same day; only the text
 *   separates them. So it gets more than all the others together.
 * - **Category 0.15** and **location 0.15.** Strong hints, but people pick different categories
 *   for the same thing (earbuds as Electronics or Accessories) and guess where they lost
 *   something, so neither may outweigh the text.
 * - **Date 0.10.** The hard filter already drops pairs more than 30 days apart, so within that
 *   window the date only nudges the ranking.
 *
 * **No score threshold; the best [maxResults] are shown instead.** The plan was to hide matches
 * below a fixed score. Measured on the real model (Phase 4), the Universal Sentence Encoder's
 * cosine values are high for *every* pair of short item descriptions: about 0.89 to 0.99 for
 * true pairs, but also 0.56 to 0.90 (average 0.73) for unrelated ones. So no fixed cut-off
 * separates them: unrelated pairs would pass, or true ones in a different category would be
 * dropped. The *ranking*, though, was right every time (the true match ranked first for all 10
 * test items). So the app trusts the order, not the absolute number, and shows the top few.
 *
 * These are reasoned starting values, not trained ones. Changing them only needs this file,
 * and `MatchScorerTest` shows what each one does.
 */
data class MatchWeights(
    val text: Double = 0.60,
    val category: Double = 0.15,
    val location: Double = 0.15,
    val date: Double = 0.10,
    /** How many matches an item shows: its best-scoring candidates. */
    val maxResults: Int = 5,
) {
    companion object {
        val DEFAULT = MatchWeights()
    }
}
