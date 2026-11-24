package com.matchitcampus.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.matchitcampus.app.R
import com.matchitcampus.app.domain.model.CampusZone
import com.matchitcampus.app.ui.util.labelRes
import kotlinx.coroutines.launch

/**
 * A bottom sheet listing the campus zones, for tagging an item's location by hand.
 *
 * `ModalBottomSheet` suits a short pick-one list: it slides over the form, keeps the form's
 * context visible above it, and closes with a swipe or a tap outside.
 *
 * Choosing a zone first animates the sheet closed (`sheetState.hide()`), and only then
 * reports the choice, so the sheet doesn't vanish abruptly when the caller removes it.
 *
 * @param onDismiss called when the sheet is closed without a choice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZonePickerSheet(
    zones: List<CampusZone>,
    selectedZoneId: String?,
    onZoneSelected: (CampusZone) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            text = stringResource(R.string.zone_picker_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyColumn {
            items(zones, key = { it.id }) { zone ->
                ListItem(
                    headlineContent = { Text(zone.name) },
                    supportingContent = { Text(stringResource(zone.type.labelRes())) },
                    trailingContent = {
                        if (zone.id == selectedZoneId) {
                            Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.selected))
                        }
                    },
                    modifier = Modifier.clickable {
                        scope.launch { sheetState.hide() }.invokeOnCompletion { onZoneSelected(zone) }
                    },
                )
            }
        }
    }
}
