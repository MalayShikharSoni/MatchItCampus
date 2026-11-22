package com.matchitcampus.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.matchitcampus.app.domain.model.Category
import com.matchitcampus.app.domain.model.ItemStatus
import com.matchitcampus.app.domain.model.ItemType
import com.matchitcampus.app.domain.model.SyncState

/**
 * One row of the `items` table: a lost or found post.
 *
 * Room is the app's single source of truth. The UI only reads this table, and Firestore
 * data reaches the UI only by being written here first (Phase 3).
 *
 * **Indices** are on the columns the app filters or sorts by: the feed filters by [type] and
 * [status], filter chips by [zoneId], "My posts" by [ownerId], and sync looks for rows newer
 * than a timestamp with [updatedAt]. Without an index, SQLite has to scan the whole table.
 *
 * **No foreign key to `campus_zones`:** items arrive from Firestore in any order, and a zone
 * id might not exist on this phone (for example after a zone is renamed). A foreign key would
 * make those inserts fail. A missing zone just shows as "unknown zone" instead.
 *
 * **[embedding]** is the item's text embedding from the on-device model (Phase 4), stored as
 * a BLOB by [com.matchitcampus.app.data.local.Converters]. It's local only and never sent to
 * Firestore. [embeddingVersion] records which model produced it, so it can be recomputed if
 * the model changes.
 *
 * This is a `data class` with an array property, and arrays compare by identity in Kotlin.
 * [equals] and [hashCode] are therefore overridden so two entities with the same embedding
 * values compare equal (tests rely on this).
 */
@Entity(
    tableName = "items",
    indices = [
        Index("type"),
        Index("status"),
        Index("zoneId"),
        Index("ownerId"),
        Index("updatedAt"),
    ],
)
data class ItemEntity(
    /** A random UUID created on the phone, so an item has its id before it's uploaded. */
    @PrimaryKey val id: String,
    val type: ItemType,
    val title: String,
    val description: String,
    val category: Category,
    val status: ItemStatus,
    val zoneId: String?,
    val latitude: Double?,
    val longitude: Double?,
    /** A readable address from reverse geocoding (Phase 6). */
    val addressLine: String?,
    /** When the item was lost or found, in epoch millis. */
    val eventDate: Long,
    val createdAt: Long,
    /** Last change, in epoch millis. Used for last-write-wins conflict resolution in sync. */
    val updatedAt: Long,
    val ownerId: String,
    val ownerName: String,
    /** Only set for FOUND items. */
    val verificationQuestion: String?,
    val hasPhoto: Boolean,
    val localPhotoPath: String?,
    val embedding: FloatArray?,
    val embeddingVersion: Int?,
    val syncState: SyncState,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ItemEntity) return false
        return id == other.id &&
            type == other.type &&
            title == other.title &&
            description == other.description &&
            category == other.category &&
            status == other.status &&
            zoneId == other.zoneId &&
            latitude == other.latitude &&
            longitude == other.longitude &&
            addressLine == other.addressLine &&
            eventDate == other.eventDate &&
            createdAt == other.createdAt &&
            updatedAt == other.updatedAt &&
            ownerId == other.ownerId &&
            ownerName == other.ownerName &&
            verificationQuestion == other.verificationQuestion &&
            hasPhoto == other.hasPhoto &&
            localPhotoPath == other.localPhotoPath &&
            // The one field that needs care: compare array contents, not references.
            embedding.contentEquals(other.embedding) &&
            embeddingVersion == other.embeddingVersion &&
            syncState == other.syncState
    }

    /** Uses a few fields only, which is allowed: equal objects still get equal hash codes. */
    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + updatedAt.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}
