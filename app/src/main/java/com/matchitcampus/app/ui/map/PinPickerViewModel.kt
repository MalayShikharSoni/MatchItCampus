package com.matchitcampus.app.ui.map

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.matchitcampus.app.R
import com.matchitcampus.app.location.CampusMap
import com.matchitcampus.app.location.GeocoderClient
import com.matchitcampus.app.location.LatLng
import com.matchitcampus.app.location.LocationClient
import com.matchitcampus.app.location.PlaceResult
import com.matchitcampus.app.ui.navigation.Routes
import com.matchitcampus.app.ui.util.appContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class PinPickerUiState(
    val results: List<PlaceResult> = emptyList(),
    val isSearching: Boolean = false,
    /** A search ran and found nothing (or the phone has no geocoder). */
    val noResults: Boolean = false,
    val isLocating: Boolean = false,
)

sealed interface PinPickerEvent {
    /** Move the camera (and so the pin) here. */
    data class MoveTo(val position: LatLng) : PinPickerEvent
    data class Message(val text: Int) : PinPickerEvent
}

/**
 * The full-screen pin picker opened from the Post screen.
 *
 * **The pin is the centre of the map.** Dragging the map moves the place under the fixed pin;
 * tapping somewhere moves the camera (and so the pin) there. So the chosen point is simply the
 * camera centre when "Use this spot" is pressed; this class never has to track a separate marker.
 *
 * It only helps move the camera: place search (forward geocoding) and "my location".
 */
class PinPickerViewModel(
    savedStateHandle: SavedStateHandle,
    private val geocoder: GeocoderClient,
    private val locationClient: LocationClient,
) : ViewModel() {

    /** Where the map opens: the item's existing pin, or the campus centre. */
    val initialPosition: LatLng = savedStateHandle.toRoute<Routes.PinPicker>().let { route ->
        if (route.latitude != null && route.longitude != null) LatLng(route.latitude, route.longitude) else CampusMap.CENTER
    }

    /** The search box text, read directly by the screen so typing is never delayed. */
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _uiState = MutableStateFlow(PinPickerUiState())
    val uiState: StateFlow<PinPickerUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<PinPickerEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<PinPickerEvent> = _events.asSharedFlow()

    private var searchJob: Job? = null

    fun onQueryChange(text: String) {
        _query.value = text
        if (text.isBlank()) _uiState.update { it.copy(results = emptyList(), noResults = false) }
    }

    /** Runs when the keyboard's search key is pressed. A newer search cancels an older one. */
    fun onSearch() {
        val text = _query.value.trim()
        if (text.isEmpty()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, noResults = false) }
            val results = geocoder.search(text)
            _uiState.update { it.copy(results = results, isSearching = false, noResults = results.isEmpty()) }
        }
    }

    fun onResultClick(result: PlaceResult) {
        _uiState.update { it.copy(results = emptyList()) }
        _events.tryEmit(PinPickerEvent.MoveTo(result.position))
    }

    /** "My location" pressed and permission granted. */
    fun onMyLocationGranted(precise: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLocating = true) }
            val position = locationClient.currentLocation()
            _uiState.update { it.copy(isLocating = false) }
            when {
                position == null -> _events.emit(PinPickerEvent.Message(R.string.location_unavailable))
                else -> {
                    _events.emit(PinPickerEvent.MoveTo(position))
                    if (!precise) _events.emit(PinPickerEvent.Message(R.string.location_approximate))
                }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                PinPickerViewModel(createSavedStateHandle(), container.geocoderClient, container.locationClient)
            }
        }
    }
}
