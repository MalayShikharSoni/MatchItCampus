package com.matchitcampus.app.location

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Distance helpers in plain Kotlin (no Android `Location` class), so they're unit-tested.
 *
 * Added in Phase 4 because matching needs distances; Phase 6's location features reuse it.
 */
object GeoUtils {

    /** Mean Earth radius in metres, the usual value for the haversine formula. */
    private const val EARTH_RADIUS_METERS = 6_371_008.8

    /**
     * Straight-line ("great-circle") distance in metres between two latitude/longitude points,
     * using the haversine formula. It treats the Earth as a sphere, which is off by at most
     * about 0.5%: a few metres across a campus, far below GPS error.
     */
    fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val h = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
}
