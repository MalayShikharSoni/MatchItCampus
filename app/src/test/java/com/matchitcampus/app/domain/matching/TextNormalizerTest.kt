package com.matchitcampus.app.domain.matching

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextNormalizerTest {

    @Test
    fun normalize_lowercasesTrimsAndCollapsesWhitespace() {
        assertEquals("black jbl case", TextNormalizer.normalize("  Black \t JBL\n\ncase "))
    }

    @Test
    fun forEmbedding_joinsTitleCategoryAndDescription() {
        assertEquals(
            "black earbuds case. electronics. jbl, has a scratch",
            TextNormalizer.forEmbedding("Black earbuds case", "Electronics", "JBL, has a scratch"),
        )
    }

    @Test
    fun forEmbedding_skipsBlankPartsAndDoesNotDoubleFullStops() {
        assertEquals(
            "blue bottle. water bottles",
            TextNormalizer.forEmbedding("Blue bottle.", "Water bottles", "   "),
        )
    }

    @Test
    fun forEmbedding_isCappedInLength() {
        val text = TextNormalizer.forEmbedding("Title", "Other", "word ".repeat(500))
        assertEquals(TextNormalizer.MAX_EMBEDDING_CHARS, text.length)
        assertTrue(text.startsWith("title. other. word"))
    }

    @Test
    fun forEmbedding_putsTheColourAfterTheCategory_andNoColourChangesNothing() {
        assertEquals(
            "black earbuds case. electronics. matte black. jbl",
            TextNormalizer.forEmbedding("Black earbuds case", "Electronics", "JBL", color = "Matte black"),
        )
        // Items from before database v2 have no colour: their text (and stored embedding) is unchanged.
        assertEquals(
            TextNormalizer.forEmbedding("Black earbuds case", "Electronics", "JBL"),
            TextNormalizer.forEmbedding("Black earbuds case", "Electronics", "JBL", color = "  "),
        )
    }

    @Test
    fun tokens_dropsPunctuationStopWordsAndSingleLetters() {
        assertEquals(
            setOf("black", "jbl", "case", "block", "34"),
            TextNormalizer.tokens("I lost my BLACK JBL case, near Block-34 (a)!"),
        )
    }
}
