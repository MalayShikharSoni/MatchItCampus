package com.matchitcampus.app.ui.post

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.matchitcampus.app.R
import com.matchitcampus.app.ui.components.PlaceholderScreen
import com.matchitcampus.app.ui.theme.MatchItCampusTheme

/** Placeholder for the Post item form (Lost/Found, details, location, photo). Built in Phase 5. */
@Composable
fun PostItemScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaceholderScreen(
        title = stringResource(R.string.title_post_item),
        modifier = modifier,
        onBack = onBack,
    )
}

@Preview(showBackground = true)
@Composable
private fun PostItemScreenPreview() {
    MatchItCampusTheme { PostItemScreen(onBack = {}) }
}
