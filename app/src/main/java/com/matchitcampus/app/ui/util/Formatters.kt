package com.matchitcampus.app.ui.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Small, pure formatting helpers shared by several screens. No Compose or Android imports,
 * so they are unit-tested on the JVM (`FormattersTest`).
 *
 * **How event dates are stored.** The date picker works in UTC: it returns midnight UTC of the
 * chosen calendar day. So an item's `eventDate` means "this calendar day", stored as midnight
 * UTC, and it's always turned back into a date with [ZoneOffset.UTC]. Using the phone's time
 * zone instead would show 22 Nov for an item picked as 23 Nov on a phone west of UTC.
 */
object Formatters {

    private val eventDateFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    /** Midnight UTC of [date], the form `eventDate` is stored in. */
    fun eventDateMillis(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    /** Today on this phone's calendar, as an `eventDate` value. */
    fun todayEventDateMillis(zone: ZoneId = ZoneId.systemDefault()): Long = eventDateMillis(LocalDate.now(zone))

    /** The calendar day an `eventDate` stands for. */
    fun eventLocalDate(eventDateMillis: Long): LocalDate =
        Instant.ofEpochMilli(eventDateMillis).atZone(ZoneOffset.UTC).toLocalDate()

    /** For example "23 Nov 2026" (the exact form follows the phone's language). */
    fun eventDate(eventDateMillis: Long): String = eventLocalDate(eventDateMillis).format(eventDateFormatter)

    /**
     * A distance rounded the way a person would say it: to 10 m under 1 km ("about 150 m"),
     * to 0.1 km above that ("about 1.2 km").
     */
    fun distance(meters: Double): String = if (meters < 1_000) {
        val rounded = ((meters / 10).roundToInt() * 10).coerceAtLeast(10)
        "$rounded m"
    } else {
        val tenths = (meters / 100).roundToInt()
        "${tenths / 10}.${tenths % 10} km"
    }

    /**
     * Coordinates for display, such as "31.25510, 75.70490". Five decimals is about 1 m, finer
     * than any phone's GPS. `Locale.US` keeps the decimal point a point on every phone language.
     */
    fun coordinates(latitude: Double, longitude: Double): String =
        String.format(Locale.US, "%.5f, %.5f", latitude, longitude)

    /** A 0..1 score as a whole percentage, for example 0.823 → 82. */
    fun percent(score: Double): Int = (score.coerceIn(0.0, 1.0) * 100).roundToInt()
}
