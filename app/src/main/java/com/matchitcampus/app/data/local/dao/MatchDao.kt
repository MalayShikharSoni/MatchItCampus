package com.matchitcampus.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.matchitcampus.app.data.local.entity.MatchEntity
import com.matchitcampus.app.data.local.relation.MatchWithFallback
import kotlinx.coroutines.flow.Flow

/**
 * Database access for cached match results (written by `MatchRepository`).
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

    /**
     * Replaces every match an item takes part in, as source **or** candidate, in one transaction.
     *
     * `MatchRepository` stores each result in both directions (lost → found and found → lost),
     * because the score is symmetric and both owners should see the match. So when an item's
     * matches are recomputed, the rows that point *at* it from other items must be replaced too.
     */
    @Transaction
    open suspend fun replaceMatchesInvolving(itemId: String, matches: List<MatchEntity>) {
        deleteInvolving(itemId)
        upsertAll(matches)
    }

    /**
     * An item's best [limit] matches for the UI, best first, with two extras the plain table
     * can't give:
     *
     * - **Only live pairs.** The join drops rows whose source or candidate is no longer OPEN or
     *   is being deleted. So when an item is claimed or returned it disappears from everyone's
     *   matches at once, without recomputing anything.
     * - **`usedFallback`**, true while either item has no embedding (its text score came from
     *   keyword overlap). Worked out here rather than stored, so the v1 table didn't need a new
     *   column. Matches are recomputed as soon as an embedding is filled in, so it stays accurate.
     *
     * Room re-runs the query when either `matches` or `items` changes.
     */
    @Query(
        """
        SELECT m.*, (s.embedding IS NULL OR c.embedding IS NULL) AS usedFallback
        FROM matches m
        JOIN items s ON s.id = m.sourceItemId
        JOIN items c ON c.id = m.candidateItemId
        WHERE m.sourceItemId = :sourceItemId
          AND s.status = 'OPEN' AND c.status = 'OPEN'
          AND s.syncState != 'PENDING_DELETE' AND c.syncState != 'PENDING_DELETE'
        ORDER BY m.score DESC
        LIMIT :limit
        """,
    )
    abstract fun observeLiveMatches(sourceItemId: String, limit: Int): Flow<List<MatchWithFallback>>

    /** An item's matches, best first. */
    @Query("SELECT * FROM matches WHERE sourceItemId = :sourceItemId ORDER BY score DESC")
    abstract fun observeForSource(sourceItemId: String): Flow<List<MatchEntity>>
}
