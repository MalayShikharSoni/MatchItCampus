package com.matchitcampus.app.ui.detail

import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.matchitcampus.app.R
import com.matchitcampus.app.data.repository.AuthRepository
import com.matchitcampus.app.data.repository.ClaimRepository
import com.matchitcampus.app.data.repository.ItemRepository
import com.matchitcampus.app.data.repository.MatchRepository
import com.matchitcampus.app.data.repository.PhotoRepository
import com.matchitcampus.app.domain.model.Claim
import com.matchitcampus.app.domain.model.ClaimStatus
import com.matchitcampus.app.domain.model.Item
import com.matchitcampus.app.domain.model.ItemStatus
import com.matchitcampus.app.domain.model.ItemType
import com.matchitcampus.app.domain.model.MatchResult
import com.matchitcampus.app.domain.model.SyncState
import com.matchitcampus.app.ui.navigation.Routes
import com.matchitcampus.app.ui.util.appContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

private const val TAG = "MatchIt/ItemDetailVM"

/** One possible match, with the other item and the "why" lines already worked out. */
@Immutable
data class MatchUi(val result: MatchResult, val candidate: Item, val why: List<WhyLine>)

/**
 * The "This is mine" sheet while it's open.
 *
 * @property linkedLostItemId one of my own open LOST posts, so it's marked RETURNED too when
 *   the claim is accepted. Optional.
 */
@Immutable
data class ClaimDraft(
    val answer: String = "",
    val message: String = "",
    val linkedLostItemId: String? = null,
    val showErrors: Boolean = false,
    val isSubmitting: Boolean = false,
) {
    /** A string resource for what's wrong, or null if the claim can be sent. */
    @StringRes
    fun problem(hasQuestion: Boolean): Int? = when {
        hasQuestion && answer.isBlank() -> R.string.claim_error_answer_required
        answer.trim().length > ANSWER_MAX -> R.string.claim_error_answer_long
        message.trim().length > MESSAGE_MAX -> R.string.claim_error_message_long
        else -> null
    }

    companion object {
        const val ANSWER_MAX = 200
        const val MESSAGE_MAX = 300
    }
}

/** An action that needs an "Are you sure?" dialog first. */
sealed interface Confirmation {
    data object Delete : Confirmation
    data object MarkReturned : Confirmation
    data class AcceptClaim(val claimId: String, val claimantName: String) : Confirmation
}

@Immutable
data class ItemDetailUiState(
    val isLoading: Boolean = true,
    /** Null once loaded means the item is gone (deleted here or by its owner). */
    val item: Item? = null,
    val isOwner: Boolean = false,
    val matches: List<MatchUi> = emptyList(),
    /** Finder: every claim on this item. Anyone else: only their own (the only ones on this phone). */
    val claims: List<Claim> = emptyList(),
    /** My open LOST posts, offered as a link in the claim sheet. */
    val myOpenLostItems: List<Item> = emptyList(),
    val photoUnavailable: Boolean = false,
    val confirmation: Confirmation? = null,
    val isBusy: Boolean = false,
) {
    /** My latest claim on this item, if I've made one. */
    val myClaim: Claim? get() = if (isOwner) null else claims.firstOrNull()

    /** "This is mine" is offered on someone else's FOUND item that isn't returned, unless I already have a live claim. */
    val canClaim: Boolean
        get() = item != null && !isOwner && item.type == ItemType.FOUND && item.status != ItemStatus.RETURNED &&
            myClaim?.status.let { it != ClaimStatus.PENDING && it != ClaimStatus.ACCEPTED }
}

sealed interface ItemDetailEvent {
    /** The item was deleted: leave the screen. */
    data object Deleted : ItemDetailEvent
    data class Message(@param:StringRes val text: Int) : ItemDetailEvent
}

