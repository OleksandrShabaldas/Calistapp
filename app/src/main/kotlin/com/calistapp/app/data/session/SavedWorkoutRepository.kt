package com.calistapp.app.data.session

import com.calistapp.app.data.local.WorkoutTemplateDao
import com.calistapp.app.data.local.WorkoutTemplateEntity
import com.calistapp.core.model.SavedWorkout
import com.calistapp.core.model.WorkoutPlan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Workouts kept for reuse.
 *
 * Until this existed, every session meant rebuilding a plan from a gallery of 834 exercises — which
 * for anyone training the same programme twice a week is the app asking them to do the same work
 * over and over. A saved workout is just a named plan; loading one copies it into the draft, so
 * editing it afterwards changes the session and not the saved copy unless you save again.
 */
@Singleton
class SavedWorkoutRepository @Inject constructor(
    private val dao: WorkoutTemplateDao,
    private val json: Json,
) {
    val saved: Flow<List<SavedWorkout>> =
        dao.observeAll().map { list -> list.mapNotNull { it.toDomain() } }

    /** Store [plan] under [name], replacing [id] when re-saving an existing workout. */
    suspend fun save(name: String, plan: WorkoutPlan, id: String? = null) {
        val now = System.currentTimeMillis()
        dao.upsert(
            WorkoutTemplateEntity(
                id = id ?: UUID.randomUUID().toString(),
                name = name.ifBlank { "Workout" },
                planJson = json.encodeToString(WorkoutPlan.serializer(), plan.copy(name = name)),
                createdMs = now,
                lastUsedMs = null,
            ),
        )
    }

    suspend fun markUsed(id: String) = dao.markUsed(id, System.currentTimeMillis())

    suspend fun delete(id: String) = dao.delete(id)

    /**
     * A stored row that no longer parses is skipped rather than crashing the list. The plan format
     * only ever gains fields with defaults, so this should stay theoretical — but a saved workout
     * failing to decode must not take the whole planner down with it.
     */
    private fun WorkoutTemplateEntity.toDomain(): SavedWorkout? = runCatching {
        SavedWorkout(
            id = id,
            name = name,
            plan = json.decodeFromString(WorkoutPlan.serializer(), planJson),
            createdMs = createdMs,
            lastUsedMs = lastUsedMs,
        )
    }.getOrNull()
}
