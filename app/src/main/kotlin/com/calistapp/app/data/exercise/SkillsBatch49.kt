package com.calistapp.app.data.exercise

import com.calistapp.core.model.Skills

/**
 * Hand-authored [Skills] profiles, batch 49 — covers [videoLibraryBatch30] (glutes, part 4 of 12).
 * Excludes "mw_glutes_bulgarian_split_squat" — superseded, see [supersededByCalisthenics]. See [ExerciseSkills].
 */
internal val skillsBatch49: Map<String, Skills> = mapOf(
    "mw_glutes_bodyweight_alternating_curtsy_lunge" to Skills(strength = 20, endurance = 30, skill = 30, mobility = 15, cardio = 15),
    "mw_glutes_bodyweight_alternating_jump_lunge" to Skills(strength = 30, endurance = 30, skill = 45, mobility = 15, cardio = 45),
    "mw_glutes_bodyweight_alternating_lateral_lunge" to Skills(strength = 20, endurance = 30, skill = 25, mobility = 15, cardio = 15),
    "mw_glutes_bodyweight_alternating_reverse_lunges" to Skills(strength = 20, endurance = 30, skill = 20, mobility = 15, cardio = 15),
    "mw_glutes_bodyweight_curtsy_deficit_lunge" to Skills(strength = 25, endurance = 25, skill = 45, mobility = 20, cardio = 15),
    "mw_glutes_bodyweight_curtsy_step_down" to Skills(strength = 20, endurance = 25, skill = 40, mobility = 15, cardio = 10),
    "mw_glutes_bodyweight_deficit_reverse_lunge" to Skills(strength = 25, endurance = 25, skill = 35, mobility = 20, cardio = 15),
    "mw_glutes_bodyweight_figure_four_heels_elevated_hip_thrust" to Skills(strength = 25, endurance = 30, skill = 25, mobility = 15, cardio = 10),
    "mw_glutes_bodyweight_heels_elevated_hip_thrust" to Skills(strength = 25, endurance = 30, skill = 10, mobility = 10, cardio = 5),
    "mw_glutes_bodyweight_hip_abduction" to Skills(strength = 15, endurance = 25, skill = 10, mobility = 10, cardio = 5),
    "mw_glutes_bodyweight_kickstand_squat" to Skills(strength = 20, endurance = 30, skill = 25, mobility = 15, cardio = 10),
    "mw_glutes_bodyweight_lateral_lunge_jump" to Skills(strength = 25, endurance = 25, skill = 40, mobility = 15, cardio = 45),
    "mw_glutes_bodyweight_ninety_ninety_hip_stretch" to Skills(strength = 5, endurance = 10, skill = 15, mobility = 60, cardio = 2),
    "mw_glutes_bodyweight_pulse_squat" to Skills(strength = 20, endurance = 35, skill = 10, mobility = 10, cardio = 15),
    "mw_glutes_bodyweight_quad_stomp" to Skills(strength = 15, endurance = 25, skill = 25, mobility = 15, cardio = 15),
    "mw_glutes_bodyweight_single_leg_heels_elevated_hip_thrust" to Skills(strength = 25, endurance = 30, skill = 35, mobility = 15, cardio = 10),
    "mw_glutes_bodyweight_single_leg_squat" to Skills(strength = 45, endurance = 25, skill = 75, mobility = 25, cardio = 15),
    "mw_glutes_bodyweight_stability_ball_hyperextension" to Skills(strength = 25, endurance = 30, skill = 25, mobility = 15, cardio = 5),
    "mw_glutes_bodyweight_staggered_waiters_bow" to Skills(strength = 15, endurance = 25, skill = 25, mobility = 20, cardio = 5),
    "mw_glutes_bodyweight_swing_lunge" to Skills(strength = 25, endurance = 25, skill = 35, mobility = 15, cardio = 15),
    "mw_glutes_bodyweight_swingthrough_lunge" to Skills(strength = 25, endurance = 25, skill = 35, mobility = 15, cardio = 15),
    "mw_glutes_bodyweight_waiters_bow" to Skills(strength = 15, endurance = 25, skill = 20, mobility = 20, cardio = 5),
    "mw_glutes_bosu_ball_ball_bosu_ball_hip_thrust" to Skills(strength = 30, endurance = 30, skill = 40, mobility = 10, cardio = 10),
    "mw_glutes_bosu_ball_ball_bosu_ball_lateral_lunge" to Skills(strength = 25, endurance = 25, skill = 45, mobility = 15, cardio = 15),
    "mw_glutes_bosu_ball_ball_feet_elevated_glute_bridge" to Skills(strength = 25, endurance = 30, skill = 40, mobility = 10, cardio = 5),
    "mw_glutes_bosu_ball_ball_plank_glute_kickback" to Skills(strength = 25, endurance = 30, skill = 45, mobility = 10, cardio = 10),
    "mw_glutes_bosu_ball_ball_reverse_lunge" to Skills(strength = 25, endurance = 25, skill = 45, mobility = 15, cardio = 15),
    "mw_glutes_bosu_ball_ball_single_leg_elevated_glute_bridge" to Skills(strength = 25, endurance = 25, skill = 45, mobility = 15, cardio = 10),
    "mw_glutes_bosu_ball_ball_single_leg_hip_thrust" to Skills(strength = 30, endurance = 25, skill = 55, mobility = 15, cardio = 10),
    "mw_glutes_bosu_ball_ball_split_squat" to Skills(strength = 30, endurance = 25, skill = 45, mobility = 15, cardio = 15),
    "mw_glutes_bosu_ball_ball_squat" to Skills(strength = 25, endurance = 25, skill = 40, mobility = 10, cardio = 10),
    "mw_glutes_box_jump" to Skills(strength = 35, endurance = 25, skill = 40, mobility = 10, cardio = 40),
    "mw_glutes_cable_cable_bar_staggered_romanian_deadlift" to Skills(strength = 35, endurance = 25, skill = 30, mobility = 15, cardio = 10),
    "mw_glutes_cable_cable_belt_split_squat" to Skills(strength = 30, endurance = 25, skill = 35, mobility = 15, cardio = 10),
    "mw_glutes_cable_cable_belt_squat" to Skills(strength = 45, endurance = 25, skill = 15, mobility = 10, cardio = 10),
    "mw_glutes_cable_cable_bench_straight_leg_kickback" to Skills(strength = 15, endurance = 25, skill = 15, mobility = 15, cardio = 5),
    "mw_glutes_cable_cable_elevated_deadlift" to Skills(strength = 45, endurance = 25, skill = 25, mobility = 15, cardio = 10),
    "mw_glutes_cable_cable_glute_kickback" to Skills(strength = 20, endurance = 30, skill = 10, mobility = 10, cardio = 5),
)
