package com.matchitcampus.app.domain.matching

import com.matchitcampus.app.domain.model.CampusZone
import com.matchitcampus.app.domain.model.Category
import com.matchitcampus.app.domain.model.Item
import com.matchitcampus.app.domain.model.ItemStatus
import com.matchitcampus.app.domain.model.ItemType
import com.matchitcampus.app.domain.model.SyncState
import com.matchitcampus.app.domain.model.ZoneType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * Tests every rule of [MatchScorer]: the hard filters, each component score, the weights,
 * the top-N ranking and the keyword fallback.
 *
 * The default pair is "perfect": same embedding, category, zone and day, so each test changes
 * just the one thing it's about.
 */
class MatchScorerTest {

    private val scorer = MatchScorer()
    private val day = MatchScorer.DAY_MS
    private val t0 = 1_700_000_000_000L

    private val block34 = zone("block_34", ZoneType.ACADEMIC, isPlaceholder = false, lat = 31.2550, lng = 75.7050)
    private val library = zone("library", ZoneType.LIBRARY, isPlaceholder = false, lat = 31.2550 + metresToLat(400.0), lng = 75.7050)
    private val placeholderA = zone("ph_a", ZoneType.HOSTEL, isPlaceholder = true)
    private val placeholderB = zone("ph_b", ZoneType.FOOD, isPlaceholder = true)
    private val otherZone = zone("other", ZoneType.OTHER, isPlaceholder = true)

    private val vector = floatArrayOf(0.6f, 0.8f)

    // ---------- hard filters ----------

    @Test
    fun perfectPair_scoresOne() {
        val result = scorer.score(lost(), found())!!
        assertEquals(1.0, result.score, 1e-9)
        assertFalse(result.usedFallback)
        assertEquals(0, result.daysApart)
    }

    @Test
    fun sameType_isRejected() {
        assertNull(scorer.score(lost(), lost(id = "l2", owner = "riya")))
    }

    @Test
    fun itemThatIsNotOpen_isRejected() {
        assertNull(scorer.score(lost(), found(status = ItemStatus.CLAIM_PENDING)))
        assertNull(scorer.score(lost(status = ItemStatus.RETURNED), found()))
    }

    @Test
    fun sameOwner_isRejected() {
        assertNull(scorer.score(lost(owner = "riya"), found(owner = "riya")))
    }

    @Test
    fun foundMoreThanADayBeforeLost_isRejected() {
        assertNull(scorer.score(lost(eventDate = t0), found(eventDate = t0 - 2 * day)))
    }

    @Test
    fun foundUpToADayBeforeLost_isAllowed() {
        assertNotNull(scorer.score(lost(eventDate = t0), found(eventDate = t0 - day)))
    }

    @Test
    fun moreThan30DaysApart_isRejected_butExactly30IsAllowed() {
        assertNull(scorer.score(lost(eventDate = t0), found(eventDate = t0 + 30 * day + 1)))
        assertNotNull(scorer.score(lost(eventDate = t0), found(eventDate = t0 + 30 * day)))
    }

    // ---------- text ----------

    @Test
    fun textScore_isCosineOfTheEmbeddings() {
        // (1,0)·(0.6,0.8) = 0.6
        val result = scorer.score(lost(embedding = floatArrayOf(1f, 0f)), found(embedding = floatArrayOf(0.6f, 0.8f)))!!
        assertEquals(0.6, result.textScore, 1e-6)
    }

    @Test
    fun negativeCosine_isClampedToZero() {
        val result = scorer.score(lost(embedding = floatArrayOf(1f, 0f)), found(embedding = floatArrayOf(-1f, 0f)))!!
        assertEquals(0.0, result.textScore, 0.0)
    }

    @Test
    fun missingEmbedding_fallsBackToKeywords() {
        val result = scorer.score(
            lost(title = "Black wallet", description = "", embedding = null),
            found(title = "Brown wallet", description = ""),
        )!!
        assertTrue(result.usedFallback)
        // {black, wallet, electronics} vs {brown, wallet, electronics}: 2 shared of 4.
        assertEquals(0.5, result.textScore, 1e-9)
    }

    @Test
    fun embeddingsOfDifferentSizes_fallBackToKeywords() {
        val result = scorer.score(lost(embedding = floatArrayOf(1f)), found(embedding = floatArrayOf(1f, 0f)))!!
        assertTrue(result.usedFallback)
    }

