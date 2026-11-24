package com.matchitcampus.app.ui.components

import com.matchitcampus.app.domain.model.CampusZone
import com.matchitcampus.app.domain.model.Category
import com.matchitcampus.app.domain.model.Claim
import com.matchitcampus.app.domain.model.ClaimStatus
import com.matchitcampus.app.domain.model.Item
import com.matchitcampus.app.domain.model.ItemStatus
import com.matchitcampus.app.domain.model.ItemType
import com.matchitcampus.app.domain.model.MatchResult
import com.matchitcampus.app.domain.model.SyncState
import com.matchitcampus.app.domain.model.ZoneType

/**
 * Made-up data for `@Preview` functions only. Previews can't reach the database, so each
 * stateless screen is previewed with these values instead.
 */
internal object PreviewData {

    private const val DAY_MS = 24L * 60 * 60 * 1000
    private const val NOW = 1_795_000_000_000L

    val zone = CampusZone(
        id = "block-34",
        name = "Block 34",
        type = ZoneType.ACADEMIC,
        latitude = 31.2550,
        longitude = 75.7050,
        radiusMeters = 60.0,
        isPlaceholder = true,
    )

    val zones = listOf(
        zone,
        zone.copy(id = "uni-mall", name = "Uni Mall", type = ZoneType.FOOD, radiusMeters = 120.0),
        zone.copy(id = "central-library", name = "Central Library", type = ZoneType.LIBRARY, radiusMeters = 120.0),
    )

    val foundItem = Item(
        id = "found-1",
        type = ItemType.FOUND,
        title = "Black earphone case",
        description = "Small black case with a white sticker, found near the lifts.",
        category = Category.ELECTRONICS,
        status = ItemStatus.OPEN,
        zoneId = zone.id,
        zone = zone,
        latitude = null,
        longitude = null,
        addressLine = null,
        eventDate = NOW - DAY_MS,
        createdAt = NOW - DAY_MS,
        updatedAt = NOW - DAY_MS,
        ownerId = "riya",
        ownerName = "Riya Sharma",
        verificationQuestion = "What sticker is on it?",
        hasPhoto = false,
        localPhotoPath = null,
        syncState = SyncState.SYNCED,
    )

    val lostItem = foundItem.copy(
        id = "lost-1",
        type = ItemType.LOST,
        title = "JBL earbuds cover",
        description = "Lost my JBL earbuds case after the morning lecture.",
        ownerId = "aman",
        ownerName = "Aman Verma",
        verificationQuestion = null,
        status = ItemStatus.CLAIM_PENDING,
    )

    val match = MatchResult(
        sourceItemId = foundItem.id,
        candidateItemId = lostItem.id,
        score = 0.86,
        textScore = 0.82,
        categoryScore = 1.0,
        locationScore = 1.0,
        dateScore = 1.0,
        distanceMeters = null,
        daysApart = 0,
        usedFallback = false,
    )

    val claim = Claim(
        id = "claim-1",
        foundItemId = foundItem.id,
        lostItemId = lostItem.id,
        claimantId = "aman",
        claimantName = "Aman Verma",
        finderId = "riya",
        answer = "A white star sticker",
        message = "I can collect it from Block 34 after 4 pm.",
        status = ClaimStatus.PENDING,
        createdAt = NOW,
        updatedAt = NOW,
        syncState = SyncState.SYNCED,
        foundItem = foundItem,
        lostItem = lostItem,
    )
}
