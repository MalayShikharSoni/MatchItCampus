package com.matchitcampus.app.ui.components

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.matchitcampus.app.R
import com.matchitcampus.app.domain.model.Claim
import com.matchitcampus.app.ui.theme.MatchItCampusTheme

/** Whose side of a claim the card is shown from. */
enum class ClaimPerspective {
    /** I'm the finder: someone claims my found item. */
    INCOMING,
    /** I'm the claimant. */
    OUTGOING,
}

/**
 * One claim: which item, who, their answer to the verification question, their message,
 * and its status. The buttons (Accept/Reject, Cancel) are passed in through [actions], so the
 * same card serves the Claims tab and the item's detail screen.
 *
 * @param onOpenItem opens the claimed found item; null when the card is already on that item's screen.
 */
@Composable
fun ClaimCard(
    claim: Claim,
    perspective: ClaimPerspective,
    modifier: Modifier = Modifier,
    onOpenItem: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val content: @Composable () -> Unit = {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = claim.foundItem?.title ?: stringResource(R.string.claim_unknown_item),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                ClaimStatusChip(claim.status)
            }
            val who = when (perspective) {
                ClaimPerspective.INCOMING -> stringResource(R.string.claim_from, claim.claimantName)
                ClaimPerspective.OUTGOING -> claim.foundItem?.let { stringResource(R.string.claim_found_by, it.ownerName) }
            }
            val timeAgo = DateUtils.getRelativeTimeSpanString(claim.createdAt).toString()
            Text(
                text = listOfNotNull(who, timeAgo).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            claim.foundItem?.verificationQuestion?.let { question ->
                Text(stringResource(R.string.claim_question, question), style = MaterialTheme.typography.bodyMedium)
            }
            Text(stringResource(R.string.claim_answer, claim.answer), style = MaterialTheme.typography.bodyMedium)
            if (claim.message.isNotBlank()) {
                Text(stringResource(R.string.claim_message, claim.message), style = MaterialTheme.typography.bodyMedium)
            }
            claim.lostItem?.let { lost ->
                Text(
                    text = stringResource(
                        if (perspective == ClaimPerspective.INCOMING) R.string.claim_linked_lost else R.string.claim_linked_lost_mine,
                        lost.title,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                content = actions,
            )
        }
    }
    if (onOpenItem != null) {
        Card(onClick = onOpenItem, modifier = modifier.fillMaxWidth()) { content() }
    } else {
        Card(modifier = modifier.fillMaxWidth()) { content() }
    }
}

@Preview(showBackground = true)
@Composable
private fun ClaimCardPreview() {
    MatchItCampusTheme {
        ClaimCard(claim = PreviewData.claim, perspective = ClaimPerspective.INCOMING, onOpenItem = {}) {
            TextButton(onClick = {}) { Text("Reject") }
            OutlinedButton(onClick = {}) { Text("Accept") }
        }
    }
}
