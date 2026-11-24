package com.matchitcampus.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.matchitcampus.app.R
import com.matchitcampus.app.ui.theme.MatchItCampusTheme

/*
 * The three "not a list yet" states every screen needs: empty, error and loading. Sharing
 * them keeps the screens consistent, and each is stateless: the caller passes the text and
 * any action.
 */

/**
 * Nothing to show, with an explanation and an optional [action] slot (e.g. "Post an item"
 * or "Clear filters").
 */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (body != null) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        action?.invoke()
    }
}

/** Something went wrong; [onRetry] tries again. */
@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    EmptyState(
        title = stringResource(R.string.error_title),
        body = message,
        modifier = modifier,
        action = { OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) } },
    )
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Preview(showBackground = true)
@Composable
private fun EmptyStatePreview() {
    MatchItCampusTheme {
        EmptyState(
            title = "No found items yet",
            body = "When someone posts a found item, it shows up here.",
            action = { OutlinedButton(onClick = {}) { Text("Post an item") } },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ErrorStatePreview() {
    MatchItCampusTheme { ErrorState(message = "Couldn't load the feed.", onRetry = {}) }
}
