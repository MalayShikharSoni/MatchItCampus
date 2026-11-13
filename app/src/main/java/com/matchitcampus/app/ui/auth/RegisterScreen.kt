package com.matchitcampus.app.ui.auth

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.matchitcampus.app.R
import com.matchitcampus.app.ui.components.PlaceholderScreen
import com.matchitcampus.app.ui.theme.MatchItCampusTheme

/**
 * Placeholder for registration (name, email, password, optional LPU registration number).
 * Built in Phase 3.
 */
@Composable
fun RegisterScreen(
    onContinue: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaceholderScreen(
        title = stringResource(R.string.title_register),
        modifier = modifier,
        onBack = onBack,
    ) {
        Button(onClick = onContinue) { Text(stringResource(R.string.placeholder_continue)) }
    }
}

@Preview(showBackground = true)
@Composable
private fun RegisterScreenPreview() {
    MatchItCampusTheme { RegisterScreen(onContinue = {}, onBack = {}) }
}
