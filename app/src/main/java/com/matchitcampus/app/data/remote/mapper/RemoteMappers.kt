package com.matchitcampus.app.data.remote.mapper

import com.matchitcampus.app.data.local.entity.ClaimEntity
import com.matchitcampus.app.data.local.entity.ItemEntity
import com.matchitcampus.app.data.local.entity.UserEntity
import com.matchitcampus.app.data.remote.dto.ClaimDto
import com.matchitcampus.app.data.remote.dto.ItemDto
import com.matchitcampus.app.data.remote.dto.UserDto
import com.matchitcampus.app.domain.model.Category
import com.matchitcampus.app.domain.model.ClaimStatus
import com.matchitcampus.app.domain.model.ItemStatus
import com.matchitcampus.app.domain.model.ItemType
import com.matchitcampus.app.domain.model.SyncState

/*
 * Conversions between Room entities, DTOs and Firestore field maps.
 *
 * Everything here is plain Kotlin with no Firebase imports, so it's covered by JVM unit tests
 * (RemoteMappersTest). The Firebase-specific parts (server timestamps, DocumentSnapshot) stay
 * in the data sources.
 *
 * Field names are constants in [Fields], so the mappers, the data-source queries and
 * firebase/firestore.rules all use the same spelling.
 */

/** Firestore field names. They must match `firebase/firestore.rules`. */
object Fields {
    const val TYPE = "type"
    const val TITLE = "title"
    const val DESCRIPTION = "description"
    const val CATEGORY = "category"
    const val STATUS = "status"
    const val ZONE_ID = "zoneId"
    const val LATITUDE = "latitude"
    const val LONGITUDE = "longitude"
    const val ADDRESS_LINE = "addressLine"
    const val EVENT_DATE = "eventDate"
    const val CREATED_AT = "createdAt"
    /** Server timestamp, set by Firestore itself. */
    const val UPDATED_AT = "updatedAt"
    /** The phone's clock at the time of the change. */
    const val UPDATED_AT_MILLIS = "updatedAtMillis"
    const val OWNER_ID = "ownerId"
    const val OWNER_NAME = "ownerName"
    const val VERIFICATION_QUESTION = "verificationQuestion"
    const val HAS_PHOTO = "hasPhoto"
    const val DELETED = "deleted"

    const val FOUND_ITEM_ID = "foundItemId"
    const val LOST_ITEM_ID = "lostItemId"
    const val CLAIMANT_ID = "claimantId"
    const val CLAIMANT_NAME = "claimantName"
    const val FINDER_ID = "finderId"
    const val ANSWER = "answer"
    const val MESSAGE = "message"

    const val NAME = "name"
    const val EMAIL = "email"
    const val REG_NO = "regNo"
}

// ---------- Items ----------

fun ItemEntity.toDto(): ItemDto = ItemDto(
    id = id,
    type = type,
    title = title,
    description = description,
    category = category,
    status = status,
    zoneId = zoneId,
    latitude = latitude,
    longitude = longitude,
    addressLine = addressLine,
    eventDate = eventDate,
    createdAt = createdAt,
    updatedAtMillis = updatedAt,
    ownerId = ownerId,
    ownerName = ownerName,
    verificationQuestion = verificationQuestion,
    hasPhoto = hasPhoto,
    deleted = false,
)

/**
 * The fields written to `items/{id}`. Every key is always present (null where empty),
 * because the security rules check each field's type and a missing key would fail them.
 * The server timestamp [Fields.UPDATED_AT] is added by the data source, since it's a
 * Firebase-specific value.
 */
fun ItemDto.toFirestoreMap(): Map<String, Any?> = mapOf(
    Fields.TYPE to type.name,
    Fields.TITLE to title,
    Fields.DESCRIPTION to description,
    Fields.CATEGORY to category.name,
    Fields.STATUS to status.name,
    Fields.ZONE_ID to zoneId,
    Fields.LATITUDE to latitude,
    Fields.LONGITUDE to longitude,
    Fields.ADDRESS_LINE to addressLine,
    Fields.EVENT_DATE to eventDate,
    Fields.CREATED_AT to createdAt,
    Fields.UPDATED_AT_MILLIS to updatedAtMillis,
    Fields.OWNER_ID to ownerId,
    Fields.OWNER_NAME to ownerName,
    Fields.VERIFICATION_QUESTION to verificationQuestion,
    Fields.HAS_PHOTO to hasPhoto,
    Fields.DELETED to deleted,
)

/**
 * Reads an item document's fields.
 *
 * @return null if a required field is missing or has the wrong type (for example an enum
 *   value this version of the app doesn't know). The caller logs and skips that document
 *   instead of crashing.
 */
fun itemDtoFromMap(id: String, data: Map<String, Any?>, serverUpdatedAtMillis: Long?): ItemDto? {
    return ItemDto(
        id = id,
        type = enumOrNull<ItemType>(data[Fields.TYPE]) ?: return null,
        title = data[Fields.TITLE] as? String ?: return null,
        description = data[Fields.DESCRIPTION] as? String ?: "",
        category = enumOrNull<Category>(data[Fields.CATEGORY]) ?: return null,
        status = enumOrNull<ItemStatus>(data[Fields.STATUS]) ?: return null,
        zoneId = data[Fields.ZONE_ID] as? String,
        latitude = (data[Fields.LATITUDE] as? Number)?.toDouble(),
        longitude = (data[Fields.LONGITUDE] as? Number)?.toDouble(),
        addressLine = data[Fields.ADDRESS_LINE] as? String,
        eventDate = (data[Fields.EVENT_DATE] as? Number)?.toLong() ?: return null,
        createdAt = (data[Fields.CREATED_AT] as? Number)?.toLong() ?: return null,
        updatedAtMillis = (data[Fields.UPDATED_AT_MILLIS] as? Number)?.toLong() ?: return null,
        ownerId = data[Fields.OWNER_ID] as? String ?: return null,
        ownerName = data[Fields.OWNER_NAME] as? String ?: "",
        verificationQuestion = data[Fields.VERIFICATION_QUESTION] as? String,
        hasPhoto = data[Fields.HAS_PHOTO] as? Boolean ?: false,
        deleted = data[Fields.DELETED] as? Boolean ?: false,
        serverUpdatedAtMillis = serverUpdatedAtMillis,
    )
}

