package com.matchitcampus.app.ui.post

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.matchitcampus.app.R
import com.matchitcampus.app.domain.model.Category
import com.matchitcampus.app.domain.model.ItemType
import com.matchitcampus.app.location.LatLng
import com.matchitcampus.app.ui.components.LocationPermissionGate
import com.matchitcampus.app.ui.components.LoadingState
import com.matchitcampus.app.ui.components.PreviewData
import com.matchitcampus.app.ui.components.ZonePickerSheet
import com.matchitcampus.app.ui.theme.MatchItCampusTheme
import com.matchitcampus.app.ui.util.Formatters
import java.io.File

/** Test tags for Compose UI tests (Phase 8). */
object PostItemTestTags {
    const val TYPE_LOST = "post_type_lost"
    const val TYPE_FOUND = "post_type_found"
    const val TITLE = "post_title"
    const val DESCRIPTION = "post_description"
    const val COLOR = "post_color"
    const val QUESTION = "post_question"
    const val SUBMIT = "post_submit"
}

/**
 * Stateful entry point for the Post screen.
 *
 * - The Photo Picker launcher lives here, not in [PostItemScreen], because it needs a real
 *   Activity (previews and UI tests don't have one). `PickVisualMedia` uses the system Photo
 *   Picker, so the app needs no storage permission.
 * - `LocationPermissionGate` also lives here (it needs an Activity too). It turns "Use my
 *   location" into a permission request first, then into [PostItemAction.UseMyLocation].
 * - The pin picker is a separate screen. Its result comes back as [pickedPin] (see
 *   `MainNavHost`); a `LaunchedEffect` keyed on it hands it to the ViewModel once, then
 *   [onPinConsumed] clears it so rotation doesn't apply it twice.
 * - `LaunchedEffect(Unit)` collects the ViewModel's one-shot events while the screen is shown.
 *   `rememberUpdatedState` makes sure that long-running coroutine always calls the newest
 *   `onSaved` lambda, even if the caller passes a new one on recomposition.
 */
@Composable
fun PostItemRoute(
    onBack: () -> Unit,
    onSaved: (itemId: String, wasEdit: Boolean) -> Unit,
    onPickPin: (current: LatLng?) -> Unit,
    pickedPin: LatLng?,
    onPinConsumed: () -> Unit,
    viewModel: PostItemViewModel = viewModel(factory = PostItemViewModel.Factory),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val currentOnSaved by rememberUpdatedState(onSaved)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PostItemEvent.Saved -> currentOnSaved(event.itemId, event.wasEdit)
                is PostItemEvent.Message -> snackbarHostState.showSnackbar(
                    if (event.arg != null) context.getString(event.text, event.arg) else context.getString(event.text),
                )
            }
        }
    }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        // null means the user closed the picker without choosing.
        if (uri != null) viewModel.onAction(PostItemAction.PhotoPicked(uri))
    }

    LaunchedEffect(pickedPin) {
        if (pickedPin != null) {
            viewModel.onAction(PostItemAction.PinPicked(pickedPin))
            onPinConsumed()
        }
    }

    LocationPermissionGate(onGranted = { precise -> viewModel.onAction(PostItemAction.UseMyLocation(precise)) }) { requestLocation ->
        PostItemScreen(
            form = form,
            state = state,
            snackbarHostState = snackbarHostState,
            onAction = viewModel::onAction,
            onPickPhoto = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onUseMyLocation = requestLocation,
            onPickPin = {
                val lat = form.latitude
                val lng = form.longitude
                onPickPin(if (lat != null && lng != null) LatLng(lat, lng) else null)
            },
            onBack = onBack,
        )
    }
}

