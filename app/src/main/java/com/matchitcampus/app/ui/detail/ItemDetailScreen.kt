package com.matchitcampus.app.ui.detail

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.matchitcampus.app.R
import com.matchitcampus.app.ui.components.PlaceholderScreen
import com.matchitcampus.app.ui.theme.MatchItCampusTheme

/**
 * Placeholder for item details, mini map and possible matches. Built in Phase 5.
 * Shows the received [itemId] to prove the typed navigation argument arrives intact.
 */
@Composable
fun ItemDetailScreen(
    itemId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaceholderScreen(
        title = stringResource(R.string.title_item_detail),
        modifier = modifier,
        onBack = onBack,
    ) {
        Text(
            text = stringResource(R.string.placeholder_item_id, itemId),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ItemDetailScreenPreview() {
    MatchItCampusTheme { ItemDetailScreen(itemId = "sample-item", onBack = {}) }
}
