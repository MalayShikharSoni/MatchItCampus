package com.matchitcampus.app.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.matchitcampus.app.data.local.entity.CampusZoneEntity
import com.matchitcampus.app.data.local.entity.ItemEntity

/**
 * An item together with its campus zone, loaded in one DAO call.
 *
 * [Embedded] puts the item's columns directly in this class. [Relation] tells Room to run a
 * second query for the zone whose `id` equals the item's `zoneId`, and attach the result.
 * The zone is nullable because an item may have no zone, or a zone id this phone doesn't know.
 *
 * DAO methods that return this must be annotated `@Transaction`, so both queries see the
 * same snapshot of the database.
 */
data class ItemWithZone(
    @Embedded val item: ItemEntity,
    @Relation(parentColumn = "zoneId", entityColumn = "id")
    val zone: CampusZoneEntity?,
)
