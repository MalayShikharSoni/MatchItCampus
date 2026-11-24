package com.matchitcampus.app.ui.home

import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.matchitcampus.app.R
import com.matchitcampus.app.domain.model.CampusZone
import com.matchitcampus.app.domain.model.Item
import com.matchitcampus.app.domain.model.ItemType
import com.matchitcampus.app.location.LatLng
import com.matchitcampus.app.ui.util.appContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

private const val TAG = "MatchIt/HomeViewModel"

/** Everything the feed screen draws. Built fresh by [HomeViewModel] whenever an input changes. */
@Immutable
data class HomeUiState(
    val tab: ItemType = ItemType.LOST,
    val filters: FeedFilters = FeedFilters(),
    val zones: List<CampusZone> = emptyList(),
    /** The tab's items after filtering, in display order. */
    val items: List<Item> = emptyList(),
    /** True until Room's first answer for this tab arrives. */
    val isLoading: Boolean = true,
    /** True while the search for the current text hasn't finished. */
    val isSearching: Boolean = false,
    val isRefreshing: Boolean = false,
    val hasError: Boolean = false,
    /** True when the list is a search result, so the empty state can say "no results". */
    val isFiltered: Boolean = false,
    /** "Near me": metres to each item with a known position, shown on its card. */
    val distances: Map<String, Double> = emptyMap(),
    /** "Near me" is waiting for a location fix. */
    val isLocating: Boolean = false,
)

/** The three things the feed Flow can be: still loading, loaded, or failed. */
private sealed interface FeedLoad {
    data object Loading : FeedLoad
    data class Loaded(val items: List<Item>) : FeedLoad
    data object Failed : FeedLoad
}

