package com.calistapp.core.calorie

import com.calistapp.core.model.Exercise
import com.calistapp.core.model.ExerciseMetabolics
import com.calistapp.core.model.UserProfile
import kotlin.math.exp

/**
 * Exercise-aware corrections to the heart-rate calorie estimate.
 *
 * ## Why corrections at all — and why they're bounded
 *
 * Heart rate already reflects effort, so naively multiplying the Keytel estimate by a "difficulty
 * score" would double-count and make the number *worse*, not better. This object therefore only
 * corrects where HR is **demonstrably blind**, and every correction is clamped so a mis-tagged
 * exercise can never wreck a session's estimate:
 *
 *  1. **Muscle-mass recruitment.** Keytel was regressed on cycling/treadmill work, which recruits a
 *     particular muscle mass (legs + trunk). At an identical heart rate, a pull-up recruits far more
 *     mass than a biceps curl and costs more. Applied as a gentle multiplier around that reference.
 *  2. **HR lag on short sets.** Cardiac response to exercise onset has a time constant on the order
 *     of 30–60 s, so a 40-second set is over before heart rate reflects its true cost. Short ACTIVE
 *     blocks get an uplift that decays exponentially with block length.
 *  3. **Isometric holds.** Static work (plank, L-sit) produces less rhythmic muscle pump and lower
 *     cardiac output than dynamic work of comparable metabolic cost, so HR reads low.
 *
 * The product of the three is clamped to [MIN_CORRECTION]..[MAX_CORRECTION] — exercise metadata can
 * shift the estimate by at most about a third in either direction. HR remains the backbone.
 *
 * ## The mechanical floor
 *
 * Reps are the one signal heart rate cannot see at all. [mechanicalKcal] estimates energy from
 * physics — force × distance, divided by muscular efficiency — which is *independent evidence*
 * rather than another correlate of HR. It matters most exactly where wrist-worn optical HR is known
 * to fail: grip-heavy work (pull-ups, deadlifts, hangs) where wrist flexion corrupts the signal, and
 * outright sensor dropout mid-set. The engine takes it as a floor, never a replacement.
 */
object ExerciseIntensity {

    // ---- Correction bounds -------------------------------------------------------------------

    const val MIN_CORRECTION = 0.85
    const val MAX_CORRECTION = 1.35

    /**
     * Muscle mass recruited by the cycling/running protocols Keytel et al. calibrated on —
     * quadriceps, hamstrings, glutes, calves and adductors, plus trunk stabilizers.
     */
    private const val REFERENCE_MUSCLE_FRACTION = 0.45

    /** How strongly recruitment deviation moves the estimate. Deliberately gentle. */
    private const val RECRUITMENT_GAIN = 0.30
    private const val RECRUITMENT_MIN = 0.88
    private const val RECRUITMENT_MAX = 1.15

    /** Peak uplift for an instantaneous set, decaying with [HR_LAG_TAU_SEC]. */
    private const val HR_LAG_GAIN = 0.18
    private const val HR_LAG_TAU_SEC = 45.0

    /** Static holds read low on HR relative to their true cost. */
    private const val ISOMETRIC_UPLIFT = 1.10

    // ---- Mechanical work ---------------------------------------------------------------------

    private const val GRAVITY = 9.81

    /** Gross efficiency of concentric muscular work — the 20–25% range is well established. */
    private const val MUSCULAR_EFFICIENCY = 0.22

    /** Lowering a load costs roughly a third of raising it, for the same absolute work. */
    private const val ECCENTRIC_FACTOR = 0.33

    private const val JOULES_PER_KCAL = 4184.0

    /**
     * The bounded multiplier applied to the Keytel estimate for one ACTIVE block.
     *
     * @param metabolics the movement's physical profile; null (free work) yields 1.0 — no correction.
     * @param durationMs how long this block of work lasted, for the HR-lag term.
     */
    fun correctionFactor(metabolics: ExerciseMetabolics?, durationMs: Long): Double {
        if (metabolics == null) return 1.0

        val recruitment = (
            1.0 + RECRUITMENT_GAIN * (metabolics.muscleMassFraction - REFERENCE_MUSCLE_FRACTION)
            ).coerceIn(RECRUITMENT_MIN, RECRUITMENT_MAX)

        val durationSec = (durationMs / 1000.0).coerceAtLeast(0.0)
        val hrLag = 1.0 + HR_LAG_GAIN * exp(-durationSec / HR_LAG_TAU_SEC)

        val isometric = if (metabolics.isometric) ISOMETRIC_UPLIFT else 1.0

        return (recruitment * hrLag * isometric).coerceIn(MIN_CORRECTION, MAX_CORRECTION)
    }

