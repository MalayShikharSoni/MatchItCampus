package com.matchitcampus.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Top of the UI tree: shows the auth graph when signed out and the main app when signed in.
 *
 * The switch follows Firebase sign-in through [RootViewModel]. Because the two graphs are
 * separate composables with separate NavControllers, switching replaces the whole back
 * stack: after logging out, Back can't return to the signed-in screens.
 */
@Composable
fun MatchItRoot(viewModel: RootViewModel = viewModel(factory = RootViewModel.Factory)) {
    val isSignedIn by viewModel.isSignedIn.collectAsStateWithLifecycle()

    if (isSignedIn) {
        MainShell()
    } else {
        AuthNavHost()
    }
}
