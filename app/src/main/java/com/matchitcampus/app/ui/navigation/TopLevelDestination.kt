package com.matchitcampus.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.window.core.layout.WindowSizeClass
import com.matchitcampus.app.R

/**
 * The tabs shown in the navigation bar / rail, in display order.
 *
 * Keeping them in one enum means the bar, the rail and the "which tab is selected" logic
 * all read from the same list, so adding a tab is a one-line change.
 *
 * @property route the type-safe route the tab opens (one of the objects in [Routes]).
 * @property labelRes string resource for the tab label.
 * @property icon icon shown for the tab.
 */
enum class TopLevelDestination(
    val route: Any,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    HOME(Routes.Home, R.string.nav_home, Icons.Filled.Home),
    MAP(Routes.Map, R.string.nav_map, Icons.Filled.Place),
    MY_POSTS(Routes.MyPosts, R.string.nav_my_posts, Icons.AutoMirrored.Filled.List),
    CLAIMS(Routes.Claims, R.string.nav_claims, Icons.Filled.Email),
    PROFILE(Routes.Profile, R.string.nav_profile, Icons.Filled.Person),
}

/**
 * Picks the navigation component from the window **width** only:
 * - compact width (under 600 dp, e.g. a phone in portrait): bottom [NavigationSuiteType.NavigationBar]
 * - medium or expanded width (600 dp and up, e.g. phone landscape, tablet): side [NavigationSuiteType.NavigationRail]
 *
 * Why not the library default (`NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo`):
 * it also shows a bottom bar when the window *height* is compact, i.e. a phone in landscape.
 * A side rail suits landscape better because it doesn't take scarce vertical space.
 *
 * This is a plain function (no Compose state) so it can be unit-tested on the JVM.
 */
fun navigationSuiteTypeFor(windowSizeClass: WindowSizeClass): NavigationSuiteType =
    if (windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)) {
        NavigationSuiteType.NavigationRail
    } else {
        NavigationSuiteType.NavigationBar
    }
