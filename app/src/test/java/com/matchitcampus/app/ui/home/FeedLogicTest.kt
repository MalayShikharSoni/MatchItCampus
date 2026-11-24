package com.matchitcampus.app.ui.home

import com.matchitcampus.app.domain.model.Category
import com.matchitcampus.app.domain.model.ItemStatus
import com.matchitcampus.app.domain.model.ItemType
import com.matchitcampus.app.ui.TestItems.item
import com.matchitcampus.app.ui.TestItems.zone
import com.matchitcampus.app.ui.util.Formatters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FeedLogicTest {

    private val today = LocalDate.of(2026, 11, 24)
    private fun daysAgo(days: Long) = Formatters.eventDateMillis(today.minusDays(days))

    private val block34 = zone("block_34")
    private val earbuds = item("earbuds", category = Category.ELECTRONICS, zone = block34, eventDate = daysAgo(0))
    private val bottle = item("bottle", category = Category.WATER_BOTTLES, eventDate = daysAgo(10), status = ItemStatus.RETURNED)
    private val wallet = item("wallet", category = Category.WALLETS_CARDS, zone = block34, eventDate = daysAgo(40))
    private val feed = listOf(earbuds, bottle, wallet) // Room's order: newest post first

    @Test
    fun noFiltersNoSearch_keepsRoomsOrder() {
        assertEquals(feed, FeedLogic.visibleItems(feed, FeedFilters(), ranking = null, today = today))
    }

    @Test
    fun eachFilter_narrowsTheList() {
        assertEquals(listOf(bottle), FeedLogic.visibleItems(feed, FeedFilters(category = Category.WATER_BOTTLES), null, today))
        assertEquals(listOf(earbuds, wallet), FeedLogic.visibleItems(feed, FeedFilters(zoneId = "block_34"), null, today))
        assertEquals(listOf(bottle), FeedLogic.visibleItems(feed, FeedFilters(status = ItemStatus.RETURNED), null, today))
        assertEquals(listOf(earbuds), FeedLogic.visibleItems(feed, FeedFilters(date = DateFilter.LAST_7_DAYS), null, today))
        assertEquals(listOf(earbuds, bottle), FeedLogic.visibleItems(feed, FeedFilters(date = DateFilter.LAST_30_DAYS), null, today))
    }

    @Test
    fun filtersCombine() {
        val filters = FeedFilters(zoneId = "block_34", date = DateFilter.LAST_30_DAYS)
        assertEquals(listOf(earbuds), FeedLogic.visibleItems(feed, filters, null, today))
    }

    @Test
    fun dateFilter_countsTodayAsDayOne() {
        val sixDaysAgo = item("a", eventDate = daysAgo(6))
        val sevenDaysAgo = item("b", eventDate = daysAgo(7))
        assertTrue(FeedFilters(date = DateFilter.LAST_7_DAYS).matches(sixDaysAgo, today))
        assertFalse(FeedFilters(date = DateFilter.LAST_7_DAYS).matches(sevenDaysAgo, today))
    }

    @Test
    fun search_ordersByRelevance_unrankedItemsLast() {
        val ranking = SearchRanking("wallet", ItemType.FOUND, mapOf("wallet" to 0.9, "earbuds" to 0.4))
        assertEquals(listOf(wallet, earbuds, bottle), FeedLogic.visibleItems(feed, FeedFilters(), ranking, today))
    }

    @Test
    fun search_tiesKeepNewestFirst() {
        val ranking = SearchRanking("x", ItemType.FOUND, mapOf("earbuds" to 0.5, "bottle" to 0.5, "wallet" to 0.5))
        assertEquals(feed, FeedLogic.visibleItems(feed, FeedFilters(), ranking, today))
    }

    @Test
    fun isActive_onlyWhenSomethingIsSet() {
        assertFalse(FeedFilters().isActive)
        assertTrue(FeedFilters(date = DateFilter.LAST_7_DAYS).isActive)
    }
}
