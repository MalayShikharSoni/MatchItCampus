package com.matchitcampus.app.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.matchitcampus.app.data.remote.dto.UserDto
import com.matchitcampus.app.data.remote.mapper.toFirestoreMap
import com.matchitcampus.app.data.remote.mapper.userDtoFromMap
import kotlinx.coroutines.tasks.await

/** Reads and writes `users/{uid}`, the public profile of each account. */
class UserRemoteDataSource(private val firestore: FirebaseFirestore) {

    suspend fun save(uid: String, user: UserDto) {
        firestore.collection(COLLECTION).document(uid).set(user.toFirestoreMap()).await()
    }

    /** @return the profile, or null if the document doesn't exist (or is malformed). */
    suspend fun get(uid: String): UserDto? {
        val snapshot = firestore.collection(COLLECTION).document(uid).get().await()
        return snapshot.data?.let { userDtoFromMap(it) }
    }

    companion object {
        const val COLLECTION = "users"
    }
}
