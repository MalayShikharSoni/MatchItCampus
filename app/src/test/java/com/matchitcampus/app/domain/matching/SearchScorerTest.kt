package com.matchitcampus.app.domain.matching

import com.matchitcampus.app.domain.model.Category
import com.matchitcampus.app.domain.model.Item
import com.matchitcampus.app.domain.model.ItemStatus
import com.matchitcampus.app.domain.model.ItemType
import com.matchitcampus.app.domain.model.SyncState
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchScorerTest {

    @Test
    fun relevance_isCosinePlusSubstringBoost() {
        val withText = SearchScorer.relevance("jbl", floatArrayOf(1f, 0f), "black jbl case", floatArrayOf(0.6f, 0.8f))
        val withoutText = SearchScorer.relevance("sony", floatArrayOf(1f, 0f), "black jbl case", floatArrayOf(0.6f, 0.8f))
        assertEquals(0.6 + SearchScorer.SUBSTRING_BOOST, withText, 1e-6)
        assertEquals(0.6, withoutText, 1e-6)
    }

    @Test
    fun relevance_withoutEmbeddings_usesQueryWordCoverage() {
        assertEquals(0.5, SearchScorer.relevance("jbl bottle", null, "black jbl case", null), 1e-9)
    }

    @Test
    fun rank_returnsEveryItem_mostRelevantFirst() {
        val items = listOf(
            ItemForMatching(item("bottle", "Steel water bottle"), floatArrayOf(0f, 1f)), // cosine 0
            ItemForMatching(item("case", "Black earphone case"), floatArrayOf(0.8f, 0.6f)), // cosine 0.8
            ItemForMatching(item("jbl", "JBL earbuds"), floatArrayOf(0.6f, 0.8f)), // cosine 0.6 + boost
        )

        val hits = SearchScorer.rank("jbl", floatArrayOf(1f, 0f), items)

        assertEquals(listOf("jbl", "case", "bottle"), hits.map { it.itemId })
    }

    private fun item(id: String, title: String) = Item(
        id = id, type = ItemType.FOUND, title = title, description = "", category = Category.OTHER,
        status = ItemStatus.OPEN, zoneId = null, zone = null, latitude = null, longitude = null,
        addressLine = null, eventDate = 0, createdAt = 0, updatedAt = 0, ownerId = "riya", ownerName = "Riya",
        verificationQuestion = null, hasPhoto = false, localPhotoPath = null, syncState = SyncState.SYNCED,
    )
}