    // ---------- category ----------

    @Test
    fun categoryScore_sameOtherOrDifferent() {
        assertEquals(1.0, scorer.score(lost(), found())!!.categoryScore, 0.0)
        assertEquals(0.3, scorer.score(lost(category = Category.OTHER), found())!!.categoryScore, 0.0)
        assertEquals(0.0, scorer.score(lost(category = Category.BAGS), found())!!.categoryScore, 0.0)
    }

    // ---------- location ----------

    @Test
    fun sameZone_scoresOne() {
        assertEquals(1.0, scorer.score(lost(zone = placeholderA), found(zone = placeholderA))!!.locationScore, 0.0)
    }

    @Test
    fun bothInOtherZone_countsAsUnknown() {
        val result = scorer.score(lost(zone = otherZone), found(zone = otherZone))!!
        assertEquals(0.5, result.locationScore, 0.0)
        assertNull(result.distanceMeters)
    }

    @Test
    fun pins500MetresApart_scoreAboutAHalf() {
        val result = scorer.score(
            lost(zone = null, lat = 31.2550, lng = 75.7050),
            found(zone = null, lat = 31.2550 + metresToLat(500.0), lng = 75.7050),
        )!!
        assertEquals(500.0, result.distanceMeters!!, 1.0)
        assertEquals(0.5, result.locationScore, 0.01)
    }

    @Test
    fun pinsMoreThanAKilometreApart_scoreZero() {
        val result = scorer.score(
            lost(zone = null, lat = 31.2550, lng = 75.7050),
            found(zone = null, lat = 31.2550 + metresToLat(1500.0), lng = 75.7050),
        )!!
        assertEquals(0.0, result.locationScore, 0.0)
    }

    @Test
    fun differentRealZones_useTheZoneCentres() {
        val result = scorer.score(lost(zone = block34), found(zone = library))!!
        assertEquals(400.0, result.distanceMeters!!, 1.0)
        assertEquals(0.6, result.locationScore, 0.01)
    }

    @Test
    fun differentPlaceholderZones_countAsUnknown() {
        // Both placeholders sit at the same made-up point; that must not look like "0 m apart".
        val result = scorer.score(lost(zone = placeholderA), found(zone = placeholderB))!!
        assertNull(result.distanceMeters)
        assertEquals(0.5, result.locationScore, 0.0)
    }

    @Test
    fun noLocationAtAll_isNeutral() {
        assertEquals(0.5, scorer.score(lost(zone = null), found(zone = null))!!.locationScore, 0.0)
    }

    // ---------- date ----------

    @Test
    fun dateScore_fallsToZeroOverTwoWeeks() {
        assertEquals(1.0, scorer.score(lost(eventDate = t0), found(eventDate = t0 + day / 2))!!.dateScore, 0.0)
        assertEquals(0.5, scorer.score(lost(eventDate = t0), found(eventDate = t0 + 7 * day))!!.dateScore, 1e-9)
        assertEquals(0.0, scorer.score(lost(eventDate = t0), found(eventDate = t0 + 20 * day))!!.dateScore, 0.0)
    }

    // ---------- weights and ranking ----------

    @Test
    fun score_isTheWeightedSumOfTheComponents() {
        // text 0.6, category 0 (different), location 0.5 (unknown), date 0.5 (7 days)
        val result = scorer.score(
            lost(embedding = floatArrayOf(1f, 0f), category = Category.BAGS, zone = null, eventDate = t0),
            found(embedding = floatArrayOf(0.6f, 0.8f), zone = null, eventDate = t0 + 7 * day),
        )!!
        assertEquals(0.60 * 0.6 + 0.15 * 0.0 + 0.15 * 0.5 + 0.10 * 0.5, result.score, 1e-6)
    }

    @Test
    fun customWeights_areUsed() {
        val textOnly = MatchScorer(MatchWeights(text = 1.0, category = 0.0, location = 0.0, date = 0.0))
        val result = textOnly.score(lost(embedding = floatArrayOf(1f, 0f), category = Category.BAGS), found(embedding = floatArrayOf(0.6f, 0.8f)))!!
        assertEquals(0.6, result.score, 1e-6)
    }

