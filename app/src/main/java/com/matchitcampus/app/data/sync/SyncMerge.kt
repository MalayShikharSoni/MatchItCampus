package com.matchitcampus.app.data.sync

import com.matchitcampus.app.data.local.entity.ItemEntity
import com.matchitcampus.app.data.remote.dto.ItemDto
import com.matchitcampus.app.data.remote.mapper.toEntity

/**
 * How a change from Firestore is merged into Room. Pure Kotlin, so every rule is covered by
 * JVM unit tests (`SyncMergeTest`).
 *
 * The conflict rule is **last write wins**, comparing `updatedAt` (the phone clock at the
 * time of the change):
 * - If the local row was changed more recently than the remote copy, keep the local row.
 *   This is what stops older remote data from overwriting a `PENDING_UPLOAD` change that
 *   hasn't been pushed yet.
 * - Otherwise the remote copy wins (on a tie too: a tie is usually our own push coming back).
 *
 * Known limitation: two phones with very different clocks can pick the "wrong" winner.
 * That's acceptable here because each document normally has one writer (its owner).
 */
object SyncMerge {

    /** What to do with one remote item. */
    sealed interface ItemDecision {
        /** Write this row to Room (insert or replace). */
        data class Write(val entity: ItemEntity) : ItemDecision
        /** The owner deleted the item: remove the local row. */
        data object Delete : ItemDecision
        /** Keep what we have. */
        data object Skip : ItemDecision
    }

    /** True if a remote change should replace the local row. [localUpdatedAt] is null if there's no local row. */
    fun remoteWins(localUpdatedAt: Long?, remoteUpdatedAt: Long): Boolean =
        localUpdatedAt == null || remoteUpdatedAt >= localUpdatedAt

    /**
     * @param local the current local row with the same id, or null.
     * @param remote the item as it is in Firestore.
     */
    fun decide(local: ItemEntity?, remote: ItemDto): ItemDecision = when {
        !remoteWins(local?.updatedAt, remote.updatedAtMillis) -> ItemDecision.Skip
        remote.deleted -> if (local == null) ItemDecision.Skip else ItemDecision.Delete
        else -> ItemDecision.Write(remote.toEntity(existing = local))
    }
}
