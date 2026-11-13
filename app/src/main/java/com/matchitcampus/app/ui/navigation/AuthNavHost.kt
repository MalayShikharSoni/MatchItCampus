package com.matchitcampus.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.matchitcampus.app.ui.auth.LoginScreen
import com.matchitcampus.app.ui.auth.RegisterScreen

/**
 * Navigation graph shown while the user is signed out: Login and Register.
 *
 * It has its own NavController, separate from the main graph's, so signing in or out
 * swaps the whole graph and the user can never press Back into the other one.
 *
 * @param onSignedIn called once sign-in succeeds; the caller switches to [MainShell].
 */
@Composable
fun AuthNavHost(
    onSignedIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Routes.Login,
        modifier = modifier,
    ) {
        composable<Routes.Login> {
            LoginScreen(
                onContinue = onSignedIn,
                onRegisterClick = { navController.navigate(Routes.Register) },
            )
        }
        composable<Routes.Register> {
            RegisterScreen(
                onContinue = onSignedIn,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
