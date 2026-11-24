package com.matchitcampus.app.ui.map

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.matchitcampus.app.R
import com.matchitcampus.app.domain.model.ItemType
import com.matchitcampus.app.location.CampusMap
import com.matchitcampus.app.location.LatLng
import com.matchitcampus.app.ui.theme.MatchItCampusTheme
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position

/*
 * Small pieces shared by every map in the app (Map tab, pin picker, item mini map).
 *
 * The maps use MapLibre Compose with OpenFreeMap tiles instead of Google Maps Compose: same
 * idea (a map composable plus markers and layers declared as composables), but no API key and
 * no billing account, which a student project on the free Firebase plan can't depend on.
 */

/** The OpenFreeMap "liberty" style, a free street map built from OpenStreetMap data. */
val OpenFreeMapStyle: BaseStyle = BaseStyle.Uri(CampusMap.STYLE_URL)

/** Fixed marker colours, so "orange = lost, teal = found" is the same in light, dark and dynamic colour. */
val LostMarkerColor = Color(0xFFE65100)
val FoundMarkerColor = Color(0xFF00897B)
val MyLocationColor = Color(0xFF1E88E5)

fun ItemType.markerColor(): Color = when (this) {
    ItemType.LOST -> LostMarkerColor
    ItemType.FOUND -> FoundMarkerColor
}

/** MapLibre's positions are longitude-first; ours are latitude-first. Converting in one place avoids swaps. */
fun LatLng.toPosition() = Position(longitude = longitude, latitude = latitude)

fun Position.toLatLng() = LatLng(latitude = latitude, longitude = longitude)

fun cameraAt(center: LatLng, zoom: Double = CampusMap.DEFAULT_ZOOM) =
    CameraPosition(center = center.toPosition(), zoom = zoom)

/**
 * The map-data credit, always on screen. OpenStreetMap's licence (ODbL) and OpenFreeMap's terms
 * require visible attribution, so it is plain text rather than an "i" button that hides it.
 */
@Composable
fun MapAttribution(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Text(
            text = stringResource(R.string.map_attribution),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Preview
@Composable
private fun MapAttributionPreview() {
    MatchItCampusTheme { MapAttribution() }
}
