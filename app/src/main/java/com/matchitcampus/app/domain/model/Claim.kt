package com.matchitcampus.app.domain.model

/**
 * One person saying "this found item is mine".
 *
 * The claimant answers the finder's verification question ([answer]) and may link one of
 * their own LOST posts ([lostItemId]), so that it can be marked RETURNED when the claim is
 * accepted.
 *
 * [foundItem] and [lostItem] are filled in from the local database when those items are
 * there. They can be null when an item hasn't been synced to this phone yet.
 */
data class Claim(
    val id: String,
    val foundItemId: String,
    val lostItemId: String?,
    val claimantId: String,
    val claimantName: String,
    val finderId: String,
    val answer: String,
    val message: String,
    val status: ClaimStatus,
    val createdAt: Long,
    val updatedAt: Long,
    val syncState: SyncState,
    val foundItem: Item? = null,
    val lostItem: Item? = null,
)