/**
 * The Post/Edit form. Stateless: [form] and [state] come in, every user action goes out
 * through [onAction] (or [onPickPhoto] / [onBack]).
 *
 * Small UI-only state (is the date picker / zone sheet open?) is kept here with
 * `rememberSaveable`, so an open dialog survives rotation without involving the ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostItemScreen(
    form: PostItemForm,
    state: PostItemUiState,
    snackbarHostState: SnackbarHostState,
    onAction: (PostItemAction) -> Unit,
    onPickPhoto: () -> Unit,
    onUseMyLocation: () -> Unit,
    onPickPin: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showZonePicker by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isEditing) R.string.title_edit_item else R.string.title_post_item)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (state.isLoadingItem) {
            LoadingState(Modifier.padding(innerPadding))
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TypeSelector(form.type, enabled = !state.isEditing, onAction = onAction)

            OutlinedTextField(
                value = form.title,
                onValueChange = { onAction(PostItemAction.TitleChanged(it)) },
                label = { Text(stringResource(R.string.field_title)) },
                placeholder = { Text(stringResource(R.string.field_title_hint)) },
                singleLine = true,
                isError = PostField.TITLE in state.visibleErrors,
                supportingText = fieldSupportingText(state, PostField.TITLE),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(PostItemTestTags.TITLE),
            )
            OutlinedTextField(
                value = form.description,
                onValueChange = { onAction(PostItemAction.DescriptionChanged(it)) },
                label = { Text(stringResource(R.string.field_description)) },
                placeholder = { Text(stringResource(R.string.field_description_hint)) },
                minLines = 3,
                isError = PostField.DESCRIPTION in state.visibleErrors,
                supportingText = fieldSupportingText(state, PostField.DESCRIPTION),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(PostItemTestTags.DESCRIPTION),
            )
            CategoryField(form.category, state, onAction)
            OutlinedTextField(
                value = form.color,
                onValueChange = { onAction(PostItemAction.ColorChanged(it)) },
                label = { Text(stringResource(R.string.field_color)) },
                placeholder = { Text(stringResource(R.string.field_color_hint)) },
                singleLine = true,
                isError = PostField.COLOR in state.visibleErrors,
                supportingText = fieldSupportingText(state, PostField.COLOR),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(PostItemTestTags.COLOR),
            )
            ClickableField(
                label = stringResource(if (form.type == ItemType.LOST) R.string.field_date_lost else R.string.field_date_found),
                value = Formatters.eventDate(form.eventDate),
                icon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                isError = PostField.DATE in state.visibleErrors,
                supportingText = fieldSupportingText(state, PostField.DATE),
                onClick = { showDatePicker = true },
            )
            ClickableField(
                label = stringResource(R.string.field_zone),
                value = state.zones.firstOrNull { it.id == form.zoneId }?.name.orEmpty(),
                icon = { Icon(Icons.Filled.Place, contentDescription = null) },
                isError = PostField.ZONE in state.visibleErrors,
                supportingText = fieldSupportingText(state, PostField.ZONE),
                onClick = { showZonePicker = true },
            )
            LocationSection(
                form = form,
                isLocating = state.isLocating,
                onUseMyLocation = onUseMyLocation,
                onPickPin = onPickPin,
                onClearPin = { onAction(PostItemAction.ClearPin) },
            )
            // Only finders ask a question. AnimatedVisibility slides it in when "Found" is chosen.
            AnimatedVisibility(visible = form.type == ItemType.FOUND) {
                OutlinedTextField(
                    value = form.verificationQuestion,
                    onValueChange = { onAction(PostItemAction.QuestionChanged(it)) },
                    label = { Text(stringResource(R.string.field_question)) },
                    placeholder = { Text(stringResource(R.string.field_question_hint)) },
                    isError = PostField.QUESTION in state.visibleErrors,
                    supportingText = fieldSupportingText(state, PostField.QUESTION)
                        ?: { Text(stringResource(R.string.field_question_help)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(PostItemTestTags.QUESTION),
                )
            }
            PhotoSection(form, state.isProcessingPhoto, onPickPhoto, onRemove = { onAction(PostItemAction.RemovePhoto) })

            Button(
                onClick = { onAction(PostItemAction.Submit) },
                enabled = state.canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(PostItemTestTags.SUBMIT),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(if (state.isEditing) R.string.action_save_changes else R.string.action_post_item))
                }
            }
            if (state.missingFields.isNotEmpty()) {
                val names = state.missingFields.map { stringResource(it.labelRes()) }
                Text(
                    text = stringResource(R.string.post_still_needed, names.joinToString(", ")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showDatePicker) {
        EventDatePicker(
            selected = form.eventDate,
            latestAllowed = state.todayEventDate,
            onPicked = { onAction(PostItemAction.DateChanged(it)) },
            onDismiss = { showDatePicker = false },
        )
    }
    if (showZonePicker) {
        ZonePickerSheet(
            zones = state.zones,
            selectedZoneId = form.zoneId,
            onZoneSelected = {
                onAction(PostItemAction.ZoneChanged(it.id))
                showZonePicker = false
            },
            onDismiss = { showZonePicker = false },
        )
    }
}

@Composable
private fun TypeSelector(selected: ItemType, enabled: Boolean, onAction: (PostItemAction) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        ItemType.entries.forEachIndexed { index, type ->
            SegmentedButton(
                selected = selected == type,
                onClick = { onAction(PostItemAction.TypeChanged(type)) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = ItemType.entries.size),
                enabled = enabled,
                modifier = Modifier.testTag(if (type == ItemType.LOST) PostItemTestTags.TYPE_LOST else PostItemTestTags.TYPE_FOUND),
            ) {
                Text(stringResource(if (type == ItemType.LOST) R.string.post_type_lost else R.string.post_type_found))
            }
        }
    }
}

/** `ExposedDropdownMenuBox`: a read-only text field that opens a menu of the categories. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryField(category: Category?, state: PostItemUiState, onAction: (PostItemAction) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = category?.displayName.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.field_category)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            isError = PostField.CATEGORY in state.visibleErrors,
            supportingText = fieldSupportingText(state, PostField.CATEGORY),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Category.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.displayName) },
                    onClick = {
                        onAction(PostItemAction.CategoryChanged(option))
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * A read-only field that opens a picker when tapped. A text field normally takes the tap
 * itself (to show the keyboard), so a transparent clickable box is laid over it.
 *
 * Accessibility: the overlay hides the field from TalkBack, so the overlay itself carries the
 * label and value ("Where on campus, Block 34") and the Button role, and the field underneath
 * is cleared from the semantics tree so it isn't announced twice.
 */
