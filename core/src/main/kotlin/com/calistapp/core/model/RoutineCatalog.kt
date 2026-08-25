package com.calistapp.core.model

/**
 * The seeded warm-up and stretch routines.
 *
 * A small authored set to begin with — the curated library grows the same way the exercises, videos
 * and skills do. Item ids point at real gallery movements so a routine chip can show a thumbnail and
 * open the movement's detail; a routine reads perfectly well even where an id doesn't resolve.
 */
object RoutineCatalog {

    val all: List<Routine> = listOf(
        Routine(
            id = "warmup_full_body",
            name = "Full-body raise",
            kind = RoutineKind.WARM_UP,
            bodyFocus = "Full body",
            items = listOf(
                RoutineItem("mw_shoulders_forward_arm_circle", "Arm Circles", 30),
                RoutineItem("mw_hamstrings_butt_kick", "Butt Kicks", 40),
                RoutineItem("mw_glutes_bodyweight_pulse_squat", "Pulse Squats", 40),
                RoutineItem("mw_shoulders_cardio_jumping_jacks", "Jumping Jacks", 40),
                RoutineItem("mw_abdominals_mountain_climber", "Mountain Climbers", 30),
            ),
        ),
        Routine(
            id = "warmup_lower_body",
            name = "Lower-body primer",
            kind = RoutineKind.WARM_UP,
            bodyFocus = "Lower body",
            items = listOf(
                RoutineItem("mw_hamstrings_butt_kick", "Butt Kicks", 40),
                RoutineItem("mw_glutes_bodyweight_pulse_squat", "Pulse Squats", 40),
                RoutineItem("mw_glutes_lateral_lunge", "Lateral Lunge", 30),
                RoutineItem("mw_glutes_forward_lunge", "Forward Lunge", 30),
                RoutineItem("mw_hamstrings_hamstring_stretch_dynamic_standing_bilateral", "Dynamic Hamstring Reach", 30),
            ),
        ),
        Routine(
            id = "stretch_lower_body",
            name = "Lower-body cooldown",
            kind = RoutineKind.STRETCH,
            bodyFocus = "Lower body",
            items = listOf(
                RoutineItem("mw_hamstrings_hamstring_stretch_static_standing_single_leg", "Hamstring Stretch", 40),
                RoutineItem("mw_calves_calves_stretch_variation_1", "Calf Stretch", 40),
                RoutineItem("mw_glutes_bodyweight_ninety_ninety_hip_stretch", "90/90 Hip Stretch", 40),
                RoutineItem("mw_glutes_glutes_stretch_variation_2", "Pigeon Pose", 40),
            ),
        ),
        Routine(
            id = "stretch_upper_body",
            name = "Upper-body cooldown",
            kind = RoutineKind.STRETCH,
            bodyFocus = "Upper body",
            items = listOf(
                RoutineItem("mw_chest_chest_stretch_variation_1", "Chest Opener", 40),
                RoutineItem("mw_shoulders_shoulders_stretch_variation_1", "Shoulder Stretch", 30),
                RoutineItem("mw_triceps_triceps_stretch_variation_1", "Triceps Stretch", 30),
                RoutineItem("mw_lats_lats_stretch_variation_1", "Lat Stretch", 30),
            ),
        ),
    )

    fun byKind(kind: RoutineKind): List<Routine> = all.filter { it.kind == kind }

    fun byId(id: String): Routine? = all.firstOrNull { it.id == id }
}
