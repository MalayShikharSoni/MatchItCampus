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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.matchitcampus.app.R
import com.matchitcampus.app.ui.components.PasswordField
import com.matchitcampus.app.ui.theme.MatchItCampusTheme

/** Stateful entry point for registration. See [LoginRoute] for the pattern. */
@Composable
fun RegisterRoute(
    onLoginClick: () -> Unit,
    viewModel: RegisterViewModel = viewModel(factory = RegisterViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RegisterScreen(
        state = state,
        onNameChange = viewModel::onNameChange,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onRegNoChange = viewModel::onRegNoChange,
        onTogglePasswordVisible = viewModel::onTogglePasswordVisible,
        onSubmit = viewModel::onSubmit,
        onLoginClick = onLoginClick,
    )
}

/** The registration form. Stateless, like [LoginScreen]. */
@Composable
fun RegisterScreen(
    state: RegisterUiState,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onRegNoChange: (String) -> Unit,
    onTogglePasswordVisible: () -> Unit,
    onSubmit: () -> Unit,
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AuthFormLayout(title = stringResource(R.string.title_register), modifier = modifier) {
        OutlinedTextField(
            value = state.name,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.field_name)) },
            singleLine = true,
            enabled = !state.isLoading,
            isError = state.nameError != null,
            supportingText = state.nameError?.let { res -> { Text(stringResource(res)) } },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(AuthTestTags.NAME),
        )
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
            supportingRes = R.string.password_hint_new,
            enabled = !state.isLoading,
            imeAction = ImeAction.Next,
            modifier = Modifier.testTag(AuthTestTags.PASSWORD),
        )
        OutlinedTextField(
            value = state.regNo,
            onValueChange = onRegNoChange,
            label = { Text(stringResource(R.string.field_reg_no)) },
            singleLine = true,
            enabled = !state.isLoading,
            isError = state.regNoError != null,
            supportingText = state.regNoError?.let { res -> { Text(stringResource(res)) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(AuthTestTags.REG_NO),
        )
        state.formError?.let { FormError(stringResource(it)) }
        SubmitButton(
            text = stringResource(R.string.action_create_account),
            loading = state.isLoading,
            onClick = onSubmit,
            modifier = Modifier.testTag(AuthTestTags.SUBMIT),
        )
        TextButton(onClick = onLoginClick, enabled = !state.isLoading) {
            Text(stringResource(R.string.action_go_login))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RegisterScreenPreview() {
    MatchItCampusTheme {
        RegisterScreen(
            state = RegisterUiState(name = "Riya", passwordError = R.string.error_password_too_short),
            onNameChange = {},
            onEmailChange = {},
            onPasswordChange = {},
            onRegNoChange = {},
            onTogglePasswordVisible = {},
            onSubmit = {},
            onLoginClick = {},
        )
    }
}
