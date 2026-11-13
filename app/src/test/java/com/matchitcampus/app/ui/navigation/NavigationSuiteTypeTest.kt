package com.matchitcampus.app.ui.navigation

import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.window.core.layout.WindowSizeClass
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Checks the responsive rule in [navigationSuiteTypeFor]: bottom bar below 600 dp of
 * width, side rail from 600 dp up, regardless of height. Sizes are typical dp values.
 */
class NavigationSuiteTypeTest {

    @Test
    fun phonePortrait_usesNavigationBar() {
        val phonePortrait = WindowSizeClass(minWidthDp = 411, minHeightDp = 891)
        assertEquals(NavigationSuiteType.NavigationBar, navigationSuiteTypeFor(phonePortrait))
    }

    @Test
    fun phoneLandscape_usesNavigationRail() {
        // Height is compact here; the library default would pick a bottom bar.
        val phoneLandscape = WindowSizeClass(minWidthDp = 891, minHeightDp = 411)
        assertEquals(NavigationSuiteType.NavigationRail, navigationSuiteTypeFor(phoneLandscape))
    }

    @Test
    fun tablet_usesNavigationRail() {
        val tablet = WindowSizeClass(minWidthDp = 1280, minHeightDp = 800)
        assertEquals(NavigationSuiteType.NavigationRail, navigationSuiteTypeFor(tablet))
    }

    @Test
    fun exactlyMediumBreakpoint_usesNavigationRail() {
        val boundary = WindowSizeClass(minWidthDp = 600, minHeightDp = 900)
        assertEquals(NavigationSuiteType.NavigationRail, navigationSuiteTypeFor(boundary))
    }

    @Test
    fun justBelowMediumBreakpoint_usesNavigationBar() {
        val narrow = WindowSizeClass(minWidthDp = 599, minHeightDp = 900)
        assertEquals(NavigationSuiteType.NavigationBar, navigationSuiteTypeFor(narrow))
    }
}
