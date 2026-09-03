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
        StepDayEntity::class,
    ],
    version = 8,
    exportSchema = false,
)
abstract class CalistDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutTemplateDao(): WorkoutTemplateDao
    abstract fun weightDao(): WeightDao
    abstract fun stepDayDao(): StepDayDao
}

/**
 * The FitPal bridge. Adds `sessions.fitpalSyncedAt` (a nullable "last pushed to FitPal" stamp that
 * drives the auto-retry + manual transfer) and the `step_days` table that holds steps imported FROM
 * FitPal (with FitPal's already-trimmed step-calories). Both additive; nothing existing is touched.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sessions ADD COLUMN fitpalSyncedAt INTEGER")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS step_days (
                date TEXT NOT NULL PRIMARY KEY,
                steps INTEGER NOT NULL,
                calories REAL NOT NULL,
                reductionPercent INTEGER NOT NULL,
                importedAtMs INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }
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
