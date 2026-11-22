package com.matchitcampus.app.data.local.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.matchitcampus.app.data.local.AppDatabase
import com.matchitcampus.app.data.local.inMemoryDatabase
import com.matchitcampus.app.data.local.testClaim
import com.matchitcampus.app.data.local.testItem
import com.matchitcampus.app.domain.model.ClaimStatus
import com.matchitcampus.app.domain.model.ItemType
import com.matchitcampus.app.domain.model.SyncState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Tests [ClaimDao], including the `ClaimWithItems` relation. */
@RunWith(AndroidJUnit4::class)
class ClaimDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: ClaimDao

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        dao = db.claimDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun relation_attachesFoundAndLostItems() = runTest {
        db.itemDao().upsert(testItem("found", ownerId = "riya"))
        db.itemDao().upsert(testItem("lost", type = ItemType.LOST, ownerId = "aman"))
        dao.upsert(testClaim("c1", foundItemId = "found", lostItemId = "lost"))

        val claim = dao.observeOutgoing("aman").first().single()

        assertEquals("found", claim.foundItem!!.id)
        assertEquals("lost", claim.lostItem!!.id)
    }

    @Test
    fun relation_itemsNotOnThisPhone_areNull() = runTest {
        dao.upsert(testClaim("c1", foundItemId = "notSyncedYet", lostItemId = null))

        val claim = dao.observeIncoming("riya").first().single()

        assertNull(claim.foundItem)
        assertNull(claim.lostItem)
    }

    @Test
    fun incomingAndOutgoing_filterByUser() = runTest {
        dao.upsert(testClaim("toRiya", foundItemId = "f1", claimantId = "aman", finderId = "riya"))
        dao.upsert(testClaim("toAman", foundItemId = "f2", claimantId = "riya", finderId = "aman"))

        assertEquals(listOf("toRiya"), dao.observeIncoming("riya").first().map { it.claim.id })
        assertEquals(listOf("toAman"), dao.observeOutgoing("riya").first().map { it.claim.id })
        assertEquals(listOf("toRiya"), dao.observeForFoundItem("f1").first().map { it.claim.id })
    }

    @Test
    fun pendingIncomingCount_updatesWhenAClaimIsDecided() = runTest {
        dao.upsert(testClaim("c1", foundItemId = "f1"))
        dao.upsert(testClaim("c2", foundItemId = "f1"))

        dao.observePendingIncomingCount("riya").test {
            assertEquals(2, awaitItem())

            dao.updateStatus("c1", ClaimStatus.REJECTED, SyncState.PENDING_UPLOAD, 2L)
            assertEquals(1, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun getPendingForFoundItem_skipsDecidedClaims() = runTest {
        dao.upsert(testClaim("pending", foundItemId = "f1"))
        dao.upsert(testClaim("rejected", foundItemId = "f1", status = ClaimStatus.REJECTED))
        dao.upsert(testClaim("otherItem", foundItemId = "f2"))

        assertEquals(listOf("pending"), dao.getPendingForFoundItem("f1").map { it.id })
    }

    @Test
    fun deleteAll_removesEveryClaim() = runTest {
        dao.upsert(testClaim("c1", foundItemId = "f1"))
        dao.upsert(testClaim("c2", foundItemId = "f2"))

        dao.deleteAll()

        assertEquals(0, dao.observeIncoming("riya").first().size)
    }
}
