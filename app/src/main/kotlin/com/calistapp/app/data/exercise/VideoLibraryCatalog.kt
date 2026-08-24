package com.calistapp.app.data.exercise

/**
 * Registry of exercises hand-authored from the MuscleWiki-derived video library that have no
 * counterpart in free-exercise-db or [CalisthenicsCatalog] — same aggregation pattern as
 * [ExerciseEnrichments], but these are full [com.calistapp.core.model.Exercise] entries (there's no
 * dataset base to overlay onto). Video/GIF media is attached separately by [ExerciseVideoCatalog]
 * from `assets/exercise_videos.json`, keyed to these ids.
 *
 * Add a new `videoLibraryBatchNN` file and append it here to grow the set.
 */
object VideoLibraryCatalog {

    val exercises: List<com.calistapp.core.model.Exercise> = buildList {
        addAll(videoLibraryBatch01)
        addAll(videoLibraryBatch02)
        addAll(videoLibraryBatch03)
        addAll(videoLibraryBatch04)
        addAll(videoLibraryBatch05)
        addAll(videoLibraryBatch06)
        addAll(videoLibraryBatch07)
        addAll(videoLibraryBatch08)
        addAll(videoLibraryBatch09)
        addAll(videoLibraryBatch10)
        addAll(videoLibraryBatch11)
        addAll(videoLibraryBatch12)
        addAll(videoLibraryBatch13)
        addAll(videoLibraryBatch14)
        addAll(videoLibraryBatch15)
        addAll(videoLibraryBatch16)
        addAll(videoLibraryBatch17)
        addAll(videoLibraryBatch18)
        addAll(videoLibraryBatch19)
        addAll(videoLibraryBatch20)
        addAll(videoLibraryBatch21)
        addAll(videoLibraryBatch22)
        addAll(videoLibraryBatch23)
        addAll(videoLibraryBatch24)
        addAll(videoLibraryBatch25)
        addAll(videoLibraryBatch26)
        addAll(videoLibraryBatch27)
        addAll(videoLibraryBatch28)
        addAll(videoLibraryBatch29)
        addAll(videoLibraryBatch30)
        addAll(videoLibraryBatch31)
        addAll(videoLibraryBatch32)
        addAll(videoLibraryBatch33)
        addAll(videoLibraryBatch34)
        addAll(videoLibraryBatch35)
        addAll(videoLibraryBatch36)
        addAll(videoLibraryBatch37)
        addAll(videoLibraryBatch38)
    }
}
