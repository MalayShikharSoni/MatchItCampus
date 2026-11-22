package com.matchitcampus.app.domain.model

/*
 * The app's enums, kept together in one file because each is tiny.
 *
 * They are domain types, so they have no Room or Firebase imports. Room stores each one as
 * its constant name (TEXT) through data/local/Converters.kt, which means renaming a constant
 * here changes what is stored: existing rows would then fail to load. Treat the names as
 * part of the database format.
 */

/** Whether a post is about something someone lost or something someone found. */
enum class ItemType { LOST, FOUND }

/**
 * Where an item is in its life cycle.
 * OPEN → CLAIM_PENDING (someone claimed a found item) → RETURNED.
 */
enum class ItemStatus { OPEN, CLAIM_PENDING, RETURNED }

/**
 * Item categories.
 *
 * [displayName] is the label shown in the UI. Phase 4 also puts it into the text that is
 * embedded for matching, so "Wallets & cards" helps a "purse" match a "wallet".
 */
enum class Category(val displayName: String) {
    ELECTRONICS("Electronics"),
    BAGS("Bags"),
    WALLETS_CARDS("Wallets & cards"),
    ID_DOCUMENTS("ID & documents"),
    KEYS("Keys"),
    BOOKS_STATIONERY("Books & stationery"),
    CLOTHING("Clothing"),
    ACCESSORIES("Accessories"),
    WATER_BOTTLES("Water bottles"),
    SPORTS("Sports"),
    OTHER("Other"),
}

/** The state of a claim made on a FOUND item. */
enum class ClaimStatus { PENDING, ACCEPTED, REJECTED, CANCELLED }

/**
 * Whether a local row matches Firestore.
 *
 * - SYNCED: Room and Firestore agree.
 * - PENDING_UPLOAD: changed locally, not yet pushed (Phase 3's SyncManager pushes it).
 * - PENDING_DELETE: deleted locally. The row stays hidden in Room until the remote delete
 *   succeeds, so a delete made offline is not forgotten.
 */
enum class SyncState { SYNCED, PENDING_UPLOAD, PENDING_DELETE }

/** What kind of place a campus zone is. Used for icons and grouping. */
enum class ZoneType { ACADEMIC, HOSTEL, FOOD, LIBRARY, ADMIN, SPORTS, MEDICAL, GATE, OTHER }
