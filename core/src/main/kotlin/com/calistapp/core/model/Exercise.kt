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

/** What kind of asset a piece of [ExerciseMedia] points at, so the UI knows how to render it. */
@Serializable
enum class MediaType {
    /** A still frame or an animated GIF — rendered by Coil (the app's loader decodes GIFs). */
    IMAGE,

    /** An MP4/WebM clip — rendered by the Media3 video player. */
    VIDEO,
}

/**
 * One swipeable demonstration of an exercise. An exercise can carry several — e.g. an animated GIF
 * plus real-person video shot from a couple of angles — and the detail screen pages between them.
 */
@Serializable
data class ExerciseMedia(
    val url: String,
    val type: MediaType,
    /** Short label shown on the page (e.g. "Front", "Side", "Animation"); null hides it. */
    val angle: String? = null,
    /** Where it came from (e.g. "github", "musclewiki"); informational, for provenance/debugging. */
    val source: String = "",
)

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
    /** Animated GIF or start/finish frames — loaded from a CDN. Used for the gallery thumbnail. */
    val imageUrls: List<String> = emptyList(),
    /**
     * Richer, swipeable demonstrations for the detail screen — real-person video angles and/or an
     * animated GIF. Empty for exercises that only have [imageUrls]; the carousel falls back to those.
     */
    val media: List<ExerciseMedia> = emptyList(),
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

    /** True when at least one real-person video demonstration is attached. */
    val hasVideo: Boolean
        get() = media.any { it.type == MediaType.VIDEO }
}
