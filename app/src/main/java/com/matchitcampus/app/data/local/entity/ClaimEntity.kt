package com.matchitcampus.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.matchitcampus.app.domain.model.ClaimStatus
import com.matchitcampus.app.domain.model.SyncState

/**
 * One row of the `claims` table: someone saying a FOUND item is theirs.
 *
 * Indexed by the columns the Claims screens query: [foundItemId] ("claims on this item"),
 * [claimantId] (my outgoing claims) and [finderId] (claims on my items).
 *
 * There are no foreign keys to `items`, for the same reason as in [ItemEntity]: synced
 * rows can arrive before the items they point to.
 */
@Entity(
    tableName = "claims",
    indices = [
        Index("foundItemId"),
        Index("claimantId"),
        Index("finderId"),
    ],
)
data class ClaimEntity(
    @PrimaryKey val id: String,
    val foundItemId: String,
    /** The claimant's own LOST post, if they linked one. Marked RETURNED on acceptance. */
    val lostItemId: String?,
    val claimantId: String,
    val claimantName: String,
    /** Owner of the found item. Stored on the claim so the finder can query "my incoming claims". */
    val finderId: String,
    /** The claimant's answer to the finder's verification question. */
    val answer: String,
    val message: String,
    val status: ClaimStatus,
    val createdAt: Long,
    val updatedAt: Long,
    val syncState: SyncState,
)
