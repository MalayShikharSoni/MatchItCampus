package com.matchitcampus.app.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.matchitcampus.app.domain.model.ItemType
import com.matchitcampus.app.location.CampusMap
import com.matchitcampus.app.location.LatLng
import com.matchitcampus.app.location.MapGeoJson
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.interaction.MapInteractions
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.map.MapUiOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource

/**
 * A small, non-interactive map with one marker, for the Item detail screen.
 *
 * Gestures are switched off (`MapInteractions.None` and `MapUiOptions.None`) because it sits
 * inside a scrolling list: a swipe over it should scroll the page, not pan the map.
 *
 * `key(position)` rebuilds the map if the item's spot changes (an edit arriving by sync), since
 * `initialCameraPosition` is only read when the map is first created.
 */
@Composable
fun ItemMiniMap(position: LatLng, type: ItemType, modifier: Modifier = Modifier) {
    key(position) {
        val geoJson = remember(position) { MapGeoJson.points(listOf("item" to position)) }
        val mapState = rememberMapState(
            baseStyle = OpenFreeMapStyle,
            initialCameraPosition = cameraAt(position, CampusMap.PIN_ZOOM),
        ) {
            val source = rememberGeoJsonSource(GeoJsonData.JsonString(geoJson))
            CircleLayer(
                id = "matchit-item",
                source = source,
                color = const(type.markerColor()),
                radius = const(9.dp),
                strokeColor = const(Color.White),
                strokeWidth = const(3.dp),
            )
        }
        Box(
            modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(MaterialTheme.shapes.medium),
        ) {
            MaplibreMap(
                state = mapState,
                interactions = MapInteractions.None,
                uiOptions = MapUiOptions.None,
                overlay = { MapAttribution(Modifier.align(Alignment.BottomStart).padding(4.dp)) },
            )
        }
    }
}
