package com.matchitcampus.app.ui.debug

import com.matchitcampus.app.domain.matching.ItemForMatching
import com.matchitcampus.app.domain.matching.MatchScorer
import com.matchitcampus.app.domain.model.ItemType
import com.matchitcampus.app.location.CampusMap
import com.matchitcampus.app.location.GeoUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checks the demo set is usable for the demo: right halves, matchable pairs, sensible pins. */
class DemoDataTest {

    private val today = 1_795_392_000_000L
    private val riya = "riya-uid-123456"
    private val aman = "aman-uid-654321"

    private fun found() = DemoData.found.map { DemoData.toItem(it, riya, "Riya", today, today) }
    private fun lost() = DemoData.lost.map { DemoData.toItem(it, aman, "Aman", today, today) }

    @Test
    fun tenOfEach_withTheRightTypes_andFiveDeliberatePairs() {
        assertEquals(10, DemoData.found.size)
        assertEquals(10, DemoData.lost.size)
        assertTrue(DemoData.found.all { it.type == ItemType.FOUND })
        assertTrue(DemoData.lost.all { it.type == ItemType.LOST })
        val foundPairs = DemoData.found.mapNotNull { it.pairKey }.toSet()
        val lostPairs = DemoData.lost.mapNotNull { it.pairKey }.toSet()
        assertEquals(setOf("earbuds", "glasses", "id-card", "purse", "bottle"), foundPairs)
        assertEquals(foundPairs, lostPairs)
    }

    @Test
    fun ids_areUniqueAndStablePerOwner() {
        val ids = (found() + lost()).map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it.startsWith(DemoData.ID_PREFIX) })
        // Seeding twice as the same account produces the same ids (an update, not duplicates).
        assertEquals(found().map { it.id }, found().map { it.id })
    }

    @Test
    fun onlyFoundItemsHaveAVerificationQuestion() {
        assertTrue(found().all { !it.verificationQuestion.isNullOrBlank() })
        assertTrue(lost().all { it.verificationQuestion == null })
    }

    @Test
    fun everyPair_passesTheMatchingFilters_andIsCloseTogether() {
        val scorer = MatchScorer()
        val lostByPair = DemoData.lost.filter { it.pairKey != null }.associateBy { it.pairKey }
        for (foundTemplate in DemoData.found.filter { it.pairKey != null }) {
            val lostTemplate = lostByPair.getValue(foundTemplate.pairKey)
            val f = DemoData.toItem(foundTemplate, riya, "Riya", today, today)
            val l = DemoData.toItem(lostTemplate, aman, "Aman", today, today)
            // No embeddings here, so text uses the keyword fallback; we only need the hard filters to pass.
            assertNotNull("${foundTemplate.key} should be matchable", scorer.score(ItemForMatching(l, null), ItemForMatching(f, null)))
            val metres = GeoUtils.haversineMeters(f.latitude!!, f.longitude!!, l.latitude!!, l.longitude!!)
            assertTrue("${foundTemplate.key} pins are $metres m apart", metres < 60)
        }
    }

    @Test
    fun sameAccount_neverMatchesItself() {
        // Why the set is seeded in two halves: matching skips one person's own posts.
        val f = DemoData.toItem(DemoData.found.first(), riya, "Riya", today, today)
        val l = DemoData.toItem(DemoData.lost.first(), riya, "Riya", today, today)
        assertNull(MatchScorer().score(ItemForMatching(l, null), ItemForMatching(f, null)))
    }

    @Test
    fun pins_areOnCampus_within1KmOfTheCentre() {
        for (item in found() + lost()) {
            val metres = GeoUtils.haversineMeters(CampusMap.CENTER.latitude, CampusMap.CENTER.longitude, item.latitude!!, item.longitude!!)
            assertTrue("${item.id} is $metres m from the centre", metres < 1_000)
        }
    }
}
