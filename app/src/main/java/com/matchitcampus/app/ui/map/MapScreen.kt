package com.matchitcampus.app.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.matchitcampus.app.R
import com.matchitcampus.app.ui.components.PlaceholderScreen
import com.matchitcampus.app.ui.theme.MatchItCampusTheme

/** Placeholder for the campus map (MapLibre + OpenFreeMap, item markers, zones). Built in Phase 6. */
@Composable
fun MapScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(title = stringResource(R.string.nav_map), modifier = modifier)
}

@Preview(showBackground = true)
@Composable
private fun MapScreenPreview() {
    MatchItCampusTheme { MapScreen() }
}
