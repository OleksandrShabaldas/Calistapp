package com.calistapp.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * A recurring weekly plan: run saved workout [savedWorkoutId] every [dayOfWeek] (1=Mon..7=Sun, the
 * `java.time.DayOfWeek.value` numbering). Several rows put one workout on several weekdays, and the
 * same weekday can carry more than one workout. Deleting the saved workout should delete its rules.
 */
@Entity(tableName = "scheduled_workouts")
data class ScheduledWorkoutEntity(
    @PrimaryKey val id: String,
    val savedWorkoutId: String,
    val dayOfWeek: Int,
)

/**
 * A one-week change to the recurring plan — "move this Monday's session to Thursday", or "skip it
 * this week". Scoped to [weekStartMs] (Monday 00:00 local, epoch millis) so it never affects other
 * weeks. [savedWorkoutId] is denormalised so a moved occurrence still resolves even if its recurring
 * rule is later deleted.
 */
@Entity(tableName = "schedule_overrides")
data class ScheduleOverrideEntity(
    @PrimaryKey val id: String,
    val weekStartMs: Long,
    /** [com.calistapp.app.data.session.ScheduleAction] name — SKIP or MOVE. */
    val action: String,
    val sourceDayOfWeek: Int,
    /** MOVE destination weekday; null for SKIP. */
    val targetDayOfWeek: Int?,
    val savedWorkoutId: String,
)

@Dao
interface ScheduleDao {

    @Query("SELECT * FROM scheduled_workouts")
    fun observeRules(): Flow<List<ScheduledWorkoutEntity>>

    @Query("SELECT * FROM scheduled_workouts WHERE savedWorkoutId = :savedWorkoutId")
    fun observeRulesForWorkout(savedWorkoutId: String): Flow<List<ScheduledWorkoutEntity>>

    @Upsert
    suspend fun upsertRule(rule: ScheduledWorkoutEntity)

    @Query("DELETE FROM scheduled_workouts WHERE id = :id")
    suspend fun deleteRule(id: String)

    @Query("DELETE FROM scheduled_workouts WHERE savedWorkoutId = :savedWorkoutId")
    suspend fun deleteRulesForWorkout(savedWorkoutId: String)

    @Query("SELECT * FROM schedule_overrides")
    fun observeOverrides(): Flow<List<ScheduleOverrideEntity>>

    @Upsert
    suspend fun upsertOverride(override: ScheduleOverrideEntity)

    @Query("DELETE FROM schedule_overrides WHERE id = :id")
    suspend fun deleteOverride(id: String)

    /** Housekeeping: this-week overrides are meaningless once their week is in the past. */
    @Query("DELETE FROM schedule_overrides WHERE weekStartMs < :cutoffMs")
    suspend fun pruneOverridesBefore(cutoffMs: Long)
}
