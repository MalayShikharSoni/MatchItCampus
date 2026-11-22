package com.matchitcampus.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.matchitcampus.app.data.local.entity.CampusZoneEntity
import kotlinx.coroutines.flow.Flow

/** Database access for the `campus_zones` table. */
@Dao
interface CampusZoneDao {

    @Upsert
    suspend fun upsert(zone: CampusZoneEntity)

    @Upsert
    suspend fun upsertAll(zones: List<CampusZoneEntity>)

    /** Every zone in name order, for the zone picker and the map. */
    @Query("SELECT * FROM campus_zones ORDER BY name")
    fun observeAll(): Flow<List<CampusZoneEntity>>

    @Query("SELECT * FROM campus_zones ORDER BY name")
    suspend fun getAll(): List<CampusZoneEntity>

    @Query("SELECT * FROM campus_zones WHERE id = :id")
    suspend fun getById(id: String): CampusZoneEntity?

    /** Used by first-run seeding: 0 means the table has never been filled. */
    @Query("SELECT COUNT(*) FROM campus_zones")
    suspend fun count(): Int
}
