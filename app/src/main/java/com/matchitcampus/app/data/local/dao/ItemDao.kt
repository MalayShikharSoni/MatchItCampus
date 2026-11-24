package com.matchitcampus.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.matchitcampus.app.data.local.Converters
import com.matchitcampus.app.data.local.entity.ItemEntity
import com.matchitcampus.app.data.local.relation.ItemWithZone
import com.matchitcampus.app.domain.model.ItemStatus
import com.matchitcampus.app.domain.model.ItemType
import com.matchitcampus.app.domain.model.SyncState
import kotlinx.coroutines.flow.Flow

/**
 * Database access for the `items` table.
 *
 * Two kinds of method:
 * - `fun …(): Flow<…>` for anything a screen observes. Room re-runs the query and emits a new
 *   list whenever the table changes, so the UI updates by itself after any write (local or
 *   synced). Room runs these queries off the main thread.
 * - `suspend fun` for one-off reads and for writes. Room moves them to a background thread,
 *   so they can be called from a coroutine without blocking the UI.
 *
 * Every query the UI uses skips rows in `PENDING_DELETE`: the user deleted them, but the
 * deletion hasn't reached Firestore yet. The string literals in the SQL match the enum names
 * written by [com.matchitcampus.app.data.local.Converters].
 *
 * Methods returning [ItemWithZone] are `@Transaction`, because Room loads the zones with a
 * second query and both must read the same snapshot.
 */
@Dao
interface ItemDao {

    /** Inserts the item, or replaces the row with the same id. */
    @Upsert
    suspend fun upsert(item: ItemEntity)

    @Upsert
    suspend fun upsertAll(items: List<ItemEntity>)

    /** The feed for one tab (Lost or Found), newest post first. */
    @Transaction
    @Query(
        """
        SELECT * FROM items
        WHERE type = :type AND syncState != 'PENDING_DELETE'
        ORDER BY createdAt DESC
        """,
    )
    fun observeByType(type: ItemType): Flow<List<ItemWithZone>>

    /** One item, for the detail screen. Emits null if it doesn't exist (or is being deleted). */
    @Transaction
    @Query("SELECT * FROM items WHERE id = :id AND syncState != 'PENDING_DELETE'")
    fun observeById(id: String): Flow<ItemWithZone?>

    /** "My posts". */
    @Transaction
    @Query(
        """
        SELECT * FROM items
        WHERE ownerId = :ownerId AND syncState != 'PENDING_DELETE'
        ORDER BY createdAt DESC
        """,
    )
    fun observeByOwner(ownerId: String): Flow<List<ItemWithZone>>

    /** Every open item, for the map. */
    @Transaction
    @Query("SELECT * FROM items WHERE status = 'OPEN' AND syncState != 'PENDING_DELETE'")
    fun observeOpen(): Flow<List<ItemWithZone>>

    /** One-off read of a single row, including rows waiting to be deleted (sync needs those). */
    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun getById(id: String): ItemEntity?

    /** Candidates for matching: open items of one type. */
    @Query("SELECT * FROM items WHERE type = :type AND status = 'OPEN' AND syncState != 'PENDING_DELETE'")
    suspend fun getOpenByType(type: ItemType): List<ItemEntity>

    /** Every item of one type, for search (which ranks them itself). */
    @Query("SELECT * FROM items WHERE type = :type AND syncState != 'PENDING_DELETE'")
    suspend fun getByType(type: ItemType): List<ItemEntity>

    /**
     * Items whose embedding is missing or was made by another model version, so matching has
     * to (re)compute it. Deleted rows are skipped: they'll never be matched again.
     */
    @Query(
        """
        SELECT * FROM items
        WHERE (embedding IS NULL OR embeddingVersion IS NULL OR embeddingVersion != :version)
          AND syncState != 'PENDING_DELETE'
        """,
    )
    suspend fun getNeedingEmbedding(version: Int): List<ItemEntity>

    /** The same rows as a Flow, so `MatchRepository` notices new or edited items by itself. */
    @Query(
        """
        SELECT * FROM items
        WHERE (embedding IS NULL OR embeddingVersion IS NULL OR embeddingVersion != :version)
          AND syncState != 'PENDING_DELETE'
        """,
    )
    fun observeNeedingEmbedding(version: Int): Flow<List<ItemEntity>>

    /**
     * Stores a freshly computed embedding, but only if the row hasn't changed since its text
     * was read ([updatedAt] still the same). Otherwise the vector belongs to old text, and the
     * edited row is picked up again on the next pass.
     *
     * `updatedAt` and `syncState` are deliberately left alone: the embedding is local only, so
     * computing it is not a change that needs syncing.
     *
     * @return 1 if stored, 0 if the row had changed or is gone.
     */
    suspend fun updateEmbedding(id: String, embedding: FloatArray, version: Int, updatedAt: Long): Int =
        updateEmbeddingBlob(id, Converters().floatArrayToBytes(embedding)!!, version, updatedAt)

    /**
     * The SQL behind [updateEmbedding]. It takes the BLOB bytes, because Room treats an array
     * *parameter* as a list of values for `IN (...)` and doesn't apply the TypeConverter to it,
     * which produced invalid SQL (`embedding = ?,?`). Use [updateEmbedding] instead.
     */
    @Query("UPDATE items SET embedding = :embedding, embeddingVersion = :version WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun updateEmbeddingBlob(id: String, embedding: ByteArray, version: Int, updatedAt: Long): Int

    /** Rows Phase 3's SyncManager still has to push or delete remotely. */
    @Query("SELECT * FROM items WHERE syncState = :syncState")
    suspend fun getBySyncState(syncState: SyncState): List<ItemEntity>

    /**
     * Changes only the status (and the bookkeeping columns), instead of rewriting the whole
     * row. That way it can't accidentally wipe other columns, such as the cached embedding.
     *
     * @return the number of rows changed: 0 if no item has this id.
     */
    @Query("UPDATE items SET status = :status, syncState = :syncState, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: ItemStatus, syncState: SyncState, updatedAt: Long): Int

    /** Changes only the sync bookkeeping. @return the number of rows changed. */
    @Query("UPDATE items SET syncState = :syncState, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateSyncState(id: String, syncState: SyncState, updatedAt: Long): Int

    /**
     * Marks a row SYNCED after its upload, but only if it hasn't changed since the upload
     * started (same [updatedAt], still PENDING_UPLOAD). If the user edited it meanwhile, it
     * stays pending and the newer version is pushed next. `updatedAt` isn't touched, because
     * it's the time of the user's change, not of the sync.
     *
     * @return 1 if the row was marked, 0 if it had changed.
     */
    @Query(
        """
        UPDATE items SET syncState = 'SYNCED'
        WHERE id = :id AND updatedAt = :updatedAt AND syncState = 'PENDING_UPLOAD'
        """,
    )
    suspend fun markSynced(id: String, updatedAt: Long): Int

    /**
     * Records where this phone keeps the item's photo, after a download. Only that column
     * changes: the path is local to this phone, so it isn't a change that needs syncing.
     */
    @Query("UPDATE items SET localPhotoPath = :path WHERE id = :id")
    suspend fun updateLocalPhotoPath(id: String, path: String?): Int

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun deleteById(id: String)

    /** How many items have changes that haven't reached Firestore. Drives the logout warning. */
    @Query("SELECT COUNT(*) FROM items WHERE syncState != 'SYNCED'")
    fun observeUnsyncedCount(): Flow<Int>
}