@Composable
private fun ClickableField(
    label: String,
    value: String,
    icon: @Composable () -> Unit,
    isError: Boolean,
    supportingText: (@Composable () -> Unit)?,
    onClick: () -> Unit,
) {
    Box {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = icon,
            isError = isError,
            supportingText = supportingText,
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics {},
        )
        Box(
            Modifier
                .matchParentSize()
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = if (value.isEmpty()) label else "$label, $value" },
        )
    }
}

/** Material 3 date picker. Future days can't be selected: you can't lose something tomorrow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventDatePicker(selected: Long, latestAllowed: Long, onPicked: (Long) -> Unit, onDismiss: () -> Unit) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = selected,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= latestAllowed
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                pickerState.selectedDateMillis?.let(onPicked)
                onDismiss()
            }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) {
        DatePicker(state = pickerState)
    }
}

/**
 * Optional exact spot under the zone field: "Use my location" (GPS fix, address and a suggested
 * zone) or "Drop a pin" (the full-screen picker). The zone stays the required part; the pin
 * makes matching's distance score more precise.
 */
@Composable
private fun LocationSection(
    form: PostItemForm,
    isLocating: Boolean,
    onUseMyLocation: () -> Unit,
    onPickPin: () -> Unit,
    onClearPin: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onUseMyLocation, enabled = !isLocating) {
                Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.post_use_my_location), modifier = Modifier.padding(start = 6.dp))
            }
            OutlinedButton(onClick = onPickPin, enabled = !isLocating) {
                Icon(Icons.Filled.Place, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    stringResource(if (form.hasPin) R.string.post_move_pin else R.string.post_drop_pin),
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
        if (isLocating) LinearProgressIndicator(Modifier.fillMaxWidth())
        val lat = form.latitude
        val lng = form.longitude
        if (lat != null && lng != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.post_pin_set, form.addressLine ?: Formatters.coordinates(lat, lng)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClearPin) { Text(stringResource(R.string.post_remove_pin)) }
            }
        }
    }
}

