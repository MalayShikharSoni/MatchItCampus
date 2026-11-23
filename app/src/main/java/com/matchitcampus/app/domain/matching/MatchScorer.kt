package com.matchitcampus.app.domain.matching

import com.matchitcampus.app.domain.model.Category
import com.matchitcampus.app.domain.model.Item
import com.matchitcampus.app.domain.model.ItemStatus
import com.matchitcampus.app.domain.model.ItemType
import com.matchitcampus.app.domain.model.MatchResult
import com.matchitcampus.app.domain.model.ZoneType
import com.matchitcampus.app.location.GeoUtils
import kotlin.math.abs
import kotlin.math.max

/**
 * An item together with its text embedding, which is what the scorer compares.
 *
 * The domain [Item] deliberately has no embedding (it's a matching detail, stored only in
 * Room), so the repository pairs them up here. A plain class rather than a `data class`,
 * because a data class would compare the [FloatArray] by reference, which is misleading.
 *
 * @property embedding null if the item hasn't been embedded yet; the scorer then falls back
 *   to keyword similarity.
 */
class ItemForMatching(val item: Item, val embedding: FloatArray?)

/**
 * Scores how likely a lost item and a found item are the same object.
 *
 * Pure Kotlin with no Android imports, so every rule is covered by JVM unit tests
 * (`MatchScorerTest`). It's also the best place for a breakpoint when a match looks wrong:
 * stop in [score] and inspect each component.
 *
 * **Step 1, hard filters.** A pair is rejected outright (null) unless:
 * - one is LOST and the other FOUND,
 * - both are OPEN (a returned or claimed item is no longer looking for a match),
 * - they belong to different people (matching someone's posts against each other is never useful),
 * - the item wasn't "found" more than a day before it was "lost" (the day of grace allows for
 *   people guessing the date or posting around midnight),
 * - the dates are at most 30 days apart.
 *
 * **Step 2, four component scores**, each 0..1:
 * - **text**: cosine similarity of the embeddings, clamped to 0..1 (negative means unrelated
 *   for our purposes). If either embedding is missing, keyword Jaccard similarity instead, and
 *   the result is flagged `usedFallback`.
 * - **category**: 1 if the same, 0.3 if either is OTHER (unsure, so partly compatible), else 0.
 * - **location**: 1 in the same zone; otherwise falls from 1 to 0 over the first kilometre;
 *   0.5 (neutral) if either location is unknown.
 * - **date**: falls from 1 (same day) to 0 at 14 days apart.
 *
 * **Step 3**, the weighted sum (see [MatchWeights]). There is no score threshold: the real
 * model's raw cosine values are too close together for one (see [MatchWeights]), so results
 * are ranked and the best [MatchWeights.maxResults] are shown ([rank]).
 *
 * All the components are symmetric (A vs B scores the same as B vs A), so the repository can
 * store one computed result in both directions.
 */
class MatchScorer(private val weights: MatchWeights = MatchWeights.DEFAULT) {

