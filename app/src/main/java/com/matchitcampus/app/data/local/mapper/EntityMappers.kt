package com.matchitcampus.app.data.local.mapper

import com.matchitcampus.app.data.local.entity.CampusZoneEntity
import com.matchitcampus.app.data.local.entity.ClaimEntity
import com.matchitcampus.app.data.local.entity.ItemEntity
import com.matchitcampus.app.data.local.entity.MatchEntity
import com.matchitcampus.app.data.local.relation.ClaimWithItems
import com.matchitcampus.app.data.local.relation.ItemWithZone
import com.matchitcampus.app.data.local.relation.MatchWithFallback
import com.matchitcampus.app.domain.model.CampusZone
import com.matchitcampus.app.domain.model.Claim
import com.matchitcampus.app.domain.model.Item
import com.matchitcampus.app.domain.model.MatchResult

/*
 * Conversions between Room entities and domain models.
 *
 * Why two sets of classes: entities describe *how data is stored* (table names, indices,
 * the embedding BLOB), while domain models describe *what the app works with* (an item with
 * its zone already attached). Keeping them apart means a database change doesn't ripple
 * through every screen, and screens never need Room imports.
 *
 * These are plain extension functions with no Android code, so they're unit-tested on the JVM.
 */

fun CampusZoneEntity.toDomain(): CampusZone = CampusZone(
    id = id,
    name = name,
    type = type,
    latitude = latitude,
    longitude = longitude,
    radiusMeters = radiusMeters,
    isPlaceholder = isPlaceholder,
)

fun CampusZone.toEntity(): CampusZoneEntity = CampusZoneEntity(
    id = id,
    name = name,
    type = type,
    latitude = latitude,
    longitude = longitude,
    radiusMeters = radiusMeters,
    isPlaceholder = isPlaceholder,
)

/** @param zone the item's zone if it has been loaded; entities only store the zone id. */
fun ItemEntity.toDomain(zone: CampusZone? = null): Item = Item(
    id = id,
    type = type,
    title = title,
    description = description,
    category = category,
    status = status,
    zoneId = zoneId,
    zone = zone,
    latitude = latitude,
    longitude = longitude,
    addressLine = addressLine,
    eventDate = eventDate,
    createdAt = createdAt,
    updatedAt = updatedAt,
    ownerId = ownerId,
    ownerName = ownerName,
    verificationQuestion = verificationQuestion,
    hasPhoto = hasPhoto,
    localPhotoPath = localPhotoPath,
    syncState = syncState,
    color = color,
)

fun ItemWithZone.toDomain(): Item = item.toDomain(zone?.toDomain())

/**
 * Builds the row to store for [this] item.
 *
 * The domain model doesn't carry the embedding, so the caller passes it in. Passing null
 * (the default) means "no embedding yet", and Phase 4's matching fills it in later.
 */
fun Item.toEntity(embedding: FloatArray? = null, embeddingVersion: Int? = null): ItemEntity = ItemEntity(
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
    updatedAt = updatedAt,
    ownerId = ownerId,
    ownerName = ownerName,
    verificationQuestion = verificationQuestion,
    hasPhoto = hasPhoto,
    localPhotoPath = localPhotoPath,
    embedding = embedding,
    embeddingVersion = embeddingVersion,
    syncState = syncState,
    color = color,
)

fun ClaimEntity.toDomain(foundItem: Item? = null, lostItem: Item? = null): Claim = Claim(
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
    updatedAt = updatedAt,
    syncState = syncState,
    foundItem = foundItem,
    lostItem = lostItem,
)

fun ClaimWithItems.toDomain(): Claim = claim.toDomain(
    foundItem = foundItem?.toDomain(),
    lostItem = lostItem?.toDomain(),
)

/** The attached [Claim.foundItem] and [Claim.lostItem] aren't stored: they live in `items`. */
fun Claim.toEntity(): ClaimEntity = ClaimEntity(
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
    updatedAt = updatedAt,
    syncState = syncState,
)

/** The row to cache for a computed match. `usedFallback` isn't stored (see [MatchWithFallback]). */
fun MatchResult.toEntity(computedAt: Long): MatchEntity = MatchEntity(
    sourceItemId = sourceItemId,
    candidateItemId = candidateItemId,
    score = score,
    textScore = textScore,
    categoryScore = categoryScore,
    locationScore = locationScore,
    dateScore = dateScore,
    distanceMeters = distanceMeters,
    daysApart = daysApart,
    computedAt = computedAt,
)

fun MatchWithFallback.toDomain(): MatchResult = MatchResult(
    sourceItemId = match.sourceItemId,
    candidateItemId = match.candidateItemId,
    score = match.score,
    textScore = match.textScore,
    categoryScore = match.categoryScore,
    locationScore = match.locationScore,
    dateScore = match.dateScore,
    distanceMeters = match.distanceMeters,
    // The column is nullable in the v1 schema, but MatchRepository always writes it.
    daysApart = match.daysApart ?: 0,
    usedFallback = usedFallback,
)
