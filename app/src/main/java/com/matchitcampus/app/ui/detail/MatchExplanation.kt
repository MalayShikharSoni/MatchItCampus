package com.matchitcampus.app.ui.detail

import com.matchitcampus.app.domain.model.Item
import com.matchitcampus.app.domain.model.MatchResult
import com.matchitcampus.app.domain.model.ZoneType
import com.matchitcampus.app.ui.util.Formatters

/**
 * One line of the "Why this match" breakdown, as data. The screen turns each line into
 * text (string resources); keeping the decision here, in plain Kotlin, means the rules can be
 * unit-tested without Compose (`MatchExplanationTest`).
 */
sealed interface WhyLine {
    /** Similar meaning of the text. [fromKeywords] is true when the rougher keyword fallback was used. */
    data class Text(val percent: Int, val fromKeywords: Boolean) : WhyLine

    data object SameCategory : WhyLine
    /** One of the two is in the "Other" category, so the category says little. */
    data object CategoryUnclear : WhyLine
    data object DifferentCategory : WhyLine

    data class SameZone(val zoneName: String) : WhyLine
    /** Both items have a pin, [meters] apart. */
    data class Distance(val meters: Double) : WhyLine
    data object DifferentZones : WhyLine
    data object LocationUnknown : WhyLine

    /** The two event dates are [days] whole days apart (0 = same day). */
    data class DaysApart(val days: Int) : WhyLine
}

object MatchExplanation {

    /**
     * Explains [match] between [source] (the item being viewed) and [candidate].
     *
     * The location line mirrors `MatchScorer`'s order: same zone first (unless the zone is
     * "Other / Not sure"), then the pin distance, then what is known about the zones.
     */
    fun lines(match: MatchResult, source: Item, candidate: Item): List<WhyLine> {
        val text = WhyLine.Text(
            percent = Formatters.percent(match.textScore),
            fromKeywords = match.usedFallback,
        )
        val category = when {
            source.category == candidate.category -> WhyLine.SameCategory
            match.categoryScore > 0.0 -> WhyLine.CategoryUnclear
            else -> WhyLine.DifferentCategory
        }
        val sameZone = source.zoneId != null &&
            source.zoneId == candidate.zoneId &&
            source.zone?.type != ZoneType.OTHER
        val location = when {
            sameZone -> WhyLine.SameZone(source.zone?.name ?: source.zoneId.orEmpty())
            match.distanceMeters != null -> WhyLine.Distance(match.distanceMeters)
            source.zoneId != null && candidate.zoneId != null &&
                source.zone?.type != ZoneType.OTHER && candidate.zone?.type != ZoneType.OTHER -> WhyLine.DifferentZones
            else -> WhyLine.LocationUnknown
        }
        return listOf(text, category, location, WhyLine.DaysApart(match.daysApart))
    }
}
