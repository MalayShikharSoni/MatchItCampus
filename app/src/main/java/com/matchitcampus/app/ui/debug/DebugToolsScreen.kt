package com.matchitcampus.app.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import com.matchitcampus.app.domain.model.ItemType
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.matchitcampus.app.R
import com.matchitcampus.app.ui.theme.MatchItCampusTheme

/**
 * Stateful entry point for the debug screen.
 *
 * `LaunchedEffect(Unit)` starts one coroutine when the screen appears and cancels it when
 * the screen leaves. It collects the ViewModel's one-shot messages and shows each as a
 * snackbar. `Unit` as the key means "start once, never restart".
 */
@Composable
fun DebugToolsRoute(
    onBack: () -> Unit,
    onOpenZoneCapture: () -> Unit,
    viewModel: DebugToolsViewModel = viewModel(factory = DebugToolsViewModel.Factory),
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.messages.collect { messageRes ->
            snackbarHostState.showSnackbar(context.getString(messageRes))
        }
    }
    DebugToolsScreen(
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onForceSync = viewModel::onForceSync,
        onPostTestItem = viewModel::onPostTestItem,
        onThrowTestException = { viewModel.onThrowTestException() },
        onOpenZoneCapture = onOpenZoneCapture,
        onSeedFound = { viewModel.onSeedDemo(ItemType.FOUND) },
        onSeedLost = { viewModel.onSeedDemo(ItemType.LOST) },
        onRemoveDemo = viewModel::onRemoveDemo,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugToolsScreen(
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onForceSync: () -> Unit,
    onPostTestItem: () -> Unit,
    onThrowTestException: () -> Unit,
    onOpenZoneCapture: () -> Unit,
    onSeedFound: () -> Unit,
    onSeedLost: () -> Unit,
    onRemoveDemo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_debug_tools)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onForceSync) { Text(stringResource(R.string.debug_force_sync)) }
            OutlinedButton(onClick = onPostTestItem) { Text(stringResource(R.string.debug_post_test_item)) }
            OutlinedButton(onClick = onOpenZoneCapture) { Text(stringResource(R.string.title_zone_capture)) }
            OutlinedButton(onClick = onThrowTestException) { Text(stringResource(R.string.debug_throw_exception)) }

            Text(stringResource(R.string.debug_demo_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
            Text(
                stringResource(R.string.debug_demo_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onSeedFound) { Text(stringResource(R.string.debug_seed_found)) }
            OutlinedButton(onClick = onSeedLost) { Text(stringResource(R.string.debug_seed_lost)) }
            OutlinedButton(onClick = onRemoveDemo) { Text(stringResource(R.string.debug_remove_demo)) }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DebugToolsScreenPreview() {
    MatchItCampusTheme {
        DebugToolsScreen(
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onForceSync = {},
            onPostTestItem = {},
            onThrowTestException = {},
            onOpenZoneCapture = {},
            onSeedFound = {},
            onSeedLost = {},
            onRemoveDemo = {},
        )
    }
}
