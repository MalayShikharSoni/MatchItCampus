package com.matchitcampus.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.matchitcampus.app.domain.model.SyncState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests the 1 → 2 migration (items get a nullable `color`).
 *
 * [MigrationTestHelper] builds a real version-1 database file from the exported schema
 * `app/schemas/…/1.json`, we put a row in it with plain SQL (the v1 entity classes no longer
 * exist in code), run the migration, and the helper then checks the result against `2.json`:
 * same tables, columns, types, indices. A wrong migration fails here, not on a user's phone.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun migrate1To2_keepsExistingItems_andGivesThemNoColour() {
        createVersion1WithOneItem()

        // validateDroppedTables = true: the schema check is strict.
        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        db.query("SELECT title, description, embedding, color FROM items WHERE id = 'found-1'").use { cursor ->
            assertTrue("the row survived the migration", cursor.moveToFirst())
            assertEquals("Black earphone case", cursor.getString(0))
            assertEquals("Small JBL case with a star sticker", cursor.getString(1))
            assertNotNull("the stored embedding is kept", cursor.getBlob(2))
            assertTrue("old rows have no colour", cursor.isNull(3))
        }
        db.close()
    }

    /**
     * The same file opened the way the app opens it (Room builder with [ALL_MIGRATIONS]), then
     * read and written through the normal DAO: proves the app starts on an upgraded phone.
     */
    @Test
    fun appCanOpenAMigratedDatabase_andStoreAColour() = runTest {
        createVersion1WithOneItem()

        val context = ApplicationProvider.getApplicationContext<Context>()
        val room = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .addMigrations(*ALL_MIGRATIONS)
            .build()
        try {
            val dao = room.itemDao()
            val old = dao.getById("found-1")
            assertNotNull(old)
            assertNull(old!!.color)
            assertEquals(SyncState.SYNCED, old.syncState)

            dao.upsert(old.copy(color = "black"))
            assertEquals("black", dao.getById("found-1")!!.color)
        } finally {
            room.close()
        }
    }

    /** A version-1 file with one synced FOUND item (including an embedding BLOB). */
    private fun createVersion1WithOneItem() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO items (id, type, title, description, category, status, zoneId, latitude,
                    longitude, addressLine, eventDate, createdAt, updatedAt, ownerId, ownerName,
                    verificationQuestion, hasPhoto, localPhotoPath, embedding, embeddingVersion, syncState)
                VALUES ('found-1', 'FOUND', 'Black earphone case', 'Small JBL case with a star sticker',
                    'ELECTRONICS', 'OPEN', 'block_34', NULL, NULL, NULL, 1795392000000, 1795392000000,
                    1795392000000, 'riya', 'Riya', 'What sticker is on it?', 0, NULL, x'0000803F', 1, 'SYNCED')
                """.trimIndent(),
            )
        }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
