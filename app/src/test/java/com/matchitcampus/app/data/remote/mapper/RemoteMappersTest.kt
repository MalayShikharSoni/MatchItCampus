package com.matchitcampus.app.data.remote.mapper

import com.matchitcampus.app.data.remote.dto.ClaimDto
import com.matchitcampus.app.data.remote.dto.ItemDto
import com.matchitcampus.app.data.remote.dto.UserDto
import com.matchitcampus.app.domain.model.Category
import com.matchitcampus.app.domain.model.ClaimStatus
import com.matchitcampus.app.domain.model.ItemStatus
import com.matchitcampus.app.domain.model.ItemType
import com.matchitcampus.app.domain.model.SyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Conversions between DTOs and Firestore field maps, including malformed documents. */
class RemoteMappersTest {

    private val item = ItemDto(
        id = "item-1",
        type = ItemType.LOST,
        title = "Blue water bottle",
        description = "Milton, dented lid",
        category = Category.WATER_BOTTLES,
        status = ItemStatus.OPEN,
        zoneId = "uni-mall",
        latitude = 31.255,
        longitude = 75.705,
        addressLine = null,
        eventDate = 1_000L,
        createdAt = 2_000L,
        updatedAtMillis = 3_000L,
        ownerId = "aman",
        ownerName = "Aman",
        verificationQuestion = null,
        hasPhoto = false,
        deleted = false,
    )

    @Test
    fun item_roundTripsThroughFirestoreMap() {
        val map = item.toFirestoreMap()
        val parsed = itemDtoFromMap("item-1", map, serverUpdatedAtMillis = 3_500L)
        assertEquals(item.copy(serverUpdatedAtMillis = 3_500L), parsed)
    }

    @Test
    fun item_map_storesEnumsAsNames_andHasEveryKey() {
        val map = item.toFirestoreMap()
        assertEquals("LOST", map[Fields.TYPE])
        assertEquals("WATER_BOTTLES", map[Fields.CATEGORY])
        // Null-valued keys must still be present: the security rules check every field.
        assertTrue(map.containsKey(Fields.ADDRESS_LINE))
        assertTrue(map.containsKey(Fields.VERIFICATION_QUESTION))
        // The embedding and local-only fields are never sent.
        assertFalse(map.keys.any { it.contains("embedding", ignoreCase = true) })
        assertFalse(map.containsKey("syncState"))
        assertFalse(map.containsKey("localPhotoPath"))
    }

    @Test
    fun item_numbersStoredAsLongs_areReadAsDoubles() {
        // Firestore returns whole-number coordinates as Long; they must still parse.
        val map = item.toFirestoreMap() + (Fields.LATITUDE to 31L)
        assertEquals(31.0, itemDtoFromMap("item-1", map, null)!!.latitude!!, 0.0)
    }

    @Test
    fun item_withUnknownEnumOrMissingField_isSkipped() {
        assertNull(itemDtoFromMap("x", item.toFirestoreMap() + (Fields.STATUS to "ARCHIVED"), null))
        assertNull(itemDtoFromMap("x", item.toFirestoreMap() - Fields.OWNER_ID, null))
    }

    @Test
    fun item_toEntity_isSyncedAndUsesClientMillis() {
        val entity = item.toEntity(existing = null)
        assertEquals(SyncState.SYNCED, entity.syncState)
        assertEquals(3_000L, entity.updatedAt)
        assertNull(entity.embedding)
    }

    @Test
    fun claim_roundTripsThroughFirestoreMap() {
        val claim = ClaimDto(
            id = "claim-1",
            foundItemId = "item-2",
            lostItemId = null,
            claimantId = "aman",
            claimantName = "Aman",
            finderId = "riya",
            answer = "A Pikachu sticker",
            message = "It's mine!",
            status = ClaimStatus.PENDING,
            createdAt = 10L,
            updatedAtMillis = 20L,
        )
        assertEquals(claim, claimDtoFromMap("claim-1", claim.toFirestoreMap()))
        assertEquals(SyncState.SYNCED, claim.toEntity().syncState)
    }

    @Test
    fun user_roundTripsThroughFirestoreMap() {
        val user = UserDto(name = "Riya", email = "riya@example.com", regNo = null, createdAt = 5L)
        assertEquals(user, userDtoFromMap(user.toFirestoreMap()))
        assertNull(userDtoFromMap(mapOf(Fields.EMAIL to "no-name@example.com")))
    }
}
