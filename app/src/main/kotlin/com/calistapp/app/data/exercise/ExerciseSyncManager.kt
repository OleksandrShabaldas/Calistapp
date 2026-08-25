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
 *     launch — authoritative and idempotent, so newly-authored batches show up automatically;
 *  4. the authored full [VideoLibraryCatalog] (exercises the video library covers that no other
 *     source has) is seeded the same way as step 1 — authoritative, but skips user-edited rows.
 *  5. the hand-authored [ExerciseSkills] profiles are merged onto the stored rows every launch —
 *     kept as a separate overlay from step 3's coaching enrichments so authoring skills never
 *     touches the (large, already-finished) enrichment/video-library files.
 *  6. dataset ids in [supersededByVideoLibrary] — confirmed duplicates of a video-library entry
 *     covering the same movement — are excluded from step 2's seeding and deleted if a prior launch
 *     already inserted them, so the gallery shows the richer video-library version once, not both.
 *     The mirror case, [supersededByCalisthenics], does the same for video-library ids that
 *     duplicate a hand-authored calisthenics entry — the calisthenics version wins and carries the
 *     video (merged into `exercise_videos.json` under its own id), not the auto-generated one.
 *
 * Exercises the user AI-enriches (no overlay) are left untouched once the base is seeded.
 */
@Singleton
class ExerciseSyncManager @Inject constructor(
    private val repository: ExerciseRepository,
    private val remoteSource: FreeExerciseDbSource,
    private val videoCatalog: ExerciseVideoCatalog,
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

            // 2. Seed dataset breadth once (only add exercises not already present) — skipping ids
            //    a video-library entry already covers, so the two never get seeded side by side.
            val existing = runCatching { repository.currentById() }.getOrDefault(emptyMap())
            if (existing.size < BASE_SEED_THRESHOLD) {
                val remote = remoteSource.fetchAll()
                if (remote.isNotEmpty()) {
                    val toInsert = remote.filter {
                        it.id !in authoredFullIds && it.id !in existing && it.id !in supersededByVideoLibrary
                    }
                    runCatching { repository.upsertAll(toInsert) }
                }
            }

            // Remove any of those duplicates a launch before this one already inserted — unless the
            // user has since hand-edited that specific row, which always wins.
            val toRemove = existing.filterKeys { it in supersededByVideoLibrary }
                .filterValues { EDITED_TAG !in it.tags }
                .keys
            toRemove.forEach { id -> runCatching { repository.delete(id) } }

            // 3. Merge authored rich overlays onto the stored rows — but never clobber a row the
            //    user has hand-edited (tagged "user-edited" by the editor).
            val current = runCatching { repository.currentById() }.getOrDefault(emptyMap())
            val overlaid = ExerciseEnrichments.all.mapNotNull { enrichment ->
                current[enrichment.id]
                    ?.takeUnless { EDITED_TAG in it.tags }
                    ?.let(enrichment::applyTo)
            }
            if (overlaid.isNotEmpty()) runCatching { repository.upsertAll(overlaid) }

            // 4. Seed exercises authored purely from the video library (no dataset counterpart) —
            //    authoritative like step 1, but skips rows the user has since hand-edited, and skips
            //    ids a hand-authored calisthenics entry already covers (see supersededByCalisthenics).
            val toSeedVideoLib = VideoLibraryCatalog.exercises.filter { ex ->
                ex.id !in supersededByCalisthenics &&
                    (current[ex.id]?.let { EDITED_TAG !in it.tags } ?: true)
            }
            if (toSeedVideoLib.isNotEmpty()) runCatching { repository.upsertAll(toSeedVideoLib) }

            // Remove any video-library duplicates of a calisthenics entry a prior launch already
            // inserted — unless the user has since hand-edited that specific row.
            val toRemoveVideoLib = current.filterKeys { it in supersededByCalisthenics }
                .filterValues { EDITED_TAG !in it.tags }
                .keys
            toRemoveVideoLib.forEach { id -> runCatching { repository.delete(id) } }

            // 5. Merge authored skills profiles onto the stored rows — same non-clobbering rule.
            val currentForSkills = runCatching { repository.currentById() }.getOrDefault(current)
            val withSkills = ExerciseSkills.byId.mapNotNull { (id, skills) ->
                currentForSkills[id]
                    ?.takeUnless { EDITED_TAG in it.tags }
                    ?.takeIf { it.skills != skills }
                    ?.copy(skills = skills)
            }
            if (withSkills.isNotEmpty()) runCatching { repository.upsertAll(withSkills) }

            // 6. Attach real-person video demonstrations from the bundled manifest. Idempotent —
            //    only rows the manifest actually changes are rewritten; user-edited rows are left
            //    alone so custom media survives.
            val afterOverlay = runCatching { repository.currentById() }.getOrDefault(currentForSkills)
            val withMedia = afterOverlay.values
                .filter { EDITED_TAG !in it.tags }
                .mapNotNull { ex -> videoCatalog.applyTo(ex).takeIf { it != ex } }
            if (withMedia.isNotEmpty()) runCatching { repository.upsertAll(withMedia) }
        }
    }

    companion object {
        /** Below this many stored exercises we (re)fetch the dataset to seed breadth. */
        private const val BASE_SEED_THRESHOLD = 300

        /** Rows carrying this tag were hand-edited by the user; sync leaves them alone. */
        const val EDITED_TAG = "user-edited"
    }
}
