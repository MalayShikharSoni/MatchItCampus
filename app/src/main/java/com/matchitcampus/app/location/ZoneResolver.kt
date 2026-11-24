package com.matchitcampus.app.location

import com.matchitcampus.app.domain.model.CampusZone

/**
 * Suggests a campus zone for a point (from GPS or a dropped pin).
 *
 * Pure Kotlin with the zone list passed in, so it's unit-tested on the JVM (`ZoneResolverTest`)
 * without Room. Callers pass the zones they already observe.
 */
object ZoneResolver {

    /**
     * The closest zone whose circle contains the point, or null if the point is in none of them.
     *
     * **Placeholder zones are skipped.** Their coordinates are still the made-up campus centre,
     * so "you're in Block 34" would be a guess. They can still be picked by hand in the zone sheet.
     *
     * When circles overlap (a block inside a large area), the one whose centre is nearest wins,
     * which usually is the smaller, more specific zone.
     */
    fun nearestZone(latitude: Double, longitude: Double, zones: List<CampusZone>): CampusZone? =
        zones
            .asSequence()
            .filter { !it.isPlaceholder }
            .map { it to GeoUtils.haversineMeters(latitude, longitude, it.latitude, it.longitude) }
            .filter { (zone, distance) -> distance <= zone.radiusMeters }
            .minByOrNull { (_, distance) -> distance }
            ?.first
}
