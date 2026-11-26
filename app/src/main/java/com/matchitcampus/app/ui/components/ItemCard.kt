package com.matchitcampus.app.ui.components

import androidx.compose.material3.CardDefaults
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.matchitcampus.app.domain.model.Item
import com.matchitcampus.app.ui.theme.MatchItCampusTheme
import com.matchitcampus.app.ui.util.relativeEventDate
import java.io.File

/**
 * One item in a list: thumbnail, title, zone, relative date and status.
 *
 * Stateless and slot-based: the caller decides what tapping does ([onClick]) and can add
 * something at the end of the row ([trailing], e.g. the "⋮" menu in My posts) without this
 * component knowing about it.
 *
 * @param showType show a Lost/Found label, for lists that mix both types.
 * @param selected highlights the card, e.g. the item open in the two-pane layout's right pane.
 */
@Composable
fun ItemCard(
    item: Item,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showType: Boolean = false,
    selected: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
) {
    Card(
        onClick = onClick,
        colors = if (selected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            CardDefaults.cardColors()
        },
        // animateContentSize: when the status chip or title changes length after a sync,
        // the card grows or shrinks smoothly instead of jumping.
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ItemThumbnail(item, Modifier.size(64.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(item.zone?.name, relativeEventDate(item.eventDate)).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (showType) ItemTypeLabel(item.type)
                    StatusChip(item.status)
                }
            }
            trailing?.invoke()
        }
    }
}

/**
 * The item's photo if this phone has a copy, otherwise the category's first letter on a
 * tinted square. Feed thumbnails never download anything: photos are fetched only when an
 * item is opened (to save Firestore reads), and then they show here too.
 *
 * Coil's `AsyncImage` decodes the file off the main thread and caches the bitmap in memory.
 */
@Composable
fun ItemThumbnail(item: Item, modifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.medium
    if (item.localPhotoPath != null) {
        AsyncImage(
            model = File(item.localPhotoPath),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(shape),
        )
    } else {
        Box(
            modifier = modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = item.category.displayName.take(1),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ItemCardPreview() {
    MatchItCampusTheme {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ItemCard(item = PreviewData.foundItem, onClick = {})
            ItemCard(item = PreviewData.lostItem, onClick = {}, showType = true)
        }
    }
}
