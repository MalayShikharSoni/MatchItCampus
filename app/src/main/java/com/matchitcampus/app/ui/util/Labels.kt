package com.matchitcampus.app.ui.util

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.matchitcampus.app.R
import com.matchitcampus.app.domain.model.ClaimStatus
import com.matchitcampus.app.domain.model.ItemStatus
import com.matchitcampus.app.domain.model.ItemType
import com.matchitcampus.app.domain.model.ZoneType
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/*
 * Display text for the domain enums. The enums stay free of Android resources (they're part
 * of the database format and the JVM-tested domain), so the mapping to string resources lives
 * here, in the UI layer. `Category` is the one exception: its display name is also part of the
 * text that matching embeds, so it lives on the enum.
 */

@StringRes
fun ItemType.labelRes(): Int = when (this) {
    ItemType.LOST -> R.string.type_lost
    ItemType.FOUND -> R.string.type_found
}

@StringRes
fun ItemStatus.labelRes(): Int = when (this) {
    ItemStatus.OPEN -> R.string.status_open
    ItemStatus.CLAIM_PENDING -> R.string.status_claim_pending
    ItemStatus.RETURNED -> R.string.status_returned
}

@StringRes
fun ClaimStatus.labelRes(): Int = when (this) {
    ClaimStatus.PENDING -> R.string.claim_status_pending
    ClaimStatus.ACCEPTED -> R.string.claim_status_accepted
    ClaimStatus.REJECTED -> R.string.claim_status_rejected
    ClaimStatus.CANCELLED -> R.string.claim_status_cancelled
}

@StringRes
fun ZoneType.labelRes(): Int = when (this) {
    ZoneType.ACADEMIC -> R.string.zone_type_academic
    ZoneType.HOSTEL -> R.string.zone_type_hostel
    ZoneType.FOOD -> R.string.zone_type_food
    ZoneType.LIBRARY -> R.string.zone_type_library
    ZoneType.ADMIN -> R.string.zone_type_admin
    ZoneType.SPORTS -> R.string.zone_type_sports
    ZoneType.MEDICAL -> R.string.zone_type_medical
    ZoneType.GATE -> R.string.zone_type_gate
    ZoneType.OTHER -> R.string.zone_type_other
}

/**
 * "Today", "Yesterday", "3 days ago", or the date itself for anything older than a week.
 * The comparison is between calendar days, so an item found late last night is "Yesterday"
 * even if that was only a few hours ago.
 */
@Composable
fun relativeEventDate(eventDateMillis: Long): String {
    val days = ChronoUnit.DAYS.between(Formatters.eventLocalDate(eventDateMillis), LocalDate.now()).toInt()
    return when (days) {
        0 -> stringResource(R.string.date_today)
        1 -> stringResource(R.string.date_yesterday)
        in 2..6 -> pluralStringResource(R.plurals.date_days_ago, days, days)
        else -> Formatters.eventDate(eventDateMillis)
    }
}
