package com.matchitcampus.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The signed-in user's profile, cached locally so screens can show it offline.
 *
 * Phase 3's AuthRepository fills it after sign-in, and it's cleared on logout.
 */
@Entity(tableName = "users")
data class UserEntity(
    /** The Firebase Auth uid. */
    @PrimaryKey val uid: String,
    val name: String,
    val email: String,
    /** Optional LPU registration number. */
    val regNo: String?,
)
