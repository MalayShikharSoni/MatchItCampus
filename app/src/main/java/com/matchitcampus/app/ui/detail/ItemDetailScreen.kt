package com.matchitcampus.app.ui.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.matchitcampus.app.R
import com.matchitcampus.app.domain.model.Claim
import com.matchitcampus.app.domain.model.ClaimStatus
import com.matchitcampus.app.domain.model.Item
import com.matchitcampus.app.domain.model.ItemStatus
import com.matchitcampus.app.domain.model.ItemType
import com.matchitcampus.app.location.mapPosition
import com.matchitcampus.app.ui.components.ClaimCard
import com.matchitcampus.app.ui.components.ClaimPerspective
import com.matchitcampus.app.ui.components.EmptyState
import com.matchitcampus.app.ui.components.ItemThumbnail
import com.matchitcampus.app.ui.components.ItemTypeLabel
import com.matchitcampus.app.ui.components.LoadingState
import com.matchitcampus.app.ui.components.MatchScoreBar
import com.matchitcampus.app.ui.components.PreviewData
import com.matchitcampus.app.ui.components.StatusChip
import com.matchitcampus.app.ui.map.ItemMiniMap
import com.matchitcampus.app.ui.theme.MatchItCampusTheme
import com.matchitcampus.app.ui.util.Formatters
import com.matchitcampus.app.ui.util.relativeEventDate
import java.io.File

/** Everything the detail screen can ask its caller or ViewModel to do. */
class ItemDetailCallbacks(
    val onBack: () -> Unit = {},
    val onOpenItem: (String) -> Unit = {},
    val onEdit: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onMarkReturned: () -> Unit = {},
    val onAccept: (Claim) -> Unit = {},
    val onReject: (Claim) -> Unit = {},
    val onCancelClaim: (Claim) -> Unit = {},
    val onClaim: () -> Unit = {},
    val onConfirm: () -> Unit = {},
    val onDismissConfirmation: () -> Unit = {},
    val onClaimAnswerChange: (String) -> Unit = {},
    val onClaimMessageChange: (String) -> Unit = {},
    val onClaimLinkChange: (String?) -> Unit = {},
    val onClaimSubmit: () -> Unit = {},
    val onClaimDismiss: () -> Unit = {},
)

/**
 * Stateful entry point for an item's screen. Collects one-shot events in a `LaunchedEffect`:
 * a snackbar for messages, and leaving the screen after a delete.
 */
@Composable
fun ItemDetailRoute(
    onBack: () -> Unit,
    onOpenItem: (itemId: String) -> Unit,
    onEdit: (itemId: String) -> Unit,
    viewModel: ItemDetailViewModel = viewModel(factory = ItemDetailViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val claimDraft by viewModel.claimDraft.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val currentOnBack by rememberUpdatedState(onBack)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                ItemDetailEvent.Deleted -> currentOnBack()
                is ItemDetailEvent.Message -> snackbarHostState.showSnackbar(context.getString(event.text))
            }
        }
    }

    // remember: build the callbacks object once, not on every recomposition.
    val callbacks = remember(viewModel, onBack, onOpenItem, onEdit) {
        ItemDetailCallbacks(
            onBack = onBack,
            onOpenItem = onOpenItem,
            onEdit = { onEdit(viewModel.itemId) },
            onDelete = viewModel::onDeleteClick,
            onMarkReturned = viewModel::onMarkReturnedClick,
            onAccept = viewModel::onAcceptClick,
            onReject = viewModel::onRejectClick,
            onCancelClaim = viewModel::onCancelClaimClick,
            onClaim = viewModel::onClaimClick,
            onConfirm = viewModel::onConfirm,
            onDismissConfirmation = viewModel::onDismissConfirmation,
            onClaimAnswerChange = viewModel::onClaimAnswerChange,
            onClaimMessageChange = viewModel::onClaimMessageChange,
            onClaimLinkChange = viewModel::onClaimLinkChange,
            onClaimSubmit = viewModel::onClaimSubmit,
            onClaimDismiss = viewModel::onClaimDismiss,
        )
    }
    ItemDetailScreen(state = state, claimDraft = claimDraft, snackbarHostState = snackbarHostState, callbacks = callbacks)
}

