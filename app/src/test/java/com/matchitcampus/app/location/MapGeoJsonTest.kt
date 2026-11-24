package com.matchitcampus.app.location

import com.matchitcampus.app.domain.model.CampusZone
import com.matchitcampus.app.domain.model.ZoneType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class MapGeoJsonTest {

    @Test
    fun points_areLongitudeFirst_andCarryTheId() {
        val json = Json.parseToJsonElement(MapGeoJson.points(listOf("item-1" to LatLng(31.25, 75.70)))).jsonObject

        assertEquals("FeatureCollection", json["type"]!!.jsonPrimitive.content)
        val feature = json["features"]!!.jsonArray.single().jsonObject
        val coordinates = feature["geometry"]!!.jsonObject["coordinates"]!!.jsonArray
        assertEquals(75.70, coordinates[0].jsonPrimitive.double, 0.0)
        assertEquals(31.25, coordinates[1].jsonPrimitive.double, 0.0)
        assertEquals("item-1", feature["properties"]!!.jsonObject[MapGeoJson.PROP_ID]!!.jsonPrimitive.content)
    }

    @Test
    fun emptyList_isAValidEmptyCollection() {
        val json = Json.parseToJsonElement(MapGeoJson.points(emptyList())).jsonObject
        assertEquals(0, json["features"]!!.jsonArray.size)
    }

    @Test
    fun circlePoints_areAtTheRadius_andTheRingIsClosed() {
        val center = LatLng(31.255, 75.705)
        val ring = MapGeoJson.circlePoints(center, radiusMeters = 100.0, segments = 32)

        assertEquals(33, ring.size)
        assertEquals(ring.first(), ring.last())
        ring.forEach {
            assertEquals(100.0, GeoUtils.haversineMeters(center.latitude, center.longitude, it.latitude, it.longitude), 0.5)
        }
    }

    @Test
    fun zoneCircles_oneClosedPolygonPerZone() {
        val zone = CampusZone("block_34", "Block 34", ZoneType.ACADEMIC, 31.255, 75.705, 60.0, isPlaceholder = false)
        val json = Json.parseToJsonElement(MapGeoJson.zoneCircles(listOf(zone), segments = 16)).jsonObject

        val geometry = json["features"]!!.jsonArray.single().jsonObject["geometry"]!!.jsonObject
        assertEquals("Polygon", geometry["type"]!!.jsonPrimitive.content)
        assertEquals(17, geometry["coordinates"]!!.jsonArray.single().jsonArray.size)
    }
}
