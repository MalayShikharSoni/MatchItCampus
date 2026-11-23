package com.matchitcampus.app.ui.auth

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.matchitcampus.app.R
import com.matchitcampus.app.ui.components.PasswordField
import com.matchitcampus.app.ui.theme.MatchItCampusTheme

/**
 * Stateful entry point: gets the [LoginViewModel], collects its state, and passes state
 * and callbacks down to the stateless [LoginScreen].
 *
 * `collectAsStateWithLifecycle` stops collecting while the screen is in the background,
 * so the StateFlow isn't watched when nobody can see it.
 */
@Composable
fun LoginRoute(
    onRegisterClick: () -> Unit,
    viewModel: LoginViewModel = viewModel(factory = LoginViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LoginScreen(
        state = state,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onTogglePasswordVisible = viewModel::onTogglePasswordVisible,
        onSubmit = viewModel::onSubmit,
        onRegisterClick = onRegisterClick,
    )
}

/**
 * The login form. Stateless: it only draws [state] and reports user actions through the
 * callbacks, so it can be previewed and tested with any state.
 */
@Composable
fun LoginScreen(
    state: LoginUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePasswordVisible: () -> Unit,
    onSubmit: () -> Unit,
    onRegisterClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AuthFormLayout(title = stringResource(R.string.title_login), modifier = modifier) {
        OutlinedTextField(
            value = state.email,
            onValueChange = onEmailChange,
            label = { Text(stringResource(R.string.field_email)) },
            singleLine = true,
            enabled = !state.isLoading,
            isError = state.emailError != null,
            supportingText = state.emailError?.let { res -> { Text(stringResource(res)) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(AuthTestTags.EMAIL),
        )
        PasswordField(
            value = state.password,
            onValueChange = onPasswordChange,
            visible = state.passwordVisible,
            onToggleVisible = onTogglePasswordVisible,
            errorRes = state.passwordError,
            enabled = !state.isLoading,
            imeAction = ImeAction.Done,
            onImeAction = onSubmit,
            modifier = Modifier.testTag(AuthTestTags.PASSWORD),
        )
        state.formError?.let { FormError(stringResource(it)) }
        SubmitButton(
            text = stringResource(R.string.action_log_in),
            loading = state.isLoading,
            onClick = onSubmit,
            modifier = Modifier.testTag(AuthTestTags.SUBMIT),
        )
        TextButton(onClick = onRegisterClick, enabled = !state.isLoading) {
            Text(stringResource(R.string.action_go_register))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenPreview() {
    MatchItCampusTheme {
        LoginScreen(
            state = LoginUiState(email = "riya@example.com", formError = R.string.auth_error_wrong_credentials),
            onEmailChange = {},
            onPasswordChange = {},
            onTogglePasswordVisible = {},
            onSubmit = {},
            onRegisterClick = {},
        )
    }
}
