package com.calistapp.app.data.exercise

/**
 * Free-exercise-db ids that duplicate a [VideoLibraryCatalog] entry covering the exact same
 * movement — same normalized name, confirmed by exact word-set matching against the dataset (not a
 * fuzzy/approximate match, to avoid hiding a movement that only *sounds* similar to a genuinely
 * different variation, e.g. "Seated Dumbbell Press" vs "Seated Dumbbell Arnold Press").
 *
 * The video-library entry has real-person video and is kept; these ids are excluded from being
 * (re)seeded from the dataset and removed if already present, by [ExerciseSyncManager] — unless the
 * user has since hand-edited that row, which always wins.
 *
 * Excludes ids already promoted into [CalisthenicsCatalog] (hand-authored, richer than the raw
 * dataset) — those overlaps are a separate judgment call, not a blind "video wins" case.
 */
internal val supersededByVideoLibrary: Set<String> = setOf(
    "Farmers_Walk", // mw_forearms_farmers_walk
    "Seated_Leg_Curl", // mw_hamstrings_machine_machine_seated_leg_curl
    "Ankle_Circles", // mw_calves_ankle_circle
    "Standing_Barbell_Calf_Raise", // mw_calves_barbell_barbell_calf_raises
    "Dumbbell_Bench_Press_with_Neutral_Grip", // mw_chest_dumbbell_dumbbell_neutral_bench_press
    "Standing_Long_Jump", // mw_glutes_cardio_long_jump
)

/**
 * The mirror case: [VideoLibraryCatalog] ids that duplicate a movement [CalisthenicsCatalog] already
 * covers with hand-authored coaching content. Here the *calisthenics* entry wins — its overview,
 * tips and mistakes are richer than anything auto-generated — but the video is real and worth
 * keeping, so it's merged onto the calisthenics id via `exercise_videos.json` instead of discarded.
 * These ids are then excluded from being seeded as a second, separate gallery entry.
 */
internal val supersededByCalisthenics: Set<String> = setOf(
    "mw_lats_pullup", // -> Pullups
    "mw_lats_chinup", // -> Chin-Up
    "mw_abdominals_situp", // -> Sit-Up
    "mw_glutes_glute_bridge", // -> Butt_Lift_Bridge
    "mw_glutes_bulgarian_split_squat", // -> cal_bulgarian_split_squat
)
