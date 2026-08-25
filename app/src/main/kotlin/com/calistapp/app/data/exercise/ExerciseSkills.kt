package com.calistapp.app.data.exercise

import com.calistapp.core.model.Skills

/**
 * Registry of every hand-authored [Skills] profile, aggregated from the per-batch files — the same
 * pattern as [ExerciseEnrichments], kept **separate** rather than folded into it so authoring skills
 * never touches the 56 already-finished enrichment/video-library files. Add a new `skillsBatchNN`
 * file and append it here to grow the authored set.
 *
 * Keyed by real exercise ids across all three sources: free-exercise-db (via [ExerciseEnrichments]'
 * ids), [CalisthenicsCatalog], and [VideoLibraryCatalog] (`mw_…` ids). [ExerciseSyncManager] merges
 * this onto whichever of those rows already exists, every launch, idempotently.
 */
object ExerciseSkills {

    val byId: Map<String, Skills> = buildMap {
        putAll(skillsBatch01)
        putAll(skillsBatch02)
        putAll(skillsBatch03)
        putAll(skillsBatch04)
        putAll(skillsBatch05)
        putAll(skillsBatch06)
        putAll(skillsBatch07)
        putAll(skillsBatch08)
        putAll(skillsBatch09)
        putAll(skillsBatch10)
        putAll(skillsBatch11)
        putAll(skillsBatch12)
        putAll(skillsBatch13)
        putAll(skillsBatch14)
        putAll(skillsBatch15)
        putAll(skillsBatch16)
        putAll(skillsBatch17)
        putAll(skillsBatch18)
        putAll(skillsBatch19)
        putAll(skillsBatch20)
        putAll(skillsBatch21)
        putAll(skillsBatch22)
        putAll(skillsBatch23)
        putAll(skillsBatch24)
        putAll(skillsBatch25)
        putAll(skillsBatch26)
        putAll(skillsBatch27)
        putAll(skillsBatch28)
        putAll(skillsBatch29)
        putAll(skillsBatch30)
        putAll(skillsBatch31)
        putAll(skillsBatch32)
        putAll(skillsBatch33)
        putAll(skillsBatch34)
        putAll(skillsBatch35)
        putAll(skillsBatch36)
        putAll(skillsBatch37)
        putAll(skillsBatch38)
        putAll(skillsBatch39)
        putAll(skillsBatch40)
        putAll(skillsBatch41)
        putAll(skillsBatch42)
        putAll(skillsBatch43)
        putAll(skillsBatch44)
        putAll(skillsBatch45)
        putAll(skillsBatch46)
        putAll(skillsBatch47)
        putAll(skillsBatch48)
        putAll(skillsBatch49)
        putAll(skillsBatch50)
        putAll(skillsBatch51)
        putAll(skillsBatch52)
        putAll(skillsBatch53)
        putAll(skillsBatch54)
        putAll(skillsBatch55)
        putAll(skillsBatch56)
        putAll(skillsBatch57)
    }
}
