package com.calistapp.app.data.exercise

/**
 * Registry of every hand-authored coaching overlay, aggregated from the per-batch files. Add a new
 * `enrichmentBatchNN` file and append it here to grow the authored library.
 */
object ExerciseEnrichments {

    val all: List<ExerciseEnrichment> = buildList {
        addAll(enrichmentBatch01)
        addAll(enrichmentBatch02)
        addAll(enrichmentBatch03)
        addAll(enrichmentBatch04)
        addAll(enrichmentBatch05)
        addAll(enrichmentBatch06)
        addAll(enrichmentBatch07)
        addAll(enrichmentBatch08)
        addAll(enrichmentBatch09)
        addAll(enrichmentBatch10)
        addAll(enrichmentBatch11)
        addAll(enrichmentBatch12)
        addAll(enrichmentBatch13)
        addAll(enrichmentBatch14)
        addAll(enrichmentBatch15)
        addAll(enrichmentBatch16)
        addAll(enrichmentBatch17)
        addAll(enrichmentBatch18)
    }

    val byId: Map<String, ExerciseEnrichment> = all.associateBy { it.id }
}
