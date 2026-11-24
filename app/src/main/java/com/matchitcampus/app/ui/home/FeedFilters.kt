package com.matchitcampus.app.ui.home

import androidx.compose.runtime.Immutable
import com.matchitcampus.app.domain.model.Category
import com.matchitcampus.app.domain.model.Item
import com.matchitcampus.app.domain.model.ItemStatus
import com.matchitcampus.app.domain.model.ItemType
import com.matchitcampus.app.location.LatLng
import com.matchitcampus.app.location.distanceFrom
import com.matchitcampus.app.ui.util.Formatters
import java.time.LocalDate

/** How far back the date filter looks, counting today as day 1. */
enum class DateFilter(val days: Int?) {
    ANY(null),
    LAST_7_DAYS(7),
    LAST_30_DAYS(30),
}

/** The feed's order when there's no search: newest post first, or nearest first ("Near me"). */
enum class FeedSort { NEWEST, NEAR_ME }

/**
 * The filter chips on the feed. Every field is optional; null (or [DateFilter.ANY]) means
 * "don't filter on this".
 */
@Immutable
data class FeedFilters(
    val category: Category? = null,
    val zoneId: String? = null,
    val status: ItemStatus? = null,
    val date: DateFilter = DateFilter.ANY,
    val sort: FeedSort = FeedSort.NEWEST,
) {
    /** True if something is filtered out. The sort order alone hides nothing, so it doesn't count. */
    val isActive: Boolean get() = copy(sort = FeedSort.NEWEST) != FeedFilters()

    /** True if [item] passes every active filter. [today] is passed in so tests can fix it. */
    fun matches(item: Item, today: LocalDate): Boolean {
        if (category != null && item.category != category) return false
        if (zoneId != null && item.zoneId != zoneId) return false
        if (status != null && item.status != status) return false
        val days = date.days
        if (days != null && Formatters.eventLocalDate(item.eventDate).isBefore(today.minusDays(days - 1L))) return false
        return true
    }
}

/**
 * The result of one search: relevance per item id, for the query and tab it was run for.
 * Keeping [query] and [type] lets the screen tell whether the ranking is still up to date.
 */
data class SearchRanking(val query: String, val type: ItemType, val relevance: Map<String, Double>)

/** Pure feed logic, kept out of the ViewModel so it can be unit-tested directly (`FeedLogicTest`). */
object FeedLogic {

    /**
     * Filters the tab's items, then orders them:
     * - with a search [ranking] for this tab: most relevant first (a search beats "Near me",
     *   because typed words say more about what the user wants than distance does);
     * - "Near me" with a known [userLocation]: nearest first, by haversine distance to the item's
     *   pin or real zone; items with no position go last;
     * - otherwise: as Room delivered them (newest post first).
     *
     * `sortedByDescending` is stable, so items with equal relevance keep their newest-first order.
     * Items missing from the ranking (posted after the search ran) go to the end.
     */
    fun visibleItems(
        items: List<Item>,
        filters: FeedFilters,
        ranking: SearchRanking?,
        today: LocalDate,
        userLocation: LatLng? = null,
    ): List<Item> {
        val filtered = items.filter { filters.matches(it, today) }
        return when {
            ranking != null -> filtered.sortedByDescending { ranking.relevance[it.id] ?: -1.0 }
            filters.sort == FeedSort.NEAR_ME && userLocation != null ->
                filtered.sortedBy { it.distanceFrom(userLocation) ?: Double.MAX_VALUE }
            else -> filtered
        }
    }

    /** Distance in metres to each item that has a position, for the cards in "Near me" mode. */
    fun distances(items: List<Item>, userLocation: LatLng): Map<String, Double> =
        items.mapNotNull { item -> item.distanceFrom(userLocation)?.let { item.id to it } }.toMap()
}
