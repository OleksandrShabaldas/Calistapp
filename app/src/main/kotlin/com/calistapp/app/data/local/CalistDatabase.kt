package com.calistapp.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SessionEntity::class,
        ExerciseEntity::class,
        WorkoutTemplateEntity::class,
        WeightEntryEntity::class,
    ],
    version = 7,
    exportSchema = false,
)
abstract class CalistDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutTemplateDao(): WorkoutTemplateDao
    abstract fun weightDao(): WeightDao
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

/** Adds the rating of perceived exertion. Nullable — sessions recorded before it simply have none. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sessions ADD COLUMN rpe INTEGER")
    }
}

/** Adds the bodyweight log. A new table only — nothing existing is touched. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS weight_entries (
                dayMs INTEGER NOT NULL PRIMARY KEY,
                weightKg REAL NOT NULL
            )
            """.trimIndent(),
        )
    }
}

/** Adds saved workouts. A new table only — nothing existing is touched. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS workout_templates (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                planJson TEXT NOT NULL,
                createdMs INTEGER NOT NULL,
                lastUsedMs INTEGER
            )
            """.trimIndent(),
        )
    }
}
