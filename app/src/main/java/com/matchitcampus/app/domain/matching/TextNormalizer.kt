package com.matchitcampus.app.domain.matching

import com.matchitcampus.app.domain.model.Item

/**
 * Turns an item's fields into the text that is compared, in the same way every time.
 *
 * Doing it in one place matters: if one phone embedded "Black case" and another "black  case.",
 * the same item could get slightly different vectors. Pure Kotlin, so it's unit-tested.
 */
object TextNormalizer {

    /**
     * Longest text that is embedded. The Universal Sentence Encoder works on whole sentences;
     * past a few hundred characters extra words only dilute the meaning of the important first
     * ones (the title), and they make embedding slower.
     */
    const val MAX_EMBEDDING_CHARS = 500

    private val whitespace = Regex("\\s+")
    private val nonWordChars = Regex("[^\\p{L}\\p{N}]+")

    /**
     * Small words that carry no meaning for matching. Removing them stops two descriptions
     * looking similar just because both say "I lost my ... in the ...".
     */
    private val stopWords = setOf(
        "a", "an", "the", "and", "or", "of", "in", "on", "at", "to", "for", "with", "near", "from",
        "by", "is", "it", "its", "was", "were", "be", "been", "this", "that", "my", "mine", "i", "me",
        "we", "our", "you", "your", "has", "have", "had", "some", "one", "lost", "found", "item",
    )

    /** Lower-case, trimmed, with every run of whitespace (spaces, tabs, newlines) made a single space. */
    fun normalize(text: String): String = text.trim().lowercase().replace(whitespace, " ")

    /**
     * The text that represents [item] for embedding and keyword matching:
     * `"<title>. <category>. <color>. <description>"`, normalised and capped at [MAX_EMBEDDING_CHARS].
     *
     * The category's display name is included because it adds meaning the user may not have
     * typed: "purse" plus "wallets & cards" is closer to "wallet" than "purse" alone.
     * The colour (database v2, Phase 7) helps "black case" beat "blue case".
     *
     * An item without a colour produces exactly the same text as before v2, so the embeddings
     * already stored stay valid and nothing has to be recomputed after the migration.
     */
    fun forEmbedding(item: Item): String =
        forEmbedding(
            title = item.title,
            categoryName = item.category.displayName,
            description = item.description,
            color = item.color,
        )

    /** The same, from the separate fields. Blank parts are left out, so there are no empty ". ." pieces. */
    fun forEmbedding(title: String, categoryName: String, description: String, color: String? = null): String =
        listOf(title, categoryName, color.orEmpty(), description)
            .map { it.trim().trimEnd('.') }
            .filter { it.isNotEmpty() }
            .joinToString(". ")
            .let(::normalize)
            .take(MAX_EMBEDDING_CHARS)

    /**
     * The meaningful words in [text], for keyword matching: lower-case, split on anything that
     * isn't a letter or a digit, without [stopWords] and single characters. A set, because
     * keyword similarity only asks whether a word is present, not how often.
     */
    fun tokens(text: String): Set<String> =
        normalize(text)
            .split(nonWordChars)
            .filter { it.length > 1 && it !in stopWords }
            .toSet()
}
