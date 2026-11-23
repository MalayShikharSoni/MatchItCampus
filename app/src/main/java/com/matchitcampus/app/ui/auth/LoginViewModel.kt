package com.matchitcampus.app.ui.auth

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.matchitcampus.app.data.repository.AuthRepository
import com.matchitcampus.app.domain.model.AuthResult
import com.matchitcampus.app.ui.util.appContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Everything the login screen shows. One immutable object, so the screen is a pure
 * function of it, and every change is a new copy (`copy(...)`).
 *
 * Errors are string resource ids, not strings, so the ViewModel needs no Context.
 */
@Immutable
data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    @param:StringRes val emailError: Int? = null,
    @param:StringRes val passwordError: Int? = null,
    @param:StringRes val formError: Int? = null,
    val isLoading: Boolean = false,
)

/**
 * Login screen logic: holds the form state, validates it, and calls [AuthRepository.signIn].
 *
 * There's no "go to the main screen" event on success. Signing in changes
 * `AuthRepository.authState`, and the app root reacts to that by showing the main screens,
 * the same way it would after an app restart.
 */
class LoginViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())

    /** Read-only to the screen; only this ViewModel changes it (unidirectional data flow). */
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) =
        _uiState.update { it.copy(email = value, emailError = null, formError = null) }

    fun onPasswordChange(value: String) =
        _uiState.update { it.copy(password = value, passwordError = null, formError = null) }

    fun onTogglePasswordVisible() = _uiState.update { it.copy(passwordVisible = !it.passwordVisible) }

    fun onSubmit() {
        val state = _uiState.value
        if (state.isLoading) return
        val emailError = AuthValidator.email(state.email)
        val passwordError = AuthValidator.loginPassword(state.password)
        if (emailError != null || passwordError != null) {
            _uiState.update { it.copy(emailError = emailError, passwordError = passwordError) }
            return
        }
        _uiState.update { it.copy(isLoading = true, formError = null) }
        viewModelScope.launch {
            val result = authRepository.signIn(state.email, state.password)
            if (result is AuthResult.Failure) {
                _uiState.update { it.copy(isLoading = false, formError = result.reason.messageRes()) }
            }
        }
    }

    companion object {
        /** Builds the ViewModel with its dependency from the AppContainer (manual DI). */
        val Factory = viewModelFactory {
            initializer { LoginViewModel(appContainer().authRepository) }
        }
    }
}
