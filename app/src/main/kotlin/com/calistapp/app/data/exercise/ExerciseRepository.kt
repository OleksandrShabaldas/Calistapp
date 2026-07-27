package com.calistapp.app.data.exercise

import com.calistapp.app.data.local.ExerciseDao
import com.calistapp.app.data.local.ExerciseEntity
import com.calistapp.core.model.Exercise
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExerciseRepository @Inject constructor(
    private val dao: ExerciseDao,
    private val json: Json,
) {
    fun observeAll(): Flow<List<Exercise>> = dao.observeAll().map { list -> list.map(::toDomain) }

    fun observe(id: String): Flow<Exercise?> = dao.observeById(id).map { it?.let(::toDomain) }

    suspend fun count(): Int = dao.count()

    /** Snapshot of every stored exercise keyed by id — used by sync to merge overlays. */
    suspend fun currentById(): Map<String, Exercise> = dao.getAll().associate { it.id to toDomain(it) }

    suspend fun upsertAll(exercises: List<Exercise>) = dao.upsertAll(exercises.map(::toEntity))

    /** Add or update a single exercise (used by the manual add/edit editor). */
    suspend fun upsert(exercise: Exercise) = dao.upsertAll(listOf(toEntity(exercise)))

    suspend fun delete(id: String) = dao.delete(id)

    private fun toEntity(e: Exercise) = ExerciseEntity(
        id = e.id,
        name = e.name,
        bodyPart = e.bodyPart.name,
        difficulty = e.difficulty.name,
        isBodyweight = e.isBodyweight,
        isCalisthenics = e.isCalisthenics,
        json = json.encodeToString(Exercise.serializer(), e),
    )

    private fun toDomain(en: ExerciseEntity): Exercise =
        json.decodeFromString(Exercise.serializer(), en.json)
}
