package com.matchitcampus.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.matchitcampus.app.BuildConfig
import com.matchitcampus.app.ui.claims.ClaimsRoute
import com.matchitcampus.app.ui.debug.DebugToolsRoute
import com.matchitcampus.app.ui.detail.ItemDetailRoute
import com.matchitcampus.app.ui.home.HomeRoute
import com.matchitcampus.app.ui.map.MapScreen
import com.matchitcampus.app.ui.myposts.MyPostsScreen
import com.matchitcampus.app.ui.post.PostItemRoute
import com.matchitcampus.app.ui.profile.ProfileRoute

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
    modifier: Modifier = Modifier,
) {
    val goBack: () -> Unit = { navController.popBackStack() }

    NavHost(
        navController = navController,
        startDestination = Routes.Home,
        modifier = modifier,
    ) {
        composable<Routes.Home> {
            HomeRoute(
                onPostItem = { navController.navigate(Routes.PostItem()) },
                onOpenItem = { itemId -> navController.navigate(Routes.ItemDetail(itemId)) },
            )
        }
        composable<Routes.Map> { MapScreen() }
        composable<Routes.MyPosts> { MyPostsScreen() }
        composable<Routes.Claims> {
            ClaimsRoute(onOpenItem = { itemId -> navController.navigate(Routes.ItemDetail(itemId)) })
        }
        composable<Routes.Profile> {
            ProfileRoute(
                showDebugTools = BuildConfig.DEBUG,
                onOpenDebugTools = { navController.navigate(Routes.DebugTools) },
            )
        }

        composable<Routes.PostItem> {
            PostItemRoute(
                onBack = goBack,
                onSaved = { itemId, wasEdit ->
                    if (wasEdit) {
                        // Editing was opened from the item's own screen: just go back to it.
                        navController.popBackStack()
                    } else {
                        // Show the new item (and its matches). popUpTo removes the form from the
                        // back stack, so Back from the item returns to where the user started.
                        navController.navigate(Routes.ItemDetail(itemId)) {
                            popUpTo<Routes.PostItem> { inclusive = true }
                        }
                    }
                },
            )
        }
        composable<Routes.ItemDetail> {
            // The ViewModel reads itemId itself, with toRoute() on its SavedStateHandle.
            ItemDetailRoute(
                onBack = goBack,
                onOpenItem = { itemId -> navController.navigate(Routes.ItemDetail(itemId)) },
                onEdit = { itemId -> navController.navigate(Routes.PostItem(editItemId = itemId)) },
            )
        }

        // Release builds never register this route, so the debug screen cannot be reached there.
        if (BuildConfig.DEBUG) {
            composable<Routes.DebugTools> { DebugToolsRoute(onBack = goBack) }
        }
    }
}
