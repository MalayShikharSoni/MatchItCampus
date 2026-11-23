package com.matchitcampus.app.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.matchitcampus.app.data.local.AppDatabase
import com.matchitcampus.app.data.local.entity.ItemEntity
import com.matchitcampus.app.data.local.inMemoryDatabase
import com.matchitcampus.app.data.local.mapper.toDomain
import com.matchitcampus.app.data.local.testItem
import com.matchitcampus.app.domain.matching.Embedder
import com.matchitcampus.app.domain.model.ItemStatus
import com.matchitcampus.app.domain.model.ItemType
import com.matchitcampus.app.domain.model.SyncState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests [MatchRepository] on an in-memory database with a fake embedder, so the results don't
 * depend on the real model (that's [com.matchitcampus.app.ml.TextEmbedderHelperTest]'s job).
 */
@RunWith(AndroidJUnit4::class)
class MatchRepositoryTest {

    /**
     * Gives "case" texts one direction and "bottle" texts another, so they're orthogonal.
     * Anything else gets no embedding, like a model failure.
     */
    private class FakeEmbedder : Embedder {
        override val version = 1
        var calls = 0
        override suspend fun embed(text: String): FloatArray? {
            calls++
            return when {
                "case" in text -> floatArrayOf(1f, 0f)
                "bottle" in text -> floatArrayOf(0f, 1f)
                else -> null
            }
        }
    }

    private lateinit var db: AppDatabase
    private lateinit var embedder: FakeEmbedder
    private lateinit var scope: CoroutineScope
    private lateinit var repository: MatchRepository

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        embedder = FakeEmbedder()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        repository = MatchRepository(db, embedder, scope, clock = { NOW })
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    private fun item(id: String, type: ItemType, owner: String, title: String): ItemEntity =
        testItem(id, type = type, ownerId = owner).copy(title = title, description = "")

    private suspend fun seedDemoPair() {
        db.itemDao().upsert(item("lost", ItemType.LOST, "aman", "Black earbuds case"))
        db.itemDao().upsert(item("foundCase", ItemType.FOUND, "riya", "Earphone case"))
        db.itemDao().upsert(item("foundBottle", ItemType.FOUND, "riya", "Steel bottle"))
    }

    @Test
    fun ensureEmbeddings_storesVectors_withoutMarkingRowsForSync() = runTest {
        seedDemoPair()

        assertEquals(3, repository.ensureEmbeddings())

        val row = db.itemDao().getById("lost")!!
        assertNotNull(row.embedding)
        assertEquals(1, row.embeddingVersion)
        assertEquals(SyncState.SYNCED, row.syncState)
        assertEquals(1_000L, row.updatedAt)
        // A second pass finds nothing left to do.
        assertEquals(0, repository.ensureEmbeddings())
        assertEquals(3, embedder.calls)
    }

    @Test
    fun matches_areStoredInBothDirections_bestFirst() = runTest {
        seedDemoPair()

        repository.ensureEmbeddings()

        val lostMatches = repository.observeMatches("lost").first()
        assertEquals(listOf("foundCase", "foundBottle"), lostMatches.map { it.candidateItemId })
        assertEquals(1.0, lostMatches.first().textScore, 1e-6)
        assertEquals(0.0, lostMatches.last().textScore, 1e-6)
        assertEquals(listOf("lost"), repository.observeMatches("foundCase").first().map { it.candidateItemId })
        assertEquals(listOf("lost"), repository.observeMatches("foundBottle").first().map { it.candidateItemId })
    }

    @Test
    fun everyFoundItemListsTheLostItem_evenWhenItIsOutsideTheLostItemsTopFive() = runTest {
        // One lost item and six found cases: the lost item shows only its best 5, but each
        // found item must still list the lost item as its match.
        db.itemDao().upsert(item("lost", ItemType.LOST, "aman", "Black earbuds case"))
        (1..6).forEach { db.itemDao().upsert(item("f$it", ItemType.FOUND, "riya", "Earphone case $it")) }

        repository.ensureEmbeddings()

        assertEquals(5, repository.observeMatches("lost").first().size)
        (1..6).forEach { n ->
            assertEquals(listOf("lost"), repository.observeMatches("f$n").first().map { it.candidateItemId })
        }
    }

    @Test
    fun matchDisappears_whenTheFoundItemIsReturned() = runTest {
        seedDemoPair()
        repository.ensureEmbeddings()

        db.itemDao().updateStatus("foundCase", ItemStatus.RETURNED, SyncState.PENDING_UPLOAD, updatedAt = 2_000L)

        assertEquals(listOf("foundBottle"), repository.observeMatches("lost").first().map { it.candidateItemId })
    }

    @Test
    fun editingAnItem_reEmbedsIt_andReplacesItsMatches() = runTest {
        seedDemoPair()
        repository.ensureEmbeddings()
        val itemRepository = ItemRepository(db.itemDao(), clock = { NOW })

        // Riya realises the "case" was a bottle. saveItem clears the embedding.
        val edited = db.itemDao().getById("foundCase")!!.toDomain().copy(title = "Blue bottle")
        itemRepository.saveItem(edited)
        repository.ensureEmbeddings()

        val textScores = repository.observeMatches("lost").first().associate { it.candidateItemId to it.textScore }
        assertEquals(0.0, textScores.getValue("foundCase"), 1e-6)
    }

    @Test
    fun withoutEmbeddings_matchingFallsBackToKeywords_andSaysSo() = runTest {
        // "wallet" gets no vector from the fake, like a model that failed to load.
        db.itemDao().upsert(item("lost", ItemType.LOST, "aman", "Brown leather wallet"))
        db.itemDao().upsert(item("found", ItemType.FOUND, "riya", "Leather wallet, brown"))

        repository.ensureEmbeddings()

        val match = repository.observeMatches("lost").first().single()
        assertTrue(match.usedFallback)
        assertEquals(1.0, match.textScore, 1e-9)
    }

    @Test
    fun search_ranksByMeaning() = runTest {
        seedDemoPair()
        repository.ensureEmbeddings()

        val hits = repository.search("case", ItemType.FOUND)

        assertEquals(listOf("foundCase", "foundBottle"), hits.map { it.itemId })
    }

    @Test
    fun start_embedsAndMatchesNewItemsByItself() = runBlocking {
        repository.start()

        seedDemoPair()

        // Real time here (runBlocking, not runTest), because the watcher runs on Dispatchers.Default.
        val matches = withTimeout(5_000) {
            repository.observeMatches("lost").first { it.size == 2 }
        }
        assertEquals("foundCase", matches.first().candidateItemId)
    }

    private companion object {
        const val NOW = 5_000L
    }
}
