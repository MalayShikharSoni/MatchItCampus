package com.matchitcampus.app.ui.auth

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.matchitcampus.app.R
import com.matchitcampus.app.ui.components.PlaceholderScreen
import com.matchitcampus.app.ui.theme.MatchItCampusTheme

/**
 * Placeholder for the login screen. Phase 3 replaces it with email/password login
 * backed by Firebase Auth. For now "Continue" just enters the main app.
 */
@Composable
fun LoginScreen(
    onContinue: () -> Unit,
    onRegisterClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaceholderScreen(title = stringResource(R.string.title_login), modifier = modifier) {
        Button(onClick = onContinue) { Text(stringResource(R.string.placeholder_continue)) }
        TextButton(onClick = onRegisterClick) {
            Text(stringResource(R.string.placeholder_go_register))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenPreview() {
    MatchItCampusTheme { LoginScreen(onContinue = {}, onRegisterClick = {}) }
}
