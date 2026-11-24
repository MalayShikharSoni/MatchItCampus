package com.matchitcampus.app.location

import com.matchitcampus.app.domain.model.Item

/**
 * Where an item should appear on a map, or for "Near me" sorting.
 *
 * - The exact pin, if the poster dropped one or used their location.
 * - Otherwise the centre of its zone, but only a **real** (non-placeholder) zone: placeholder
 *   zones all sit at the campus centre, so using them would stack every item on one spot.
 * - Otherwise null: the item has no known position.
 */
fun Item.mapPosition(): LatLng? {
    if (latitude != null && longitude != null) return LatLng(latitude, longitude)
    val z = zone ?: return null
    return if (z.isPlaceholder) null else LatLng(z.latitude, z.longitude)
}

/** Distance in metres from [from] to this item, or null if the item has no position. */
fun Item.distanceFrom(from: LatLng): Double? = mapPosition()?.let {
    GeoUtils.haversineMeters(from.latitude, from.longitude, it.latitude, it.longitude)
}
