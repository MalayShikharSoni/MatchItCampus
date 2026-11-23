package com.matchitcampus.app.ui.debug

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.matchitcampus.app.R
import com.matchitcampus.app.data.repository.AuthRepository
import com.matchitcampus.app.data.repository.ItemRepository
import com.matchitcampus.app.data.sync.SyncManager
import com.matchitcampus.app.domain.model.Category
import com.matchitcampus.app.domain.model.Item
import com.matchitcampus.app.domain.model.ItemStatus
import com.matchitcampus.app.domain.model.ItemType
import com.matchitcampus.app.domain.model.SyncState
import com.matchitcampus.app.ui.util.appContainer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

private const val TAG = "MatchIt/DebugTools"

/**
 * Debug-only helpers for checking sync by hand. More tools arrive in later phases
 * (zone capture, demo data, a test crash).
 */
class DebugToolsViewModel(
    private val authRepository: AuthRepository,
    private val itemRepository: ItemRepository,
    private val syncManager: SyncManager,
) : ViewModel() {

    private val _messages = MutableSharedFlow<Int>()

    /**
     * One-shot messages for a snackbar. A SharedFlow (not state) because each message should
     * be shown once, not again after rotation.
     */
    val messages: SharedFlow<Int> = _messages.asSharedFlow()

    fun onForceSync() {
        viewModelScope.launch {
            syncManager.syncNow()
            say(R.string.debug_sync_done)
        }
    }

    /**
     * Saves a FOUND item through the normal repository path (Room first, PENDING_UPLOAD).
     * `SyncManager` notices the pending row and uploads it, so it should appear in the
     * Firestore console under `items/` a moment later.
     */
    fun onPostTestItem() {
        viewModelScope.launch {
            val user = authRepository.authState.value ?: return@launch
            val profile = authRepository.observeProfile(user.uid).first()
            val now = System.currentTimeMillis()
            val item = Item(
                id = UUID.randomUUID().toString(),
                type = ItemType.FOUND,
                title = "Debug test item",
                description = "Created from Debug tools to check that sync works.",
                category = Category.OTHER,
                status = ItemStatus.OPEN,
                zoneId = null,
                zone = null,
                latitude = null,
                longitude = null,
                addressLine = null,
                eventDate = now,
                createdAt = now,
                updatedAt = now,
                ownerId = user.uid,
                ownerName = profile?.name ?: user.email,
                verificationQuestion = "What colour is it?",
                hasPhoto = false,
                localPhotoPath = null,
                syncState = SyncState.PENDING_UPLOAD,
            )
            itemRepository.saveItem(item)
            Log.d(TAG, "Posted test item ${item.id}")
            say(R.string.debug_test_item_posted)
        }
    }

    private suspend fun say(@StringRes message: Int) = _messages.emit(message)

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                DebugToolsViewModel(container.authRepository, container.itemRepository, container.syncManager)
            }
        }
    }
}
