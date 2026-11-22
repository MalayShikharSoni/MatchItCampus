package com.matchitcampus.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.matchitcampus.app.data.local.dao.CampusZoneDao
import com.matchitcampus.app.data.local.dao.ClaimDao
import com.matchitcampus.app.data.local.dao.ItemDao
import com.matchitcampus.app.data.local.dao.MatchDao
import com.matchitcampus.app.data.local.dao.UserDao
import com.matchitcampus.app.data.local.entity.CampusZoneEntity
import com.matchitcampus.app.data.local.entity.ClaimEntity
import com.matchitcampus.app.data.local.entity.ItemEntity
import com.matchitcampus.app.data.local.entity.MatchEntity
import com.matchitcampus.app.data.local.entity.UserEntity

/**
 * The app's Room database, and its single source of truth.
 *
 * - `version` goes up by one whenever the schema changes, and each change needs a
 *   `Migration` (the first one comes in Phase 7). There is deliberately no destructive
 *   fallback: if a migration is missing, the app crashes in testing instead of silently
 *   deleting users' data.
 * - `exportSchema = true` writes each version's schema to `app/schemas/` (set up in
 *   `app/build.gradle.kts`). Those JSON files are committed, and the migration test uses them.
 * - [Converters] tells Room how to store enums and embeddings.
 *
 * Only one instance should exist per process, because each instance has its own connections
 * and its own change tracking, and that tracking is what makes Flow queries re-emit.
 * [com.matchitcampus.app.di.AppContainer] creates it once with [build].
 */
@Database(
    entities = [
        ItemEntity::class,
        CampusZoneEntity::class,
        ClaimEntity::class,
        UserEntity::class,
        MatchEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun itemDao(): ItemDao
    abstract fun campusZoneDao(): CampusZoneDao
    abstract fun claimDao(): ClaimDao
    abstract fun userDao(): UserDao
    abstract fun matchDao(): MatchDao

    companion object {
        const val DATABASE_NAME = "matchit.db"

        /** Builds the on-disk database. Call it once (AppContainer does) and share the result. */
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DATABASE_NAME)
                .build()
    }
}
