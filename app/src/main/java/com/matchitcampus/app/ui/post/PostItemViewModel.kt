package com.matchitcampus.app.ui.post

import android.net.Uri
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.matchitcampus.app.R
import com.matchitcampus.app.data.photo.PhotoTooLargeException
import com.matchitcampus.app.domain.model.CampusZone
import com.matchitcampus.app.domain.model.Category
import com.matchitcampus.app.domain.model.Item
import com.matchitcampus.app.domain.model.ItemStatus
import com.matchitcampus.app.domain.model.ItemType
import com.matchitcampus.app.domain.model.SyncState
import com.matchitcampus.app.ui.util.Formatters
import com.matchitcampus.app.ui.util.appContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

private const val TAG = "MatchIt/PostItemViewModel"

/** Everything the Post screen shows besides the form fields themselves. */
@Immutable
data class PostItemUiState(
    val isEditing: Boolean = false,
    val zones: List<CampusZone> = emptyList(),
    /** Problems the user should see now (only for fields they've touched, or after a submit attempt). */
    val visibleErrors: Map<PostField, FieldError> = emptyMap(),
    /** Every invalid field; posting is blocked while this isn't empty. */
    val missingFields: List<PostField> = emptyList(),
    val todayEventDate: Long = 0L,
    val isLoadingItem: Boolean = false,
    val isProcessingPhoto: Boolean = false,
    val isSaving: Boolean = false,
) {
    val canSubmit: Boolean get() = missingFields.isEmpty() && !isSaving && !isProcessingPhoto && !isLoadingItem
}

/** What the user did on the Post screen. One sealed type keeps the screen's API to a single `onAction`. */
sealed interface PostItemAction {
    data class TypeChanged(val type: ItemType) : PostItemAction
    data class TitleChanged(val value: String) : PostItemAction
    data class DescriptionChanged(val value: String) : PostItemAction
    data class CategoryChanged(val category: Category) : PostItemAction
    data class DateChanged(val eventDate: Long) : PostItemAction
    data class ZoneChanged(val zoneId: String) : PostItemAction
    data class QuestionChanged(val value: String) : PostItemAction
    data class PhotoPicked(val uri: Uri) : PostItemAction
    data object RemovePhoto : PostItemAction
    data object Submit : PostItemAction
}

/** One-shot results for the screen. */
sealed interface PostItemEvent {
    /** The item was saved; [wasEdit] tells the screen whether to go back or open the new item. */
    data class Saved(val itemId: String, val wasEdit: Boolean) : PostItemEvent
    data class Message(@param:StringRes val text: Int) : PostItemEvent
}

/**
 * Posting a new item, or editing one of mine.
 *
 * **The draft survives process death.** Android may kill the app in the background (e.g. while
 * the Photo Picker is open). A ViewModel doesn't survive that, but [SavedStateHandle] does: it
 * is saved in the Activity's saved state. So every change to [form] is mirrored into the handle
 * ([saveForm]), and [restoreForm] rebuilds the draft from it when the ViewModel is recreated.
 *
 * **Validation** lives in [PostItemValidator]. Errors appear under a field once the user has
 * changed it (or after pressing Post), so an empty form isn't covered in red on arrival. The
 * Post button stays disabled until the form is valid.
 *
 * **Editing:** the navigation argument `editItemId` (from `Routes.PostItem`) arrives in the
 * same [SavedStateHandle]. The item is loaded once; after that the draft in the handle wins,
 * so a rotation doesn't reload the item over the user's changes.
 */
