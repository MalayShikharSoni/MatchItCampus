package com.matchitcampus.app.ui.home

import androidx.compose.runtime.Immutable
import com.matchitcampus.app.domain.model.Category
import com.matchitcampus.app.domain.model.Item
import com.matchitcampus.app.domain.model.ItemStatus
import com.matchitcampus.app.domain.model.ItemType
import com.matchitcampus.app.ui.util.Formatters
import java.time.LocalDate

/** How far back the date filter looks, counting today as day 1. */
enum class DateFilter(val days: Int?) {
    ANY(null),
    LAST_7_DAYS(7),
    LAST_30_DAYS(30),
}

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
) {
    val isActive: Boolean get() = this != FeedFilters()

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
     * - with a search [ranking] for this tab: most relevant first;
     * - without one: as Room delivered them (newest post first).
     *
     * `sortedByDescending` is stable, so items with equal relevance keep their newest-first order.
     * Items missing from the ranking (posted after the search ran) go to the end.
     */
    fun visibleItems(
        items: List<Item>,
        filters: FeedFilters,
        ranking: SearchRanking?,
        today: LocalDate,
    ): List<Item> {
        val filtered = items.filter { filters.matches(it, today) }
        if (ranking == null) return filtered
        return filtered.sortedByDescending { ranking.relevance[it.id] ?: -1.0 }
    }
}
