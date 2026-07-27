package com.calistapp.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [SessionEntity::class, ExerciseEntity::class], version = 4, exportSchema = false)
abstract class CalistDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun exerciseDao(): ExerciseDao
}

/**
 * Adds the workout plan and per-set log to stored sessions.
 *
 * Written out rather than leaning on destructive fallback: both columns are additive with empty
 * defaults, and wiping a training history to add two nullable-ish fields would be a poor trade in
 * an app whose whole point is tracking progress over time.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sessions ADD COLUMN planJson TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE sessions ADD COLUMN setLogsJson TEXT NOT NULL DEFAULT ''")
    }
}
