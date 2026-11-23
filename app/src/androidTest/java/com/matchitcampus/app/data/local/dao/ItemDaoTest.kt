package com.matchitcampus.app.data.local.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.matchitcampus.app.data.local.AppDatabase
import com.matchitcampus.app.data.local.inMemoryDatabase
import com.matchitcampus.app.data.local.testItem
import com.matchitcampus.app.data.local.testZone
import com.matchitcampus.app.domain.model.ItemStatus
import com.matchitcampus.app.domain.model.ItemType
import com.matchitcampus.app.domain.model.SyncState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Tests [ItemDao] against a real (in-memory) SQLite database on the device. */
@RunWith(AndroidJUnit4::class)
class ItemDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: ItemDao

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        dao = db.itemDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun upsert_thenGetById_returnsTheSameRow_includingTheEmbeddingBlob() = runTest {
        val item = testItem("a", embedding = floatArrayOf(0.25f, -0.5f, 1f))

        dao.upsert(item)

        val stored = dao.getById("a")
        assertEquals(item, stored)
        assertArrayEquals(item.embedding, stored!!.embedding, 0f)
    }

    @Test
    fun upsert_sameId_replacesTheRow() = runTest {
        dao.upsert(testItem("a"))
        dao.upsert(testItem("a").copy(title = "Changed"))

        assertEquals("Changed", dao.getById("a")!!.title)
        assertEquals(1, dao.observeByType(ItemType.FOUND).first().size)
    }

    @Test
    fun observeByType_emitsOnEveryChange_filtersByType_newestFirst() = runTest {
        dao.observeByType(ItemType.FOUND).test {
            assertEquals(emptyList<Any>(), awaitItem())

            dao.upsert(testItem("old", createdAt = 1_000L))
            assertEquals(listOf("old"), awaitItem().map { it.item.id })

            // A LOST item changes the table, but the FOUND list itself stays the same.
            dao.upsert(testItem("new", createdAt = 2_000L))
            dao.upsert(testItem("lost", type = ItemType.LOST))
            // Room may emit once or twice for these writes; skip until the list is complete.
            var ids = awaitItem().map { it.item.id }
            while (ids.size < 2) ids = awaitItem().map { it.item.id }
            assertEquals(listOf("new", "old"), ids)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun uiQueries_hideRowsPendingDelete() = runTest {
        dao.upsert(testItem("kept"))
        dao.upsert(testItem("deleted", syncState = SyncState.PENDING_DELETE))

        assertEquals(listOf("kept"), dao.observeByType(ItemType.FOUND).first().map { it.item.id })
        assertEquals(listOf("kept"), dao.observeOpen().first().map { it.item.id })
        assertEquals(listOf("kept"), dao.observeByOwner("riya").first().map { it.item.id })
        assertNull(dao.observeById("deleted").first())
        // Sync still has to find it.
        assertEquals("deleted", dao.getBySyncState(SyncState.PENDING_DELETE).single().id)
    }

    @Test
    fun observeById_attachesTheZone_orNullForAnUnknownZone() = runTest {
        db.campusZoneDao().upsert(testZone("block_34", "Block 34"))
        dao.upsert(testItem("withZone", zoneId = "block_34"))
        dao.upsert(testItem("unknownZone", zoneId = "no_such_zone"))

        assertEquals("Block 34", dao.observeById("withZone").first()!!.zone!!.name)
        assertNull(dao.observeById("unknownZone").first()!!.zone)
    }

    @Test
    fun getOpenByType_returnsOnlyOpenItemsOfThatType() = runTest {
        dao.upsert(testItem("open"))
        dao.upsert(testItem("returned", status = ItemStatus.RETURNED))
        dao.upsert(testItem("claimed", status = ItemStatus.CLAIM_PENDING))
        dao.upsert(testItem("lost", type = ItemType.LOST))

        assertEquals(listOf("open"), dao.getOpenByType(ItemType.FOUND).map { it.id })
    }

    @Test
    fun updateStatus_changesOnlyStatusColumns_andKeepsTheEmbedding() = runTest {
        val embedding = floatArrayOf(0.1f, 0.2f)
        dao.upsert(testItem("a", embedding = embedding))

        val changed = dao.updateStatus("a", ItemStatus.RETURNED, SyncState.PENDING_UPLOAD, 5_000L)

        val row = dao.getById("a")!!
        assertEquals(1, changed)
        assertEquals(ItemStatus.RETURNED, row.status)
        assertEquals(SyncState.PENDING_UPLOAD, row.syncState)
        assertEquals(5_000L, row.updatedAt)
        assertArrayEquals(embedding, row.embedding, 0f)
    }

    @Test
    fun updateStatus_unknownId_changesNothing() = runTest {
        assertEquals(0, dao.updateStatus("missing", ItemStatus.RETURNED, SyncState.PENDING_UPLOAD, 1L))
    }

    @Test
    fun observeUnsyncedCount_countsEveryRowThatIsNotSynced() = runTest {
        dao.upsert(testItem("synced"))
        dao.upsert(testItem("upload", syncState = SyncState.PENDING_UPLOAD))
        dao.upsert(testItem("delete", syncState = SyncState.PENDING_DELETE))

        assertEquals(2, dao.observeUnsyncedCount().first())
    }

    @Test
    fun markSynced_onlyMarksTheVersionThatWasUploaded() = runTest {
        dao.upsert(testItem("a", createdAt = 100L, syncState = SyncState.PENDING_UPLOAD))

        // The upload started at version 100, but the user edited the row meanwhile (now 200).
        dao.upsert(testItem("a", createdAt = 200L, syncState = SyncState.PENDING_UPLOAD))
        assertEquals(0, dao.markSynced("a", updatedAt = 100L))
        assertEquals(SyncState.PENDING_UPLOAD, dao.getById("a")!!.syncState)

        // Uploading version 200 then marks it, and keeps the user's timestamp.
        assertEquals(1, dao.markSynced("a", updatedAt = 200L))
        val row = dao.getById("a")!!
        assertEquals(SyncState.SYNCED, row.syncState)
        assertEquals(200L, row.updatedAt)
    }

    @Test
    fun deleteById_removesTheRow() = runTest {
        dao.upsert(testItem("a"))

        dao.deleteById("a")

        assertNull(dao.getById("a"))
    }

    @Test
    fun getNeedingEmbedding_findsMissingAndOutdatedVectors_butNotDeletedRows() = runTest {
        dao.upsert(testItem("none"))
        dao.upsert(testItem("current", embedding = floatArrayOf(1f)))
        dao.upsert(testItem("old", embedding = floatArrayOf(1f)).copy(embeddingVersion = 0))
        dao.upsert(testItem("deleted", syncState = SyncState.PENDING_DELETE))

        assertEquals(setOf("none", "old"), dao.getNeedingEmbedding(version = 1).map { it.id }.toSet())
        assertEquals(setOf("none", "old"), dao.observeNeedingEmbedding(version = 1).first().map { it.id }.toSet())
    }

    @Test
    fun updateEmbedding_onlyIfTheRowIsUnchanged_andLeavesSyncBookkeepingAlone() = runTest {
        dao.upsert(testItem("a", createdAt = 100L, syncState = SyncState.SYNCED))

        // The text was read at version 50, but the row is now at 100: the vector is stale.
        assertEquals(0, dao.updateEmbedding("a", floatArrayOf(1f, 2f), version = 1, updatedAt = 50L))
        assertNull(dao.getById("a")!!.embedding)

        assertEquals(1, dao.updateEmbedding("a", floatArrayOf(1f, 2f), version = 1, updatedAt = 100L))
        val row = dao.getById("a")!!
        assertArrayEquals(floatArrayOf(1f, 2f), row.embedding, 0f)
        assertEquals(1, row.embeddingVersion)
        assertEquals(SyncState.SYNCED, row.syncState)
        assertEquals(100L, row.updatedAt)
    }
}
