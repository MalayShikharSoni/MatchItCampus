package com.matchitcampus.app.ui.detail

import com.matchitcampus.app.domain.model.Category
import com.matchitcampus.app.domain.model.ItemType
import com.matchitcampus.app.domain.model.MatchResult
import com.matchitcampus.app.domain.model.ZoneType
import com.matchitcampus.app.ui.TestItems.item
import com.matchitcampus.app.ui.TestItems.zone
import org.junit.Assert.assertEquals
import org.junit.Test

/** The "Why this match" lines must describe the score the way `MatchScorer` computed it. */
class MatchExplanationTest {

    private val block34 = zone("block_34", name = "Block 34")
    private val library = zone("library", ZoneType.LIBRARY, name = "Central Library")
    private val notSure = zone("other", ZoneType.OTHER, name = "Other / Not sure")

    private val found = item("f", ItemType.FOUND, zone = block34)
    private val lost = item("l", ItemType.LOST, zone = block34)

    private fun match(
        textScore: Double = 0.82,
        categoryScore: Double = 1.0,
        distanceMeters: Double? = null,
        daysApart: Int = 1,
        usedFallback: Boolean = false,
    ) = MatchResult(
        sourceItemId = "f",
        candidateItemId = "l",
        score = 0.8,
        textScore = textScore,
        categoryScore = categoryScore,
        locationScore = 1.0,
        dateScore = 0.9,
        distanceMeters = distanceMeters,
        daysApart = daysApart,
        usedFallback = usedFallback,
    )

    @Test
    fun perfectPair_listsTextCategoryZoneAndDays() {
        assertEquals(
            listOf(WhyLine.Text(82, fromKeywords = false), WhyLine.SameCategory, WhyLine.SameZone("Block 34"), WhyLine.DaysApart(1)),
            MatchExplanation.lines(match(), found, lost),
        )
    }

    @Test
    fun keywordFallback_isFlagged() {
        val text = MatchExplanation.lines(match(textScore = 0.25, usedFallback = true), found, lost).first()
        assertEquals(WhyLine.Text(25, fromKeywords = true), text)
    }

    @Test
    fun category_otherIsUnclear_andMismatchIsDifferent() {
        val other = lost.copy(category = Category.OTHER)
        assertEquals(WhyLine.CategoryUnclear, MatchExplanation.lines(match(categoryScore = 0.3), found, other)[1])
        val bags = lost.copy(category = Category.BAGS)
        assertEquals(WhyLine.DifferentCategory, MatchExplanation.lines(match(categoryScore = 0.0), found, bags)[1])
    }

    @Test
    fun location_pinDistanceWinsWhenZonesDiffer() {
        val elsewhere = lost.copy(zoneId = library.id, zone = library)
        assertEquals(WhyLine.Distance(150.0), MatchExplanation.lines(match(distanceMeters = 150.0), found, elsewhere)[2])
    }

    @Test
    fun location_differentZonesWithoutPins() {
        val elsewhere = lost.copy(zoneId = library.id, zone = library)
        assertEquals(WhyLine.DifferentZones, MatchExplanation.lines(match(), found, elsewhere)[2])
    }

    @Test
    fun location_bothNotSure_isNotTheSameZone() {
        // Two "Other / Not sure" items aren't known to be in the same place (MatchScorer agrees).
        val a = found.copy(zoneId = notSure.id, zone = notSure)
        val b = lost.copy(zoneId = notSure.id, zone = notSure)
        assertEquals(WhyLine.LocationUnknown, MatchExplanation.lines(match(), a, b)[2])
    }

    @Test
    fun location_missingZone_isUnknown() {
        val noZone = lost.copy(zoneId = null, zone = null)
        assertEquals(WhyLine.LocationUnknown, MatchExplanation.lines(match(), found, noZone)[2])
    }
}
