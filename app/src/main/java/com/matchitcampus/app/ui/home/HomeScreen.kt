package com.matchitcampus.app.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.matchitcampus.app.R
import com.matchitcampus.app.ui.components.PlaceholderScreen
import com.matchitcampus.app.ui.theme.MatchItCampusTheme

/** Id used by the placeholder "Open a sample item" button until real items exist. */
private const val SAMPLE_ITEM_ID = "sample-item"

/**
 * Placeholder for the feed (Lost/Found tabs, search, filters, item list). Built in Phase 5.
 * The FAB and the sample-item button already exercise the navigation to Post item and
 * Item detail.
 */
@Composable
fun HomeScreen(
    onPostItemClick: () -> Unit,
    onOpenItem: (itemId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaceholderScreen(
        title = stringResource(R.string.nav_home),
        modifier = modifier,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onPostItemClick,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.action_post_item)) },
            )
        },
    ) {
        OutlinedButton(onClick = { onOpenItem(SAMPLE_ITEM_ID) }) {
            Text(stringResource(R.string.placeholder_open_sample_item))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    MatchItCampusTheme { HomeScreen(onPostItemClick = {}, onOpenItem = {}) }
}