/**
 * An item's details, actions and possible matches, in one scrolling `LazyColumn` (the matches
 * and claims are lists, and a LazyColumn only composes the rows on screen).
 *
 * The callbacks are grouped in [ItemDetailCallbacks] because this screen has many actions.
 *
 * @param claimDraft the open claim sheet's fields (null = closed), passed apart from [state]
 *   so typing in the sheet is never delayed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(
    state: ItemDetailUiState,
    claimDraft: ClaimDraft?,
    snackbarHostState: SnackbarHostState,
    callbacks: ItemDetailCallbacks,
    modifier: Modifier = Modifier,
) {
    val item = state.item
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_item_detail)) },
                navigationIcon = {
                    IconButton(onClick = callbacks.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (state.isOwner) {
                        IconButton(onClick = callbacks.onEdit, enabled = !state.isBusy) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit))
                        }
                        IconButton(onClick = callbacks.onDelete, enabled = !state.isBusy) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when {
            state.isLoading -> LoadingState(Modifier.padding(innerPadding))
            item == null -> EmptyState(
                title = stringResource(R.string.detail_gone_title),
                body = stringResource(R.string.detail_gone_body),
                modifier = Modifier.padding(innerPadding),
            )
            else -> DetailContent(state, item, callbacks, Modifier.padding(innerPadding))
        }
    }

    state.confirmation?.let { ConfirmationDialog(it, callbacks) }
    if (item != null && claimDraft != null) {
        ClaimSheet(item, claimDraft, state.myOpenLostItems, callbacks)
    }
}

@Composable
private fun DetailContent(state: ItemDetailUiState, item: Item, callbacks: ItemDetailCallbacks, modifier: Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header", contentType = "header") { ItemHeader(state, item) }
        item(key = "actions", contentType = "actions") { ItemActions(state, item, callbacks) }

        if (state.isOwner && item.type == ItemType.FOUND && state.claims.isNotEmpty()) {
            item(key = "claimsTitle", contentType = "title") {
                SectionTitle(pluralStringResource(R.plurals.detail_claims_title, state.claims.size, state.claims.size))
            }
            items(state.claims, key = { "claim-${it.id}" }, contentType = { "claim" }) { claim ->
                ClaimCard(claim = claim, perspective = ClaimPerspective.INCOMING) {
                    if (claim.status == ClaimStatus.PENDING) {
                        TextButton(onClick = { callbacks.onReject(claim) }, enabled = !state.isBusy) {
                            Text(stringResource(R.string.action_reject))
                        }
                        Button(onClick = { callbacks.onAccept(claim) }, enabled = !state.isBusy) {
                            Text(stringResource(R.string.action_accept))
                        }
                    }
                }
            }
        }

        item(key = "matchesTitle", contentType = "title") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                HorizontalDivider()
                SectionTitle(stringResource(R.string.detail_matches_title))
                Text(
                    text = stringResource(
                        when {
                            item.status != ItemStatus.OPEN -> R.string.detail_matches_paused
                            state.matches.isEmpty() -> R.string.detail_matches_empty
                            else -> R.string.detail_matches_subtitle
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(state.matches, key = { "match-${it.candidate.id}" }, contentType = { "match" }) { match ->
            MatchRow(match, onClick = { callbacks.onOpenItem(match.candidate.id) })
        }
    }
}

@Composable
private fun ItemHeader(state: ItemDetailUiState, item: Item) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ItemPhoto(item, state.photoUnavailable)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ItemTypeLabel(item.type)
            StatusChip(item.status)
        }
        Text(item.title, style = MaterialTheme.typography.headlineSmall)
        Text(item.description, style = MaterialTheme.typography.bodyLarge)
        InfoLine(stringResource(R.string.field_category), item.category.displayName)
        item.color?.let { InfoLine(stringResource(R.string.field_color_short), it) }
        InfoLine(
            label = stringResource(if (item.type == ItemType.LOST) R.string.field_date_lost else R.string.field_date_found),
            value = "${Formatters.eventDate(item.eventDate)} (${relativeEventDate(item.eventDate)})",
        )
        InfoLine(
            label = stringResource(R.string.field_zone),
            value = listOfNotNull(item.zone?.name, item.addressLine).joinToString(", ")
                .ifEmpty { stringResource(R.string.detail_location_unknown) },
        )
        // The pin, or the centre of a real zone. Previews skip it: MapLibre can't render there.
        val position = item.mapPosition()
        if (position != null && !LocalInspectionMode.current) {
            ItemMiniMap(position = position, type = item.type)
        }
        InfoLine(
            label = stringResource(R.string.detail_posted_by),
            value = if (state.isOwner) stringResource(R.string.detail_posted_by_you) else item.ownerName,
        )
        if (state.isOwner && item.verificationQuestion != null) {
            InfoLine(stringResource(R.string.field_question), item.verificationQuestion)
        }
    }
}

/**
 * The photo if this phone has it. While a download is in progress there's a spinner; if it
 * failed (e.g. offline), a short note instead.
 */
