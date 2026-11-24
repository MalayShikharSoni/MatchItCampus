package com.matchitcampus.app.ui.map

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.matchitcampus.app.data.repository.ItemRepository
import com.matchitcampus.app.data.repository.ZoneRepository
import com.matchitcampus.app.domain.model.CampusZone
import com.matchitcampus.app.domain.model.Item
import com.matchitcampus.app.domain.model.ItemType
import com.matchitcampus.app.location.LatLng
import com.matchitcampus.app.location.LocationClient
import com.matchitcampus.app.location.MapGeoJson
import com.matchitcampus.app.location.mapPosition
import com.matchitcampus.app.location.toLatLng
import com.matchitcampus.app.ui.util.appContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "MatchIt/MapViewModel"

/**
 * What the Map tab draws. The GeoJSON is built here, in the ViewModel, rather than during
 * composition, so a recomposition never rebuilds it.
 *
 * @property unplacedCount open items with neither a pin nor a real zone, which can't be drawn.
 */
@Immutable
data class MapUiState(
    val items: Map<String, Item> = emptyMap(),
    val lostGeoJson: String = MapGeoJson.points(emptyList()),
    val foundGeoJson: String = MapGeoJson.points(emptyList()),
    val zonesGeoJson: String = MapGeoJson.zoneCircles(emptyList()),
    val unplacedCount: Int = 0,
    val selectedItem: Item? = null,
    val myLocation: LatLng? = null,
    val isTrackingLocation: Boolean = false,
)

/**
 * The Map tab: open items as coloured dots, real zones as circles, and an optional live
 * "my location" dot.
 *
 * **Live location is started and stopped by the screen** ([startLocationUpdates] /
 * [stopLocationUpdates]), from a `DisposableEffect` with a lifecycle observer, so the GPS runs
 * only while the map is actually visible. Collecting [LocationClient.locationUpdates] starts the
 * updates; cancelling [updatesJob] reaches the flow's `awaitClose`, which removes them.
 */
class MapViewModel(
    itemRepository: ItemRepository,
    zoneRepository: ZoneRepository,
    private val locationClient: LocationClient,
) : ViewModel() {

    private val selectedItemId = MutableStateFlow<String?>(null)
    private val myLocation = MutableStateFlow<LatLng?>(null)

    /** The user switched "my location" on (and has granted permission). */
    private val isTracking = MutableStateFlow(false)
    private var updatesJob: Job? = null
    private var centerOnNextFix = false

    private val _centerRequests = MutableSharedFlow<LatLng>(extraBufferCapacity = 1)

    /** One-shot "move the camera here" requests (the first fix after tapping "my location"). */
    val centerRequests: SharedFlow<LatLng> = _centerRequests.asSharedFlow()

    private val placed = combine(
        itemRepository.observeOpenItems(),
        zoneRepository.observeZones(),
    ) { items, zones -> buildPlacement(items, zones) }
        .catch { e ->
            Log.e(TAG, "Couldn't load the map's items or zones", e)
            emit(Placement())
        }

    val uiState: StateFlow<MapUiState> = combine(placed, selectedItemId, myLocation, isTracking) { placement, selectedId, location, tracking ->
        MapUiState(
            items = placement.items,
            lostGeoJson = placement.lostGeoJson,
            foundGeoJson = placement.foundGeoJson,
            zonesGeoJson = placement.zonesGeoJson,
            unplacedCount = placement.unplacedCount,
            selectedItem = selectedId?.let { placement.items[it] },
            myLocation = location,
            isTrackingLocation = tracking,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState())

    fun onMarkerClick(itemId: String) {
        selectedItemId.value = itemId
    }

    fun onDismissPreview() {
        selectedItemId.value = null
    }

    /** "My location" tapped and permission granted: show the dot and centre on the next fix. */
    fun onMyLocationGranted() {
        isTracking.value = true
        val known = myLocation.value
        if (known != null) _centerRequests.tryEmit(known) else centerOnNextFix = true
        startLocationUpdates()
    }

    /** Called by the screen when it becomes visible. Does nothing until "my location" is on. */
    fun startLocationUpdates() {
        if (!isTracking.value || updatesJob?.isActive == true) return
        updatesJob = viewModelScope.launch {
            locationClient.locationUpdates()
                .catch { e -> Log.e(TAG, "Location updates failed", e) }
                .collect { location ->
                    val position = location.toLatLng()
                    myLocation.value = position
                    if (centerOnNextFix) {
                        centerOnNextFix = false
                        _centerRequests.tryEmit(position)
                    }
                }
        }
    }

    /** Called by the screen when it stops being visible, so the GPS can switch off. */
    fun stopLocationUpdates() {
        updatesJob?.cancel()
        updatesJob = null
    }

    private data class Placement(
        val items: Map<String, Item> = emptyMap(),
        val lostGeoJson: String = MapGeoJson.points(emptyList()),
        val foundGeoJson: String = MapGeoJson.points(emptyList()),
        val zonesGeoJson: String = MapGeoJson.zoneCircles(emptyList()),
        val unplacedCount: Int = 0,
    )

    /** Splits items by type and turns them and the real zones into GeoJSON. */
    private fun buildPlacement(items: List<Item>, zones: List<CampusZone>): Placement {
        val withPosition = items.mapNotNull { item -> item.mapPosition()?.let { item to it } }
        fun pointsOf(type: ItemType) = MapGeoJson.points(
            withPosition.filter { (item, _) -> item.type == type }.map { (item, position) -> item.id to position },
        )
        return Placement(
            items = items.associateBy { it.id },
            lostGeoJson = pointsOf(ItemType.LOST),
            foundGeoJson = pointsOf(ItemType.FOUND),
            // Placeholder zones all sit on the campus centre, so drawing them would only stack circles.
            zonesGeoJson = MapGeoJson.zoneCircles(zones.filterNot { it.isPlaceholder }),
            unplacedCount = items.size - withPosition.size,
        )
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                MapViewModel(container.itemRepository, container.zoneRepository, container.locationClient)
            }
        }
    }
}
