package com.matchitcampus.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * A cached match result: how well [candidateItemId] matches [sourceItemId].
 *
 * Phase 4's MatchRepository writes these rows. Caching them means the detail screen shows
 * matches straight away instead of recomputing every comparison each time it opens.
 *
 * The primary key is the pair (source, candidate), so each pair is stored at most once and
 * recomputing just replaces the row. The separate component scores are kept so the UI can
 * explain *why* two items match.
 *
 * [candidateItemId] has its own index because the composite key's index only helps queries
 * that start with [sourceItemId], and deleting an item has to remove rows where it's the
 * candidate too.
 */
@Entity(
    tableName = "matches",
    primaryKeys = ["sourceItemId", "candidateItemId"],
    indices = [Index("candidateItemId")],
)
data class MatchEntity(
    val sourceItemId: String,
    val candidateItemId: String,
    /** Weighted total, 0..1. */
    val score: Double,
    val textScore: Double,
    val categoryScore: Double,
    val locationScore: Double,
    val dateScore: Double,
    val distanceMeters: Double?,
    val daysApart: Int?,
    val computedAt: Long,
)
