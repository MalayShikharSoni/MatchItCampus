package com.matchitcampus.app.domain.model

/**
 * A named area of the LPU campus, such as "Block 34" or "Uni Mall".
 *
 * A zone is a circle: a centre point plus [radiusMeters]. Phase 6's ZoneResolver uses that
 * circle to suggest a zone from the phone's location.
 *
 * @property isPlaceholder true while the coordinates are still the made-up campus centre
 *   from the seed file. Placeholder zones can be picked by hand but are never auto-suggested,
 *   because their location is wrong.
 */
data class CampusZone(
    val id: String,
    val name: String,
    val type: ZoneType,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double,
    val isPlaceholder: Boolean,
)
