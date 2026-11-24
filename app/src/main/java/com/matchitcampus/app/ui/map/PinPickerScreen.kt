package com.matchitcampus.app.ui.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.matchitcampus.app.R
import com.matchitcampus.app.location.CampusMap
import com.matchitcampus.app.location.LatLng
import com.matchitcampus.app.ui.components.LocationPermissionGate
import kotlinx.coroutines.launch
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.interaction.MapInteractions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState

/**
 * Full-screen map for dropping an exact pin (see [PinPickerViewModel] for how the pin works).
 *
 * @param onPicked called with the camera centre when "Use this spot" is pressed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinPickerRoute(
    onBack: () -> Unit,
    onPicked: (LatLng) -> Unit,
    viewModel: PinPickerViewModel = viewModel(factory = PinPickerViewModel.Factory),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    val mapState = rememberMapState(
        baseStyle = OpenFreeMapStyle,
        initialCameraPosition = cameraAt(viewModel.initialPosition, CampusMap.PIN_ZOOM),
    )
    val currentMapState by rememberUpdatedState(mapState)

    fun moveTo(position: LatLng) {
        scope.launch { currentMapState.animateCamera(CameraUpdate(center = position.toPosition(), zoom = CampusMap.PIN_ZOOM)) }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PinPickerEvent.MoveTo -> moveTo(event.position)
                is PinPickerEvent.Message -> snackbarHostState.showSnackbar(context.getString(event.text))
            }
        }
    }

    // A tap on the map (not on a control) moves the pin there.
    val interactions = remember {
        MapInteractions(MapInteractions.Standard) {
            callbacks {
                click {
                    onUnhandled { event ->
                        event.position?.let { moveTo(it.toLatLng()) }
                        ClickResult.Consume
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_pin_picker)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            MaplibreMap(
                state = mapState,
                interactions = interactions,
                overlay = { MapAttribution(Modifier.align(Alignment.BottomStart).padding(8.dp)) },
            )

            // The pin: an icon whose tip sits exactly on the map's centre point.
            Icon(
                imageVector = Icons.Filled.Place,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp)
                    .offset(y = (-24).dp),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(12.dp),
            ) {
                Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 3.dp, shadowElevation = 3.dp) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = viewModel::onQueryChange,
                        placeholder = { Text(stringResource(R.string.pin_search_hint)) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (state.isSearching) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            focusManager.clearFocus()
                            viewModel.onSearch()
                        }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (state.results.isNotEmpty() || state.noResults) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        tonalElevation = 3.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    ) {
                        Column {
                            if (state.noResults) {
                                Text(
                                    text = stringResource(R.string.pin_search_no_results),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(16.dp),
                                )
                            }
                            state.results.forEachIndexed { index, result ->
                                if (index > 0) HorizontalDivider()
                                Text(
                                    text = result.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.onResultClick(result) }
                                        .padding(16.dp),
                                )
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(16.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LocationPermissionGate(onGranted = viewModel::onMyLocationGranted) { requestLocation ->
                    SmallFloatingActionButton(onClick = requestLocation) {
                        if (state.isLocating) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.LocationOn, contentDescription = stringResource(R.string.map_my_location))
                        }
                    }
                }
                Button(onClick = { onPicked(mapState.cameraPosition.center.toLatLng()) }) {
                    Text(stringResource(R.string.pin_use_this_spot))
                }
            }
        }
    }
}
