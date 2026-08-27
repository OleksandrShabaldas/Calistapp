package com.calistapp.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * A workout you built once and want back.
 *
 * The plan is stored as the same JSON a session carries, so a saved workout and a recorded one
 * describe a plan identically — loading one is a copy, not a translation.
 */
@Entity(tableName = "workout_templates")
data class WorkoutTemplateEntity(
    @PrimaryKey val id: String,
    val name: String,
    val planJson: String,
    val createdMs: Long,
    /** Null until it's been run. Drives the ordering, so what you use stays at the top. */
    val lastUsedMs: Long?,
)

@Dao
interface WorkoutTemplateDao {

    /** Most recently used first, then newest. Never run sorts below everything that has been. */
    @Query("SELECT * FROM workout_templates ORDER BY COALESCE(lastUsedMs, 0) DESC, createdMs DESC")
    fun observeAll(): Flow<List<WorkoutTemplateEntity>>

    @Upsert
    suspend fun upsert(template: WorkoutTemplateEntity)

    /** Update just the content of an existing workout, keeping its created/last-used timestamps. */
    @Query("UPDATE workout_templates SET name = :name, planJson = :planJson WHERE id = :id")
    suspend fun updateContent(id: String, name: String, planJson: String)

    @Query("UPDATE workout_templates SET lastUsedMs = :usedMs WHERE id = :id")
    suspend fun markUsed(id: String, usedMs: Long)

    @Query("DELETE FROM workout_templates WHERE id = :id")
    suspend fun delete(id: String)
}
