package com.calistapp.app.data.exercise

import com.calistapp.core.model.Skills

/**
 * Hand-authored [Skills] profiles, batch 18 — covers [enrichmentBatch17]. See [ExerciseSkills].
 * Excludes "Standing_Barbell_Calf_Raise" and "Standing_Long_Jump" — superseded, see [supersededByVideoLibrary].
 */
internal val skillsBatch18: Map<String, Skills> = mapOf(
    "Stairmaster" to Skills(strength = 15, endurance = 55, skill = 10, mobility = 5, cardio = 65),
    "Standing_Alternating_Dumbbell_Press" to Skills(strength = 45, endurance = 25, skill = 25, mobility = 10, cardio = 10),
    "Standing_Barbell_Press_Behind_Neck" to Skills(strength = 50, endurance = 20, skill = 30, mobility = 25, cardio = 10),
    "Standing_Bent-Over_One-Arm_Dumbbell_Triceps_Extension" to Skills(strength = 25, endurance = 25, skill = 15, mobility = 5, cardio = 5),
    "Standing_Bent-Over_Two-Arm_Dumbbell_Triceps_Extension" to Skills(strength = 25, endurance = 25, skill = 10, mobility = 5, cardio = 5),
    "Standing_Biceps_Cable_Curl" to Skills(strength = 30, endurance = 30, skill = 10, mobility = 5, cardio = 5),
    "Standing_Biceps_Stretch" to Skills(strength = 5, endurance = 10, skill = 5, mobility = 65, cardio = 2),
    "Standing_Bradford_Press" to Skills(strength = 35, endurance = 35, skill = 35, mobility = 15, cardio = 15),
    "Standing_Cable_Chest_Press" to Skills(strength = 35, endurance = 30, skill = 25, mobility = 10, cardio = 10),
    "Standing_Cable_Lift" to Skills(strength = 25, endurance = 30, skill = 35, mobility = 20, cardio = 10),
    "Standing_Cable_Wood_Chop" to Skills(strength = 25, endurance = 30, skill = 35, mobility = 20, cardio = 10),
    "Standing_Calf_Raises" to Skills(strength = 30, endurance = 35, skill = 5, mobility = 10, cardio = 5),
    "Standing_Concentration_Curl" to Skills(strength = 30, endurance = 30, skill = 15, mobility = 5, cardio = 5),
    "Standing_Dumbbell_Calf_Raise" to Skills(strength = 25, endurance = 35, skill = 5, mobility = 10, cardio = 5),
    "Standing_Dumbbell_Press" to Skills(strength = 55, endurance = 25, skill = 30, mobility = 10, cardio = 15),
    "Standing_Dumbbell_Reverse_Curl" to Skills(strength = 25, endurance = 30, skill = 15, mobility = 5, cardio = 5),
    "Standing_Dumbbell_Straight-Arm_Front_Delt_Raise_Above_Head" to Skills(strength = 25, endurance = 30, skill = 20, mobility = 15, cardio = 10),
    "Standing_Dumbbell_Triceps_Extension" to Skills(strength = 30, endurance = 30, skill = 15, mobility = 10, cardio = 5),
    "Standing_Dumbbell_Upright_Row" to Skills(strength = 30, endurance = 30, skill = 15, mobility = 10, cardio = 5),
    "Standing_Elevated_Quad_Stretch" to Skills(strength = 5, endurance = 10, skill = 5, mobility = 70, cardio = 2),
    "Standing_Front_Barbell_Raise_Over_Head" to Skills(strength = 25, endurance = 30, skill = 20, mobility = 15, cardio = 10),
    "Standing_Gastrocnemius_Calf_Stretch" to Skills(strength = 5, endurance = 10, skill = 5, mobility = 70, cardio = 2),
    "Standing_Hamstring_and_Calf_Stretch" to Skills(strength = 5, endurance = 10, skill = 5, mobility = 75, cardio = 2),
    "Standing_Hip_Circles" to Skills(strength = 5, endurance = 15, skill = 10, mobility = 55, cardio = 10),
    "Standing_Hip_Flexors" to Skills(strength = 5, endurance = 10, skill = 5, mobility = 70, cardio = 2),
    "Standing_Inner-Biceps_Curl" to Skills(strength = 30, endurance = 30, skill = 15, mobility = 5, cardio = 5),
    "Standing_Lateral_Stretch" to Skills(strength = 5, endurance = 10, skill = 5, mobility = 65, cardio = 2),
    "Standing_Leg_Curl" to Skills(strength = 30, endurance = 30, skill = 15, mobility = 5, cardio = 5),
    "Standing_Low-Pulley_Deltoid_Raise" to Skills(strength = 25, endurance = 30, skill = 20, mobility = 10, cardio = 5),
    "Standing_Low-Pulley_One-Arm_Triceps_Extension" to Skills(strength = 25, endurance = 30, skill = 20, mobility = 5, cardio = 5),
    "Standing_Military_Press" to Skills(strength = 65, endurance = 20, skill = 35, mobility = 15, cardio = 15),
    "Standing_Olympic_Plate_Hand_Squeeze" to Skills(strength = 30, endurance = 40, skill = 10, mobility = 5, cardio = 5),
    "Standing_One-Arm_Cable_Curl" to Skills(strength = 25, endurance = 30, skill = 15, mobility = 5, cardio = 5),
    "Standing_One-Arm_Dumbbell_Curl_Over_Incline_Bench" to Skills(strength = 30, endurance = 30, skill = 15, mobility = 5, cardio = 5),
    "Standing_One-Arm_Dumbbell_Triceps_Extension" to Skills(strength = 25, endurance = 30, skill = 20, mobility = 5, cardio = 5),
    "Standing_Overhead_Barbell_Triceps_Extension" to Skills(strength = 35, endurance = 30, skill = 20, mobility = 10, cardio = 5),
    "Standing_Palm-In_One-Arm_Dumbbell_Press" to Skills(strength = 45, endurance = 25, skill = 35, mobility = 15, cardio = 15),
    "Standing_Palms-In_Dumbbell_Press" to Skills(strength = 50, endurance = 25, skill = 25, mobility = 10, cardio = 10),
    "Standing_Palms-Up_Barbell_Behind_The_Back_Wrist_Curl" to Skills(strength = 20, endurance = 30, skill = 10, mobility = 5, cardio = 2),
    "Standing_Pelvic_Tilt" to Skills(strength = 5, endurance = 10, skill = 10, mobility = 45, cardio = 2),
    "Standing_Rope_Crunch" to Skills(strength = 30, endurance = 35, skill = 15, mobility = 5, cardio = 10),
    "Standing_Soleus_And_Achilles_Stretch" to Skills(strength = 5, endurance = 10, skill = 5, mobility = 70, cardio = 2),
    "Standing_Toe_Touches" to Skills(strength = 5, endurance = 10, skill = 5, mobility = 70, cardio = 2),
    "Standing_Towel_Triceps_Extension" to Skills(strength = 25, endurance = 30, skill = 20, mobility = 10, cardio = 10),
)
