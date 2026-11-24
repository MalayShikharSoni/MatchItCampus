package com.matchitcampus.app.ui.home

import com.matchitcampus.app.data.repository.ItemRepository
import com.matchitcampus.app.data.repository.MatchRepository
import com.matchitcampus.app.data.repository.ZoneRepository
import com.matchitcampus.app.data.sync.SyncManager
import com.matchitcampus.app.domain.matching.SearchHit
import com.matchitcampus.app.domain.model.CampusZone
import com.matchitcampus.app.domain.model.Item
import com.matchitcampus.app.domain.model.ItemType
import kotlinx.coroutines.flow.Flow

/**
 * Everything the feed needs from the data layer, and nothing more.
 *
 * [HomeViewModel] depends on this small interface instead of four concrete repositories, so a
 * unit test can hand it a fake that returns fixed lists (no Room, no Firebase, no model).
 * The real app uses [RepositoryFeedDataSource].
 */
interface FeedDataSource {
    fun observeFeed(type: ItemType): Flow<List<Item>>
    fun observeZones(): Flow<List<CampusZone>>

    /** Every item of [type], most relevant to [query] first (semantic + keyword). */
    suspend fun search(query: String, type: ItemType): List<SearchHit>

    /** Pull-to-refresh: push pending changes and fetch remote ones. */
    suspend fun refresh()
}

/** The real [FeedDataSource]: forwards each call to the repository that owns that data. */
class RepositoryFeedDataSource(
    private val itemRepository: ItemRepository,
    private val zoneRepository: ZoneRepository,
    private val matchRepository: MatchRepository,
    private val syncManager: SyncManager,
) : FeedDataSource {
    override fun observeFeed(type: ItemType) = itemRepository.observeFeed(type)
    override fun observeZones() = zoneRepository.observeZones()
    override suspend fun search(query: String, type: ItemType) = matchRepository.search(query, type)
    override suspend fun refresh() = syncManager.syncNow()
}
