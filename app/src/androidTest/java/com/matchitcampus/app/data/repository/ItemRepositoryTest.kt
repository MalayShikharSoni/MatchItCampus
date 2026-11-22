package com.matchitcampus.app.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.matchitcampus.app.data.local.AppDatabase
import com.matchitcampus.app.data.local.inMemoryDatabase
import com.matchitcampus.app.data.local.mapper.toDomain
import com.matchitcampus.app.data.local.testItem
import com.matchitcampus.app.data.local.testZone
import com.matchitcampus.app.domain.model.ItemStatus
import com.matchitcampus.app.domain.model.ItemType
import com.matchitcampus.app.domain.model.SyncState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Tests [ItemRepository]'s local write rules (sync states, timestamps, hidden deletes). */
@RunWith(AndroidJUnit4::class)
class ItemRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: ItemRepository

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        repository = ItemRepository(db.itemDao(), clock = { NOW })
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun saveItem_marksPendingUpload_stampsTheTime_andClearsTheEmbedding() = runTest {
        db.itemDao().upsert(testItem("a", embedding = floatArrayOf(1f)))
        val edited = db.itemDao().getById("a")!!.toDomain().copy(title = "Edited title")

        repository.saveItem(edited)

        val row = db.itemDao().getById("a")!!
        assertEquals("Edited title", row.title)
        assertEquals(SyncState.PENDING_UPLOAD, row.syncState)
        assertEquals(NOW, row.updatedAt)
        assertNull(row.embedding)
    }

    @Test
    fun observeFeed_returnsDomainItemsWithTheirZone() = runTest {
        db.campusZoneDao().upsert(testZone("block_34", "Block 34"))
        db.itemDao().upsert(testItem("a", zoneId = "block_34"))

        val item = repository.observeFeed(ItemType.FOUND).first().single()

        assertEquals("Block 34", item.zone!!.name)
    }

    @Test
    fun deleteItem_hidesTheItemButKeepsTheRowForSync() = runTest {
        db.itemDao().upsert(testItem("a"))

        assertTrue(repository.deleteItem("a"))

        assertEquals(0, repository.observeFeed(ItemType.FOUND).first().size)
        assertEquals(SyncState.PENDING_DELETE, db.itemDao().getById("a")!!.syncState)
        assertEquals(1, repository.observeUnsyncedCount().first())
    }

    @Test
    fun markReturned_changesTheStatus_orReportsAMissingItem() = runTest {
        db.itemDao().upsert(testItem("a"))

        assertTrue(repository.markReturned("a"))
        assertFalse(repository.markReturned("missing"))

        assertEquals(ItemStatus.RETURNED, repository.observeItem("a").first()!!.status)
    }

    private companion object {
        const val NOW = 42_000L
    }
}
