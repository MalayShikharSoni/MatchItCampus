package com.matchitcampus.app.location

/**
 * A point on the map, in degrees.
 *
 * Our own tiny class instead of `android.location.Location` (an Android class, so it can't be
 * built in plain JVM tests), Firestore's `GeoPoint` or MapLibre's `Position` (which is
 * longitude-first). Everything outside the map and location wrappers uses this.
 */
data class LatLng(val latitude: Double, val longitude: Double)

/**
 * Fixed facts about the campus map.
 *
 * The centre is the approximate LPU campus centre from the brief, the same point the placeholder
 * zones in `campus_zones.json` use. Only the coordinates live here: zone *names* stay in the JSON.
 */
object CampusMap {
    val CENTER = LatLng(31.2550, 75.7050)

    /** Close enough to see a few blocks, far enough to see most of the campus. */
    const val DEFAULT_ZOOM = 15.0

    /** Close enough to place a pin on a single building. */
    const val PIN_ZOOM = 17.0

    /**
     * OpenFreeMap's "liberty" style: free vector tiles from OpenStreetMap data, with no API key
     * or billing account (which is why it's used instead of Google Maps).
     */
    const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
}