@Composable
private fun ItemPhoto(item: Item, unavailable: Boolean) {
    val photoModifier = Modifier
        .fillMaxWidth()
        .height(220.dp)
        .clip(MaterialTheme.shapes.large)
    when {
        item.localPhotoPath != null -> AsyncImage(
            model = File(item.localPhotoPath),
            contentDescription = stringResource(R.string.post_photo_description),
            contentScale = ContentScale.Crop,
            modifier = photoModifier,
        )
        item.hasPhoto && !unavailable -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(stringResource(R.string.detail_photo_loading), style = MaterialTheme.typography.bodySmall)
        }
        item.hasPhoto -> Text(
            text = stringResource(R.string.detail_photo_unavailable),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

/** The buttons that depend on who is looking: owner, claimant, or anyone else. */
@Composable
private fun ItemActions(state: ItemDetailUiState, item: Item, callbacks: ItemDetailCallbacks) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.isOwner && item.status != ItemStatus.RETURNED) {
            OutlinedButton(onClick = callbacks.onMarkReturned, enabled = !state.isBusy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_mark_returned))
            }
        }
        state.myClaim?.let { claim ->
            ClaimCard(claim = claim, perspective = ClaimPerspective.OUTGOING) {
                if (claim.status == ClaimStatus.PENDING) {
                    TextButton(onClick = { callbacks.onCancelClaim(claim) }, enabled = !state.isBusy) {
                        Text(stringResource(R.string.action_cancel_claim))
                    }
                }
            }
        }
        if (state.canClaim) {
            Button(onClick = callbacks.onClaim, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_this_is_mine))
            }
        }
    }
}

/**
 * One possible match: the other item, an animated score bar, and an expandable "Why this
 * match" list. Whether it's expanded is UI-only state, kept with `rememberSaveable` so it
 * survives rotation.
 */
