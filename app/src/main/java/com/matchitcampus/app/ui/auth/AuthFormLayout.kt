package com.matchitcampus.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.matchitcampus.app.R

/**
 * The shared frame of the login and registration screens: app name, tagline, then the
 * form [content], in a column that scrolls (so it works in landscape and with the keyboard
 * open) and is at most 480 dp wide (so it doesn't stretch across a tablet).
 */
@Composable
fun AuthFormLayout(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(modifier = modifier) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(text = stringResource(R.string.auth_tagline), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 16.dp),
                )
                content()
            }
        }
    }
}

/** A full-width button that shows a spinner instead of its label while [loading]. */
@Composable
fun SubmitButton(
    text: String,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = !loading,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Text(text)
        }
    }
}

/** The error from the server (wrong password, no network…), shown above the submit button. */
@Composable
fun FormError(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier.testTag(AuthTestTags.FORM_ERROR),
    )
}

/** Tags that Compose UI tests use to find the auth screens' widgets. */
object AuthTestTags {
    const val EMAIL = "auth_email"
    const val PASSWORD = "auth_password"
    const val NAME = "auth_name"
    const val REG_NO = "auth_reg_no"
    const val SUBMIT = "auth_submit"
    const val FORM_ERROR = "auth_form_error"
}
