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

/** Everything the registration screen shows. See [LoginUiState] for the conventions. */
@Immutable
data class RegisterUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val regNo: String = "",
    val passwordVisible: Boolean = false,
    @param:StringRes val nameError: Int? = null,
    @param:StringRes val emailError: Int? = null,
    @param:StringRes val passwordError: Int? = null,
    @param:StringRes val regNoError: Int? = null,
    @param:StringRes val formError: Int? = null,
    val isLoading: Boolean = false,
)

/**
 * Registration logic: validates every field at once (so all problems show together), then
 * calls [AuthRepository.register]. On success the user is signed in, and the app root
 * switches to the main screens by itself.
 */
class RegisterViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value, nameError = null, formError = null) }

    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, emailError = null, formError = null) }

    fun onPasswordChange(value: String) =
        _uiState.update { it.copy(password = value, passwordError = null, formError = null) }

    fun onRegNoChange(value: String) = _uiState.update { it.copy(regNo = value, regNoError = null, formError = null) }

    fun onTogglePasswordVisible() = _uiState.update { it.copy(passwordVisible = !it.passwordVisible) }

    fun onSubmit() {
        val state = _uiState.value
        if (state.isLoading) return
        val nameError = AuthValidator.name(state.name)
        val emailError = AuthValidator.email(state.email)
        val passwordError = AuthValidator.newPassword(state.password)
        val regNoError = AuthValidator.regNo(state.regNo)
        if (listOf(nameError, emailError, passwordError, regNoError).any { it != null }) {
            _uiState.update {
                it.copy(
                    nameError = nameError,
                    emailError = emailError,
                    passwordError = passwordError,
                    regNoError = regNoError,
                )
            }
            return
        }
        _uiState.update { it.copy(isLoading = true, formError = null) }
        viewModelScope.launch {
            val result = authRepository.register(
                name = state.name,
                email = state.email,
                password = state.password,
                regNo = state.regNo,
            )
            if (result is AuthResult.Failure) {
                _uiState.update { it.copy(isLoading = false, formError = result.reason.messageRes()) }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { RegisterViewModel(appContainer().authRepository) }
        }
    }
}
