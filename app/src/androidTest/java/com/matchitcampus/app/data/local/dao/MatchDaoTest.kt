package com.matchitcampus.app.data.local.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.matchitcampus.app.data.local.AppDatabase
import com.matchitcampus.app.data.local.entity.MatchEntity
import com.matchitcampus.app.data.local.inMemoryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
}
