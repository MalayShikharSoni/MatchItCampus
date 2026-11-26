package com.matchitcampus.app.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.matchitcampus.app.R
import com.matchitcampus.app.domain.model.Item
import com.matchitcampus.app.location.CampusMap
import com.matchitcampus.app.location.LatLng
import com.matchitcampus.app.location.MapGeoJson
import com.matchitcampus.app.ui.components.ItemCard
import com.matchitcampus.app.ui.components.LocationPermissionGate
import kotlinx.serialization.json.jsonPrimitive
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.FillLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.util.MaplibreComposable

/**
 * The Map tab.
 *
 * - The style, sources and layers are declared as composables inside `rememberMapState { … }`,
 *   the same way Compose UI is declared: when [MapUiState] changes, only the GeoJSON sources
 *   whose text changed are updated on the map.
 * - Tapping a dot opens a `ModalBottomSheet` preview; its card opens the item.
 * - **`DisposableEffect` + lifecycle observer** starts live location when the screen is visible
 *   (ON_START) and stops it when it isn't (ON_STOP, or leaving the tab: `onDispose`). That's the
 *   effect's purpose: something must be undone (GPS updates removed) when the screen goes away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapRoute(
    onOpenItem: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MapViewModel = viewModel(factory = MapViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, state.isTrackingLocation) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.startLocationUpdates()
                Lifecycle.Event.ON_STOP -> viewModel.stopLocationUpdates()
                else -> Unit
            }
        }
        // Adding the observer replays the current state, so ON_START arrives at once if visible.
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopLocationUpdates()
        }
    }

    val mapState = rememberMapState(
        baseStyle = OpenFreeMapStyle,
        initialCameraPosition = cameraAt(CampusMap.CENTER),
    ) {
        MapContent(state = state, onMarkerClick = viewModel::onMarkerClick)
    }

    // The first fix after "my location" moves the camera there; later fixes only move the dot.
    LaunchedEffect(Unit) {
        viewModel.centerRequests.collect { position ->
            mapState.animateCamera(CameraUpdate(center = position.toPosition(), zoom = CampusMap.PIN_ZOOM))
        }
    }

    Box(modifier.fillMaxSize()) {
        MaplibreMap(
            state = mapState,
            overlay = { MapAttribution(Modifier.align(Alignment.BottomStart).padding(8.dp)) },
        )
        Column(
            // The map is drawn edge to edge (no top app bar), so the legend steps down below the
            // status bar instead of sitting under the clock.
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MapLegend()
            if (state.unplacedCount > 0) {
                MapNote(pluralStringResource(R.plurals.map_unplaced_items, state.unplacedCount, state.unplacedCount))
            }
        }
        LocationPermissionGate(onGranted = { viewModel.onMyLocationGranted() }) { requestLocation ->
            FloatingActionButton(
                onClick = requestLocation,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            ) {
                Icon(Icons.Filled.LocationOn, contentDescription = stringResource(R.string.map_my_location))
            }
        }
    }

    state.selectedItem?.let { item ->
        ModalBottomSheet(onDismissRequest = viewModel::onDismissPreview) {
            ItemPreview(
                item = item,
                onOpen = {
                    viewModel.onDismissPreview()
                    onOpenItem(item.id)
                },
            )
        }
    }
}

/** The map's sources and layers. Order matters: later layers draw on top. */
@Composable
@MaplibreComposable
private fun MapContent(state: MapUiState, onMarkerClick: (String) -> Unit) {
    val zoneColor = MaterialTheme.colorScheme.primary
    val zones = rememberGeoJsonSource(GeoJsonData.JsonString(state.zonesGeoJson))
    FillLayer(id = "matchit-zones-fill", source = zones, color = const(zoneColor), opacity = const(0.10f))
    LineLayer(id = "matchit-zones-outline", source = zones, color = const(zoneColor), width = const(1.dp))

    ItemDots(id = "matchit-lost", geoJson = state.lostGeoJson, color = LostMarkerColor, onMarkerClick = onMarkerClick)
    ItemDots(id = "matchit-found", geoJson = state.foundGeoJson, color = FoundMarkerColor, onMarkerClick = onMarkerClick)

    state.myLocation?.let { MyLocationDot(it) }
}

/** One layer of item dots. A tap reads the item id back from the tapped feature's properties. */
@Composable
@MaplibreComposable
private fun ItemDots(id: String, geoJson: String, color: Color, onMarkerClick: (String) -> Unit) {
    val source = rememberGeoJsonSource(GeoJsonData.JsonString(geoJson))
    CircleLayer(
        id = id,
        source = source,
        color = const(color),
        radius = const(8.dp),
        strokeColor = const(Color.White),
        strokeWidth = const(2.dp),
        hitPadding = 8.dp,
        onClick = { features ->
            val itemId = features.firstOrNull()?.properties?.get(MapGeoJson.PROP_ID)?.jsonPrimitive?.content
            if (itemId != null) {
                onMarkerClick(itemId)
                ClickResult.Consume
            } else {
                ClickResult.Pass
            }
        },
    )
}

@Composable
@MaplibreComposable
fun MyLocationDot(position: LatLng) {
    val source = rememberGeoJsonSource(GeoJsonData.JsonString(MapGeoJson.points(listOf("me" to position))))
    CircleLayer(
        id = "matchit-my-location",
        source = source,
        color = const(MyLocationColor),
        radius = const(7.dp),
        strokeColor = const(Color.White),
        strokeWidth = const(3.dp),
    )
}

@Composable
private fun MapLegend() {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LegendEntry(LostMarkerColor, stringResource(R.string.type_lost))
            LegendEntry(FoundMarkerColor, stringResource(R.string.type_found))
        }
    }
}

@Composable
private fun LegendEntry(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(color = color, shape = CircleShape, modifier = Modifier.size(10.dp)) {}
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun MapNote(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun ItemPreview(item: Item, onOpen: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .navigationBarsPadding(),
    ) {
        ItemCard(item = item, onClick = onOpen, showType = true, modifier = Modifier.clip(MaterialTheme.shapes.medium))
        Spacer(Modifier.size(16.dp))
    }
}
