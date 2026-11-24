package com.matchitcampus.app.ui.debug

import android.content.ClipData
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.matchitcampus.app.R
import com.matchitcampus.app.ui.components.LocationPermissionGate
import com.matchitcampus.app.ui.components.PreviewData
import com.matchitcampus.app.ui.theme.MatchItCampusTheme
import com.matchitcampus.app.ui.util.Formatters

/**
 * Stateful entry point. The clipboard write happens here because it needs the platform
 * clipboard (`LocalClipboard`); the ViewModel only produces the text.
 */
@Composable
fun ZoneCaptureRoute(
    onBack: () -> Unit,
    viewModel: ZoneCaptureViewModel = viewModel(factory = ZoneCaptureViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    // Which zone's button asked for permission, so the grant callback knows what to capture.
    var pendingZoneId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ZoneCaptureEvent.Message -> snackbarHostState.showSnackbar(
                    if (event.arg != null) context.getString(event.text, event.arg) else context.getString(event.text),
                )
                is ZoneCaptureEvent.CopyJson -> {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("campus_zones.json", event.json)))
                    snackbarHostState.showSnackbar(context.getString(R.string.zone_capture_copied))
                }
            }
        }
    }

    LocationPermissionGate(
        onGranted = { precise -> pendingZoneId?.let { viewModel.onCapture(it, precise) } },
    ) { requestLocation ->
        ZoneCaptureScreen(
            state = state,
            snackbarHostState = snackbarHostState,
            onCapture = { zoneId ->
                pendingZoneId = zoneId
                requestLocation()
            },
            onExport = viewModel::onExport,
            onBack = onBack,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZoneCaptureScreen(
    state: ZoneCaptureUiState,
    snackbarHostState: SnackbarHostState,
    onCapture: (zoneId: String) -> Unit,
    onExport: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_zone_capture)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = { TextButton(onClick = onExport) { Text(stringResource(R.string.zone_capture_export)) } },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item(key = "help", contentType = "help") {
                Text(
                    text = stringResource(R.string.zone_capture_help),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
                HorizontalDivider()
            }
            items(state.zones, key = { it.id }, contentType = { "zone" }) { zone ->
                ListItem(
                    headlineContent = { Text(zone.name) },
                    supportingContent = {
                        Text(
                            if (zone.isPlaceholder) {
                                stringResource(R.string.zone_capture_placeholder)
                            } else {
                                Formatters.coordinates(zone.latitude, zone.longitude)
                            },
                        )
                    },
                    trailingContent = {
                        if (state.capturingZoneId == zone.id) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            TextButton(onClick = { onCapture(zone.id) }, enabled = state.capturingZoneId == null) {
                                Text(stringResource(R.string.zone_capture_set_here))
                            }
                        }
                    },
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ZoneCaptureScreenPreview() {
    MatchItCampusTheme {
        ZoneCaptureScreen(
            state = ZoneCaptureUiState(zones = PreviewData.zones, capturingZoneId = PreviewData.zones.first().id),
            snackbarHostState = remember { SnackbarHostState() },
            onCapture = {},
            onExport = {},
            onBack = {},
        )
    }
}