    @Test
    fun scoreAll_keepsEveryAllowedPair_evenLowOnes_bestFirst() {
        val good = found(id = "good")
        val weak = found(id = "weak", embedding = floatArrayOf(-0.8f, 0.6f)) // text 0 -> 0.40
        val rejected = found(id = "mine", owner = "aman")

        val all = scorer.scoreAll(lost(), listOf(weak, rejected, good))

        assertEquals(listOf("good", "weak"), all.map { it.candidateItemId })
        assertEquals(0.40, all.last().score, 1e-9)
    }

    @Test
    fun rank_keepsOnlyTheBestMaxResults_bestFirst() {
        // Six candidates with text scores 0.0, 0.2, ..., 1.0 (unit vectors at different angles).
        val candidates = (0..5).map { i ->
            val cos = i / 5.0
            found(id = "f$i", embedding = floatArrayOf(cos.toFloat(), sqrt(1 - cos * cos).toFloat()))
        }

        val ranked = scorer.rank(lost(embedding = floatArrayOf(1f, 0f)), candidates)

        assertEquals(MatchWeights.DEFAULT.maxResults, ranked.size)
        assertEquals(listOf("f5", "f4", "f3", "f2", "f1"), ranked.map { it.candidateItemId })
    }

    @Test
    fun rank_withCustomMaxResults() {
        val oneOnly = MatchScorer(MatchWeights(maxResults = 1))
        val ranked = oneOnly.rank(lost(), listOf(found(id = "a"), found(id = "b", category = Category.BAGS)))
        assertEquals(listOf("a"), ranked.map { it.candidateItemId })
    }

    @Test
    fun score_isSymmetric() {
        val a = lost(embedding = floatArrayOf(1f, 0f), zone = block34, eventDate = t0)
        val b = found(zone = library, eventDate = t0 + 3 * day)
        val ab = scorer.score(a, b)!!
        val ba = scorer.score(b, a)!!
        assertEquals(ab.score, ba.score, 1e-12)
        assertEquals(ab.copy(sourceItemId = "", candidateItemId = ""), ba.copy(sourceItemId = "", candidateItemId = ""))
    }

    // ---------- builders ----------

    private fun lost(
        id: String = "lost",
        owner: String = "aman",
        status: ItemStatus = ItemStatus.OPEN,
        title: String = "Black earbuds case",
        description: String = "JBL",
        category: Category = Category.ELECTRONICS,
        zone: CampusZone? = block34,
        lat: Double? = null,
        lng: Double? = null,
        eventDate: Long = t0,
        embedding: FloatArray? = vector,
    ) = ItemForMatching(item(id, ItemType.LOST, owner, status, title, description, category, zone, lat, lng, eventDate), embedding)

    private fun found(
        id: String = "found",
        owner: String = "riya",
        status: ItemStatus = ItemStatus.OPEN,
        title: String = "Earphone case",
        description: String = "Black",
        category: Category = Category.ELECTRONICS,
        zone: CampusZone? = block34,
        lat: Double? = null,
        lng: Double? = null,
        eventDate: Long = t0,
        embedding: FloatArray? = vector,
    ) = ItemForMatching(item(id, ItemType.FOUND, owner, status, title, description, category, zone, lat, lng, eventDate), embedding)

    private fun item(
        id: String,
        type: ItemType,
        owner: String,
        status: ItemStatus,
        title: String,
        description: String,
        category: Category,
        zone: CampusZone?,
        lat: Double?,
        lng: Double?,
        eventDate: Long,
    ) = Item(
        id = id,
        type = type,
        title = title,
        description = description,
        category = category,
        status = status,
        zoneId = zone?.id,
        zone = zone,
        latitude = lat,
        longitude = lng,
        addressLine = null,
        eventDate = eventDate,
        createdAt = eventDate,
        updatedAt = eventDate,
        ownerId = owner,
        ownerName = owner,
        verificationQuestion = null,
        hasPhoto = false,
        localPhotoPath = null,
        syncState = SyncState.SYNCED,
    )

    private fun zone(id: String, type: ZoneType, isPlaceholder: Boolean, lat: Double = 31.255, lng: Double = 75.705) =
        CampusZone(id, id, type, lat, lng, radiusMeters = 60.0, isPlaceholder = isPlaceholder)

    /** Degrees of latitude for a north–south distance (on the haversine sphere). */
    private fun metresToLat(metres: Double) = metres / 111_195.08
}
