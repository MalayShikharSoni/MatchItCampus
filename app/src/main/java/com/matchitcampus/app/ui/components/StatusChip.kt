package com.matchitcampus.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.matchitcampus.app.domain.model.ClaimStatus
import com.matchitcampus.app.domain.model.ItemStatus
import com.matchitcampus.app.domain.model.ItemType
import com.matchitcampus.app.ui.theme.MatchItCampusTheme
import com.matchitcampus.app.ui.util.labelRes

/*
 * Small coloured labels for an item's status, a claim's status and an item's type.
 *
 * They're display-only (not clickable), so they're drawn as a tinted Surface with text rather
 * than a Material `AssistChip`, which would announce itself as a button to TalkBack.
 * Colours come from the theme's container roles, so they work in light and dark mode and
 * with dynamic colour.
 */

/** OPEN / Claim pending / Returned. */
@Composable
fun StatusChip(status: ItemStatus, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (status) {
        ItemStatus.OPEN -> colors.primaryContainer to colors.onPrimaryContainer
        ItemStatus.CLAIM_PENDING -> colors.tertiaryContainer to colors.onTertiaryContainer
        ItemStatus.RETURNED -> colors.surfaceVariant to colors.onSurfaceVariant
    }
    LabelChip(stringResource(status.labelRes()), container, content, modifier)
}

/** Pending / Accepted / Rejected / Cancelled. */
@Composable
fun ClaimStatusChip(status: ClaimStatus, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (status) {
        ClaimStatus.PENDING -> colors.tertiaryContainer to colors.onTertiaryContainer
        ClaimStatus.ACCEPTED -> colors.primaryContainer to colors.onPrimaryContainer
        ClaimStatus.REJECTED -> colors.errorContainer to colors.onErrorContainer
        ClaimStatus.CANCELLED -> colors.surfaceVariant to colors.onSurfaceVariant
    }
    LabelChip(stringResource(status.labelRes()), container, content, modifier)
}

/** Lost / Found, for lists that mix both types (My posts, matches). */
@Composable
fun ItemTypeLabel(type: ItemType, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (type) {
        ItemType.LOST -> colors.secondaryContainer to colors.onSecondaryContainer
        ItemType.FOUND -> colors.inversePrimary to colors.onSurface
    }
    LabelChip(stringResource(type.labelRes()), container, content, modifier)
}

@Composable
private fun LabelChip(text: String, containerColor: Color, contentColor: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun StatusChipPreview() {
    MatchItCampusTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(8.dp)) {
            ItemTypeLabel(ItemType.LOST)
            ItemStatus.entries.forEach { StatusChip(it) }
            ClaimStatusChip(ClaimStatus.REJECTED)
        }
    }
}
