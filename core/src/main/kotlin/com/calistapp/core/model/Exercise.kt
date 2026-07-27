package com.calistapp.core.model

import kotlinx.serialization.Serializable

/** The main muscle region an exercise trains — the primary way users browse the gallery. */
@Serializable
enum class BodyPart(val displayName: String) {
    CHEST("Chest"),
    BACK("Back"),
    SHOULDERS("Shoulders"),
    ARMS("Arms"),
    CORE("Core"),
    LEGS("Legs"),
    GLUTES("Glutes"),
    FULL_BODY("Full body"),
    CARDIO("Cardio"),
    OTHER("Other"),
}

@Serializable
enum class Difficulty(val displayName: String) {
    BEGINNER("Beginner"),
    INTERMEDIATE("Intermediate"),
    ADVANCED("Advanced"),
}

/**
 * A single exercise in the gallery. Basic fields (muscles, equipment, images, instructions) come
 * from the open free-exercise-db; the richer coaching fields ([overview], [commonMistakes], [tips],
 * [problematicAreas], [efficiency]) are authored by Calistapp for the curated calisthenics set.
 */
@Serializable
data class Exercise(
    val id: String,
    val name: String,
    val bodyPart: BodyPart,
    val category: String = "strength",
    val difficulty: Difficulty = Difficulty.BEGINNER,
    val primaryMuscles: List<String> = emptyList(),
    val secondaryMuscles: List<String> = emptyList(),
    val equipment: List<String> = emptyList(),
    /** push / pull / static */
    val force: String? = null,
    /** compound / isolation */
    val mechanic: String? = null,
    /** Joints/areas this movement can stress (e.g. wrists, lower back, shoulders). */
    val problematicAreas: List<String> = emptyList(),
    /** Strength built vs energy required, 1 (poor) .. 5 (excellent). 0 = unrated. */
    val efficiency: Int = 0,
    /** Animated GIF or start/finish frames — loaded from a CDN. */
    val imageUrls: List<String> = emptyList(),
    val instructions: List<String> = emptyList(),
    val commonMistakes: List<String> = emptyList(),
    val tips: List<String> = emptyList(),
    /** Who it's for and how effective it really is. */
    val overview: String = "",
    val tags: List<String> = emptyList(),
    val source: String = "",
    val isCalisthenics: Boolean = false,
) {
    val isBodyweight: Boolean
        get() = equipment.isEmpty() || equipment.any { it.equals("body only", true) || it.contains("bar", true) }
}
