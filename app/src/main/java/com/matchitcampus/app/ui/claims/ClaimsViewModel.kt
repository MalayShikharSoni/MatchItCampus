package com.matchitcampus.app.ui.claims

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
import com.matchitcampus.app.data.repository.AuthRepository
import com.matchitcampus.app.data.repository.ClaimRepository
import com.matchitcampus.app.domain.model.Claim
import com.matchitcampus.app.domain.model.ClaimStatus
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "MatchIt/ClaimsViewModel"

enum class ClaimsTab { INCOMING, OUTGOING }

@Immutable
data class ClaimsUiState(
    val tab: ClaimsTab = ClaimsTab.INCOMING,
    /** Claims on my found items. */
    val incoming: List<Claim> = emptyList(),
    /** Claims I made. */
    val outgoing: List<Claim> = emptyList(),
    val isLoading: Boolean = true,
    /** The claim waiting for "Accept?" confirmation, if the dialog is open. */
    val confirmAccept: Claim? = null,
    val isBusy: Boolean = false,
) {
    val pendingIncoming: Int get() = incoming.count { it.status == ClaimStatus.PENDING }
}

/**
 * The Claims tab: incoming (I'm the finder) and outgoing (I'm the claimant).
 *
 * Both lists are Room Flows filtered by the signed-in user. `flatMapLatest` follows the auth
 * state, so after logging in as someone else the lists switch to that user's claims.
 *
 * Accepting goes through `ClaimRepository.acceptClaim`, which in one transaction accepts the
 * claim, marks the found item RETURNED and rejects the other pending claims on it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ClaimsViewModel(
    private val claimRepository: ClaimRepository,
    authRepository: AuthRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val tab = savedStateHandle.getStateFlow(KEY_TAB, ClaimsTab.INCOMING.name).map { ClaimsTab.valueOf(it) }
    private val confirmAccept = MutableStateFlow<Claim?>(null)
    private val isBusy = MutableStateFlow(false)

    private val claims = authRepository.authState.flatMapLatest { user ->
        if (user == null) {
            flowOf(emptyList<Claim>() to emptyList())
        } else {
            combine(claimRepository.observeIncoming(user.uid), claimRepository.observeOutgoing(user.uid), ::Pair)
        }
    }

    private val _messages = MutableSharedFlow<Int>()

    /** One-shot snackbar messages (string resources). */
    val messages: SharedFlow<Int> = _messages.asSharedFlow()

    val uiState: StateFlow<ClaimsUiState> = combine(tab, claims, confirmAccept, isBusy) { currentTab, (incoming, outgoing), confirm, busy ->
        ClaimsUiState(
            tab = currentTab,
            incoming = incoming,
            outgoing = outgoing,
            isLoading = false,
            confirmAccept = confirm,
            isBusy = busy,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClaimsUiState())

    fun onTabSelected(newTab: ClaimsTab) {
        savedStateHandle[KEY_TAB] = newTab.name
    }

    fun onAcceptClick(claim: Claim) {
        confirmAccept.value = claim
    }

    fun onDismissAccept() {
        confirmAccept.value = null
    }

    fun onConfirmAccept() {
        val claim = confirmAccept.value ?: return
        confirmAccept.value = null
        runBusy { if (claimRepository.acceptClaim(claim.id)) R.string.claim_accepted else R.string.claim_no_longer_pending }
    }

    fun onRejectClick(claim: Claim) =
        runBusy { if (claimRepository.rejectClaim(claim.id)) R.string.claim_rejected else R.string.claim_no_longer_pending }

    fun onCancelClick(claim: Claim) =
        runBusy { if (claimRepository.cancelClaim(claim.id)) R.string.claim_cancelled else R.string.claim_no_longer_pending }

    /** Runs one claim action, then shows the message it returns. Ignores taps while one is running. */
    private fun runBusy(action: suspend () -> Int) {
        if (isBusy.value) return
        viewModelScope.launch {
            isBusy.value = true
            @StringRes val message = try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Claim action failed", e)
                R.string.detail_action_failed
            } finally {
                isBusy.value = false
            }
            _messages.emit(message)
        }
    }

    companion object {
        private const val KEY_TAB = "claims_tab"

        val Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                ClaimsViewModel(container.claimRepository, container.authRepository, createSavedStateHandle())
            }
        }
    }
}
