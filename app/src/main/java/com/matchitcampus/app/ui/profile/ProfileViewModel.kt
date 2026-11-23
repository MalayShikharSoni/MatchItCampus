package com.matchitcampus.app.ui.profile

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.matchitcampus.app.data.repository.AuthRepository
import com.matchitcampus.app.data.repository.ClaimRepository
import com.matchitcampus.app.data.repository.ItemRepository
import com.matchitcampus.app.ui.util.appContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class ProfileUiState(
    val name: String = "",
    val email: String = "",
    val regNo: String? = null,
    /** Local changes not yet in Firestore (items + claims). */
    val unsyncedCount: Int = 0,
    val showLogoutWarning: Boolean = false,
)

/**
 * Profile screen logic: shows who is signed in and handles logout.
 *
 * Logging out with unsynced changes would lose them (the next account can't upload
 * another user's rows), so in that case the screen first asks for confirmation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModel(
    private val authRepository: AuthRepository,
    itemRepository: ItemRepository,
    claimRepository: ClaimRepository,
) : ViewModel() {

    private val showLogoutWarning = MutableStateFlow(false)

    /**
     * `flatMapLatest` switches to the profile of whoever is signed in now, and drops the old
     * one if the account changes. `combine` merges it with the unsynced counts and the dialog
     * flag into one state object for the screen.
     */
    val uiState: StateFlow<ProfileUiState> = combine(
        authRepository.authState.flatMapLatest { user ->
            if (user == null) flowOf(null) else authRepository.observeProfile(user.uid).map { it to user }
        },
        combine(itemRepository.observeUnsyncedCount(), claimRepository.observeUnsyncedCount()) { a, b -> a + b },
        showLogoutWarning,
    ) { profileAndUser, unsynced, warning ->
        val (profile, user) = profileAndUser ?: (null to null)
        ProfileUiState(
            name = profile?.name.orEmpty(),
            email = profile?.email ?: user?.email.orEmpty(),
            regNo = profile?.regNo,
            unsyncedCount = unsynced,
            showLogoutWarning = warning,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    fun onLogoutClick() {
        if (uiState.value.unsyncedCount > 0) showLogoutWarning.value = true else logOut()
    }

    fun onConfirmLogout() {
        showLogoutWarning.value = false
        logOut()
    }

    fun onDismissLogoutWarning() {
        showLogoutWarning.value = false
    }

    private fun logOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                ProfileViewModel(container.authRepository, container.itemRepository, container.claimRepository)
            }
        }
    }
}