/**
 * The feed: Lost/Found tabs, search, filters and pull-to-refresh.
 *
 * **Unidirectional data flow.** The screen sends events (`onQueryChange`, `onTabSelected`, …);
 * this class updates its inputs; [uiState] is recomputed from all inputs with `combine` and
 * flows back down. The screen never changes state itself.
 *
 * **What is saved where.**
 * - The selected tab and the search text are in [SavedStateHandle], so they survive rotation
 *   (the ViewModel itself survives that) and process death (the ViewModel doesn't).
 * - Filters are plain `MutableStateFlow`s: losing them after process death is acceptable.
 *
 * **The search chain** (the Flow operators from Unit II):
 * 1. `debounce(300)`: wait until typing pauses for 300 ms, so "earbuds" runs one search, not seven.
 * 2. `distinctUntilChanged()`: skip a search for the same text as the last one.
 * 3. `mapLatest { … }`: run the search; if new text arrives while the model is still embedding
 *    the old one, the old search is cancelled, so a slow search never overwrites a newer one.
 *
 * @param today today's date for the date filter; a parameter so tests can fix it.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val data: FeedDataSource,
    private val savedStateHandle: SavedStateHandle,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private val tab: Flow<ItemType> =
        savedStateHandle.getStateFlow(KEY_TAB, ItemType.LOST.name).map { ItemType.valueOf(it) }

    /**
     * The search box text, straight from [SavedStateHandle]. The screen reads this directly
     * (not through [uiState]) so typing is never delayed by the combine below, which would make
     * the cursor jump.
     */
    val query: StateFlow<String> = savedStateHandle.getStateFlow(KEY_QUERY, "")

    private val filters = MutableStateFlow(FeedFilters())
    private val isRefreshing = MutableStateFlow(false)

    /**
     * Where the user was when they turned on "Near me". One fix, not live updates: the list
     * shouldn't reshuffle under the user's finger as they walk. Tapping the chip again refreshes it.
     */
    private val userLocation = MutableStateFlow<LatLng?>(null)
    private val isLocating = MutableStateFlow(false)

    private val _messages = MutableSharedFlow<Int>()

    /** One-shot snackbar messages (string resources), such as "couldn't get your location". */
    val messages: SharedFlow<Int> = _messages.asSharedFlow()

    /** Bumped by "Try again" to re-subscribe to Room after an error. */
    private val retryCount = MutableStateFlow(0)

    /**
     * The current tab's items from Room. `flatMapLatest` switches to the other tab's query
     * when the tab changes (and stops observing the old one).
     */
    private val feed: Flow<FeedLoad> = combine(tab, retryCount) { type, _ -> type }
        .flatMapLatest { type ->
            data.observeFeed(type)
                .map<List<Item>, FeedLoad> { FeedLoad.Loaded(it) }
                .onStart { emit(FeedLoad.Loading) }
                .catch { e ->
                    Log.e(TAG, "Couldn't load the $type feed", e)
                    emit(FeedLoad.Failed)
                }
        }

    /** The latest search result, or null when the search box is empty. See the class KDoc. */
    private val ranking: Flow<SearchRanking?> = combine(
        query.debounce(SEARCH_DEBOUNCE_MS).map { it.trim() }.distinctUntilChanged(),
        tab,
    ) { text, type -> text to type }
        .mapLatest { (text, type) -> if (text.isEmpty()) null else search(text, type) }
        .onStart { emit(null) }

    private val zones: Flow<List<CampusZone>> = data.observeZones()
        .catch { e ->
            Log.e(TAG, "Couldn't load zones", e)
            emit(emptyList())
        }

    val uiState: StateFlow<HomeUiState> = combine(
        combine(tab, query, filters, userLocation, isLocating) { type, text, activeFilters, location, locating ->
            FeedInputs(type, text.trim(), activeFilters, location, locating)
        },
        feed,
        ranking,
        zones,
        isRefreshing,
    ) { inputs, load, currentRanking, zoneList, refreshing ->
        val (type, text, activeFilters, location, locating) = inputs
        // A ranking made for another tab or older text is ignored until the new one arrives.
        val upToDate = currentRanking?.takeIf { it.type == type && it.query == text }
        val items = if (load is FeedLoad.Loaded) FeedLogic.visibleItems(load.items, activeFilters, upToDate, today(), location) else emptyList()
        HomeUiState(
            tab = type,
            filters = activeFilters,
            zones = zoneList,
            items = items,
            isLoading = load is FeedLoad.Loading,
            isSearching = text.isNotEmpty() && upToDate == null,
            isRefreshing = refreshing,
            hasError = load is FeedLoad.Failed,
            isFiltered = text.isNotEmpty() || activeFilters.isActive,
            distances = if (activeFilters.sort == FeedSort.NEAR_ME && location != null) FeedLogic.distances(items, location) else emptyMap(),
            isLocating = locating,
        )
    }.stateIn(
        scope = viewModelScope,
        // Keep the flows alive for 5 s after the screen stops collecting, so a rotation doesn't
        // restart them, but stop them when the user really leaves.
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(tab = ItemType.valueOf(savedStateHandle[KEY_TAB] ?: ItemType.LOST.name)),
    )

    fun onTabSelected(type: ItemType) {
        savedStateHandle[KEY_TAB] = type.name
    }

    fun onQueryChange(text: String) {
        savedStateHandle[KEY_QUERY] = text
    }

    fun onFiltersChange(newFilters: FeedFilters) {
        filters.value = newFilters
    }

    fun onClearSearchAndFilters() {
        savedStateHandle[KEY_QUERY] = ""
        filters.value = FeedFilters()
    }

    /** Pull-to-refresh. `finally` hides the spinner even if the sync fails or is cancelled. */
    fun onRefresh() {
        if (isRefreshing.value) return
        viewModelScope.launch {
            isRefreshing.value = true
            try {
                data.refresh()
            } finally {
                isRefreshing.value = false
            }
        }
    }

    fun onRetry() = retryCount.update { it + 1 }

    /**
     * "Near me" tapped and permission granted: get one fix, then switch the sort. Without a fix
     * the sort stays as it was and a snackbar explains why.
     */
    fun onNearMeGranted(precise: Boolean) {
        if (isLocating.value) return
        viewModelScope.launch {
            isLocating.value = true
            val location = try {
                data.currentLocation()
            } finally {
                isLocating.value = false
            }
            if (location == null) {
                say(R.string.location_unavailable)
                return@launch
            }
            userLocation.value = location
            filters.update { it.copy(sort = FeedSort.NEAR_ME) }
            if (!precise) say(R.string.location_approximate)
        }
    }

    fun onNearMeOff() {
        filters.update { it.copy(sort = FeedSort.NEWEST) }
    }

    private suspend fun say(@StringRes message: Int) = _messages.emit(message)

    /** One search. A failure is logged and gives an empty ranking, so the feed keeps its normal order. */
    private suspend fun search(text: String, type: ItemType): SearchRanking {
        val hits = try {
            data.search(text, type)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Search failed for \"$text\"", e)
            emptyList()
        }
        return SearchRanking(text, type, hits.associate { it.itemId to it.relevance })
    }

    /** The first `combine`'s inputs, named, so the outer `combine` stays within five flows. */
    private data class FeedInputs(
        val type: ItemType,
        val text: String,
        val filters: FeedFilters,
        val location: LatLng?,
        val isLocating: Boolean,
    )

    companion object {
        const val KEY_TAB = "home_tab"
        const val KEY_QUERY = "home_query"
        const val SEARCH_DEBOUNCE_MS = 300L

        val Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                HomeViewModel(
                    data = RepositoryFeedDataSource(
                        itemRepository = container.itemRepository,
                        zoneRepository = container.zoneRepository,
                        matchRepository = container.matchRepository,
                        syncManager = container.syncManager,
                        locationClient = container.locationClient,
                    ),
                    savedStateHandle = createSavedStateHandle(),
                )
            }
        }
    }
}
