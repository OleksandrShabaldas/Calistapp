package com.calistapp.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnilateralTest {
    @Test
    fun `one-side-at-a-time movements are recognised`() {
        listOf(
            "Dumbbell Bulgarian Split Squat", "Barbell Lunge", "Single-Leg Glute Bridge",
            "One-Arm Dumbbell Row", "Pistol Squat", "Dumbbell Step-Up", "Archer Push-Up",
            "Alternating Dumbbell Curl",
        ).forEach { assertTrue(it, isUnilateral(it)) }
    }

    @Test
    fun `bilateral movements are not`() {
        listOf("Pull-Up", "Parallel Bar Dips", "Push-Up", "Barbell Squat", "Barbell Rollout", "Plank")
            .forEach { assertFalse(it, isUnilateral(it)) }
    }
}