/**
 * Turns a remote item into a Room row, keeping what only this phone knows.
 *
 * - The **embedding** is kept if the text it was computed from (title, description,
 *   category) is unchanged. Otherwise it's cleared so Phase 4 recomputes it. Without this,
 *   every sync would wipe every embedding.
 * - The **local photo path** is kept while the item still has a photo.
 *
 * The result is [SyncState.SYNCED], because it now matches Firestore.
 *
 * @param existing the current local row with this id, if any.
 */
fun ItemDto.toEntity(existing: ItemEntity?): ItemEntity {
    val sameText = existing != null &&
        existing.title == title &&
        existing.description == description &&
        existing.category == category
    return ItemEntity(
        id = id,
        type = type,
        title = title,
        description = description,
        category = category,
        status = status,
        zoneId = zoneId,
        latitude = latitude,
        longitude = longitude,
        addressLine = addressLine,
        eventDate = eventDate,
        createdAt = createdAt,
        updatedAt = updatedAtMillis,
        ownerId = ownerId,
        ownerName = ownerName,
        verificationQuestion = verificationQuestion,
        hasPhoto = hasPhoto,
        localPhotoPath = if (hasPhoto) existing?.localPhotoPath else null,
        // Kotlin knows `existing` isn't null here, because `sameText` checked it.
        embedding = if (sameText) existing.embedding else null,
        embeddingVersion = if (sameText) existing.embeddingVersion else null,
        syncState = SyncState.SYNCED,
    )
}

// ---------- Claims ----------

fun ClaimEntity.toDto(): ClaimDto = ClaimDto(
    id = id,
    foundItemId = foundItemId,
    lostItemId = lostItemId,
    claimantId = claimantId,
    claimantName = claimantName,
    finderId = finderId,
    answer = answer,
    message = message,
    status = status,
    createdAt = createdAt,
    updatedAtMillis = updatedAt,
)

/** The fields written to `claims/{id}`. The server timestamp is added by the data source. */
fun ClaimDto.toFirestoreMap(): Map<String, Any?> = mapOf(
    Fields.FOUND_ITEM_ID to foundItemId,
    Fields.LOST_ITEM_ID to lostItemId,
    Fields.CLAIMANT_ID to claimantId,
    Fields.CLAIMANT_NAME to claimantName,
    Fields.FINDER_ID to finderId,
    Fields.ANSWER to answer,
    Fields.MESSAGE to message,
    Fields.STATUS to status.name,
    Fields.CREATED_AT to createdAt,
    Fields.UPDATED_AT_MILLIS to updatedAtMillis,
)

/** @return null if a required field is missing or malformed. */
fun claimDtoFromMap(id: String, data: Map<String, Any?>): ClaimDto? {
    return ClaimDto(
        id = id,
        foundItemId = data[Fields.FOUND_ITEM_ID] as? String ?: return null,
        lostItemId = data[Fields.LOST_ITEM_ID] as? String,
        claimantId = data[Fields.CLAIMANT_ID] as? String ?: return null,
        claimantName = data[Fields.CLAIMANT_NAME] as? String ?: "",
        finderId = data[Fields.FINDER_ID] as? String ?: return null,
        answer = data[Fields.ANSWER] as? String ?: "",
        message = data[Fields.MESSAGE] as? String ?: "",
        status = enumOrNull<ClaimStatus>(data[Fields.STATUS]) ?: return null,
        createdAt = (data[Fields.CREATED_AT] as? Number)?.toLong() ?: return null,
        updatedAtMillis = (data[Fields.UPDATED_AT_MILLIS] as? Number)?.toLong() ?: return null,
    )
}

fun ClaimDto.toEntity(): ClaimEntity = ClaimEntity(
    id = id,
    foundItemId = foundItemId,
    lostItemId = lostItemId,
    claimantId = claimantId,
    claimantName = claimantName,
    finderId = finderId,
    answer = answer,
    message = message,
    status = status,
    createdAt = createdAt,
    updatedAt = updatedAtMillis,
    syncState = SyncState.SYNCED,
)

// ---------- Users ----------

fun UserDto.toFirestoreMap(): Map<String, Any?> = mapOf(
    Fields.NAME to name,
    Fields.EMAIL to email,
    Fields.REG_NO to regNo,
    Fields.CREATED_AT to createdAt,
)

/** @return null if the name or email is missing. */
fun userDtoFromMap(data: Map<String, Any?>): UserDto? = UserDto(
    name = data[Fields.NAME] as? String ?: return null,
    email = data[Fields.EMAIL] as? String ?: return null,
    regNo = data[Fields.REG_NO] as? String,
    createdAt = (data[Fields.CREATED_AT] as? Number)?.toLong() ?: 0L,
)

fun UserDto.toEntity(uid: String): UserEntity = UserEntity(uid = uid, name = name, email = email, regNo = regNo)

/** Enum constant by name, or null for anything else (unknown value, wrong type, null). */
private inline fun <reified T : Enum<T>> enumOrNull(value: Any?): T? =
    (value as? String)?.let { name -> enumValues<T>().firstOrNull { it.name == name } }
