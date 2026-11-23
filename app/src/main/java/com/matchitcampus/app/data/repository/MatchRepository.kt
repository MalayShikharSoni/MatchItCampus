package com.matchitcampus.app.data.repository

import android.util.Log
import com.matchitcampus.app.data.local.AppDatabase
import com.matchitcampus.app.data.local.entity.ItemEntity
import com.matchitcampus.app.data.local.mapper.toDomain
import com.matchitcampus.app.data.local.mapper.toEntity
import com.matchitcampus.app.domain.matching.Embedder
import com.matchitcampus.app.domain.matching.ItemForMatching
import com.matchitcampus.app.domain.matching.MatchScorer
import com.matchitcampus.app.domain.matching.SearchHit
import com.matchitcampus.app.domain.matching.SearchScorer
import com.matchitcampus.app.domain.matching.TextNormalizer
import com.matchitcampus.app.domain.model.CampusZone
import com.matchitcampus.app.domain.model.ItemStatus
import com.matchitcampus.app.domain.model.ItemType
import com.matchitcampus.app.domain.model.MatchResult
import com.matchitcampus.app.domain.model.SyncState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale

private const val TAG = "MatchIt/MatchRepository"

/**
 * Computes, caches and serves match results, and fills in missing embeddings.
 *
 * **Embedding lifecycle.** Embeddings are local only (never in Firestore, so every phone uses
 * vectors from the same bundled model). An item needs one when it's created or edited here
 * (`ItemRepository.saveItem` clears it), or when it arrives from sync without one. [start]
 * watches Room for such rows and runs [ensureEmbeddings] whenever the set changes, so neither
 * `ItemRepository` nor `SyncManager` has to call this class (no dependency cycle).
 *
 * **Matching.** After its embedding is filled, an item is scored against every OPEN item of
 * the opposite type ([recomputeMatchesFor]). The scoring runs on [computeDispatcher]
 * (`Dispatchers.Default`, for CPU work). **Every** pair that passes the hard filters is stored
 * in the `matches` table, **in both directions**, so the found item's owner sees the new lost
 * item too, without recomputing their own item.
 *
 * Why every pair and not just the top 5: there's no score threshold (see `MatchWeights`), and
 * each item shows its own best 5. A new lost item may be in a found item's top 5 even when that
 * found item isn't in the lost item's top 5. Storing all pairs keeps both lists right. The
 * hard filters (opposite type, open, other owner, 30 days) keep this to a few thousand small
 * rows at campus scale.
 *
 * **Reading.** [observeMatches] is a Room Flow of the best few, so the detail screen updates
 * by itself whenever matches are recomputed or an item stops being OPEN.
 *
 * Known limitation: a remote change that keeps the text (for example a moved pin) keeps the
 * embedding, so matches aren't recomputed automatically. The detail screen can call
 * [recomputeMatchesFor] when it opens (cheap: one item against the open pool).
 *
 * @param scope where [start]'s watcher runs (the app-wide scope).
 * @param clock current time in epoch millis, for `computedAt`. Passed in so tests can fix it.
 * @param computeDispatcher where scoring runs. Tests can pass their own dispatcher.
 */