class PostItemViewModel(
    private val data: PostItemDataSource,
    private val savedStateHandle: SavedStateHandle,
    private val todayEventDate: () -> Long = { Formatters.todayEventDateMillis() },
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    /** The item being edited, or null for a new post. Type-safe navigation stores the argument under its property name. */
    private val editItemId: String? = savedStateHandle[KEY_EDIT_ITEM_ID]

    /** The id a new post will get. Chosen once and saved, so a restored draft keeps it. */
    private val newItemId: String = savedStateHandle[KEY_NEW_ID] ?: UUID.randomUUID().toString().also {
        savedStateHandle[KEY_NEW_ID] = it
    }

    private val _form = MutableStateFlow(restoreForm())

    /** The form fields. The screen reads this directly so typing updates without delay. */
    val form: StateFlow<PostItemForm> = _form.asStateFlow()

    private val touched = MutableStateFlow(savedStateHandle.get<ArrayList<String>>(KEY_TOUCHED).orEmpty().map(PostField::valueOf).toSet())
    private val submitAttempted = MutableStateFlow(savedStateHandle[KEY_SUBMIT_ATTEMPTED] ?: false)
    private val isLoadingItem = MutableStateFlow(false)
    private val isProcessingPhoto = MutableStateFlow(false)
    private val isSaving = MutableStateFlow(false)

    private val _events = MutableSharedFlow<PostItemEvent>()

    /**
     * One-shot events (navigate after saving, show a snackbar). A SharedFlow rather than state,
     * because each should happen once, not again when the screen is recreated.
     */
    val events: SharedFlow<PostItemEvent> = _events.asSharedFlow()

    val uiState: StateFlow<PostItemUiState> = combine(
        _form,
        combine(touched, submitAttempted, ::Pair),
        combine(isLoadingItem, isProcessingPhoto, isSaving, ::Triple),
        data.observeZones().catch { e ->
            Log.e(TAG, "Couldn't load zones", e)
            emit(emptyList())
        },
    ) { currentForm, (touchedFields, attempted), (loading, processing, saving), zones ->
        val today = todayEventDate()
        val errors = PostItemValidator.validate(currentForm, today)
        PostItemUiState(
            isEditing = editItemId != null,
            zones = zones,
            visibleErrors = if (attempted) errors else errors.filterKeys { it in touchedFields },
            missingFields = PostField.entries.filter { it in errors },
            todayEventDate = today,
            isLoadingItem = loading,
            isProcessingPhoto = processing,
            isSaving = saving,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PostItemUiState(isEditing = editItemId != null))

    init {
        if (editItemId != null && savedStateHandle.get<Boolean>(KEY_LOADED) != true) loadItemForEditing(editItemId)
    }

    fun onAction(action: PostItemAction) {
        when (action) {
            is PostItemAction.TypeChanged -> if (editItemId == null) updateForm(null) { it.copy(type = action.type) }
            is PostItemAction.TitleChanged -> updateForm(PostField.TITLE) { it.copy(title = action.value) }
            is PostItemAction.DescriptionChanged -> updateForm(PostField.DESCRIPTION) { it.copy(description = action.value) }
            is PostItemAction.CategoryChanged -> updateForm(PostField.CATEGORY) { it.copy(category = action.category) }
            is PostItemAction.DateChanged -> updateForm(PostField.DATE) { it.copy(eventDate = action.eventDate) }
            is PostItemAction.ZoneChanged -> updateForm(PostField.ZONE) { it.copy(zoneId = action.zoneId) }
            is PostItemAction.QuestionChanged -> updateForm(PostField.QUESTION) { it.copy(verificationQuestion = action.value) }
            is PostItemAction.PhotoPicked -> processPhoto(action.uri)
            PostItemAction.RemovePhoto -> removePhoto()
            PostItemAction.Submit -> submit()
        }
    }

    /** Applies a change, marks [field] as touched (so its error may show), and saves the draft. */
    private fun updateForm(field: PostField?, change: (PostItemForm) -> PostItemForm) {
        _form.update(change)
        saveForm(_form.value)
        if (field != null && field !in touched.value) {
            touched.update { it + field }
            savedStateHandle[KEY_TOUCHED] = ArrayList(touched.value.map { it.name })
        }
    }

    private fun loadItemForEditing(id: String) {
        viewModelScope.launch {
            isLoadingItem.value = true
            try {
                val item = data.loadItem(id)
                if (item == null) {
                    Log.w(TAG, "Item $id to edit is gone")
                    _events.emit(PostItemEvent.Message(R.string.post_error_item_gone))
                    return@launch
                }
                _form.value = PostItemForm(
                    type = item.type,
                    title = item.title,
                    description = item.description,
                    category = item.category,
                    eventDate = item.eventDate,
                    zoneId = item.zoneId,
                    verificationQuestion = item.verificationQuestion.orEmpty(),
                    existingPhotoPath = item.localPhotoPath,
                    existingHasPhoto = item.hasPhoto,
                )
                saveForm(_form.value)
                savedStateHandle[KEY_LOADED] = true
            } finally {
                isLoadingItem.value = false
            }
        }
    }

    /** Compresses the picked image off the main thread, then swaps it in for any earlier draft. */
    private fun processPhoto(uri: Uri) {
        viewModelScope.launch {
            isProcessingPhoto.value = true
            try {
                val path = data.createPhotoDraft(uri)
                _form.value.draftPhotoPath?.let { data.discardPhotoDraft(it) }
                updateForm(null) { it.copy(draftPhotoPath = path) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: PhotoTooLargeException) {
                Log.w(TAG, "Picked photo is too large even after compression", e)
                _events.emit(PostItemEvent.Message(R.string.post_error_photo_too_large))
            } catch (e: Exception) {
                Log.e(TAG, "Couldn't read the picked photo", e)
                _events.emit(PostItemEvent.Message(R.string.post_error_photo_unreadable))
            } finally {
                isProcessingPhoto.value = false
            }
        }
    }

    private fun removePhoto() {
        _form.value.draftPhotoPath?.let { data.discardPhotoDraft(it) }
        updateForm(null) { it.copy(draftPhotoPath = null, removeExistingPhoto = true) }
    }

    private fun submit() {
        submitAttempted.value = true
        savedStateHandle[KEY_SUBMIT_ATTEMPTED] = true
        val current = _form.value
        if (PostItemValidator.validate(current, todayEventDate()).isNotEmpty() || isSaving.value) return

        viewModelScope.launch {
            isSaving.value = true
            try {
                val author = data.currentAuthor()
                if (author == null) {
                    _events.emit(PostItemEvent.Message(R.string.post_error_signed_out))
                    return@launch
                }
                val existing = editItemId?.let { data.loadItem(it) }
                if (editItemId != null && existing == null) {
                    _events.emit(PostItemEvent.Message(R.string.post_error_item_gone))
                    return@launch
                }
                val itemId = editItemId ?: newItemId
                val photoPath = when {
                    current.draftPhotoPath != null -> data.commitPhotoDraft(current.draftPhotoPath, itemId)
                    current.removeExistingPhoto -> null.also { data.removePhoto(itemId) }
                    else -> existing?.localPhotoPath
                }
                val item = buildItem(current, itemId, author, existing, photoPath)
                data.saveItem(item, uploadPhoto = current.draftPhotoPath != null)
                // The draft file has become the item's photo: forget it so onCleared doesn't delete it.
                updateForm(null) { it.copy(draftPhotoPath = null) }
                Log.d(TAG, "Saved item ${item.id} (${if (existing == null) "new" else "edit"})")
                _events.emit(PostItemEvent.Saved(item.id, wasEdit = existing != null))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Couldn't save the item", e)
                _events.emit(PostItemEvent.Message(R.string.post_error_save_failed))
            } finally {
                isSaving.value = false
            }
        }
    }

    /**
     * Builds the item to save. An edit keeps everything the form doesn't show (owner, creation
     * time, status, pin); a new post starts OPEN. `updatedAt` and `syncState` are set by
     * `ItemRepository.saveItem`.
     */
    private fun buildItem(form: PostItemForm, id: String, author: Author, existing: Item?, photoPath: String?): Item {
        val question = form.verificationQuestion.trim().takeIf { form.type == ItemType.FOUND }
        val common = existing ?: Item(
            id = id,
            type = form.type,
            title = "",
            description = "",
            category = Category.OTHER,
            status = ItemStatus.OPEN,
            zoneId = null,
            zone = null,
            latitude = null,
            longitude = null,
            addressLine = null,
            eventDate = form.eventDate,
            createdAt = clock(),
            updatedAt = clock(),
            ownerId = author.uid,
            ownerName = author.name,
            verificationQuestion = null,
            hasPhoto = false,
            localPhotoPath = null,
            syncState = SyncState.PENDING_UPLOAD,
        )
        return common.copy(
            title = form.title.trim(),
            description = form.description.trim(),
            category = form.category ?: Category.OTHER,
            eventDate = form.eventDate,
            zoneId = form.zoneId,
            zone = null,
            verificationQuestion = question,
            hasPhoto = form.willHavePhoto,
            localPhotoPath = photoPath,
        )
    }

    /** Leaving the screen without posting: delete the unused draft photo. */
    override fun onCleared() {
        _form.value.draftPhotoPath?.let { data.discardPhotoDraft(it) }
    }

    // ---------- SavedStateHandle ----------

    private fun saveForm(form: PostItemForm) {
        savedStateHandle[KEY_TYPE] = form.type.name
        savedStateHandle[KEY_TITLE] = form.title
        savedStateHandle[KEY_DESCRIPTION] = form.description
        savedStateHandle[KEY_CATEGORY] = form.category?.name
        savedStateHandle[KEY_EVENT_DATE] = form.eventDate
        savedStateHandle[KEY_ZONE] = form.zoneId
        savedStateHandle[KEY_QUESTION] = form.verificationQuestion
        savedStateHandle[KEY_DRAFT_PHOTO] = form.draftPhotoPath
        savedStateHandle[KEY_EXISTING_PHOTO] = form.existingPhotoPath
        savedStateHandle[KEY_EXISTING_HAS_PHOTO] = form.existingHasPhoto
        savedStateHandle[KEY_REMOVE_EXISTING] = form.removeExistingPhoto
    }

    private fun restoreForm(): PostItemForm = PostItemForm(
        type = savedStateHandle.get<String>(KEY_TYPE)?.let(ItemType::valueOf) ?: ItemType.LOST,
        title = savedStateHandle[KEY_TITLE] ?: "",
        description = savedStateHandle[KEY_DESCRIPTION] ?: "",
        category = savedStateHandle.get<String>(KEY_CATEGORY)?.let(Category::valueOf),
        eventDate = savedStateHandle[KEY_EVENT_DATE] ?: todayEventDate(),
        zoneId = savedStateHandle[KEY_ZONE],
        verificationQuestion = savedStateHandle[KEY_QUESTION] ?: "",
        draftPhotoPath = savedStateHandle[KEY_DRAFT_PHOTO],
        existingPhotoPath = savedStateHandle[KEY_EXISTING_PHOTO],
        existingHasPhoto = savedStateHandle[KEY_EXISTING_HAS_PHOTO] ?: false,
        removeExistingPhoto = savedStateHandle[KEY_REMOVE_EXISTING] ?: false,
    )

    companion object {
        /** Must match the property name in `Routes.PostItem`. */
        const val KEY_EDIT_ITEM_ID = "editItemId"

        private const val KEY_NEW_ID = "post_new_id"
        private const val KEY_LOADED = "post_loaded"
        private const val KEY_TOUCHED = "post_touched"
        private const val KEY_SUBMIT_ATTEMPTED = "post_submit_attempted"
        private const val KEY_TYPE = "post_type"
        private const val KEY_TITLE = "post_title"
        private const val KEY_DESCRIPTION = "post_description"
        private const val KEY_CATEGORY = "post_category"
        private const val KEY_EVENT_DATE = "post_event_date"
        private const val KEY_ZONE = "post_zone"
        private const val KEY_QUESTION = "post_question"
        private const val KEY_DRAFT_PHOTO = "post_draft_photo"
        private const val KEY_EXISTING_PHOTO = "post_existing_photo"
        private const val KEY_EXISTING_HAS_PHOTO = "post_existing_has_photo"
        private const val KEY_REMOVE_EXISTING = "post_remove_existing"

        val Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                PostItemViewModel(
                    data = RepositoryPostItemDataSource(
                        itemRepository = container.itemRepository,
                        zoneRepository = container.zoneRepository,
                        authRepository = container.authRepository,
                        photoRepository = container.photoRepository,
                        appScope = container.applicationScope,
                    ),
                    savedStateHandle = createSavedStateHandle(),
                )
            }
        }
    }
}
