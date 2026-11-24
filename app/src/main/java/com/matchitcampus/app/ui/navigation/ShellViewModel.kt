package com.matchitcampus.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.matchitcampus.app.data.repository.AuthRepository
import com.matchitcampus.app.data.repository.ClaimRepository
import com.matchitcampus.app.data.sync.SyncEvent
import com.matchitcampus.app.data.sync.SyncManager
import com.matchitcampus.app.ui.util.appContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * State for the signed-in frame around all tabs ([MainShell]):
 * - [pendingIncomingClaims] for the badge on the Claims tab, straight from Room;
 * - [syncEvents], such as "a new claim arrived", for a snackbar shown on whatever tab is open.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShellViewModel(
    authRepository: AuthRepository,
    claimRepository: ClaimRepository,
    syncManager: SyncManager,
) : ViewModel() {

    val pendingIncomingClaims: StateFlow<Int> = authRepository.authState
        .flatMapLatest { user -> if (user == null) flowOf(0) else claimRepository.observePendingIncomingCount(user.uid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Passed through from [SyncManager.events]: a SharedFlow, so each event is shown once. */
    val syncEvents: SharedFlow<SyncEvent> = syncManager.events

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                ShellViewModel(container.authRepository, container.claimRepository, container.syncManager)
            }
        }
    }
}
