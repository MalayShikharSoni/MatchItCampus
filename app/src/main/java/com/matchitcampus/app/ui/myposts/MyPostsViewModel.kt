package com.matchitcampus.app.ui.myposts

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.matchitcampus.app.R
import com.matchitcampus.app.data.repository.AuthRepository
import com.matchitcampus.app.data.repository.ItemRepository
import com.matchitcampus.app.domain.model.Item
import com.matchitcampus.app.domain.model.ItemStatus
import com.matchitcampus.app.ui.util.appContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "MatchIt/MyPostsViewModel"

/** My items of one status, under one heading. */
@Immutable
data class PostGroup(val status: ItemStatus, val items: List<Item>)

@Immutable
data class MyPostsUiState(
    val groups: List<PostGroup> = emptyList(),
    val isLoading: Boolean = true,
    /** The item waiting for "Delete?" confirmation, if the dialog is open. */
    val confirmDelete: Item? = null,
) {
    val isEmpty: Boolean get() = groups.isEmpty()
}

/**
 * "My posts": my items grouped by status (open, claim pending, returned), with edit,
 * mark-returned and delete.
 *
 * Grouping happens here, not in SQL: Room already gives my items newest first, and grouping a
 * short list in Kotlin is simple and keeps that order inside each group.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MyPostsViewModel(
    private val itemRepository: ItemRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    private val confirmDelete = MutableStateFlow<Item?>(null)

    private val myItems = authRepository.authState.flatMapLatest { user ->
        if (user == null) flowOf(emptyList()) else itemRepository.observeMyItems(user.uid)
    }

    private val _messages = MutableSharedFlow<Int>()
    val messages: SharedFlow<Int> = _messages.asSharedFlow()

    val uiState: StateFlow<MyPostsUiState> = combine(myItems, confirmDelete) { items, toDelete ->
        MyPostsUiState(
            groups = groupByStatus(items),
            isLoading = false,
            confirmDelete = toDelete,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MyPostsUiState())

    fun onMarkReturned(item: Item) = launchAction {
        if (itemRepository.markReturned(item.id)) R.string.detail_marked_returned else R.string.detail_action_failed
    }

    fun onDeleteClick(item: Item) {
        confirmDelete.value = item
    }

    fun onDismissDelete() {
        confirmDelete.value = null
    }

    fun onConfirmDelete() {
        val item = confirmDelete.value ?: return
        confirmDelete.value = null
        launchAction { if (itemRepository.deleteItem(item.id)) R.string.myposts_deleted else R.string.detail_action_failed }
    }

    private fun launchAction(action: suspend () -> Int) {
        viewModelScope.launch {
            val message = try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Action on my post failed", e)
                R.string.detail_action_failed
            }
            _messages.emit(message)
        }
    }

    companion object {
        /** Groups in a fixed order (open first), leaving out statuses with no items. */
        fun groupByStatus(items: List<Item>): List<PostGroup> {
            val byStatus = items.groupBy { it.status }
            return ItemStatus.entries.mapNotNull { status -> byStatus[status]?.let { PostGroup(status, it) } }
        }

        val Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                MyPostsViewModel(container.itemRepository, container.authRepository)
            }
        }
    }
}
