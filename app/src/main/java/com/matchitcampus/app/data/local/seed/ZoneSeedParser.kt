package com.matchitcampus.app.data.local.seed

import com.matchitcampus.app.domain.model.CampusZone
import com.matchitcampus.app.domain.model.ZoneType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Reads the campus zone list from the JSON in `app/src/main/assets/campus_zones.json`.
 *
 * The file looks like this, and is meant to be edited by hand:
 * ```
 * {
 *   "_readme": "free text, ignored by the app",
 *   "zones": [
 *     { "id": "block_34", "name": "Block 34", "type": "ACADEMIC",
 *       "latitude": 31.255, "longitude": 75.705, "radiusMeters": 60.0, "isPlaceholder": true }
 *   ]
 * }
 * ```
 * Zone names live only in this file, never in Kotlin code, so they can be corrected without
 * touching code. `type` must be one of the [ZoneType] constant names.
 *
 * Uses kotlinx.serialization (already in the project for navigation routes). Unknown keys,
 * such as `_readme`, are ignored. This parser does no Android work, so it's unit-tested on the
 * JVM against the real asset file.
 */
object ZoneSeedParser {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * @throws kotlinx.serialization.SerializationException if the JSON is malformed or a
     *   required field is missing.
     * @throws IllegalArgumentException if a value is invalid (for example an unknown `type`).
     */
    fun parse(text: String): List<CampusZone> =
        json.decodeFromString<ZoneSeedFile>(text).zones.map { it.toDomain() }

    @Serializable
    private data class ZoneSeedFile(val zones: List<ZoneSeed>)

    /** One zone as written in the file. */
    @Serializable
    private data class ZoneSeed(
        val id: String,
        val name: String,
        val type: ZoneType,
        val latitude: Double,
        val longitude: Double,
        val radiusMeters: Double,
        val isPlaceholder: Boolean,
    ) {
        fun toDomain() = CampusZone(
            id = id,
            name = name,
            type = type,
            latitude = latitude,
            longitude = longitude,
            radiusMeters = radiusMeters,
            isPlaceholder = isPlaceholder,
        )
    }
}
