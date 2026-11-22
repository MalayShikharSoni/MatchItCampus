package com.matchitcampus.app.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.matchitcampus.app.data.local.AppDatabase
import com.matchitcampus.app.data.local.inMemoryDatabase
import com.matchitcampus.app.data.local.testZone
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Tests first-run zone seeding, using the real asset file bundled in the app. */
@RunWith(AndroidJUnit4::class)
class ZoneRepositoryTest {

    private lateinit var db: AppDatabase

    /** Reads campus_zones.json from the app's assets, the same way AppContainer does. */
    private val realAsset: () -> String = {
        InstrumentationRegistry.getInstrumentation().targetContext.assets
            .open("campus_zones.json").bufferedReader().use { it.readText() }
    }

    @Before
    fun setUp() {
        db = inMemoryDatabase()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun seedIfEmpty_fillsAnEmptyTableFromTheAsset_andOnlyOnce() = runTest {
        val repository = ZoneRepository(db.campusZoneDao(), realAsset)

        assertTrue(repository.seedIfEmpty())
        assertEquals(17, repository.getZones().size)

        assertFalse(repository.seedIfEmpty())
        assertEquals(17, repository.getZones().size)
    }

    @Test
    fun seedIfEmpty_neverOverwritesExistingZones() = runTest {
        // For example, a zone already corrected with the Zone Capture tool.
        db.campusZoneDao().upsert(testZone("block_34").copy(isPlaceholder = false))
        val repository = ZoneRepository(db.campusZoneDao(), realAsset)

        assertFalse(repository.seedIfEmpty())

        assertEquals(listOf("block_34"), repository.getZones().map { it.id })
    }

    @Test
    fun seedIfEmpty_brokenJson_isLoggedAndLeavesTheTableEmpty() = runTest {
        val repository = ZoneRepository(db.campusZoneDao()) { "{ not json" }

        assertFalse(repository.seedIfEmpty())

        assertEquals(0, repository.getZones().size)
    }
}
