package com.matchitcampus.app.ui.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

/**
 * The signed-in app frame: an adaptive navigation bar/rail around the [MainNavHost].
 *
 * [NavigationSuiteScaffold] draws either a bottom bar or a side rail from the same list
 * of items. We choose which one with [navigationSuiteTypeFor], from the current window
 * size, so rotating the phone or running on a tablet switches automatically.
 *
 * @param onLogout called when the user logs out; the caller switches to [AuthNavHost].
 */
@Composable
fun MainShell(
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    // Observing the back stack as State makes this recompose whenever the screen changes,
    // so the highlighted tab always matches what is on screen.
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass

    NavigationSuiteScaffold(
        modifier = modifier,
        layoutType = navigationSuiteTypeFor(windowSizeClass),
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { destination ->
                item(
                    selected = currentDestination.isInTab(destination),
                    onClick = { navController.navigateToTab(destination) },
                    icon = { Icon(destination.icon, contentDescription = null) },
                    label = { Text(stringResource(destination.labelRes)) },
                )
            }
        },
    ) {
        MainNavHost(navController = navController, onLogout = onLogout)
    }
}

/**
 * True if this destination (or any graph containing it) is the tab's route.
 * `hierarchy` walks up through parent graphs, which keeps working if tabs later get
 * their own nested graphs.
 */
private fun NavDestination?.isInTab(tab: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(tab.route::class) } == true

/**
 * Standard tab navigation:
 * - `popUpTo(start) { saveState = true }`: don't pile up tabs on the back stack, but
 *   remember each tab's own back stack and scroll state.
 * - `launchSingleTop`: tapping the current tab again doesn't open a second copy.
 * - `restoreState`: coming back to a tab restores what it was showing.
 */
private fun NavHostController.navigateToTab(tab: TopLevelDestination) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
