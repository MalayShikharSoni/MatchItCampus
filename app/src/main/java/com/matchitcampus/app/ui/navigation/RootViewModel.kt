package com.matchitcampus.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.matchitcampus.app.data.repository.AuthRepository
import com.matchitcampus.app.ui.util.appContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Decides which half of the app to show: the auth screens or the main screens.
 *
 * It follows [AuthRepository.authState], so logging in, registering, logging out, and
 * restarting the app all go through the same path. No screen ever navigates between the
 * two halves itself.
 */
class RootViewModel(authRepository: AuthRepository) : ViewModel() {

    /** Starts with the current value, so the first frame is already the right half. */
    val isSignedIn: StateFlow<Boolean> = authRepository.authState
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, authRepository.authState.value != null)

    companion object {
        val Factory = viewModelFactory {
            initializer { RootViewModel(appContainer().authRepository) }
        }
    }
}
