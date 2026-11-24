package com.matchitcampus.app.location

import com.matchitcampus.app.domain.model.CampusZone
import com.matchitcampus.app.domain.model.ZoneType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ZoneResolverTest {

    /** About 0.0009° of latitude is 100 m. */
    private val center = LatLng(31.2550, 75.7050)

    private fun zone(id: String, latitude: Double, longitude: Double, radius: Double, placeholder: Boolean = false) =
        CampusZone(id, id, ZoneType.ACADEMIC, latitude, longitude, radius, placeholder)

    private val block34 = zone("block_34", center.latitude, center.longitude, radius = 60.0)
    private val hostels = zone("hostels", center.latitude + 0.0009, center.longitude, radius = 120.0)

    @Test
    fun pointInsideAZone_returnsIt() {
        assertEquals(block34, ZoneResolver.nearestZone(center.latitude + 0.0002, center.longitude, listOf(block34, hostels)))
    }

    @Test
    fun pointOutsideEveryZone_returnsNull() {
        // About 500 m north: outside both circles.
        assertNull(ZoneResolver.nearestZone(center.latitude + 0.0045, center.longitude, listOf(block34, hostels)))
    }

    @Test
    fun overlappingZones_nearestCentreWins() {
        // 70 m north of Block 34's centre: outside Block 34 (60 m), inside Hostels (30 m from its centre).
        val point = LatLng(center.latitude + 0.00063, center.longitude)
        assertEquals(hostels, ZoneResolver.nearestZone(point.latitude, point.longitude, listOf(block34, hostels)))

        // A big zone around both: the point is still closer to Block 34's centre, so Block 34 wins.
        val campus = zone("campus", center.latitude + 0.0005, center.longitude, radius = 1_000.0)
        assertEquals(block34, ZoneResolver.nearestZone(center.latitude, center.longitude, listOf(campus, block34)))
    }

    @Test
    fun placeholderZones_areNeverSuggested() {
        val placeholder = block34.copy(isPlaceholder = true)
        assertNull(ZoneResolver.nearestZone(center.latitude, center.longitude, listOf(placeholder)))
    }

    @Test
    fun placeholderSkipped_realZoneStillFound() {
        val placeholder = block34.copy(id = "placeholder", isPlaceholder = true)
        val big = zone("big", center.latitude + 0.0005, center.longitude, radius = 200.0)
        assertEquals(big, ZoneResolver.nearestZone(center.latitude, center.longitude, listOf(placeholder, big)))
    }

    @Test
    fun noZones_returnsNull() {
        assertNull(ZoneResolver.nearestZone(center.latitude, center.longitude, emptyList()))
    }
}
