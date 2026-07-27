package com.calistapp.app.data.exercise

import com.calistapp.app.di.IoDispatcher
import com.calistapp.core.model.BodyPart
import com.calistapp.core.model.Difficulty
import com.calistapp.core.model.Exercise
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches the open-source, public-domain free-exercise-db (873 exercises, no API key) for gallery
 * breadth. Images come from the same repo via jsDelivr's CDN. This is one provider behind the
 * repository — the authored [CalisthenicsCatalog] is layered on top for the curated set.
 */
@Singleton
class FreeExerciseDbSource @Inject constructor(
    private val okHttp: OkHttpClient,
    private val json: Json,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend fun fetchAll(): List<Exercise> = withContext(io) {
        val request = Request.Builder().url(DATA_URL).build()
        okHttp.newCall(request).execute().use { resp ->
            val body = resp.body?.string()
            if (!resp.isSuccessful || body == null) return@use emptyList()
            runCatching {
                json.decodeFromString(ListSerializer(FreeExerciseDto.serializer()), body)
                    .map { it.toExercise() }
            }.getOrDefault(emptyList())
        }
    }

    companion object {
        const val DATA_URL = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/dist/exercises.json"
        const val IMAGE_BASE = "https://cdn.jsdelivr.net/gh/yuhonas/free-exercise-db@main/exercises/"
    }
}

@Serializable
private data class FreeExerciseDto(
    val id: String,
    val name: String,
    val force: String? = null,
    val level: String = "beginner",
    val mechanic: String? = null,
    val equipment: String? = null,
    val primaryMuscles: List<String> = emptyList(),
    val secondaryMuscles: List<String> = emptyList(),
    val instructions: List<String> = emptyList(),
    val category: String = "strength",
    val images: List<String> = emptyList(),
) {
    fun toExercise(): Exercise = Exercise(
        id = id,
        name = name,
        bodyPart = bodyPartFor(primaryMuscles.firstOrNull(), category),
        category = category,
        difficulty = difficultyFor(level),
        primaryMuscles = primaryMuscles.map { it.replaceFirstChar(Char::uppercase) },
        secondaryMuscles = secondaryMuscles.map { it.replaceFirstChar(Char::uppercase) },
        equipment = equipment?.let { listOf(it.replaceFirstChar(Char::uppercase)) } ?: emptyList(),
        force = force,
        mechanic = mechanic,
        efficiency = efficiencyFor(mechanic),
        problematicAreas = problematicAreasFor(primaryMuscles.firstOrNull(), force),
        imageUrls = images.map { FreeExerciseDbSource.IMAGE_BASE + it },
        instructions = instructions,
        tags = listOfNotNull(category, mechanic, equipment).distinct(),
        source = "free-exercise-db",
        isCalisthenics = equipment.equals("body only", ignoreCase = true),
    )
}

private fun difficultyFor(level: String): Difficulty = when (level.lowercase()) {
    "beginner" -> Difficulty.BEGINNER
    "intermediate" -> Difficulty.INTERMEDIATE
    else -> Difficulty.ADVANCED // "expert"
}

private fun efficiencyFor(mechanic: String?): Int = when (mechanic?.lowercase()) {
    "compound" -> 4
    "isolation" -> 2
    else -> 3
}

/**
 * A conservative, rules-based guess at which joints/areas an exercise tends to stress, derived from
 * the primary muscle and force direction. Honest heuristic (no fabricated per-exercise specifics);
 * the AI enrichment pass can refine it.
 */
private fun problematicAreasFor(primaryMuscle: String?, force: String?): List<String> {
    val base = when (primaryMuscle?.lowercase()) {
        "chest" -> listOf("Shoulders", "Elbows")
        "shoulders" -> listOf("Shoulders", "Neck")
        "triceps", "biceps" -> listOf("Elbows")
        "forearms" -> listOf("Wrists", "Elbows")
        "lats", "middle back" -> listOf("Shoulders")
        "lower back" -> listOf("Lower back")
        "traps", "neck" -> listOf("Neck")
        "abdominals" -> listOf("Neck", "Lower back")
        "quadriceps" -> listOf("Knees")
        "hamstrings" -> listOf("Lower back", "Knees")
        "glutes", "abductors", "adductors" -> listOf("Lower back", "Hips")
        "calves" -> listOf("Ankles")
        else -> emptyList()
    }
    // Overhead/vertical pressing adds wrist load.
    return if (force == "push" && primaryMuscle?.lowercase() == "shoulders") (base + "Wrists").distinct() else base
}

/** Map the primary muscle (and category) onto a browsable body-part bucket. */
private fun bodyPartFor(primaryMuscle: String?, category: String): BodyPart {
    if (category.equals("cardio", true)) return BodyPart.CARDIO
    return when (primaryMuscle?.lowercase()) {
        "chest" -> BodyPart.CHEST
        "lats", "middle back", "lower back", "traps", "neck" -> BodyPart.BACK
        "shoulders" -> BodyPart.SHOULDERS
        "biceps", "triceps", "forearms" -> BodyPart.ARMS
        "abdominals" -> BodyPart.CORE
        "quadriceps", "hamstrings", "calves", "abductors", "adductors" -> BodyPart.LEGS
        "glutes" -> BodyPart.GLUTES
        null -> BodyPart.OTHER
        else -> BodyPart.OTHER
    }
}
