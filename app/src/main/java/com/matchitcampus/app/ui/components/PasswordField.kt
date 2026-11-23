package com.matchitcampus.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import com.matchitcampus.app.R
import com.matchitcampus.app.ui.theme.MatchItCampusTheme

/**
 * A password text field with a Show/Hide toggle.
 *
 * Stateless: the caller owns both the text and whether it's visible, and is told about
 * changes through callbacks (state hoisting). That's why the same field works on the login
 * and registration screens and survives being re-created.
 *
 * The toggle is a text button rather than an eye icon because the eye icons aren't in the
 * small built-in icon set we use.
 *
 * @param errorRes a string resource to show as an error under the field, or null.
 * @param supportingRes a hint shown under the field when there's no error.
 */
@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    visible: Boolean,
    onToggleVisible: () -> Unit,
    modifier: Modifier = Modifier,
    @StringRes errorRes: Int? = null,
    @StringRes supportingRes: Int? = null,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.field_password)) },
        singleLine = true,
        enabled = enabled,
        isError = errorRes != null,
        supportingText = (errorRes ?: supportingRes)?.let { res -> { Text(stringResource(res)) } },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onAny = { onImeAction() }),
        trailingIcon = {
            TextButton(onClick = onToggleVisible, enabled = enabled) {
                Text(stringResource(if (visible) R.string.password_hide else R.string.password_show))
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun PasswordFieldPreview() {
    MatchItCampusTheme {
        PasswordField(
            value = "secret",
            onValueChange = {},
            visible = false,
            onToggleVisible = {},
            errorRes = R.string.error_password_too_short,
        )
    }
}
