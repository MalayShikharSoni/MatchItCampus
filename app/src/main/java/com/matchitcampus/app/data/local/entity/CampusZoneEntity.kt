package com.matchitcampus.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.matchitcampus.app.domain.model.ZoneType

/**
 * One row of the `campus_zones` table.
 *
 * Seeded once from `assets/campus_zones.json` the first time the app runs (see
 * [com.matchitcampus.app.data.repository.ZoneRepository.seedIfEmpty]). After that, Room is the
 * source, and the debug Zone Capture tool (Phase 6) edits these rows directly.
 */
@Entity(tableName = "campus_zones")
data class CampusZoneEntity(
    /** A readable, stable id such as `block_34`. Items refer to zones by this id. */
    @PrimaryKey val id: String,
    val name: String,
    val type: ZoneType,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double,
    val isPlaceholder: Boolean,
)
