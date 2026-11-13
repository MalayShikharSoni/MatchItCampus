package com.matchitcampus.app.ui.profile

import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.matchitcampus.app.R
import com.matchitcampus.app.ui.components.PlaceholderScreen
import com.matchitcampus.app.ui.theme.MatchItCampusTheme

/**
 * Placeholder for the profile screen (name, email, logout, about/attribution). Built in Phase 5.
 *
 * @param showDebugTools whether to offer the Debug tools entry. The caller passes
 *   `BuildConfig.DEBUG`; taking it as a parameter keeps this screen previewable both ways.
 */
@Composable
fun ProfileScreen(
    showDebugTools: Boolean,
    onOpenDebugTools: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaceholderScreen(title = stringResource(R.string.nav_profile), modifier = modifier) {
        if (showDebugTools) {
            OutlinedButton(onClick = onOpenDebugTools) {
                Text(stringResource(R.string.placeholder_debug_tools))
            }
        }
        Button(onClick = onLogout) { Text(stringResource(R.string.placeholder_logout)) }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileScreenPreview() {
    MatchItCampusTheme {
        ProfileScreen(showDebugTools = true, onOpenDebugTools = {}, onLogout = {})
    }
}
