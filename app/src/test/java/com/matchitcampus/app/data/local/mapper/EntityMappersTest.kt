package com.matchitcampus.app.data.local.mapper

import com.matchitcampus.app.data.local.entity.ItemEntity
import com.matchitcampus.app.domain.model.CampusZone
import com.matchitcampus.app.domain.model.Category
import com.matchitcampus.app.domain.model.ItemStatus
import com.matchitcampus.app.domain.model.ItemType
import com.matchitcampus.app.domain.model.SyncState
import com.matchitcampus.app.domain.model.ZoneType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** JVM tests for the entity ↔ domain mappers, and for [ItemEntity]'s array-aware equals. */
class EntityMappersTest {

    private val zone = CampusZone("block_34", "Block 34", ZoneType.ACADEMIC, 31.25, 75.70, 60.0, true)

    private val entity = ItemEntity(
        id = "item-1",
        type = ItemType.FOUND,
        title = "Black earphone case",
        description = "Found near the lift",
        category = Category.ELECTRONICS,
        status = ItemStatus.OPEN,
        zoneId = "block_34",
        latitude = 31.25,
        longitude = 75.70,
        addressLine = "Block 34, LPU",
        eventDate = 1_000L,
        createdAt = 2_000L,
        updatedAt = 3_000L,
        ownerId = "riya",
        ownerName = "Riya",
        verificationQuestion = "What sticker is on it?",
        hasPhoto = false,
        localPhotoPath = null,
        embedding = floatArrayOf(0.1f, 0.2f),
        embeddingVersion = 1,
        syncState = SyncState.SYNCED,
    )

    @Test
    fun item_entityToDomainAndBack_keepsEveryStoredField() {
        val domain = entity.toDomain(zone)
        val back = domain.toEntity(embedding = entity.embedding, embeddingVersion = entity.embeddingVersion)

        assertEquals(zone, domain.zone)
        assertEquals(entity, back)
    }

    @Test
    fun item_toEntity_withoutEmbedding_storesNoEmbedding() {
        val back = entity.toDomain().toEntity()

        assertNull(back.embedding)
        assertNull(back.embeddingVersion)
    }

    @Test
    fun itemEntity_equals_comparesEmbeddingByContent() {
        val sameValues = entity.copy(embedding = floatArrayOf(0.1f, 0.2f))
        val differentValues = entity.copy(embedding = floatArrayOf(0.1f, 0.3f))

        assertEquals(entity, sameValues)
        assertEquals(entity.hashCode(), sameValues.hashCode())
        assertNotEquals(entity, differentValues)
        assertNotEquals(entity, entity.copy(embedding = null))
    }

    @Test
    fun zone_roundTrip() {
        assertEquals(zone, zone.toEntity().toDomain())
    }
}
