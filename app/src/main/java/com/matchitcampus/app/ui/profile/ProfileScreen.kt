package com.matchitcampus.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.matchitcampus.app.R
import com.matchitcampus.app.ui.theme.MatchItCampusTheme

/**
 * Stateful entry point for the profile tab.
 *
 * @param showDebugTools whether to offer the Debug tools entry. The caller passes
 *   `BuildConfig.DEBUG`; taking it as a parameter keeps the screen previewable both ways.
 */
@Composable
fun ProfileRoute(
    showDebugTools: Boolean,
    onOpenDebugTools: () -> Unit,
    viewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProfileScreen(
        state = state,
        showDebugTools = showDebugTools,
        onOpenDebugTools = onOpenDebugTools,
        onLogoutClick = viewModel::onLogoutClick,
        onConfirmLogout = viewModel::onConfirmLogout,
        onDismissLogoutWarning = viewModel::onDismissLogoutWarning,
    )
}

/**
 * Who's signed in, sync status, and logout. The full profile screen (about, attribution)
 * comes in Phase 5.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    state: ProfileUiState,
    showDebugTools: Boolean,
    onOpenDebugTools: () -> Unit,
    onLogoutClick: () -> Unit,
    onConfirmLogout: () -> Unit,
    onDismissLogoutWarning: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_profile)) }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(state.name, style = MaterialTheme.typography.headlineSmall)
            Text(state.email, style = MaterialTheme.typography.bodyLarge)
            state.regNo?.let { Text(stringResource(R.string.profile_reg_no, it)) }
            Text(
                text = if (state.unsyncedCount > 0) {
                    stringResource(R.string.profile_unsynced, state.unsyncedCount)
                } else {
                    stringResource(R.string.profile_all_synced)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (showDebugTools) {
                OutlinedButton(onClick = onOpenDebugTools, modifier = Modifier.padding(top = 16.dp)) {
                    Text(stringResource(R.string.placeholder_debug_tools))
                }
            }
            Button(onClick = onLogoutClick, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.action_log_out))
            }
        }
    }

    if (state.showLogoutWarning) {
        AlertDialog(
            onDismissRequest = onDismissLogoutWarning,
            title = { Text(stringResource(R.string.logout_warning_title)) },
            text = { Text(stringResource(R.string.logout_warning_body, state.unsyncedCount)) },
            confirmButton = {
                TextButton(onClick = onConfirmLogout) { Text(stringResource(R.string.logout_anyway)) }
            },
            dismissButton = {
                TextButton(onClick = onDismissLogoutWarning) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileScreenPreview() {
    MatchItCampusTheme {
        ProfileScreen(
            state = ProfileUiState(name = "Riya Sharma", email = "riya@example.com", regNo = "12345678", unsyncedCount = 2),
            showDebugTools = true,
            onOpenDebugTools = {},
            onLogoutClick = {},
            onConfirmLogout = {},
            onDismissLogoutWarning = {},
        )
    }
}
