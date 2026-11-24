package com.matchitcampus.app.data.local.seed

import com.matchitcampus.app.domain.model.ZoneType
import java.io.File
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests [ZoneSeedParser], including against the real `campus_zones.json`, so a typo made
 * while editing that file by hand fails a test instead of the app.
 */
class ZoneSeedParserTest {

    /** Gradle runs unit tests with the module folder (`app/`) as the working directory. */
    private val realSeedFile = File("src/main/assets/campus_zones.json")

    @Test
    fun realSeedFile_parses_withUniqueIdsAndNames() {
        val zones = ZoneSeedParser.parse(realSeedFile.readText())

        assertEquals(17, zones.size)
        assertEquals("zone ids must be unique", zones.size, zones.map { it.id }.toSet().size)
        assertTrue(zones.all { it.name.isNotBlank() && it.radiusMeters > 0 })
    }

    @Test
    fun realSeedFile_isStillAllPlaceholders() {
        // This test must be updated once the zones are captured on campus (Phase 6). Until then,
        // no zone may be auto-suggested, because every coordinate is the made-up centre.
        val zones = ZoneSeedParser.parse(realSeedFile.readText())

        assertTrue(zones.all { it.isPlaceholder })
    }

    @Test
    fun parse_readsEveryField_andIgnoresUnknownKeys() {
        val json = """
            {
              "_readme": "ignored",
              "zones": [
                { "id": "block_34", "name": "Block 34", "type": "ACADEMIC",
                  "latitude": 31.25, "longitude": 75.70, "radiusMeters": 60.0,
                  "isPlaceholder": false, "someFutureField": 1 }
              ]
            }
        """.trimIndent()

        val zone = ZoneSeedParser.parse(json).single()

        assertEquals("block_34", zone.id)
        assertEquals("Block 34", zone.name)
        assertEquals(ZoneType.ACADEMIC, zone.type)
        assertEquals(31.25, zone.latitude, 0.0)
        assertEquals(75.70, zone.longitude, 0.0)
        assertEquals(60.0, zone.radiusMeters, 0.0)
        assertEquals(false, zone.isPlaceholder)
    }

    @Test(expected = SerializationException::class)
    fun parse_unknownZoneType_throws() {
        ZoneSeedParser.parse(
            """{ "zones": [ { "id": "x", "name": "X", "type": "CAFE", "latitude": 0, "longitude": 0,
               "radiusMeters": 1, "isPlaceholder": true } ] }""",
        )
    }

    @Test(expected = SerializationException::class)
    fun parse_missingField_throws() {
        ZoneSeedParser.parse("""{ "zones": [ { "id": "x", "name": "X" } ] }""")
    }

    @Test
    fun encode_thenParse_givesTheSameZones_andKeepsTheReadme() {
        val zones = ZoneSeedParser.parse(realSeedFile.readText())
            .mapIndexed { i, zone -> if (i == 0) zone.copy(latitude = 31.2561, longitude = 75.7042, isPlaceholder = false) else zone }

        val exported = ZoneSeedParser.encode(zones, readme = "hello")

        assertEquals(zones, ZoneSeedParser.parse(exported))
        assertEquals("hello", ZoneSeedParser.readme(exported))
        // The readme comes first, like in the hand-written file.
        assertTrue(exported.indexOf("_readme") < exported.indexOf("zones"))
    }

    @Test
    fun readme_ofTheRealFile_isPresent() {
        assertTrue(ZoneSeedParser.readme(realSeedFile.readText())!!.isNotBlank())
    }
}
