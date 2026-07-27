package com.calistapp.app.data.exercise

import com.calistapp.app.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Populates and maintains the exercise gallery by combining providers, non-destructively:
 *  1. the authored full [CalisthenicsCatalog] (bundled, rich, offline) is always seeded;
 *  2. the open free-exercise-db is fetched ONCE to seed breadth (only inserts exercises not already
 *     stored, so it never clobbers existing content on later launches);
 *  3. the hand-authored rich overlays in [ExerciseEnrichments] are merged onto the stored rows every
 *     launch — authoritative and idempotent, so newly-authored batches show up automatically.
 *
 * Exercises the user AI-enriches (no overlay) are left untouched once the base is seeded.
 */
@Singleton
class ExerciseSyncManager @Inject constructor(
    private val repository: ExerciseRepository,
    private val remoteSource: FreeExerciseDbSource,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            // 1. Authored full catalog (calisthenics) — authoritative.
            runCatching { repository.upsertAll(CalisthenicsCatalog.exercises) }
            val authoredFullIds = CalisthenicsCatalog.exercises.map { it.id }.toSet()

            // 2. Seed dataset breadth once (only add exercises not already present).
            val existing = runCatching { repository.currentById() }.getOrDefault(emptyMap())
            if (existing.size < BASE_SEED_THRESHOLD) {
                val remote = remoteSource.fetchAll()
                if (remote.isNotEmpty()) {
                    val toInsert = remote.filter { it.id !in authoredFullIds && it.id !in existing }
                    runCatching { repository.upsertAll(toInsert) }
                }
            }

            // 3. Merge authored rich overlays onto the stored rows — but never clobber a row the
            //    user has hand-edited (tagged "user-edited" by the editor).
            val current = runCatching { repository.currentById() }.getOrDefault(emptyMap())
            val overlaid = ExerciseEnrichments.all.mapNotNull { enrichment ->
                current[enrichment.id]
                    ?.takeUnless { EDITED_TAG in it.tags }
                    ?.let(enrichment::applyTo)
            }
            if (overlaid.isNotEmpty()) runCatching { repository.upsertAll(overlaid) }
        }
    }

    companion object {
        /** Below this many stored exercises we (re)fetch the dataset to seed breadth. */
        private const val BASE_SEED_THRESHOLD = 300

        /** Rows carrying this tag were hand-edited by the user; sync leaves them alone. */
        const val EDITED_TAG = "user-edited"
    }
}
