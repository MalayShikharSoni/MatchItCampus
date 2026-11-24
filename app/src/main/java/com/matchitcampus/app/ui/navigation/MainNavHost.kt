package com.matchitcampus.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.matchitcampus.app.BuildConfig
import com.matchitcampus.app.location.LatLng
import com.matchitcampus.app.ui.claims.ClaimsRoute
import com.matchitcampus.app.ui.debug.DebugToolsRoute
import com.matchitcampus.app.ui.debug.ZoneCaptureRoute
import com.matchitcampus.app.ui.detail.ItemDetailRoute
import com.matchitcampus.app.ui.home.HomeRoute
import com.matchitcampus.app.ui.map.MapRoute
import com.matchitcampus.app.ui.map.PinPickerRoute
import com.matchitcampus.app.ui.myposts.MyPostsRoute
import com.matchitcampus.app.ui.post.PostItemRoute
import com.matchitcampus.app.ui.profile.ProfileRoute

/**
 * Key under which the pin picker leaves its result in the Post screen's back-stack entry.
 * Stored as a `DoubleArray` (latitude, longitude), a type a saved-state Bundle can hold.
 */
private const val PIN_RESULT_KEY = "picked_pin"

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
        composable<Routes.Map> {
            MapRoute(onOpenItem = { itemId -> navController.navigate(Routes.ItemDetail(itemId)) })
        }
        composable<Routes.MyPosts> {
            MyPostsRoute(
                onOpenItem = { itemId -> navController.navigate(Routes.ItemDetail(itemId)) },
                onEditItem = { itemId -> navController.navigate(Routes.PostItem(editItemId = itemId)) },
                onPostItem = { navController.navigate(Routes.PostItem()) },
            )
        }
        composable<Routes.Claims> {
            ClaimsRoute(onOpenItem = { itemId -> navController.navigate(Routes.ItemDetail(itemId)) })
        }
        composable<Routes.Profile> {
            ProfileRoute(
                showDebugTools = BuildConfig.DEBUG,
                onOpenDebugTools = { navController.navigate(Routes.DebugTools) },
            )
        }

        composable<Routes.PostItem> { entry ->
            // "Returning a result": the pin picker writes into this entry's SavedStateHandle
            // before popping back, and this screen observes it.
            val pickedPin by entry.savedStateHandle.getStateFlow<DoubleArray?>(PIN_RESULT_KEY, null)
                .collectAsStateWithLifecycle()
            PostItemRoute(
                onPickPin = { current -> navController.navigate(Routes.PinPicker(current?.latitude, current?.longitude)) },
                pickedPin = pickedPin?.let { LatLng(it[0], it[1]) },
                onPinConsumed = { entry.savedStateHandle[PIN_RESULT_KEY] = null },
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
        composable<Routes.PinPicker> {
            PinPickerRoute(
                onBack = goBack,
                onPicked = { position ->
                    navController.previousBackStackEntry?.savedStateHandle?.set(
                        PIN_RESULT_KEY,
                        doubleArrayOf(position.latitude, position.longitude),
                    )
                    navController.popBackStack()
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
            composable<Routes.DebugTools> {
                DebugToolsRoute(onBack = goBack, onOpenZoneCapture = { navController.navigate(Routes.ZoneCapture) })
            }
            composable<Routes.ZoneCapture> { ZoneCaptureRoute(onBack = goBack) }
        }
    }
}
