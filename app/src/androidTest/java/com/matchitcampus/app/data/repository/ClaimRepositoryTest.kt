package com.matchitcampus.app.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.matchitcampus.app.data.local.AppDatabase
import com.matchitcampus.app.data.local.inMemoryDatabase
import com.matchitcampus.app.data.local.mapper.toDomain
import com.matchitcampus.app.data.local.testClaim
import com.matchitcampus.app.data.local.testItem
import com.matchitcampus.app.domain.model.ClaimStatus
import com.matchitcampus.app.domain.model.ItemStatus
import com.matchitcampus.app.domain.model.ItemType
import com.matchitcampus.app.domain.model.SyncState
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Tests the claim rules in [ClaimRepository], especially the accept transaction. */
@RunWith(AndroidJUnit4::class)
class ClaimRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: ClaimRepository

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        repository = ClaimRepository(db, clock = { NOW })
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun createClaim_storesItPendingUpload_withTheRepositoryTime() = runTest {
        repository.createClaim(testClaim("c1", foundItemId = "f1").toDomain())

        val stored = db.claimDao().getById("c1")!!
        assertEquals(ClaimStatus.PENDING, stored.status)
        assertEquals(SyncState.PENDING_UPLOAD, stored.syncState)
        assertEquals(NOW, stored.createdAt)
    }

    @Test(expected = IllegalArgumentException::class)
    fun createClaim_onYourOwnItem_isRefused() = runTest {
        repository.createClaim(testClaim("c1", foundItemId = "f1", claimantId = "riya", finderId = "riya").toDomain())
    }

    @Test
    fun acceptClaim_returnsTheItem_andRejectsOtherPendingClaims() = runTest {
        db.itemDao().upsert(testItem("f1"))
        db.itemDao().upsert(testItem("lost", type = ItemType.LOST, ownerId = "aman"))
        db.claimDao().upsert(testClaim("winner", foundItemId = "f1", lostItemId = "lost", claimantId = "aman"))
        db.claimDao().upsert(testClaim("loser", foundItemId = "f1", claimantId = "karan"))
        db.claimDao().upsert(testClaim("cancelled", foundItemId = "f1", status = ClaimStatus.CANCELLED))

        assertTrue(repository.acceptClaim("winner"))

        assertEquals(ClaimStatus.ACCEPTED, db.claimDao().getById("winner")!!.status)
        assertEquals(ClaimStatus.REJECTED, db.claimDao().getById("loser")!!.status)
        // Already decided claims are left alone.
        assertEquals(ClaimStatus.CANCELLED, db.claimDao().getById("cancelled")!!.status)
        val found = db.itemDao().getById("f1")!!
        assertEquals(ItemStatus.RETURNED, found.status)
        assertEquals(SyncState.PENDING_UPLOAD, found.syncState)
        // The claimant's lost item is theirs to update, on their own phone.
        assertEquals(ItemStatus.OPEN, db.itemDao().getById("lost")!!.status)
    }

    @Test
    fun acceptClaim_onAClaimThatIsNotPending_changesNothing() = runTest {
        db.itemDao().upsert(testItem("f1"))
        db.claimDao().upsert(testClaim("c1", foundItemId = "f1", status = ClaimStatus.REJECTED))

        assertFalse(repository.acceptClaim("c1"))
        assertFalse(repository.acceptClaim("missing"))

        assertEquals(ItemStatus.OPEN, db.itemDao().getById("f1")!!.status)
    }

    @Test
    fun rejectAndCancel_onlyWorkWhilePending() = runTest {
        db.claimDao().upsert(testClaim("c1", foundItemId = "f1"))
        db.claimDao().upsert(testClaim("c2", foundItemId = "f1"))

        assertTrue(repository.rejectClaim("c1"))
        assertTrue(repository.cancelClaim("c2"))
        assertFalse(repository.cancelClaim("c1"))

        assertEquals(ClaimStatus.REJECTED, db.claimDao().getById("c1")!!.status)
        assertEquals(ClaimStatus.CANCELLED, db.claimDao().getById("c2")!!.status)
    }

    @Test
    fun refreshFoundItemStatus_followsPendingClaims_onTheFindersPhone() = runTest {
        db.itemDao().upsert(testItem("f1", ownerId = "riya"))
        db.claimDao().upsert(testClaim("c1", foundItemId = "f1", finderId = "riya"))

        // A pending claim arrived: OPEN -> CLAIM_PENDING, queued for upload.
        assertTrue(repository.refreshFoundItemStatus("f1", ownerUid = "riya"))
        val claimed = db.itemDao().getById("f1")!!
        assertEquals(ItemStatus.CLAIM_PENDING, claimed.status)
        assertEquals(SyncState.PENDING_UPLOAD, claimed.syncState)

        // Nothing more to change while the claim is still pending.
        assertFalse(repository.refreshFoundItemStatus("f1", ownerUid = "riya"))

        // Rejecting the only claim puts the item back to OPEN.
        assertTrue(repository.rejectClaim("c1"))
        assertEquals(ItemStatus.OPEN, db.itemDao().getById("f1")!!.status)
    }

    @Test
    fun refreshFoundItemStatus_neverTouchesSomeoneElsesItem() = runTest {
        db.itemDao().upsert(testItem("f1", ownerId = "riya"))
        db.claimDao().upsert(testClaim("c1", foundItemId = "f1", claimantId = "aman", finderId = "riya"))

        // On Aman's (the claimant's) phone, Riya's item must not be written.
        assertFalse(repository.refreshFoundItemStatus("f1", ownerUid = "aman"))
        assertEquals(ItemStatus.OPEN, db.itemDao().getById("f1")!!.status)
    }

    private companion object {
        const val NOW = 42_000L
    }
}