    /**
     * Scores [source] against [candidate].
     *
     * @return the full breakdown, or null if a hard filter rules the pair out. The result is
     *   returned however low it is; deciding what to show is [rank]'s job.
     */
    fun score(source: ItemForMatching, candidate: ItemForMatching): MatchResult? {
        val a = source.item
        val b = candidate.item
        if (a.type == b.type) return null
        if (a.status != ItemStatus.OPEN || b.status != ItemStatus.OPEN) return null
        if (a.ownerId == b.ownerId) return null

        val lost = if (a.type == ItemType.LOST) a else b
        val found = if (a.type == ItemType.FOUND) a else b
        if (found.eventDate < lost.eventDate - FOUND_BEFORE_LOST_GRACE_MS) return null
        val millisApart = abs(found.eventDate - lost.eventDate)
        if (millisApart > MAX_DAYS_APART * DAY_MS) return null
        val daysApart = (millisApart / DAY_MS).toInt()

        val usedFallback: Boolean
        val textScore: Double
        val embeddingA = source.embedding
        val embeddingB = candidate.embedding
        if (embeddingA != null && embeddingB != null && embeddingA.size == embeddingB.size) {
            usedFallback = false
            textScore = VectorMath.cosine(embeddingA, embeddingB).coerceIn(0.0, 1.0)
        } else {
            usedFallback = true
            textScore = KeywordSimilarity.jaccard(TextNormalizer.forEmbedding(a), TextNormalizer.forEmbedding(b))
        }

        val categoryScore = categoryScore(a.category, b.category)
        val distanceMeters = distanceMeters(a, b)
        val locationScore = when {
            isSameZone(a, b) -> 1.0
            distanceMeters != null -> max(0.0, 1.0 - distanceMeters / LOCATION_DECAY_METERS)
            else -> UNKNOWN_LOCATION_SCORE
        }
        val dateScore = max(0.0, 1.0 - daysApart / DATE_DECAY_DAYS)

        val total = weights.text * textScore +
            weights.category * categoryScore +
            weights.location * locationScore +
            weights.date * dateScore

        return MatchResult(
            sourceItemId = a.id,
            candidateItemId = b.id,
            score = total,
            textScore = textScore,
            categoryScore = categoryScore,
            locationScore = locationScore,
            dateScore = dateScore,
            distanceMeters = distanceMeters,
            daysApart = daysApart,
            usedFallback = usedFallback,
        )
    }

    /** How many matches an item shows. */
    val maxResults: Int get() = weights.maxResults

    /** Scores [source] against every candidate: every pair that passes the hard filters, best first. */
    fun scoreAll(source: ItemForMatching, candidates: List<ItemForMatching>): List<MatchResult> =
        candidates
            .mapNotNull { score(source, it) }
            .sortedByDescending { it.score }

    /** The best [maxResults] candidates for [source], best first. */
    fun rank(source: ItemForMatching, candidates: List<ItemForMatching>): List<MatchResult> =
        scoreAll(source, candidates).take(maxResults)

    private fun categoryScore(a: Category, b: Category): Double = when {
        a == b -> 1.0
        a == Category.OTHER || b == Category.OTHER -> OTHER_CATEGORY_SCORE
        else -> 0.0
    }

    /**
     * Same zone, unless that zone is "Other / Not sure": two items both tagged "not sure" are
     * not known to be in the same place.
     */
    private fun isSameZone(a: Item, b: Item): Boolean =
        a.zoneId != null && a.zoneId == b.zoneId && a.zone?.type != ZoneType.OTHER

    /** Distance between the two items, or null if either location is unknown. */
    private fun distanceMeters(a: Item, b: Item): Double? {
        val (latA, lngA) = knownPoint(a) ?: return null
        val (latB, lngB) = knownPoint(b) ?: return null
        return GeoUtils.haversineMeters(latA, lngA, latB, lngB)
    }

    /**
     * Where the item was: its exact pin if it has one, otherwise the centre of its zone.
     * Placeholder zones (still at the made-up campus centre) and the "Other" zone don't count,
     * because their centre says nothing about where the item really was.
     */
    private fun knownPoint(item: Item): Pair<Double, Double>? {
        if (item.latitude != null && item.longitude != null) return item.latitude to item.longitude
        val zone = item.zone ?: return null
        if (zone.isPlaceholder || zone.type == ZoneType.OTHER) return null
        return zone.latitude to zone.longitude
    }

    companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
        /** A found date may be up to this much earlier than the lost date (guessing, time zones). */
        const val FOUND_BEFORE_LOST_GRACE_MS = DAY_MS
        /** Pairs further apart than this are never matched. */
        const val MAX_DAYS_APART = 30
        /** The date score reaches 0 at this many days apart. */
        const val DATE_DECAY_DAYS = 14.0
        /** The location score reaches 0 at this distance. Roughly the width of the main campus. */
        const val LOCATION_DECAY_METERS = 1000.0
        /** Category score when either item is OTHER: it might be the same thing. */
        const val OTHER_CATEGORY_SCORE = 0.3
        /** Location score when we can't tell: neither helps nor hurts. */
        const val UNKNOWN_LOCATION_SCORE = 0.5
    }
}
