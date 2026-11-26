package com.matchitcampus.app.domain.model

/**
 * A lost or found post, as the rest of the app sees it.
 *
 * This is the domain model, separate from Room's `ItemEntity`. The UI and (later) the
 * matching engine work with this class, so they don't depend on database annotations.
 * It also carries the resolved [zone], which the entity only knows as an id.
 *
 * The text embedding is deliberately not here yet: it's an implementation detail of
 * matching. Phase 4 decides how matching reads it.
 *
 * Times are epoch milliseconds (UTC).
 *
 * @property eventDate when the item was lost or found (not when it was posted).
 * @property verificationQuestion asked of anyone who claims a FOUND item; null for LOST.
 * @property localPhotoPath the photo's file on this device, if it has been saved or downloaded.
 * @property color the item's colour as the poster typed it ("black", "navy blue"), or null.
 *   Added in database version 2 (Phase 7); it's part of the text that matching embeds.
 */
data class Item(
    val id: String,
    val type: ItemType,
    val title: String,
    val description: String,
    val category: Category,
    val status: ItemStatus,
    val zoneId: String?,
    val zone: CampusZone?,
    val latitude: Double?,
    val longitude: Double?,
    val addressLine: String?,
    val eventDate: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val ownerId: String,
    val ownerName: String,
    val verificationQuestion: String?,
    val hasPhoto: Boolean,
    val localPhotoPath: String?,
    val syncState: SyncState,
    val color: String? = null,
)