@Composable
private fun PhotoSection(form: PostItemForm, isProcessing: Boolean, onPickPhoto: () -> Unit, onRemove: () -> Unit) {
    val shownPath = form.shownPhotoPath
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            isProcessing -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.post_photo_processing))
            }
            shownPath != null -> AsyncImage(
                model = File(shownPath),
                contentDescription = stringResource(R.string.post_photo_description),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(MaterialTheme.shapes.medium),
            )
            form.willHavePhoto -> Text(
                // Editing an item whose photo isn't on this phone: it's kept unless removed.
                text = stringResource(R.string.post_photo_kept),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onPickPhoto, enabled = !isProcessing) {
                Text(stringResource(if (form.willHavePhoto) R.string.action_change_photo else R.string.action_add_photo))
            }
            if (form.willHavePhoto) {
                TextButton(onClick = onRemove, enabled = !isProcessing) { Text(stringResource(R.string.action_remove_photo)) }
            }
        }
    }
}

/** The error under a field, or null so the field keeps its normal look. */
private fun fieldSupportingText(state: PostItemUiState, field: PostField): (@Composable () -> Unit)? {
    val error = state.visibleErrors[field] ?: return null
    return { Text(stringResource(errorRes(field, error))) }
}

private fun errorRes(field: PostField, error: FieldError): Int = when (field) {
    PostField.TITLE -> when (error) {
        FieldError.TOO_SHORT -> R.string.post_error_title_short
        FieldError.TOO_LONG -> R.string.post_error_title_long
        else -> R.string.post_error_title_required
    }
    PostField.DESCRIPTION ->
        if (error == FieldError.TOO_LONG) R.string.post_error_description_long else R.string.post_error_description_required
    PostField.CATEGORY -> R.string.post_error_category_required
    PostField.COLOR -> R.string.post_error_color_long
    PostField.DATE -> R.string.post_error_date_future
    PostField.ZONE -> R.string.post_error_zone_required
    PostField.QUESTION ->
        if (error == FieldError.TOO_LONG) R.string.post_error_question_long else R.string.post_error_question_required
}

private fun PostField.labelRes(): Int = when (this) {
    PostField.TITLE -> R.string.field_title
    PostField.DESCRIPTION -> R.string.field_description
    PostField.CATEGORY -> R.string.field_category
    PostField.COLOR -> R.string.field_color
    PostField.DATE -> R.string.field_date
    PostField.ZONE -> R.string.field_zone
    PostField.QUESTION -> R.string.field_question
}

@Preview(showBackground = true, heightDp = 1000)
@Composable
private fun PostItemScreenPreview() {
    MatchItCampusTheme {
        PostItemScreen(
            form = PostItemForm(
                type = ItemType.FOUND,
                title = "Black earphone case",
                eventDate = 1_795_000_000_000L,
                latitude = 31.2551,
                longitude = 75.7049,
                addressLine = "Block 34, Phagwara",
            ),
            state = PostItemUiState(
                zones = PreviewData.zones,
                missingFields = listOf(PostField.DESCRIPTION, PostField.CATEGORY),
                visibleErrors = mapOf(PostField.DESCRIPTION to FieldError.REQUIRED),
                todayEventDate = 1_795_000_000_000L,
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
            onPickPhoto = {},
            onUseMyLocation = {},
            onPickPin = {},
            onBack = {},
        )
    }
}
