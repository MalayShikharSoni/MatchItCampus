package com.matchitcampus.app.ui.home

import androidx.window.core.layout.WindowSizeClass
import com.matchitcampus.app.domain.model.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 7: when Home goes two-pane, and what "Clear filters" resets. */
class HomeLayoutTest {

    @Test
    fun twoPane_onlyFromTheExpandedWidth_840dp() {
        assertFalse(isTwoPaneWidth(WindowSizeClass(minWidthDp = 411, minHeightDp = 891))) // phone portrait
        assertFalse(isTwoPaneWidth(WindowSizeClass(minWidthDp = 839, minHeightDp = 600)))
        assertTrue(isTwoPaneWidth(WindowSizeClass(minWidthDp = 840, minHeightDp = 600)))
        assertTrue(isTwoPaneWidth(WindowSizeClass(minWidthDp = 952, minHeightDp = 411))) // big phone in landscape
        assertTrue(isTwoPaneWidth(WindowSizeClass(minWidthDp = 1280, minHeightDp = 800))) // tablet
    }

    @Test
    fun cleared_turnsEveryFilterOff_butKeepsTheSort() {
        val filters = FeedFilters(
            category = Category.KEYS,
            zoneId = "block_34",
            date = DateFilter.LAST_7_DAYS,
            sort = FeedSort.NEAR_ME,
        )
        assertTrue(filters.isActive)
        val cleared = filters.cleared()
        assertEquals(FeedFilters(sort = FeedSort.NEAR_ME), cleared)
        assertFalse(cleared.isActive)
    }
}
