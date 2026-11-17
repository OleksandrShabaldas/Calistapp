package com.calistapp.core.medals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Names taken from the three real datasets: free-exercise-db ids, the authored set and the video library. */
class MovementTest {

    private fun assertMatches(movement: Movement, vararg names: String) =
        names.forEach { assertTrue("$movement should match '$it'", movement.matches(it)) }

    private fun assertRefuses(movement: Movement, vararg names: String) =
        names.forEach { assertFalse("$movement should refuse '$it'", movement.matches(it)) }

    @Test
    fun `every spelling of a movement reads as one token`() {
        assertEquals(setOf("pullup"), tokenize("Pull-Ups"))
        assertEquals(setOf("pullup"), tokenize("Pullups"))
        assertEquals(setOf("lsit"), tokenize("L-Sit"))
        assertEquals(setOf("cal", "lsit"), tokenize("cal_l_sit"))
        assertEquals(setOf("dip", "triceps", "version"), tokenize("Dips_-_Triceps_Version"))
    }

    @Test
    fun `pull-ups count every real variant and no regression`() {
        assertMatches(
            Movement.PULL_UP,
            "Pull-Up", "Pullups", "Ring Pull-Up", "Weighted Plate Pull-Ups", "Explosive Pull-Up", "L-Sit Pull-Up",
            "Pull-Up +20 kg",
        )
        assertRefuses(
            Movement.PULL_UP,
            "Band-Assisted Pull-Up", "Negative Pull-Up", "cal_negative_pullup", "Machine Assisted Pull-Up",
            "Smith Machine Assisted Pull-Up", "Jumping Pull-Up", "Kipping Pull-Up", "Chin-Up",
            // Single-side variants are logged as both sides' total: 20 archers aren't 20 pull-ups.
            "Archer Pull-Up", "cal_archer_pullup", "One-Arm Pull-Up",
        )
    }

    @Test
    fun `push-ups refuse knee incline and the drills done in push-up position`() {
        assertMatches(
            Movement.PUSH_UP,
            "Push-Up", "Pushups", "Push-Ups - Close Triceps Position", "Decline (Feet-Elevated) Push-Up",
            "Plyometric (Clap) Push-Up", "Diamond Push-Ups",
        )
        assertRefuses(
            Movement.PUSH_UP,
            "Knee Push-Ups", "Incline Push-Up", "Diamond Knee Push-Ups", "Handstand Push-Up", "Pike Push-Up",
            "Gastrocnemius Stretch in Push-Up Position", "Cross-Lateral Limb Raise (Push-Up Position)",
            "Push-Up to Side Plank", "Push-Up to Renegade Row", "Archer Push-Up", "Wall Push-Up",
        )
    }

    @Test
    fun `dips are bar and ring dips, not bench or box`() {
        assertMatches(Movement.DIP, "Dips_-_Triceps_Version", "Parallel Bar Dips", "Ring Dip", "Plate-Weighted Dip")
        assertRefuses(Movement.DIP, "Bench_Dips", "Bench Dip", "Box-Assisted Dips", "Machine Dips", "Hip Dips")
    }

    @Test
    fun `skills`() {
        assertMatches(Movement.MUSCLE_UP, "Muscle-Up", "Muscle_Up", "Ring Muscle-Up")
        assertRefuses(Movement.MUSCLE_UP, "Negative Muscle-Up", "Band-Assisted Muscle-Up", "Jumping Muscle-Up")

        assertMatches(Movement.PISTOL_SQUAT, "Pistol Squat", "cal_pistol_squat", "Bodyweight Single-Leg Squat (Pistol Squat)")
        assertRefuses(Movement.PISTOL_SQUAT, "Box Pistol Squat", "cal_box_pistol_squat", "Assisted Pistol Squat")

        assertMatches(Movement.L_SIT, "L-Sit", "cal_l_sit")
        assertRefuses(Movement.L_SIT, "Hanging L-Sit", "L-Sit Pull-Up", "Tuck L-Sit", "cal_l_sit_pullup")

        assertMatches(Movement.FRONT_LEVER, "Tuck Front Lever", "cal_tuck_front_lever", "Straddle Front Lever")
        assertRefuses(Movement.FRONT_LEVER, "Front Lever Raise", "Front Lever Row", "Back Lever", "cal_back_lever")
    }
}
