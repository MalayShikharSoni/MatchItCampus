package com.matchitcampus.app.ui.myposts

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.matchitcampus.app.R
import com.matchitcampus.app.ui.components.PlaceholderScreen
import com.matchitcampus.app.ui.theme.MatchItCampusTheme

/** Placeholder for the user's own posts grouped by status. Built in Phase 5. */
@Composable
fun MyPostsScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(title = stringResource(R.string.nav_my_posts), modifier = modifier)
}

@Preview(showBackground = true)
@Composable
private fun MyPostsScreenPreview() {
    MatchItCampusTheme { MyPostsScreen() }
}
