package com.matchitcampus.app.data.local.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.matchitcampus.app.data.local.AppDatabase
import com.matchitcampus.app.data.local.entity.MatchEntity
import com.matchitcampus.app.data.local.inMemoryDatabase
import com.matchitcampus.app.data.local.testItem
import com.matchitcampus.app.domain.model.ItemStatus
import com.matchitcampus.app.domain.model.ItemType
import com.matchitcampus.app.domain.model.SyncState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Tests [MatchDao]: the composite primary key, the replace transaction and ordering. */
@RunWith(AndroidJUnit4::class)
class MatchDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: MatchDao

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        dao = db.matchDao()
    }

    @After
    fun tearDown() = db.close()

    private fun match(source: String, candidate: String, score: Double) = MatchEntity(
        sourceItemId = source,
        candidateItemId = candidate,
        score = score,
        textScore = score,
        categoryScore = 1.0,
        locationScore = 0.5,
        dateScore = 1.0,
        distanceMeters = null,
        daysApart = 1,
        computedAt = 0L,
    )

    @Test
    fun observeForSource_bestScoreFirst() = runTest {
        dao.upsertAll(listOf(match("lost", "f1", 0.5), match("lost", "f2", 0.9), match("other", "f3", 1.0)))

        assertEquals(listOf("f2", "f1"), dao.observeForSource("lost").first().map { it.candidateItemId })
    }

    @Test
    fun replaceMatchesFor_dropsOldRowsForThatSourceOnly() = runTest {
        dao.upsertAll(listOf(match("lost", "f1", 0.5), match("other", "f1", 0.7)))

        dao.replaceMatchesFor("lost", listOf(match("lost", "f2", 0.8)))

        assertEquals(listOf("f2"), dao.observeForSource("lost").first().map { it.candidateItemId })
        assertEquals(1, dao.observeForSource("other").first().size)
    }

    @Test
    fun samePair_isStoredOnce() = runTest {
        dao.upsertAll(listOf(match("lost", "f1", 0.5)))
        dao.upsertAll(listOf(match("lost", "f1", 0.6)))

        assertEquals(0.6, dao.observeForSource("lost").first().single().score, 0.0)
    }

    @Test
    fun deleteInvolving_removesTheItemAsSourceAndAsCandidate() = runTest {
        dao.upsertAll(listOf(match("x", "f1", 0.5), match("lost", "x", 0.6), match("lost", "f2", 0.7)))

        dao.deleteInvolving("x")

        assertEquals(0, dao.observeForSource("x").first().size)
        assertEquals(listOf("f2"), dao.observeForSource("lost").first().map { it.candidateItemId })
    }

    @Test
    fun replaceMatchesInvolving_replacesRowsInBothDirections_only() = runTest {
        dao.upsertAll(listOf(match("lost", "f1", 0.5), match("f1", "lost", 0.5), match("other", "f2", 0.7)))

        dao.replaceMatchesInvolving("lost", listOf(match("lost", "f2", 0.8), match("f2", "lost", 0.8)))

        assertEquals(listOf("f2"), dao.observeForSource("lost").first().map { it.candidateItemId })
        assertEquals(listOf("lost"), dao.observeForSource("f2").first().map { it.candidateItemId })
        assertEquals(0, dao.observeForSource("f1").first().size)
        assertEquals(1, dao.observeForSource("other").first().size)
    }

    @Test
    fun observeLiveMatches_hidesPairsThatAreNoLongerOpen() = runTest {
        val items = db.itemDao()
        items.upsert(testItem("lost", type = ItemType.LOST, ownerId = "aman", embedding = floatArrayOf(1f)))
        items.upsert(testItem("open", embedding = floatArrayOf(1f)))
        items.upsert(testItem("claimed", status = ItemStatus.CLAIM_PENDING, embedding = floatArrayOf(1f)))
        items.upsert(testItem("deleting", syncState = SyncState.PENDING_DELETE, embedding = floatArrayOf(1f)))
        dao.upsertAll(
            listOf(
                match("lost", "open", 0.6),
                match("lost", "claimed", 0.9),
                match("lost", "deleting", 0.8),
                match("lost", "missing", 0.7),
            ),
        )

        assertEquals(listOf("open"), dao.observeLiveMatches("lost", limit = 10).first().map { it.match.candidateItemId })

        // Once the source itself is returned, it has no live matches at all.
        items.updateStatus("lost", ItemStatus.RETURNED, SyncState.PENDING_UPLOAD, updatedAt = 2_000L)
        assertEquals(0, dao.observeLiveMatches("lost", limit = 10).first().size)
    }

    @Test
    fun observeLiveMatches_flagsFallbackWhileAnItemHasNoEmbedding() = runTest {
        val items = db.itemDao()
        items.upsert(testItem("lost", type = ItemType.LOST, ownerId = "aman", embedding = floatArrayOf(1f)))
        items.upsert(testItem("withVector", embedding = floatArrayOf(1f)))
        items.upsert(testItem("withoutVector"))
        dao.upsertAll(listOf(match("lost", "withVector", 0.9), match("lost", "withoutVector", 0.5)))

        val live = dao.observeLiveMatches("lost", limit = 10).first().associate { it.match.candidateItemId to it.usedFallback }

        assertFalse(live.getValue("withVector"))
        assertTrue(live.getValue("withoutVector"))
    }

    @Test
    fun observeLiveMatches_returnsOnlyTheBestLimitRows() = runTest {
        val items = db.itemDao()
        items.upsert(testItem("lost", type = ItemType.LOST, ownerId = "aman"))
        (1..4).forEach { items.upsert(testItem("f$it")) }
        dao.upsertAll((1..4).map { match("lost", "f$it", it / 10.0) })

        val best = dao.observeLiveMatches("lost", limit = 2).first()

        assertEquals(listOf("f4", "f3"), best.map { it.match.candidateItemId })
    }
}
