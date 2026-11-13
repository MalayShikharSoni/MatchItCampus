package com.matchitcampus.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.matchitcampus.app.BuildConfig
import com.matchitcampus.app.ui.claims.ClaimsScreen
import com.matchitcampus.app.ui.debug.DebugToolsScreen
import com.matchitcampus.app.ui.detail.ItemDetailScreen
import com.matchitcampus.app.ui.home.HomeScreen
import com.matchitcampus.app.ui.map.MapScreen
import com.matchitcampus.app.ui.myposts.MyPostsScreen
import com.matchitcampus.app.ui.post.PostItemScreen
import com.matchitcampus.app.ui.profile.ProfileScreen

/**
 * Navigation graph for the signed-in part of the app.
 *
 * Screens never get the NavController themselves. They receive plain lambdas such as
 * `onBack` or `onOpenItem`, and only this file knows how those map to navigation calls.
 * That keeps the screens previewable and testable without a real navigation setup.
 */
@Composable
fun MainNavHost(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val goBack: () -> Unit = { navController.popBackStack() }

    NavHost(
        navController = navController,
        startDestination = Routes.Home,
        modifier = modifier,
    ) {
        composable<Routes.Home> {
            HomeScreen(
                onPostItemClick = { navController.navigate(Routes.PostItem) },
                onOpenItem = { itemId -> navController.navigate(Routes.ItemDetail(itemId)) },
            )
        }
        composable<Routes.Map> { MapScreen() }
        composable<Routes.MyPosts> { MyPostsScreen() }
        composable<Routes.Claims> { ClaimsScreen() }
        composable<Routes.Profile> {
            ProfileScreen(
                showDebugTools = BuildConfig.DEBUG,
                onOpenDebugTools = { navController.navigate(Routes.DebugTools) },
                onLogout = onLogout,
            )
        }

        composable<Routes.PostItem> { PostItemScreen(onBack = goBack) }
        composable<Routes.ItemDetail> { backStackEntry ->
            // toRoute() rebuilds the typed route object, so itemId is a String, not a raw argument.
            val route = backStackEntry.toRoute<Routes.ItemDetail>()
            ItemDetailScreen(itemId = route.itemId, onBack = goBack)
        }

        // Release builds never register this route, so the debug screen cannot be reached there.
        if (BuildConfig.DEBUG) {
            composable<Routes.DebugTools> { DebugToolsScreen(onBack = goBack) }
        }
    }
}
