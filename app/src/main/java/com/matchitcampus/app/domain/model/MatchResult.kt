package com.matchitcampus.app.domain.model

/**
 * How well one item matches another, with the full breakdown behind the score.
 *
 * The breakdown is the point: the detail screen shows "Why this match" (text similarity 82%,
 * same category, about 150 m apart, 1 day apart) instead of just a number, so a user can judge
 * a match for themselves.
 *
 * Every score is in 0..1. [score] is the weighted total of the four component scores
 * (see `MatchWeights`).
 *
 * @property sourceItemId the item whose matches these are (the one being viewed).
 * @property candidateItemId the possible match, always of the opposite type.
 * @property distanceMeters how far apart the two items were, or null if either location is unknown.
 * @property daysApart whole days between the two event dates.
 * @property usedFallback true if [textScore] came from keyword overlap because an item had no
 *   embedding yet (for example the model failed to load). The UI can say the score is rougher.
 */
data class MatchResult(
    val sourceItemId: String,
    val candidateItemId: String,
    val score: Double,
    val textScore: Double,
    val categoryScore: Double,
    val locationScore: Double,
    val dateScore: Double,
    val distanceMeters: Double?,
    val daysApart: Int,
    val usedFallback: Boolean,
)
