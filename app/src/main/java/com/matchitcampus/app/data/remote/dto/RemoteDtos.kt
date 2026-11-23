package com.matchitcampus.app.data.remote.dto

import com.matchitcampus.app.domain.model.Category
import com.matchitcampus.app.domain.model.ClaimStatus
import com.matchitcampus.app.domain.model.ItemStatus
import com.matchitcampus.app.domain.model.ItemType

/*
 * DTOs ("data transfer objects"): the shape of each Firestore document, as Kotlin classes.
 *
 * They're separate from the Room entities on purpose. A Firestore document leaves out the
 * fields that only make sense on this phone (embedding, local photo path, sync state) and
 * adds fields that only make sense on the server (`deleted`, the server timestamp).
 *
 * Mapping to and from Firestore's `Map<String, Any?>` is written by hand in
 * RemoteMappers.kt rather than with Firestore's reflection-based `toObject()`. That keeps
 * every field name visible, turns enums into readable strings, and lets a malformed document
 * be skipped instead of crashing the app.
 */

/**
 * One `items/{id}` document.
 *
 * @property updatedAtMillis the phone's clock when the change was made. Used for
 *   last-write-wins against the local row, which uses the same clock.
 * @property serverUpdatedAtMillis when Firestore accepted the write (a server timestamp).
 *   Used only to fetch "everything changed since my last sync", because it can't be skewed
 *   by a phone with a wrong clock. Null while our own write is still on its way to the server.
 * @property deleted a "tombstone": the owner deleted the item. Documents are never removed,
 *   because a phone that already has the item would never hear about a removed document.
 */
data class ItemDto(
    val id: String,
    val type: ItemType,
    val title: String,
    val description: String,
    val category: Category,
    val status: ItemStatus,
    val zoneId: String?,
    val latitude: Double?,
    val longitude: Double?,
    val addressLine: String?,
    val eventDate: Long,
    val createdAt: Long,
    val updatedAtMillis: Long,
    val ownerId: String,
    val ownerName: String,
    val verificationQuestion: String?,
    val hasPhoto: Boolean,
    val deleted: Boolean,
    val serverUpdatedAtMillis: Long? = null,
)

/** One `claims/{id}` document. Same timestamp fields as [ItemDto]. */
data class ClaimDto(
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
    val updatedAtMillis: Long,
)

/** One `users/{uid}` document. The uid is the document id, so it isn't stored as a field. */
data class UserDto(
    val name: String,
    val email: String,
    val regNo: String?,
    val createdAt: Long,
)
