package com.matchitcampus.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.matchitcampus.app.data.local.entity.MatchEntity
import kotlinx.coroutines.flow.Flow

/**
 * Database access for cached match results (written by Phase 4's MatchRepository).
 *
 * This is an `abstract class` rather than an interface so that [replaceMatchesFor] can have
 * a body, which Room wraps in a single database transaction.
 */
@Dao
abstract class MatchDao {

    @Upsert
    abstract suspend fun upsertAll(matches: List<MatchEntity>)

    @Query("DELETE FROM matches WHERE sourceItemId = :sourceItemId")
    abstract suspend fun deleteForSource(sourceItemId: String)

    /** Removes every match an item takes part in, as source or candidate. Used when it's deleted. */
    @Query("DELETE FROM matches WHERE sourceItemId = :itemId OR candidateItemId = :itemId")
    abstract suspend fun deleteInvolving(itemId: String)

    /**
     * Replaces an item's matches with a freshly computed list.
     *
     * It runs as one transaction, so observers never see the in-between state where the old
     * matches are deleted but the new ones aren't written yet (an empty "Possible matches"
     * list flashing on screen).
     */
    @Transaction
    open suspend fun replaceMatchesFor(sourceItemId: String, matches: List<MatchEntity>) {
        deleteForSource(sourceItemId)
        upsertAll(matches)
    }

    /** An item's matches, best first. */
    @Query("SELECT * FROM matches WHERE sourceItemId = :sourceItemId ORDER BY score DESC")
    abstract fun observeForSource(sourceItemId: String): Flow<List<MatchEntity>>
}
