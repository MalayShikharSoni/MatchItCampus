package com.matchitcampus.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * Top of the UI tree: shows the auth graph when signed out and the main app when signed in.
 *
 * Phase 1 placeholder: "signed in" is just a local flag flipped by a button. In Phase 3
 * this flag is replaced by `AuthRepository.authState`, so the switch follows real
 * Firebase sign-in. `rememberSaveable` keeps the flag across rotation in the meantime.
 */
@Composable
fun MatchItRoot() {
    var isSignedIn by rememberSaveable { mutableStateOf(false) }

    if (isSignedIn) {
        MainShell(onLogout = { isSignedIn = false })
    } else {
        AuthNavHost(onSignedIn = { isSignedIn = true })
    }
}
