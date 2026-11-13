package com.matchitcampus.app.ui.debug

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.matchitcampus.app.R
import com.matchitcampus.app.ui.components.PlaceholderScreen
import com.matchitcampus.app.ui.theme.MatchItCampusTheme

/**
 * Placeholder for the debug-only tools (seed demo data, zone capture, test crash, force
 * sync). Reachable only in debug builds; the tools arrive in Phases 3, 6 and 7.
 */
@Composable
fun DebugToolsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaceholderScreen(
        title = stringResource(R.string.title_debug_tools),
        modifier = modifier,
        onBack = onBack,
    )
}

@Preview(showBackground = true)
@Composable
private fun DebugToolsScreenPreview() {
    MatchItCampusTheme { DebugToolsScreen(onBack = {}) }
}
