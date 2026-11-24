package com.matchitcampus.app.location

import com.matchitcampus.app.domain.model.CampusZone
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.math.cos
import kotlin.math.sin

/**
 * Builds the GeoJSON text that the map's sources draw.
 *
 * GeoJSON is the standard JSON format for map features: each feature has a geometry (a point
 * or a polygon) and free-form `properties`. The map reads an item's id back from its
 * properties when a marker is tapped.
 *
 * Plain kotlinx.serialization with no MapLibre types, so it's unit-tested on the JVM
 * (`MapGeoJsonTest`). Note GeoJSON's order: **longitude first**, then latitude.
 */
object MapGeoJson {

    /** Name of the property that holds an item's id. */
    const val PROP_ID = "id"

    /** Points for the given ids and positions, as a FeatureCollection. */
    fun points(points: List<Pair<String, LatLng>>): String = featureCollection(
        points.map { (id, position) ->
            feature(
                geometry = buildJsonObject {
                    put("type", "Point")
                    put("coordinates", coordinates(position))
                },
                id = id,
            )
        },
    )

    /**
     * One polygon per zone, approximating its circle with [segments] corners.
     *
     * A circle layer's radius is in screen pixels, so it would stay the same size when zooming.
     * A polygon in real coordinates grows and shrinks with the map, showing the zone's true area.
     */
    fun zoneCircles(zones: List<CampusZone>, segments: Int = 48): String = featureCollection(
        zones.map { zone ->
            feature(
                geometry = buildJsonObject {
                    put("type", "Polygon")
                    putJsonArray("coordinates") { add(circleRing(zone, segments)) }
                },
                id = zone.id,
            )
        },
    )

    /**
     * The corners of the zone's circle. One degree of latitude is about 111.32 km everywhere;
     * a degree of longitude shrinks with cos(latitude). Over a ~100 m circle that flat
     * approximation is accurate to well under a metre.
     */
    internal fun circlePoints(center: LatLng, radiusMeters: Double, segments: Int): List<LatLng> {
        val latDegrees = radiusMeters / METERS_PER_DEGREE_LAT
        val lngDegrees = radiusMeters / (METERS_PER_DEGREE_LAT * cos(Math.toRadians(center.latitude)))
        val corners = (0 until segments).map { i ->
            val angle = 2 * Math.PI * i / segments
            LatLng(center.latitude + latDegrees * sin(angle), center.longitude + lngDegrees * cos(angle))
        }
        // A GeoJSON ring must end where it starts.
        return corners + corners.first()
    }

    private fun circleRing(zone: CampusZone, segments: Int): JsonArray = buildJsonArray {
        circlePoints(LatLng(zone.latitude, zone.longitude), zone.radiusMeters, segments).forEach { add(coordinates(it)) }
    }

    private fun coordinates(position: LatLng): JsonArray = buildJsonArray {
        add(JsonPrimitive(position.longitude))
        add(JsonPrimitive(position.latitude))
    }

    private fun feature(geometry: JsonObject, id: String): JsonObject = buildJsonObject {
        put("type", "Feature")
        put("geometry", geometry)
        putJsonObject("properties") { put(PROP_ID, id) }
    }

    private fun featureCollection(features: List<JsonObject>): String = buildJsonObject {
        put("type", "FeatureCollection")
        put("features", JsonArray(features))
    }.toString()

    private const val METERS_PER_DEGREE_LAT = 111_320.0
}
