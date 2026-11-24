package com.matchitcampus.app.ui

import com.matchitcampus.app.domain.model.CampusZone
import com.matchitcampus.app.domain.model.Category
import com.matchitcampus.app.domain.model.Item
import com.matchitcampus.app.domain.model.ItemStatus
import com.matchitcampus.app.domain.model.ItemType
import com.matchitcampus.app.domain.model.SyncState
import com.matchitcampus.app.domain.model.ZoneType

/** Builders for the UI-layer unit tests. Each test overrides only the fields it cares about. */
object TestItems {

    fun zone(id: String, type: ZoneType = ZoneType.ACADEMIC, name: String = id) = CampusZone(
        id = id,
        name = name,
        type = type,
        latitude = 31.2550,
        longitude = 75.7050,
        radiusMeters = 60.0,
        isPlaceholder = true,
    )

    fun item(
        id: String,
        type: ItemType = ItemType.FOUND,
        category: Category = Category.ELECTRONICS,
        status: ItemStatus = ItemStatus.OPEN,
        zone: CampusZone? = null,
        eventDate: Long = 0L,
        title: String = "Item $id",
    ) = Item(
        id = id,
        type = type,
        title = title,
        description = "",
        category = category,
        status = status,
        zoneId = zone?.id,
        zone = zone,
        latitude = null,
        longitude = null,
        addressLine = null,
        eventDate = eventDate,
        createdAt = 0L,
        updatedAt = 0L,
        ownerId = "owner",
        ownerName = "Owner",
        verificationQuestion = null,
        hasPhoto = false,
        localPhotoPath = null,
        syncState = SyncState.SYNCED,
    )
}
