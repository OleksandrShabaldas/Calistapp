package com.calistapp.core.calorie

import com.calistapp.core.model.SegmentType
import com.calistapp.core.model.SessionSummary
import com.calistapp.core.model.UserProfile

/**
 * The full derivation of a session's calorie figure — every input, every intermediate term, and
 * which candidate won for each block of work.
 *
 * This exists because a calorie number with no visible reasoning is indistinguishable from a made-up
 * one. The engine's whole claim is that it integrates a real heart-rate curve through published
 * regressions and corrects only where heart rate is demonstrably blind; that claim is only worth
 * anything if it can be checked. Every figure here is reproducible with a calculator from the
 * numbers shown beside it.
 *
 * Produced by [CalorieEngine.explain] as a by-product of the *same* pass that produces the summary,
 * not by a second implementation that could quietly disagree with it.
 */
data class CalorieAudit(
    /** The result this audit explains. Identical to what [CalorieEngine.compute] returns. */
    val summary: SessionSummary,
    val profile: UserProfile,
    val resting: Formulas.Resting,
    val keytel: Formulas.KeytelVariant,
    val settings: Settings,
    val sampling: Sampling,
    /** One entry per segment, in the order they happened. */
    val blocks: List<Block>,
    /** Energy from time no segment covered — before the first toggle, typically. */
    val unsegmentedKcal: Double,
    val unsegmentedMs: Long,
) {
    /** What the blocks add up to. Should equal [SessionSummary.totalKcal] to within rounding. */
    val blockKcalTotal: Double get() = blocks.sumOf { it.kcal } + unsegmentedKcal

    val workBlocks: List<Block> get() = blocks.filter { it.type == SegmentType.ACTIVE }
    val restBlocks: List<Block> get() = blocks.filter { it.type == SegmentType.REST }

    /** The engine constants in force for this computation. */
    data class Settings(
        val hrCalibration: Double,
        val netOfResting: Boolean,
        val restCeilingMultiplier: Double,
        val maxIntervalMs: Long,
        val minCorrection: Double,
        val maxCorrection: Double,
    )

    /** What the heart-rate stream actually contained, including what had to be discarded. */
    data class Sampling(
        val sampleCount: Int,
        val firstSampleMs: Long,
        val lastSampleMs: Long,
        /** Integration slices — one per sample, plus one per segment edge. */
        val sliceCount: Int,
        /** Slices longer than [Settings.maxIntervalMs], i.e. sensor dropouts. */
        val gapSliceCount: Int,
        /** Time inside those gaps that was deliberately not credited. */
        val uncreditedGapMs: Long,
    )

    /** Which of the competing estimates a block of work was ultimately counted at. */
    enum class Basis {
        /** Heart rate, after the exercise correction. The normal case. */
        HEART_RATE,

        /** The rep-work floor beat heart rate — usually a sensor dropout or grip-heavy work. */
        REP_WORK,

        /** Both came in under resting metabolism for the elapsed time. */
        RESTING_FLOOR,

        /** A rest block: heart rate only, bounded above and below. */
        REST,
    }

    /**
     * One segment's arithmetic. For ACTIVE blocks the three candidates ([correctedKcal],
     * [mechanical]'s kcal, [restingFloorKcal]) compete and the largest wins — that's [kcal], and
     * [basis] names the winner.
     */
    data class Block(
        /** 1-based position in the session. */
        val ordinal: Int,
        val type: SegmentType,
        val exerciseName: String?,
        /** Which set of this exercise it was, 1-based. Null for rest and unstructured work. */
        val setIndex: Int?,
        val startMs: Long,
        /** How long the block lasted on the clock. Drives the HR-lag term. */
        val wallDurationMs: Long,
        /** How much of it the heart-rate stream actually covered. Drives the energy. */
        val coveredDurationMs: Long,
        val reps: Int,
        val heartRate: HeartRateTerm,
        /** Null when the block carried no exercise context, so nothing was corrected. */
        val correction: ExerciseIntensity.Correction?,
        /** [HeartRateTerm.kcal] × the correction factor. */
        val correctedKcal: Double,
        /** Null when the block carried no exercise context. */
        val mechanical: ExerciseIntensity.MechanicalWork?,
        val restingFloorKcal: Double,
        val kcal: Double,
        val basis: Basis,
    )

    /**
     * The heart-rate term for one block: what the regression gave, integrated over the real curve.
     *
     * [kcal] is the authoritative figure — it comes from the slice-by-slice integration, not from
     * re-evaluating the formula at [avgBpm]. [keytelAtAvgKcalPerMin] is there as a cross-check: for
     * a steady block the two agree closely, and a wide divergence means the heart rate moved a lot
     * within the block.
     */
    data class HeartRateTerm(
        val avgBpm: Int,
        val keytelAtAvgKcalPerMin: Double,
        val restingKcalPerMin: Double,
        val calibration: Double,
        val kcal: Double,
        val coveredDurationMs: Long,
        /** Slices bounded by the rest ceiling. Rest blocks only. */
        val ceilingSlices: Int,
        /** Slices raised to the resting floor before the net subtraction. */
        val floorSlices: Int,
        val sliceCount: Int,
    ) {
        /** [kcal] expressed as a rate, so `rate × minutes = kcal` checks out by hand. */
        val effectiveKcalPerMin: Double
            get() = if (coveredDurationMs <= 0L) 0.0 else kcal / (coveredDurationMs / 60_000.0)
    }
}