/**
 * One item: its details, possible matches, and what the viewer can do with it.
 *
 * - **Matches** come from `MatchRepository.observeMatches` (a Room Flow), joined with the open
 *   items so each row can show the other item. When the screen opens, the item's matches are
 *   recomputed once, because a remote change that keeps the text (a new date, a new zone)
 *   doesn't trigger matching by itself.
 * - **Photo:** if the item has one and this phone doesn't, it's downloaded once
 *   ([PhotoRepository.ensureLocalCopy]); Room then emits the item with its new path.
 * - **Who sees what:** the owner gets Edit / Delete / Mark returned and, for a found item, the
 *   claims with Accept / Reject. Anyone else gets "This is mine" on a found item, or the state of
 *   their own claim.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ItemDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val itemRepository: ItemRepository,
    private val matchRepository: MatchRepository,
    private val claimRepository: ClaimRepository,
    private val authRepository: AuthRepository,
    private val photoRepository: PhotoRepository,
) : ViewModel() {

    /** `toRoute` rebuilds the typed route from the navigation arguments in the SavedStateHandle. */
    val itemId: String = savedStateHandle.toRoute<Routes.ItemDetail>().itemId

    private val uid: Flow<String?> = authRepository.authState.map { it?.uid }

    /** Pairs the item with "has Room answered yet", so the screen can tell loading from gone. */
    private val item: Flow<Pair<Item?, Boolean>> = itemRepository.observeItem(itemId)
        .map { it to true }
        .onStart { emit(null to false) }

    private val matches: Flow<List<MatchUi>> = combine(
        matchRepository.observeMatches(itemId),
        itemRepository.observeOpenItems(),
        itemRepository.observeItem(itemId),
    ) { results, openItems, source ->
        if (source == null) return@combine emptyList()
        val byId = openItems.associateBy { it.id }
        results.mapNotNull { result ->
            byId[result.candidateItemId]?.let { MatchUi(result, it, MatchExplanation.lines(result, source, it)) }
        }
    }

    private val myOpenLostItems: Flow<List<Item>> = uid.flatMapLatest { id ->
        if (id == null) {
            flowOf(emptyList())
        } else {
            itemRepository.observeMyItems(id).map { items ->
                items.filter { it.type == ItemType.LOST && it.status == ItemStatus.OPEN }
            }
        }
    }

    private val photoUnavailable = MutableStateFlow(false)
    private val _claimDraft = MutableStateFlow<ClaimDraft?>(null)

    /**
     * The open claim sheet's fields, or null when it's closed. Exposed on its own, not inside
     * [uiState]: text fields must get their new value back immediately, and going through
     * `combine` adds an asynchronous step that makes fast typing drop letters.
     */
    val claimDraft: StateFlow<ClaimDraft?> = _claimDraft.asStateFlow()
    private val confirmation = MutableStateFlow<Confirmation?>(null)
    private val isBusy = MutableStateFlow(false)

    private val _events = MutableSharedFlow<ItemDetailEvent>()
    val events: SharedFlow<ItemDetailEvent> = _events.asSharedFlow()

    val uiState: StateFlow<ItemDetailUiState> = combine(
        combine(item, uid, ::Pair),
        matches,
        claimRepository.observeForFoundItem(itemId),
        myOpenLostItems,
        combine(photoUnavailable, confirmation, isBusy) { a, b, c -> UiFlags(a, b, c) },
    ) { (itemAndLoaded, currentUid), matchList, claimList, myLost, flags ->
        val (currentItem, loaded) = itemAndLoaded
        ItemDetailUiState(
            isLoading = !loaded,
            item = currentItem,
            isOwner = currentItem != null && currentItem.ownerId == currentUid,
            matches = matchList,
            claims = claimList,
            myOpenLostItems = myLost,
            photoUnavailable = flags.photoUnavailable,
            confirmation = flags.confirmation,
            isBusy = flags.isBusy,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ItemDetailUiState())

    private data class UiFlags(
        val photoUnavailable: Boolean,
        val confirmation: Confirmation?,
        val isBusy: Boolean,
    )

    init {
        viewModelScope.launch {
            try {
                matchRepository.recomputeMatchesFor(itemId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Recomputing matches for $itemId failed", e)
            }
        }
        viewModelScope.launch {
            val loaded = itemRepository.observeItem(itemId).filterNotNull().first()
            if (!photoRepository.ensureLocalCopy(loaded)) photoUnavailable.value = true
        }
    }

    // ---------- Owner actions ----------

    fun onDeleteClick() { confirmation.value = Confirmation.Delete }

    fun onMarkReturnedClick() { confirmation.value = Confirmation.MarkReturned }

    fun onAcceptClick(claim: Claim) { confirmation.value = Confirmation.AcceptClaim(claim.id, claim.claimantName) }

    fun onDismissConfirmation() { confirmation.value = null }

    fun onConfirm() {
        val action = confirmation.value ?: return
        confirmation.value = null
        runBusy {
            when (action) {
                Confirmation.Delete -> if (itemRepository.deleteItem(itemId)) _events.emit(ItemDetailEvent.Deleted)
                Confirmation.MarkReturned -> if (itemRepository.markReturned(itemId)) say(R.string.detail_marked_returned)
                is Confirmation.AcceptClaim ->
                    if (claimRepository.acceptClaim(action.claimId)) say(R.string.claim_accepted) else say(R.string.claim_no_longer_pending)
            }
        }
    }

    fun onRejectClick(claim: Claim) = runBusy {
        if (claimRepository.rejectClaim(claim.id)) say(R.string.claim_rejected) else say(R.string.claim_no_longer_pending)
    }

    // ---------- Claimant actions ----------

    fun onCancelClaimClick(claim: Claim) = runBusy {
        if (claimRepository.cancelClaim(claim.id)) say(R.string.claim_cancelled) else say(R.string.claim_no_longer_pending)
    }

    /**
     * Opens the claim sheet. The linked lost post is preselected: the best of my open lost posts
     * among this item's matches (they're sorted best first), if any of them is mine.
     */
    fun onClaimClick() {
        val state = uiState.value
        val myLostIds = state.myOpenLostItems.map { it.id }.toSet()
        val best = state.matches.firstOrNull { it.candidate.id in myLostIds }?.candidate?.id
        _claimDraft.value = ClaimDraft(linkedLostItemId = best)
    }

    fun onClaimAnswerChange(value: String) = _claimDraft.update { it?.copy(answer = value) }

    fun onClaimMessageChange(value: String) = _claimDraft.update { it?.copy(message = value) }

    fun onClaimLinkChange(lostItemId: String?) = _claimDraft.update { it?.copy(linkedLostItemId = lostItemId) }

    fun onClaimDismiss() { _claimDraft.value = null }

    /**
     * Saves the claim locally as PENDING; sync uploads it. It doesn't change the found item:
     * that's the finder's document, and their phone moves it to CLAIM_PENDING when it sees the claim.
     */
    fun onClaimSubmit() {
        val draft = _claimDraft.value ?: return
        val item = uiState.value.item ?: return
        if (draft.isSubmitting) return
        if (draft.problem(hasQuestion = item.verificationQuestion != null) != null) {
            _claimDraft.value = draft.copy(showErrors = true)
            return
        }
        _claimDraft.value = draft.copy(isSubmitting = true)
        viewModelScope.launch {
            try {
                val user = authRepository.authState.value
                if (user == null) {
                    say(R.string.post_error_signed_out)
                    return@launch
                }
                val name = authRepository.observeProfile(user.uid).first()?.name?.takeIf { it.isNotBlank() } ?: user.email
                claimRepository.createClaim(
                    Claim(
                        id = UUID.randomUUID().toString(),
                        foundItemId = item.id,
                        lostItemId = draft.linkedLostItemId,
                        claimantId = user.uid,
                        claimantName = name,
                        finderId = item.ownerId,
                        answer = draft.answer.trim(),
                        message = draft.message.trim(),
                        status = ClaimStatus.PENDING,
                        createdAt = 0L, // set by ClaimRepository
                        updatedAt = 0L,
                        syncState = SyncState.PENDING_UPLOAD,
                    ),
                )
                _claimDraft.value = null
                say(R.string.claim_sent)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Couldn't create the claim", e)
                _claimDraft.update { it?.copy(isSubmitting = false) }
                say(R.string.claim_send_failed)
            }
        }
    }

    /** Runs a repository action with the busy flag set, so buttons can't be pressed twice. */
    private fun runBusy(block: suspend () -> Unit) {
        if (isBusy.value) return
        viewModelScope.launch {
            isBusy.value = true
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Action on item $itemId failed", e)
                say(R.string.detail_action_failed)
            } finally {
                isBusy.value = false
            }
        }
    }

    private suspend fun say(@StringRes text: Int) = _events.emit(ItemDetailEvent.Message(text))

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                ItemDetailViewModel(
                    savedStateHandle = createSavedStateHandle(),
                    itemRepository = container.itemRepository,
                    matchRepository = container.matchRepository,
                    claimRepository = container.claimRepository,
                    authRepository = container.authRepository,
                    photoRepository = container.photoRepository,
                )
            }
        }
    }
}
