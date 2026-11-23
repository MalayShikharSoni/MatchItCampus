package com.matchitcampus.app.ml

import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.matchitcampus.app.domain.matching.SearchScorer
import com.matchitcampus.app.domain.matching.TextNormalizer
import com.matchitcampus.app.domain.matching.VectorMath
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import kotlin.math.sqrt

private const val TAG = "MatchIt/EmbedderTest"

/**
 * Runs the **real** bundled model on the device, to check that semantic matching actually
 * understands meaning, not just that the code compiles. Similarity values are logged
 * (`tag:MatchIt/EmbedderTest`), so the numbers behind the design can be checked.
 *
 * These tests only assert *orderings*, never fixed similarity values: this model gives high
 * cosine values to every pair (see `MatchWeights`), and the app relies on the ranking only.
 */
@RunWith(AndroidJUnit4::class)
class TextEmbedderHelperTest {

    private val helper = TextEmbedderHelper(ApplicationProvider.getApplicationContext())

    @After
    fun tearDown() = runBlocking { helper.close() }

    private suspend fun embed(text: String): FloatArray =
        checkNotNull(helper.embed(text)) { "No embedding for \"$text\"" }

    @Test
    fun modelLoads_andReturnsUnitLengthVectors() = runTest {
        val vector = embed("black earphone case")

        Log.d(TAG, "Embedding size: ${vector.size}")
        assertEquals(100, vector.size)
        val length = sqrt(vector.sumOf { (it * it).toDouble() })
        assertEquals("L2 normalisation should give length 1", 1.0, length, 1e-3)
    }

    /** The sanity check from the plan: different words for the same thing beat an unrelated item. */
    @Test
    fun earbudsCase_isCloserToEarphoneCase_thanToWaterBottle() = runTest {
        val lost = embed("lost black JBL earbuds case")
        val earphoneCase = embed("found black earphone case")
        val bottle = embed("found steel water bottle")

        val toCase = VectorMath.cosine(lost, earphoneCase)
        val toBottle = VectorMath.cosine(lost, bottle)
        Log.d(TAG, "earbuds case vs earphone case = $toCase, vs water bottle = $toBottle")

        assertTrue("Expected $toCase > $toBottle", toCase > toBottle)
    }

    /**
     * Ten realistic lost posts against ten found posts, worded differently (spectacles/glasses,
     * purse/wallet, flask/bottle, hoodie/sweatshirt...). For every lost post, its true found post
     * must rank first among all ten. This is what lets matching show "the top 5" instead of
     * using a score threshold.
     */
    @Test
    fun forEveryLostPost_theTrueFoundPostRanksFirst() = runTest {
        val lost = listOf(
            post("Black JBL earbuds case", "Electronics", "Small black case, scratch on lid"),
            post("Spectacles", "Accessories", "Black frame, in a brown cover"),
            post("Student ID card", "ID & documents", "LPU identity card, name Aman"),
            post("Purse", "Wallets & cards", "Brown leather purse with some cash"),
            post("Steel flask", "Water bottles", "Milton steel flask, 750 ml"),
            post("Room keys", "Keys", "Two keys on a red keychain"),
            post("Calculator", "Electronics", "Casio fx-991 scientific calculator"),
            post("Blue hoodie", "Clothing", "Navy blue hoodie, size M"),
            post("Data structures book", "Books & stationery", "Cormen textbook with notes inside"),
            post("Cricket bat", "Sports", "SG bat with grip tape"),
        )
        val found = listOf(
            post("Earphone case", "Electronics", "Black case found near block 34"),
            post("Glasses", "Accessories", "Pair of glasses in a cover"),
            post("Identity card", "ID & documents", "University ID card found in library"),
            post("Wallet", "Wallets & cards", "Brown wallet, leather"),
            post("Water bottle", "Water bottles", "Metal bottle, silver"),
            post("Keychain with keys", "Keys", "Red keychain, two keys"),
            post("Scientific calculator", "Electronics", "Casio calculator"),
            post("Sweatshirt", "Clothing", "Dark blue sweatshirt with hood"),
            post("Algorithms textbook", "Books & stationery", "Thick CLRS book"),
            post("Bat", "Sports", "Cricket bat left at the ground"),
        )
        val lostVectors = lost.map { embed(it) }
        val foundVectors = found.map { embed(it) }

        for (i in lost.indices) {
            val similarities = foundVectors.map { VectorMath.cosine(lostVectors[i], it) }
            val best = similarities.indices.maxBy { similarities[it] }
            Log.d(TAG, "\"${lost[i]}\": " + similarities.joinToString(" ") { String.format(Locale.ROOT, "%.3f", it) })
            assertEquals("Best match for \"${lost[i]}\" should be \"${found[i]}\", got \"${found[best]}\"", i, best)
        }
    }

    @Test
    fun search_ranksTheRelatedItemAboveTheUnrelatedOne() = runTest {
        val query = "earphones"
        val related = post("JBL earbuds case", "Electronics", "Black, small scratch on the lid")
        val unrelated = post("Steel water bottle", "Water bottles", "Milton, 1 litre")

        val queryVector = embed(query)
        val relatedScore = SearchScorer.relevance(query, queryVector, related, embed(related))
        val unrelatedScore = SearchScorer.relevance(query, queryVector, unrelated, embed(unrelated))
        Log.d(TAG, "search \"$query\": related = $relatedScore, unrelated = $unrelatedScore")

        assertTrue("Expected $relatedScore > $unrelatedScore", relatedScore > unrelatedScore)
    }

    @Test
    fun blankText_returnsNull() = runTest {
        assertNull(helper.embed("   "))
    }

    private fun post(title: String, category: String, description: String) =
        TextNormalizer.forEmbedding(title, category, description)
}
