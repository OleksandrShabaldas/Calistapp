package com.calistapp.app.data.exercise

import com.calistapp.core.model.Faq

/**
 * Hand-authored FAQ overlay, keyed to a real exercise id.
 *
 * Kept as its own overlay — separate from the coaching enrichments and the skills profiles — for the
 * same reason those are split from each other: authoring one kind of content should never mean editing
 * the (large, already-finished) files that hold another. Drop new entries into [byId] (or split them
 * into `FaqsBatchNN` files and concatenate here, the way the skills batches do) and they show up on
 * the next launch; [ExerciseSyncManager] merges them onto the stored rows **additively**, so an
 * authored set never wipes a question a user asked the app to answer (those carry [Faq.generated] =
 * true and are preserved).
 *
 * Example (ids are free-exercise-db / calisthenics catalog ids — the same ones the enrichment and
 * skills overlays use):
 *
 * ```
 * "Machine_Roll-Outs" to listOf(
 *     Faq("Will it hurt my back?", "Not if you brace and keep the ribs down — shorten the range if you feel the lower back."),
 *     Faq("No machine — a substitute?", "A free ab wheel or a barbell roll-out trains the same anti-extension pattern."),
 * ),
 * ```
 */
object ExerciseFaqs {
    /** exercise id → its authored Q&A. Empty until the curated set is written; see the file docs. */
    val byId: Map<String, List<Faq>> = emptyMap()
}
