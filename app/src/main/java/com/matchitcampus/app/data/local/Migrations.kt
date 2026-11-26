package com.matchitcampus.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/*
 * Database migrations: how to turn a database file made by an older app version into the
 * current schema without losing anything.
 *
 * Room compares the version stored in the file with `AppDatabase.version`. If the file is
 * older, it runs the matching migrations in order, then checks that the result matches the
 * schema it expects (exported to app/schemas/). There's deliberately no destructive fallback,
 * so a missing or wrong migration fails loudly in testing instead of wiping users' posts.
 */

/**
 * Version 1 → 2: items get an optional colour.
 *
 * `ADD COLUMN` changes the table in place: SQLite appends the column and every existing row
 * reads NULL for it, so no data is copied. That only works because the column is nullable
 * with no default, which is exactly what Room expects for `val color: String?`.
 * `MigrationTest` (instrumented) checks this against the exported schemas `1.json` and `2.json`.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE items ADD COLUMN color TEXT")
    }
}

/** Every migration, in order. `AppDatabase.build` registers them and so does the migration test. */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
