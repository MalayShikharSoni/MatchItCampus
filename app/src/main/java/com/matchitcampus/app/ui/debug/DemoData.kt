package com.matchitcampus.app.ui.debug

import com.matchitcampus.app.domain.model.Category
import com.matchitcampus.app.domain.model.Item
import com.matchitcampus.app.domain.model.ItemStatus
import com.matchitcampus.app.domain.model.ItemType
import com.matchitcampus.app.domain.model.SyncState
import com.matchitcampus.app.location.CampusMap

/**
 * One demo post, before it belongs to anyone.
 *
 * @property key stable short name; with the owner's uid it makes the item id, so seeding twice
 *   updates the same items instead of creating duplicates.
 * @property pairKey items with the same pairKey are the same real object, posted once as LOST
 *   and once as FOUND in different words. Null for distractors.
 * @property daysAgo when it was lost/found, counted back from today.
 * @property northMeters, eastMeters the pin, as an offset from the campus centre.
 */
data class DemoTemplate(
    val key: String,
    val type: ItemType,
    val title: String,
    val description: String,
    val category: Category,
    val color: String?,
    val zoneId: String,
    val daysAgo: Int,
    val northMeters: Double,
    val eastMeters: Double,
    val question: String? = null,
    val pairKey: String? = null,
)

/**
 * Realistic LPU-flavoured demo posts for the debug "Seed demo data" buttons.
 *
 * **Why two halves.** Matching never pairs two posts by the same person (a hard filter in
 * `MatchScorer`), so seeding everything under one account would show no matches. Instead the
 * FOUND half is seeded while signed in as one account (e.g. Riya) and the LOST half as another
 * (e.g. Aman). Together they contain five deliberate pairs that use different words for the same
 * thing (earphone case / earbuds cover, specs / glasses, identity card / ID card, wallet / purse,
 * flask / bottle) plus distractors that shouldn't match anything well.
 *
 * **Positions.** Every item gets a pin near the campus centre ([CampusMap.CENTER]), spread out so
 * the map shows separate dots and each pair is a few tens of metres apart. They're illustrative:
 * the real zone coordinates still have to be captured on campus.
 *
 * Pure Kotlin, so `DemoDataTest` can check the set on the JVM.
 */
object DemoData {

    /** Every demo item id starts with this, so "Remove my demo items" can find them. */
    const val ID_PREFIX = "demo-"

    private const val DAY_MS = 24L * 60 * 60 * 1000
    private const val METERS_PER_DEGREE_LAT = 111_320.0

