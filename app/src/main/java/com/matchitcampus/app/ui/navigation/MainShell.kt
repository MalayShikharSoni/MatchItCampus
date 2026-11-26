package com.matchitcampus.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.matchitcampus.app.R
import com.matchitcampus.app.data.sync.SyncEvent
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
 * It also shows a badge with the number of pending incoming claims on the Claims tab, and a
 * snackbar when a new claim arrives (from `SyncManager.events`), whichever tab is open.
 */
@Composable
fun MainShell(
    modifier: Modifier = Modifier,
    viewModel: ShellViewModel = viewModel(factory = ShellViewModel.Factory),
) {
    val navController = rememberNavController()
    val pendingClaims by viewModel.pendingIncomingClaims.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Collect one-shot sync events for as long as the signed-in frame is on screen.
    // LaunchedEffect cancels this collection automatically when MainShell leaves (logout).
    LaunchedEffect(Unit) {
        viewModel.syncEvents.collect { event ->
            when (event) {
                is SyncEvent.NewIncomingClaim -> {
                    val result = snackbarHostState.showSnackbar(
                        message = context.getString(R.string.snackbar_new_claim, event.claimantName),
                        actionLabel = context.getString(R.string.action_view),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) navController.navigateToTab(TopLevelDestination.CLAIMS)
                }
            }
        }
    }

    // Observing the back stack as State makes this recompose whenever the screen changes,
    // so the highlighted tab always matches what is on screen.
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
    // The bar/rail is for switching tabs, so it only shows on the five tab screens. Screens
    // opened from a tab (Post, Item details, pin picker, Debug tools) get the whole window and
    // a Back arrow instead. null = the NavHost hasn't set its first destination yet.
    val onTabScreen = currentDestination == null || TopLevelDestination.entries.any { currentDestination.isInTab(it) }

    NavigationSuiteScaffold(
        modifier = modifier,
        layoutType = if (onTabScreen) navigationSuiteTypeFor(windowSizeClass) else NavigationSuiteType.None,
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { destination ->
                item(
                    selected = currentDestination.isInTab(destination),
                    onClick = { navController.navigateToTab(destination) },
                    icon = {
                        if (destination == TopLevelDestination.CLAIMS && pendingClaims > 0) {
                            BadgedBox(badge = { Badge { Text("$pendingClaims") } }) {
                                Icon(destination.icon, contentDescription = null)
                            }
                        } else {
                            Icon(destination.icon, contentDescription = null)
                        }
                    },
                    label = { Text(stringResource(destination.labelRes)) },
                )
            }
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            MainNavHost(navController = navController)
            // Lifted above the bottom of the screen so it doesn't sit on top of a screen's FAB.
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp),
            )
        }
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
