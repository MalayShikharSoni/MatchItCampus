package com.matchitcampus.app.ui.debug

import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.matchitcampus.app.R
import com.matchitcampus.app.data.repository.ZoneRepository
import com.matchitcampus.app.domain.model.CampusZone
import com.matchitcampus.app.location.LocationClient
import com.matchitcampus.app.ui.util.Formatters
import com.matchitcampus.app.ui.util.appContainer
import kotlinx.coroutines.CancellationException
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

private const val TAG = "MatchIt/ZoneCapture"

@Immutable
data class ZoneCaptureUiState(
    val zones: List<CampusZone> = emptyList(),
    /** The zone whose location is being read right now, if any. */
    val capturingZoneId: String? = null,
)

sealed interface ZoneCaptureEvent {
    data class Message(@param:StringRes val text: Int, val arg: String? = null) : ZoneCaptureEvent

    /** The exported `campus_zones.json` text, for the screen to put on the clipboard. */
    data class CopyJson(val json: String) : ZoneCaptureEvent
}

/**
 * Debug-only tool for fixing the placeholder zone coordinates on campus.
 *
 * Walk to the middle of a zone, tap it, and its centre becomes the phone's current location
 * (and it stops being a placeholder, so "Use my location" can now suggest it). When done,
 * "Copy JSON" puts the whole zone list on the clipboard in the `campus_zones.json` format, to
 * paste into the project so every phone gets the real coordinates.
 */
class ZoneCaptureViewModel(
    private val zoneRepository: ZoneRepository,
    private val locationClient: LocationClient,
) : ViewModel() {

    private val capturingZoneId = MutableStateFlow<String?>(null)

    private val _events = MutableSharedFlow<ZoneCaptureEvent>()
    val events: SharedFlow<ZoneCaptureEvent> = _events.asSharedFlow()

    val uiState: StateFlow<ZoneCaptureUiState> = combine(
        zoneRepository.observeZones().catch { e ->
            Log.e(TAG, "Couldn't load zones", e)
            emit(emptyList())
        },
        capturingZoneId,
    ) { zones, capturing -> ZoneCaptureUiState(zones, capturing) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ZoneCaptureUiState())

    /** Called after the location permission is granted. Approximate location is refused: it's km off. */
    fun onCapture(zoneId: String, precise: Boolean) {
        if (capturingZoneId.value != null) return
        if (!precise) {
            viewModelScope.launch { _events.emit(ZoneCaptureEvent.Message(R.string.zone_capture_needs_precise)) }
            return
        }
        viewModelScope.launch {
            capturingZoneId.value = zoneId
            try {
                val position = locationClient.currentLocation()
                if (position == null) {
                    _events.emit(ZoneCaptureEvent.Message(R.string.location_unavailable))
                    return@launch
                }
                val zone = zoneRepository.captureZoneLocation(zoneId, position.latitude, position.longitude)
                if (zone != null) {
                    _events.emit(
                        ZoneCaptureEvent.Message(
                            R.string.zone_capture_done,
                            "${zone.name}: ${Formatters.coordinates(zone.latitude, zone.longitude)}",
                        ),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Couldn't capture zone $zoneId", e)
                _events.emit(ZoneCaptureEvent.Message(R.string.zone_capture_failed))
            } finally {
                capturingZoneId.value = null
            }
        }
    }

    fun onExport() {
        viewModelScope.launch {
            val json = zoneRepository.exportSeedJson()
            // Also in Logcat, in case the clipboard is awkward to reach (e.g. on an emulator).
            Log.i(TAG, "Exported zones:\n$json")
            _events.emit(ZoneCaptureEvent.CopyJson(json))
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                ZoneCaptureViewModel(container.zoneRepository, container.locationClient)
            }
        }
    }
}
