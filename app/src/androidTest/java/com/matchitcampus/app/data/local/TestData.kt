package com.matchitcampus.app.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.matchitcampus.app.data.local.entity.CampusZoneEntity
import com.matchitcampus.app.data.local.entity.ClaimEntity
import com.matchitcampus.app.data.local.entity.ItemEntity
import com.matchitcampus.app.domain.model.Category
import com.matchitcampus.app.domain.model.ClaimStatus
import com.matchitcampus.app.domain.model.ItemStatus
import com.matchitcampus.app.domain.model.ItemType
import com.matchitcampus.app.domain.model.SyncState
import com.matchitcampus.app.domain.model.ZoneType

/*
 * Shared helpers for the database tests: an in-memory database, and builders that fill in
 * sensible defaults so each test only spells out the fields it cares about.
 */

/**
 * A fresh database that lives only in memory. It's empty for every test and disappears when
 * closed, so tests can't affect each other or the app's real data.
 */
fun inMemoryDatabase(): AppDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
        .build()

fun testItem(
    id: String,
    type: ItemType = ItemType.FOUND,
    status: ItemStatus = ItemStatus.OPEN,
    ownerId: String = "riya",
    zoneId: String? = null,
    createdAt: Long = 1_000L,
    embedding: FloatArray? = null,
    syncState: SyncState = SyncState.SYNCED,
) = ItemEntity(
    id = id,
    type = type,
    title = "Item $id",
    description = "Description of $id",
    category = Category.ELECTRONICS,
    status = status,
    zoneId = zoneId,
    latitude = null,
    longitude = null,
    addressLine = null,
    eventDate = createdAt,
    createdAt = createdAt,
    updatedAt = createdAt,
    ownerId = ownerId,
    ownerName = ownerId.replaceFirstChar { it.uppercase() },
    verificationQuestion = if (type == ItemType.FOUND) "What colour is it?" else null,
    hasPhoto = false,
    localPhotoPath = null,
    embedding = embedding,
    embeddingVersion = embedding?.let { 1 },
    syncState = syncState,
)

fun testClaim(
    id: String,
    foundItemId: String,
    lostItemId: String? = null,
    claimantId: String = "aman",
    finderId: String = "riya",
    status: ClaimStatus = ClaimStatus.PENDING,
    createdAt: Long = 1_000L,
    syncState: SyncState = SyncState.SYNCED,
) = ClaimEntity(
    id = id,
    foundItemId = foundItemId,
    lostItemId = lostItemId,
    claimantId = claimantId,
    claimantName = claimantId.replaceFirstChar { it.uppercase() },
    finderId = finderId,
    answer = "Blue",
    message = "",
    status = status,
    createdAt = createdAt,
    updatedAt = createdAt,
    syncState = syncState,
)

fun testZone(id: String, name: String = id) = CampusZoneEntity(
    id = id,
    name = name,
    type = ZoneType.ACADEMIC,
    latitude = 31.255,
    longitude = 75.705,
    radiusMeters = 60.0,
    isPlaceholder = true,
)