    val found: List<DemoTemplate> = listOf(
        DemoTemplate(
            key = "earphone-case", type = ItemType.FOUND, title = "Black earphone case",
            description = "Small JBL case with a white star sticker, found on a bench outside the lifts.",
            category = Category.ELECTRONICS, color = "black", zoneId = "block_34", daysAgo = 1,
            northMeters = 90.0, eastMeters = 40.0, question = "What sticker is on it?", pairKey = "earbuds",
        ),
        DemoTemplate(
            key = "specs", type = ItemType.FOUND, title = "Specs in a brown case",
            description = "Thin metal-frame specs in a hard case, left on a table in the reading hall.",
            category = Category.ACCESSORIES, color = "gold", zoneId = "central_library", daysAgo = 2,
            northMeters = 170.0, eastMeters = -95.0, question = "What is written inside the case?", pairKey = "glasses",
        ),
        DemoTemplate(
            key = "identity-card", type = ItemType.FOUND, title = "Student identity card",
            description = "University identity card on a lanyard, found near the food court tables.",
            category = Category.ID_DOCUMENTS, color = "blue", zoneId = "uni_mall", daysAgo = 0,
            northMeters = -110.0, eastMeters = 190.0, question = "Whose name is on the card?", pairKey = "id-card",
        ),
        DemoTemplate(
            key = "wallet", type = ItemType.FOUND, title = "Leather wallet",
            description = "Wallet with some cash and a metro card, found under a seat after the fest.",
            category = Category.WALLETS_CARDS, color = "brown", zoneId = "sdm_auditorium", daysAgo = 3,
            northMeters = -220.0, eastMeters = -140.0, question = "Which cards are inside?", pairKey = "purse",
        ),
        DemoTemplate(
            key = "flask", type = ItemType.FOUND, title = "Steel flask",
            description = "Steel flask with a black lid and a couple of dents, left near the basketball court.",
            category = Category.WATER_BOTTLES, color = "silver", zoneId = "sports_complex", daysAgo = 1,
            northMeters = -440.0, eastMeters = -330.0, question = "What mark is on the lid?", pairKey = "bottle",
        ),
        DemoTemplate(
            key = "umbrella", type = ItemType.FOUND, title = "Folding umbrella",
            description = "Compact umbrella with a wooden handle, left at the security desk.",
            category = Category.ACCESSORIES, color = "red", zoneId = "main_gate", daysAgo = 2,
            northMeters = 500.0, eastMeters = 290.0, question = "What pattern is on it?",
        ),
        DemoTemplate(
            key = "calculator", type = ItemType.FOUND, title = "Scientific calculator",
            description = "Casio fx-991 calculator without its cover, found after the maths exam.",
            category = Category.ELECTRONICS, color = "grey", zoneId = "block_36", daysAgo = 5,
            northMeters = -60.0, eastMeters = 120.0, question = "What is scratched on the back?",
        ),
        DemoTemplate(
            key = "keys", type = ItemType.FOUND, title = "Bunch of keys",
            description = "Three keys on a ring with a small football keychain.",
            category = Category.KEYS, color = "silver", zoneId = "boys_hostels", daysAgo = 4,
            northMeters = -560.0, eastMeters = 95.0, question = "What is on the keychain?",
        ),
        DemoTemplate(
            key = "hoodie", type = ItemType.FOUND, title = "Hoodie",
            description = "Plain hoodie, size M, left on the stands at the football ground.",
            category = Category.CLOTHING, color = "grey", zoneId = "sports_complex", daysAgo = 6,
            northMeters = -470.0, eastMeters = -380.0, question = "What brand is the label?",
        ),
        DemoTemplate(
            key = "notebook", type = ItemType.FOUND, title = "Spiral notebook",
            description = "Notebook full of physics notes, found in a lecture room.",
            category = Category.BOOKS_STATIONERY, color = "green", zoneId = "block_38", daysAgo = 2,
            northMeters = 120.0, eastMeters = 150.0, question = "Whose name is on the first page?",
        ),
    )

