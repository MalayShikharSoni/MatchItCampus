package com.matchitcampus.app.data.sync

import com.matchitcampus.app.data.local.entity.ItemEntity
import com.matchitcampus.app.data.remote.dto.ItemDto
import com.matchitcampus.app.domain.model.Category
import com.matchitcampus.app.domain.model.ItemStatus
import com.matchitcampus.app.domain.model.ItemType
import com.matchitcampus.app.domain.model.SyncState
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The last-write-wins rules used when Firestore data is merged into Room. */
class SyncMergeTest {

    @Test
    fun remoteWins_whenNoLocalRow() {
        assertTrue(SyncMerge.remoteWins(localUpdatedAt = null, remoteUpdatedAt = 1L))
    }

    @Test
    fun remoteWins_whenNewerOrEqual_losesWhenOlder() {
        assertTrue(SyncMerge.remoteWins(localUpdatedAt = 100L, remoteUpdatedAt = 200L))
        assertTrue(SyncMerge.remoteWins(localUpdatedAt = 100L, remoteUpdatedAt = 100L))
        assertFalse(SyncMerge.remoteWins(localUpdatedAt = 200L, remoteUpdatedAt = 100L))
    }

    @Test
    fun newRemoteItem_isInsertedAsSynced() {
        val decision = SyncMerge.decide(local = null, remote = remote(updatedAt = 100L))
        val entity = (decision as SyncMerge.ItemDecision.Write).entity
        assertEquals("item-1", entity.id)
        assertEquals(SyncState.SYNCED, entity.syncState)
        assertEquals(100L, entity.updatedAt)
    }

    @Test
    fun olderRemote_neverOverwritesNewerPendingLocalChange() {
        val local = local(updatedAt = 300L, syncState = SyncState.PENDING_UPLOAD, title = "Edited offline")
        val decision = SyncMerge.decide(local, remote(updatedAt = 200L, title = "Old title"))
        assertEquals(SyncMerge.ItemDecision.Skip, decision)
    }

    @Test
    fun newerRemote_replacesLocal_evenIfPending() {
        val local = local(updatedAt = 100L, syncState = SyncState.PENDING_UPLOAD)
        val decision = SyncMerge.decide(local, remote(updatedAt = 200L, status = ItemStatus.RETURNED))
        val entity = (decision as SyncMerge.ItemDecision.Write).entity
        assertEquals(ItemStatus.RETURNED, entity.status)
        assertEquals(SyncState.SYNCED, entity.syncState)
    }

    @Test
    fun remoteTombstone_deletesLocalRow() {
        val decision = SyncMerge.decide(local(updatedAt = 100L), remote(updatedAt = 200L, deleted = true))
        assertEquals(SyncMerge.ItemDecision.Delete, decision)
    }

    @Test
    fun remoteTombstone_withoutLocalRow_isIgnored() {
        assertEquals(SyncMerge.ItemDecision.Skip, SyncMerge.decide(null, remote(updatedAt = 200L, deleted = true)))
    }

    @Test
    fun embedding_isKept_whenTextUnchanged() {
        val embedding = floatArrayOf(0.1f, 0.2f)
        val local = local(updatedAt = 100L, embedding = embedding)
        val entity = (SyncMerge.decide(local, remote(updatedAt = 200L, status = ItemStatus.CLAIM_PENDING))
            as SyncMerge.ItemDecision.Write).entity
        assertArrayEquals(embedding, entity.embedding, 0f)
        assertEquals(1, entity.embeddingVersion)
    }

    @Test
    fun embedding_isCleared_whenTextChanged() {
        val local = local(updatedAt = 100L, embedding = floatArrayOf(0.1f, 0.2f))
        val entity = (SyncMerge.decide(local, remote(updatedAt = 200L, title = "New title"))
            as SyncMerge.ItemDecision.Write).entity
        assertNull(entity.embedding)
        assertNull(entity.embeddingVersion)
    }

    private fun remote(
        updatedAt: Long,
        title: String = "Black earbuds case",
        status: ItemStatus = ItemStatus.OPEN,
        deleted: Boolean = false,
    ) = ItemDto(
        id = "item-1",
        type = ItemType.FOUND,
        title = title,
        description = "Near the library",
        category = Category.ELECTRONICS,
        status = status,
        zoneId = "central-library",
        latitude = null,
        longitude = null,
        addressLine = null,
        eventDate = 50L,
        createdAt = 50L,
        updatedAtMillis = updatedAt,
        ownerId = "riya",
        ownerName = "Riya",
        verificationQuestion = "What sticker is on it?",
        hasPhoto = false,
        deleted = deleted,
    )

    private fun local(
        updatedAt: Long,
        syncState: SyncState = SyncState.SYNCED,
        title: String = "Black earbuds case",
        embedding: FloatArray? = null,
    ) = ItemEntity(
        id = "item-1",
        type = ItemType.FOUND,
        title = title,
        description = "Near the library",
        category = Category.ELECTRONICS,
        status = ItemStatus.OPEN,
        zoneId = "central-library",
        latitude = null,
        longitude = null,
        addressLine = null,
        eventDate = 50L,
        createdAt = 50L,
        updatedAt = updatedAt,
        ownerId = "riya",
        ownerName = "Riya",
        verificationQuestion = "What sticker is on it?",
        hasPhoto = false,
        localPhotoPath = null,
        embedding = embedding,
        embeddingVersion = embedding?.let { 1 },
        syncState = syncState,
    )
}
