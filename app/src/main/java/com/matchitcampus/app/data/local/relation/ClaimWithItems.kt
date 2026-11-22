package com.matchitcampus.app.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.matchitcampus.app.data.local.entity.ClaimEntity
import com.matchitcampus.app.data.local.entity.ItemEntity

/**
 * A claim with the found item it's about and, optionally, the claimant's linked lost item.
 *
 * Both items are nullable: on the finder's phone, the claimant's lost item may never have
 * been synced, and a found item could be missing briefly while sync catches up.
 *
 * See [ItemWithZone] for how `@Relation` works and why queries need `@Transaction`.
 */
data class ClaimWithItems(
    @Embedded val claim: ClaimEntity,
    @Relation(parentColumn = "foundItemId", entityColumn = "id")
    val foundItem: ItemEntity?,
    @Relation(parentColumn = "lostItemId", entityColumn = "id")
    val lostItem: ItemEntity?,
)