@Composable
private fun MatchRow(match: MatchUi, onClick: () -> Unit) {
    var expanded by rememberSaveable(match.candidate.id) { mutableStateOf(false) }
    Card(onClick = onClick, colors = CardDefaults.outlinedCardColors(), border = CardDefaults.outlinedCardBorder()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                ItemThumbnail(match.candidate, Modifier.size(48.dp))
                Column(Modifier.weight(1f)) {
                    Text(match.candidate.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    Text(
                        text = listOfNotNull(match.candidate.zone?.name, relativeEventDate(match.candidate.eventDate)).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            MatchScoreBar(score = match.result.score)
            TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Text(stringResource(R.string.detail_why_match))
                Icon(if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null)
            }
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    match.why.forEach { line -> Text("• ${whyText(line)}", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable
private fun whyText(line: WhyLine): String = when (line) {
    is WhyLine.Text -> stringResource(if (line.fromKeywords) R.string.why_text_keywords else R.string.why_text, line.percent)
    WhyLine.SameCategory -> stringResource(R.string.why_same_category)
    WhyLine.CategoryUnclear -> stringResource(R.string.why_category_unclear)
    WhyLine.DifferentCategory -> stringResource(R.string.why_different_category)
    is WhyLine.SameZone -> stringResource(R.string.why_same_zone, line.zoneName)
    is WhyLine.Distance -> stringResource(R.string.why_distance, Formatters.distance(line.meters))
    WhyLine.DifferentZones -> stringResource(R.string.why_different_zones)
    WhyLine.LocationUnknown -> stringResource(R.string.why_location_unknown)
    is WhyLine.DaysApart ->
        if (line.days == 0) stringResource(R.string.why_same_day) else pluralStringResource(R.plurals.why_days_apart, line.days, line.days)
}

/** `AlertDialog` before anything that can't be undone easily. */
@Composable
private fun ConfirmationDialog(confirmation: Confirmation, callbacks: ItemDetailCallbacks) {
    val (title, body, confirmLabel) = when (confirmation) {
        Confirmation.Delete -> Triple(R.string.detail_confirm_delete_title, R.string.detail_confirm_delete_body, R.string.action_delete)
        Confirmation.MarkReturned -> Triple(R.string.detail_confirm_returned_title, R.string.detail_confirm_returned_body, R.string.action_mark_returned)
        is Confirmation.AcceptClaim -> Triple(R.string.claim_confirm_accept_title, R.string.claim_confirm_accept_body, R.string.action_accept)
    }
    AlertDialog(
        onDismissRequest = callbacks.onDismissConfirmation,
        title = { Text(stringResource(title)) },
        text = {
            Text(
                if (confirmation is Confirmation.AcceptClaim) stringResource(body, confirmation.claimantName) else stringResource(body),
            )
        },
        confirmButton = { TextButton(onClick = callbacks.onConfirm) { Text(stringResource(confirmLabel)) } },
        dismissButton = { TextButton(onClick = callbacks.onDismissConfirmation) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * The "This is mine" sheet: the finder's question, the answer, an optional message, and an
 * optional link to one of my own lost posts (a radio group).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClaimSheet(item: Item, draft: ClaimDraft, myLostItems: List<Item>, callbacks: ItemDetailCallbacks) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val problem = draft.problem(hasQuestion = item.verificationQuestion != null).takeIf { draft.showErrors }
    ModalBottomSheet(onDismissRequest = callbacks.onClaimDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.claim_sheet_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.claim_sheet_body), style = MaterialTheme.typography.bodyMedium)
            item.verificationQuestion?.let { question ->
                Text(stringResource(R.string.claim_question, question), style = MaterialTheme.typography.titleSmall)
            }
            OutlinedTextField(
                value = draft.answer,
                onValueChange = callbacks.onClaimAnswerChange,
                label = { Text(stringResource(R.string.claim_field_answer)) },
                isError = problem == R.string.claim_error_answer_required || problem == R.string.claim_error_answer_long,
                supportingText = problem?.takeIf { it != R.string.claim_error_message_long }?.let { { Text(stringResource(it)) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.message,
                onValueChange = callbacks.onClaimMessageChange,
                label = { Text(stringResource(R.string.claim_field_message)) },
                isError = problem == R.string.claim_error_message_long,
                supportingText = problem?.takeIf { it == R.string.claim_error_message_long }?.let { { Text(stringResource(it)) } },
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            if (myLostItems.isNotEmpty()) {
                Text(stringResource(R.string.claim_link_title), style = MaterialTheme.typography.titleSmall)
                // selectableGroup + selectable(role = RadioButton): TalkBack reads this as one radio group.
                Column(Modifier.selectableGroup()) {
                    LinkOption(stringResource(R.string.claim_link_none), selected = draft.linkedLostItemId == null) {
                        callbacks.onClaimLinkChange(null)
                    }
                    myLostItems.forEach { lost ->
                        LinkOption(lost.title, selected = draft.linkedLostItemId == lost.id) { callbacks.onClaimLinkChange(lost.id) }
                    }
                }
            }
            Button(onClick = callbacks.onClaimSubmit, enabled = !draft.isSubmitting, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_send_claim))
            }
        }
    }
}

@Composable
private fun LinkOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
    }
}

@Preview(showBackground = true, heightDp = 1100)
@Composable
private fun ItemDetailScreenPreview() {
    MatchItCampusTheme {
        ItemDetailScreen(
            state = ItemDetailUiState(
                isLoading = false,
                item = PreviewData.foundItem,
                isOwner = true,
                claims = listOf(PreviewData.claim),
                matches = listOf(
                    MatchUi(
                        result = PreviewData.match,
                        candidate = PreviewData.lostItem,
                        why = MatchExplanation.lines(PreviewData.match, PreviewData.foundItem, PreviewData.lostItem),
                    ),
                ),
            ),
            claimDraft = null,
            snackbarHostState = remember { SnackbarHostState() },
            callbacks = ItemDetailCallbacks(),
        )
    }
}