class MatchRepository(
    database: AppDatabase,
    private val embedder: Embedder,
    private val scope: CoroutineScope,
    private val scorer: MatchScorer = MatchScorer(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val itemDao = database.itemDao()
    private val matchDao = database.matchDao()
    private val zoneDao = database.campusZoneDao()

    /** Only one [ensureEmbeddings] pass at a time, so an item is never embedded twice in parallel. */
    private val embedMutex = Mutex()

    private var started = false

    /**
     * Starts watching for items that need an embedding. Called once, from `MatchItApp.onCreate`.
     *
     * The Flow emits the full list of such rows whenever the `items` table changes. Mapping it
     * to (id, updatedAt) pairs and `distinctUntilChanged` means a pass only runs when that set
     * really changes: a new or edited item, not every unrelated status update.
     */
    fun start() {
        check(!started) { "MatchRepository already started" }
        started = true
        scope.launch {
            itemDao.observeNeedingEmbedding(embedder.version)
                .map { rows -> rows.map { it.id to it.updatedAt } }
                .distinctUntilChanged()
                .filter { it.isNotEmpty() }
                .collect { ensureEmbeddings() }
        }
    }

    /** The best live matches for one item (at most `MatchWeights.maxResults`), best first. */
    fun observeMatches(itemId: String): Flow<List<MatchResult>> =
        matchDao.observeLiveMatches(itemId, limit = scorer.maxResults).map { rows -> rows.map { it.toDomain() } }

    /**
     * Embeds every item that has no (current) embedding, then recomputes those items' matches.
     *
     * Embedding all of them first and matching afterwards means that after a big sync each
     * pair is scored with both vectors present, instead of half the pairs falling back to
     * keywords. Items whose embedding fails are still matched, using the keyword fallback.
     *
     * @return how many embeddings were stored.
     */
    suspend fun ensureEmbeddings(): Int = embedMutex.withLock {
        val pending = itemDao.getNeedingEmbedding(embedder.version)
        if (pending.isEmpty()) return@withLock 0

        var stored = 0
        for (row in pending) {
            val vector = embedder.embed(TextNormalizer.forEmbedding(row.toDomain()))
            if (vector == null) {
                Log.w(TAG, "No embedding for item ${row.id}; its matches will use keywords")
            } else if (itemDao.updateEmbedding(row.id, vector, embedder.version, row.updatedAt) > 0) {
                stored++
            }
        }
        Log.d(TAG, "Embedded $stored of ${pending.size} items")

        for (row in pending) recomputeMatchesFor(row.id)
        stored
    }

    /**
     * Scores one item against the open items of the opposite type and replaces every cached
     * match that involves it (in both directions). An item that is no longer OPEN, or is gone,
     * just loses its matches.
     */
    suspend fun recomputeMatchesFor(itemId: String) {
        val row = itemDao.getById(itemId)
        if (row == null || row.syncState == SyncState.PENDING_DELETE || row.status != ItemStatus.OPEN) {
            matchDao.deleteInvolving(itemId)
            return
        }
        val zones = zoneDao.getAll().associate { it.id to it.toDomain() }
        val source = row.forMatching(zones)
        val opposite = if (row.type == ItemType.LOST) ItemType.FOUND else ItemType.LOST
        val candidates = itemDao.getOpenByType(opposite).map { it.forMatching(zones) }

        val results = withContext(computeDispatcher) { scorer.scoreAll(source, candidates) }

        val now = clock()
        val rows = results.flatMap { listOf(it.toEntity(now), it.reversed().toEntity(now)) }
        matchDao.replaceMatchesInvolving(itemId, rows)

        Log.d(TAG, "Item $itemId: ${results.size} scored pairs from ${candidates.size} candidates; best:")
        results.take(scorer.maxResults).forEach {
            Log.d(
                TAG,
                "  ${it.candidateItemId} score=%.2f text=%.2f%s category=%.2f location=%.2f date=%.2f".format(
                    Locale.ROOT,
                    it.score, it.textScore, if (it.usedFallback) " (keywords)" else "",
                    it.categoryScore, it.locationScore, it.dateScore,
                ),
            )
        }
    }

    /**
     * Semantic search over one feed tab: every item, most relevant to [query] first.
     * The query is embedded with the same model as the items, so "earphones" finds "earbuds".
     * The feed's ViewModel (Phase 5) calls this from its debounced query Flow.
     */
    suspend fun search(query: String, type: ItemType): List<SearchHit> {
        val normalizedQuery = TextNormalizer.normalize(query)
        if (normalizedQuery.isEmpty()) return emptyList()
        val queryEmbedding = embedder.embed(normalizedQuery)
        val items = itemDao.getByType(type).map { it.forMatching(zones = emptyMap()) }
        return withContext(computeDispatcher) { SearchScorer.rank(normalizedQuery, queryEmbedding, items) }
    }

    /**
     * Pairs a row with its embedding for the scorer. An embedding from another model version
     * is treated as missing, because vectors from different models can't be compared.
     */
    private fun ItemEntity.forMatching(zones: Map<String, CampusZone>): ItemForMatching =
        ItemForMatching(
            item = toDomain(zone = zoneId?.let { zones[it] }),
            embedding = embedding.takeIf { embeddingVersion == embedder.version },
        )

    /** The same result seen from the candidate's side. Every component is symmetric, so only the ids swap. */
    private fun MatchResult.reversed(): MatchResult =
        copy(sourceItemId = candidateItemId, candidateItemId = sourceItemId)
}
