package com.matchitcampus.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.matchitcampus.app.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

/** Database access for the cached profile of the signed-in user (filled from Phase 3). */
@Dao
interface UserDao {

    @Upsert
    suspend fun upsert(user: UserEntity)

    @Query("SELECT * FROM users WHERE uid = :uid")
    fun observe(uid: String): Flow<UserEntity?>

    /** Called on logout. */
    @Query("DELETE FROM users")
    suspend fun deleteAll()
}
