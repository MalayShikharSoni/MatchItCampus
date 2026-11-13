package com.matchitcampus.app.ui.claims

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.matchitcampus.app.R
import com.matchitcampus.app.ui.components.PlaceholderScreen
import com.matchitcampus.app.ui.theme.MatchItCampusTheme

/** Placeholder for incoming and outgoing claims. Built in Phase 5. */
@Composable
fun ClaimsScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(title = stringResource(R.string.nav_claims), modifier = modifier)
}

@Preview(showBackground = true)
@Composable
private fun ClaimsScreenPreview() {
    MatchItCampusTheme { ClaimsScreen() }
}
