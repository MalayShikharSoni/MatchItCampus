package com.matchitcampus.app.ui.util

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {

    @Test
    fun eventDate_roundTripsThroughMidnightUtc() {
        val day = LocalDate.of(2026, 11, 23)
        val millis = Formatters.eventDateMillis(day)
        assertEquals(1_795_392_000_000L, millis)
        assertEquals(day, Formatters.eventLocalDate(millis))
    }

    @Test
    fun todayEventDate_usesThePhonesCalendarDay() {
        // Whatever the zone, "today" is stored as midnight UTC of that zone's date.
        val zone = ZoneId.of("Asia/Kolkata")
        val millis = Formatters.todayEventDateMillis(zone)
        assertEquals(LocalDate.now(zone), Formatters.eventLocalDate(millis))
    }

    @Test
    fun distance_under1Km_roundsTo10Metres() {
        assertEquals("150 m", Formatters.distance(148.0))
        assertEquals("10 m", Formatters.distance(2.0))
        assertEquals("990 m", Formatters.distance(994.0))
    }

    @Test
    fun distance_from1Km_showsOneDecimalKm() {
        assertEquals("1.0 km", Formatters.distance(1_000.0))
        assertEquals("1.2 km", Formatters.distance(1_234.0))
        assertEquals("12.5 km", Formatters.distance(12_450.0))
    }

    @Test
    fun percent_roundsAndClamps() {
        assertEquals(82, Formatters.percent(0.823))
        assertEquals(0, Formatters.percent(-0.2))
        assertEquals(100, Formatters.percent(1.4))
    }

    @Test
    fun coordinates_fiveDecimals_withAPoint() {
        assertEquals("31.25510, 75.70490", Formatters.coordinates(31.2551, 75.7049))
    }
}