    /**
     * Energy from mechanical work for [reps] repetitions — force × distance ÷ muscular efficiency,
     * counting the eccentric phase at [ECCENTRIC_FACTOR].
     *
     * Calibration check at 75 kg bodyweight:
     *  - pull-up (load 0.95, ROM 0.45 m) → ≈0.45 kcal/rep (published ≈0.5)
     *  - push-up (load 0.64, ROM 0.35 m) → ≈0.24 kcal/rep (published ≈0.2–0.3)
     *  - bodyweight squat (load 0.75, ROM 0.40 m) → ≈0.32 kcal/rep (published ≈0.3)
     *
     * Returns 0 for isometric holds: a static hold produces no displacement, so there is no external
     * work to compute. Its cost is entirely internal and is handled by HR plus [ISOMETRIC_UPLIFT].
     */
    fun mechanicalKcal(metabolics: ExerciseMetabolics?, reps: Int, profile: UserProfile): Double {
        if (metabolics == null || reps <= 0 || metabolics.isometric) return 0.0

        val movedKg = metabolics.loadFraction * profile.weightKg + metabolics.externalLoadKg
        if (movedKg <= 0.0 || metabolics.romMetres <= 0.0) return 0.0

        val concentricJoules = movedKg * GRAVITY * metabolics.romMetres
        val grossJoules = concentricJoules * (1.0 + ECCENTRIC_FACTOR) / MUSCULAR_EFFICIENCY
        return grossJoules * reps / JOULES_PER_KCAL
    }

    // ---- Deriving a profile from a gallery entry ----------------------------------------------

    /**
     * Approximate share of total skeletal muscle mass per free-exercise-db muscle name. Values are
     * rounded from segmental cadaver/MRI data; they sum to ≈0.89, the remainder being intrinsic
     * muscle the dataset never names.
     */
    private val MUSCLE_MASS = mapOf(
        "quadriceps" to 0.150,
        "glutes" to 0.120,
        "hamstrings" to 0.085,
        "adductors" to 0.060,
        "calves" to 0.055,
        "abductors" to 0.030,
        "lats" to 0.065,
        "lower back" to 0.045,
        "middle back" to 0.040,
        "traps" to 0.035,
        "chest" to 0.050,
        "shoulders" to 0.045,
        "triceps" to 0.030,
        "biceps" to 0.020,
        "forearms" to 0.020,
        "abdominals" to 0.030,
        "neck" to 0.010,
    )

    /**
     * Bodyweight-support fraction and per-rep vertical travel, keyed by movement pattern. Matched
     * against the exercise name; the first hit wins, so longer/more specific keys are listed first.
     */
    private val PATTERNS: List<Triple<String, Double, Double>> = listOf(
        Triple("muscle up", 0.95, 0.60),
        Triple("pull-up", 0.95, 0.45),
        Triple("pullup", 0.95, 0.45),
        Triple("pull up", 0.95, 0.45),
        Triple("chin-up", 0.95, 0.45),
        Triple("chinup", 0.95, 0.45),
        Triple("chin up", 0.95, 0.45),
        Triple("burpee", 0.70, 0.60),
        Triple("dip", 0.95, 0.40),
        Triple("push-up", 0.64, 0.35),
        Triple("pushup", 0.64, 0.35),
        Triple("push up", 0.64, 0.35),
        Triple("handstand", 0.90, 0.35),
        Triple("deadlift", 0.85, 0.50),
        Triple("lunge", 0.75, 0.35),
        Triple("step-up", 0.80, 0.35),
        Triple("squat", 0.75, 0.40),
        Triple("calf raise", 0.90, 0.12),
        Triple("glute bridge", 0.55, 0.25),
        Triple("hip thrust", 0.55, 0.25),
        Triple("leg raise", 0.32, 0.45),
        Triple("knee raise", 0.28, 0.35),
        Triple("sit-up", 0.30, 0.35),
        Triple("situp", 0.30, 0.35),
        Triple("crunch", 0.25, 0.20),
        Triple("bench press", 0.45, 0.40),
        Triple("overhead press", 0.35, 0.45),
        Triple("shoulder press", 0.35, 0.45),
        Triple("row", 0.35, 0.40),
        Triple("curl", 0.12, 0.35),
        Triple("extension", 0.12, 0.35),
        Triple("raise", 0.10, 0.35),
        Triple("fly", 0.12, 0.40),
    )

    /** Names that indicate a static hold rather than a rep-counted movement. */
    private val ISOMETRIC_KEYS =
        listOf("plank", "hold", "l-sit", "l sit", "hang", "wall sit", "isometric", "bridge hold")

    /**
     * Build a physical profile from a gallery entry. Muscle recruitment comes from the dataset's
     * muscle lists; load and range of motion from movement-pattern matching, falling back to
     * compound/isolation defaults when nothing matches.
     */
    fun deriveMetabolics(exercise: Exercise, externalLoadKg: Double = 0.0): ExerciseMetabolics {
        val name = exercise.name.lowercase()

        val primary = exercise.primaryMuscles.sumOf { MUSCLE_MASS[it.lowercase()] ?: 0.0 }
        val secondary = exercise.secondaryMuscles.sumOf { MUSCLE_MASS[it.lowercase()] ?: 0.0 }
        val recruited = (primary + 0.5 * secondary).coerceIn(0.05, 0.95)

        val isometric = ISOMETRIC_KEYS.any { name.contains(it) } ||
            exercise.force.equals("static", ignoreCase = true)

        val compound = !exercise.mechanic.equals("isolation", ignoreCase = true)

        val pattern = PATTERNS.firstOrNull { (key, _, _) -> name.contains(key) }
        val loadFraction = pattern?.second ?: if (compound) 0.55 else 0.15
        val rom = pattern?.third ?: if (compound) 0.40 else 0.35

        return ExerciseMetabolics(
            muscleMassFraction = recruited,
            loadFraction = loadFraction,
            externalLoadKg = externalLoadKg,
            romMetres = rom,
            isometric = isometric,
            compound = compound,
        )
    }
}
