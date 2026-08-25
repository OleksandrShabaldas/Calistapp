package com.calistapp.app.data.exercise

import com.calistapp.core.model.Exercise
import com.calistapp.core.model.Skills

/**
 * A compact, hand-authored coaching overlay keyed to a real free-exercise-db id. It carries only
 * the fields no dataset provides (overview, mistakes, tips, optionally refined problematic areas /
 * efficiency, and the [skills] attribute profile); the muscles, equipment, instructions and images
 * come from the dataset entry it's merged onto at sync time. This keeps authoring all ~873 exercises
 * manageable and free of duplication. See [ExerciseEnrichments].
 */
data class ExerciseEnrichment(
    val id: String,
    val overview: String,
    val commonMistakes: List<String>,
    val tips: List<String>,
    val problematicAreas: List<String> = emptyList(),
    val efficiency: Int = 0,
    /** The training-attribute bars on the detail screen. Null when this movement isn't rated yet. */
    val skills: Skills? = null,
) {
    fun applyTo(base: Exercise): Exercise = base.copy(
        overview = overview.ifBlank { base.overview },
        commonMistakes = commonMistakes.ifEmpty { base.commonMistakes },
        tips = tips.ifEmpty { base.tips },
        problematicAreas = problematicAreas.ifEmpty { base.problematicAreas },
        efficiency = if (efficiency > 0) efficiency else base.efficiency,
        skills = skills ?: base.skills,
        tags = (base.tags + "authored").distinct(),
    )
}
