package com.matchitcampus.app.data.local.seed

import com.matchitcampus.app.domain.model.CampusZone
import com.matchitcampus.app.domain.model.ZoneType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

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

    /** Pretty-printed with two-space indents, so the export reads like the hand-written file. */
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private val prettyJson = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    /**
     * @throws kotlinx.serialization.SerializationException if the JSON is malformed or a
     *   required field is missing.
     * @throws IllegalArgumentException if a value is invalid (for example an unknown `type`).
     */
    fun parse(text: String): List<CampusZone> =
        json.decodeFromString<ZoneSeedFile>(text).zones.map { it.toDomain() }

    /**
     * The file's `_readme` text, or null if it has none.
     *
     * @throws kotlinx.serialization.SerializationException if the text isn't valid JSON.
     */
    fun readme(text: String): String? =
        (json.parseToJsonElement(text).jsonObject["_readme"] as? JsonPrimitive)?.content

    /**
     * The opposite of [parse]: the whole file, ready to paste over `campus_zones.json`.
     * Used by the debug Zone Capture tool. [readme] is kept as the `_readme` key, first.
     */
    fun encode(zones: List<CampusZone>, readme: String): String {
        val body = json.encodeToJsonElement(ZoneSeedFile(zones.map { ZoneSeed.fromDomain(it) })).jsonObject
        val file: JsonObject = buildJsonObject {
            put("_readme", JsonPrimitive(readme))
            body.forEach { (key, value) -> put(key, value) }
        }
        return prettyJson.encodeToString(JsonObject.serializer(), file)
    }

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

        companion object {
            fun fromDomain(zone: CampusZone) = ZoneSeed(
                id = zone.id,
                name = zone.name,
                type = zone.type,
                latitude = zone.latitude,
                longitude = zone.longitude,
                radiusMeters = zone.radiusMeters,
                isPlaceholder = zone.isPlaceholder,
            )
        }
    }
}
