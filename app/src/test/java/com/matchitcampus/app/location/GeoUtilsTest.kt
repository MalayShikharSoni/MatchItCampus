package com.matchitcampus.app.location

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoUtilsTest {

    @Test
    fun samePoint_isZero() {
        assertEquals(0.0, GeoUtils.haversineMeters(31.255, 75.705, 31.255, 75.705), 1e-9)
    }

    @Test
    fun oneDegreeOfLatitude_isAbout111Km() {
        // A degree of latitude is 111.19 km on a sphere of the mean Earth radius.
        assertEquals(111_195.0, GeoUtils.haversineMeters(31.0, 75.0, 32.0, 75.0), 10.0)
    }

    @Test
    fun oneDegreeOfLongitudeAtTheEquator_isAbout111Km() {
        assertEquals(111_195.0, GeoUtils.haversineMeters(0.0, 0.0, 0.0, 1.0), 10.0)
    }

    @Test
    fun londonToParis_isAbout344Km() {
        // A well-known reference distance (London 51.5074,-0.1278 to Paris 48.8566,2.3522).
        assertEquals(343_500.0, GeoUtils.haversineMeters(51.5074, -0.1278, 48.8566, 2.3522), 1_000.0)
    }

    @Test
    fun isSymmetric() {
        val there = GeoUtils.haversineMeters(31.2550, 75.7050, 31.2565, 75.7071)
        val back = GeoUtils.haversineMeters(31.2565, 75.7071, 31.2550, 75.7050)
        assertEquals(there, back, 1e-9)
    }
}
