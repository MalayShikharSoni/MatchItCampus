package com.matchitcampus.app.data.local.relation

import androidx.room.Embedded
import com.matchitcampus.app.data.local.entity.MatchEntity

/**
 * A cached match plus a flag computed by the query in
 * [com.matchitcampus.app.data.local.dao.MatchDao.observeLiveMatches].
 *
 * Not a table: Room fills it from the query's result columns. [Embedded] maps the `m.*`
 * columns onto a [MatchEntity], and [usedFallback] comes from the `AS usedFallback` column.
 */
data class MatchWithFallback(
    @Embedded val match: MatchEntity,
    val usedFallback: Boolean,
)
