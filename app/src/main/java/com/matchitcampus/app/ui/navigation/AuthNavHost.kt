package com.matchitcampus.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.matchitcampus.app.ui.auth.LoginRoute
import com.matchitcampus.app.ui.auth.RegisterRoute

/**
 * Navigation graph shown while the user is signed out: Login and Register.
 *
 * It has its own NavController, separate from the main graph's. A successful sign-in
 * doesn't navigate anywhere from here: [MatchItRoot] sees the new auth state and replaces
 * this whole graph with [MainShell].
 */
@Composable
fun AuthNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Routes.Login,
        modifier = modifier,
    ) {
        composable<Routes.Login> {
            LoginRoute(onRegisterClick = { navController.navigate(Routes.Register) })
        }
        composable<Routes.Register> {
            RegisterRoute(onLoginClick = { navController.popBackStack() })
        }
    }
}
