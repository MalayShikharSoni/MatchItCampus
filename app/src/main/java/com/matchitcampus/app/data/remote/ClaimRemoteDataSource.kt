package com.matchitcampus.app.data.remote

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.matchitcampus.app.data.remote.dto.ClaimDto
import com.matchitcampus.app.data.remote.mapper.Fields
import com.matchitcampus.app.data.remote.mapper.claimDtoFromMap
import com.matchitcampus.app.data.remote.mapper.toFirestoreMap
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.tasks.await

private const val TAG = "MatchIt/ClaimRemote"

/**
 * Reads and writes the `claims` collection.
 *
 * Claims are private: the security rules only let the claimant and the finder read a claim.
 * So instead of one query over all claims, we run two that the rules allow:
 * "claims I made" (`claimantId == me`) and "claims on my items" (`finderId == me`).
 * A user only has a handful of claims, so listening to all of them costs few reads.
 */
class ClaimRemoteDataSource(private val firestore: FirebaseFirestore) {

    private val claims get() = firestore.collection(COLLECTION)

    /** Creates or updates `claims/{id}`. The rules decide which status changes are allowed. */
    suspend fun upsert(claim: ClaimDto) {
        val data = claim.toFirestoreMap() + (Fields.UPDATED_AT to FieldValue.serverTimestamp())
        claims.document(claim.id).set(data).await()
    }

    /**
     * Every claim where [uid] is the claimant or the finder, as one live list.
     *
     * `combine` waits for both listeners' first results, then emits the merged list again
     * whenever either side changes.
     */
    fun observeClaimsFor(uid: String): Flow<List<ClaimDto>> =
        combine(
            observeQuery(claims.whereEqualTo(Fields.CLAIMANT_ID, uid), label = "outgoing"),
            observeQuery(claims.whereEqualTo(Fields.FINDER_ID, uid), label = "incoming"),
        ) { outgoing, incoming -> (outgoing + incoming).distinctBy { it.id } }

    /** Wraps one Firestore listener in a Flow. The listener is removed when collection stops. */
    private fun observeQuery(query: Query, label: String): Flow<List<ClaimDto>> = callbackFlow {
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Claims listener ($label) failed", error)
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val parsed = snapshot.documents.mapNotNull { doc ->
                    doc.data?.let { claimDtoFromMap(doc.id, it) }
                        ?: null.also { Log.w(TAG, "Skipping malformed claim document ${doc.id}") }
                }
                trySend(parsed)
            }
        }
        Log.d(TAG, "Claims listener ($label) attached")
        awaitClose {
            registration.remove()
            Log.d(TAG, "Claims listener ($label) removed")
        }
    }

    companion object {
        const val COLLECTION = "claims"
    }
}