    val lost: List<DemoTemplate> = listOf(
        DemoTemplate(
            key = "earbuds-cover", type = ItemType.LOST, title = "Lost JBL earbuds cover",
            description = "Cover for my wireless earbuds with a star sticker. Probably dropped near the lifts.",
            category = Category.ELECTRONICS, color = "matte black", zoneId = "block_34", daysAgo = 1,
            northMeters = 70.0, eastMeters = 25.0, pairKey = "earbuds",
        ),
        DemoTemplate(
            key = "glasses", type = ItemType.LOST, title = "Lost my reading glasses",
            description = "Glasses with golden frames in a hard brown case, left in the library.",
            category = Category.ACCESSORIES, color = "gold", zoneId = "central_library", daysAgo = 2,
            northMeters = 150.0, eastMeters = -110.0, pairKey = "glasses",
        ),
        DemoTemplate(
            key = "id-card", type = ItemType.LOST, title = "Lost ID card",
            description = "My university ID with a blue strap, lost at lunch in the mall.",
            category = Category.ID_DOCUMENTS, color = "blue", zoneId = "uni_mall", daysAgo = 1,
            northMeters = -90.0, eastMeters = 210.0, pairKey = "id-card",
        ),
        DemoTemplate(
            key = "purse", type = ItemType.LOST, title = "Lost purse",
            description = "Small brown purse with my bank card and some money, lost during the event.",
            category = Category.WALLETS_CARDS, color = "brown", zoneId = "sdm_auditorium", daysAgo = 3,
            northMeters = -240.0, eastMeters = -120.0, pairKey = "purse",
        ),
        DemoTemplate(
            key = "bottle", type = ItemType.LOST, title = "Lost water bottle",
            description = "Metal bottle with a black cap, lost after basketball practice.",
            category = Category.WATER_BOTTLES, color = "silver", zoneId = "sports_complex", daysAgo = 2,
            northMeters = -420.0, eastMeters = -350.0, pairKey = "bottle",
        ),
        DemoTemplate(
            key = "charger", type = ItemType.LOST, title = "Laptop charger",
            description = "Dell 65 W charger with a frayed cable, left in a lab.",
            category = Category.ELECTRONICS, color = "black", zoneId = "block_32", daysAgo = 3,
            northMeters = 10.0, eastMeters = 95.0,
        ),
        DemoTemplate(
            key = "textbook", type = ItemType.LOST, title = "Data structures textbook",
            description = "Thick textbook with my name on the inside cover and lots of highlighting.",
            category = Category.BOOKS_STATIONERY, color = "blue", zoneId = "central_library", daysAgo = 7,
            northMeters = 190.0, eastMeters = -60.0,
        ),
        DemoTemplate(
            key = "watch", type = ItemType.LOST, title = "Digital watch",
            description = "Sports watch with a rubber strap, took it off at the gym.",
            category = Category.ACCESSORIES, color = "black", zoneId = "sports_complex", daysAgo = 4,
            northMeters = -500.0, eastMeters = -300.0,
        ),
        DemoTemplate(
            key = "jacket", type = ItemType.LOST, title = "Denim jacket",
            description = "Denim jacket with a badge on the pocket, left in the common room.",
            category = Category.CLOTHING, color = "blue", zoneId = "girls_hostels", daysAgo = 5,
            northMeters = 330.0, eastMeters = -420.0,
        ),
        DemoTemplate(
            key = "scooty-key", type = ItemType.LOST, title = "Scooty key",
            description = "Single Activa key with a red plastic tag.",
            category = Category.KEYS, color = "red", zoneId = "main_gate", daysAgo = 3,
            northMeters = 480.0, eastMeters = 320.0,
        ),
    )

    /** The half to seed for [type]. */
    fun templates(type: ItemType): List<DemoTemplate> = if (type == ItemType.FOUND) found else lost

    /** The demo item id for this template and owner. Stable, so re-seeding overwrites. */
    fun itemId(template: DemoTemplate, ownerId: String): String =
        "$ID_PREFIX${template.key}-${ownerId.take(8)}"

    /**
     * Builds the item as if the owner had lost/found it, and posted it, [DemoTemplate.daysAgo] days ago.
     * `updatedAt` and `syncState` are set again by `ItemRepository.saveItem`.
     */
    fun toItem(template: DemoTemplate, ownerId: String, ownerName: String, todayEventDate: Long, now: Long): Item {
        val center = CampusMap.CENTER
        val metersPerDegreeLng = METERS_PER_DEGREE_LAT * kotlin.math.cos(Math.toRadians(center.latitude))
        return Item(
            id = itemId(template, ownerId),
            type = template.type,
            title = template.title,
            description = template.description,
            category = template.category,
            status = ItemStatus.OPEN,
            zoneId = template.zoneId,
            zone = null,
            latitude = center.latitude + template.northMeters / METERS_PER_DEGREE_LAT,
            longitude = center.longitude + template.eastMeters / metersPerDegreeLng,
            addressLine = null,
            eventDate = todayEventDate - template.daysAgo * DAY_MS,
            // "Posted" on the day it was lost/found, so the feed (newest post first) reads naturally.
            createdAt = now - template.daysAgo * DAY_MS,
            updatedAt = now,
            ownerId = ownerId,
            ownerName = ownerName,
            verificationQuestion = template.question.takeIf { template.type == ItemType.FOUND },
            hasPhoto = false,
            localPhotoPath = null,
            syncState = SyncState.PENDING_UPLOAD,
            color = template.color,
        )
    }
}
