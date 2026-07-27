package com.calistapp.core.calorie

import com.calistapp.core.model.ExerciseBreakdown
import com.calistapp.core.model.HeartRateSample
import com.calistapp.core.model.HrZone
import com.calistapp.core.model.Segment
import com.calistapp.core.model.SegmentType
import com.calistapp.core.model.SessionSummary
import com.calistapp.core.model.UserProfile
import com.calistapp.core.model.WorkoutSession
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The heart of Calistapp's accuracy claim.
 *
 * Rather than averaging heart rate across a whole workout and applying one formula, this
 * engine walks the real-time HR stream sample-by-sample and **integrates energy expenditure
 * over the actual time delta between readings** (trapezoidal). Each slice of time is attributed
 * to ACTIVE or REST based on the user's real-time input, and the two are accumulated separately:
 *
 *  - ACTIVE slices are credited the full Keytel HR-based estimate, floored at resting metabolism.
 *  - REST slices are bounded between resting metabolism and a modest ceiling, so sensor spikes or
 *    lingering elevated HR during recovery don't inflate the count.
 *
 * When a segment records **which exercise** was performed, two further refinements apply — see
 * [ExerciseIntensity] for the reasoning and the bounds:
 *
 *  - a **bounded correction** for heart rate's known blind spots (muscle-mass recruitment, cardiac
 *    lag on short sets, isometric holds), applied to the whole block of work rather than per slice;
 *  - a **mechanical-work floor** derived from logged reps, which is independent of heart rate and so
 *    survives the sensor dropouts that plague grip-heavy movements.
 *
 * The result is a per-segment, per-exercise breakdown that reflects what actually happened.
 */
class CalorieEngine(
    private val config: Config = Config(),
) {
    data class Config(
        /** REST slices are capped at this multiple of resting metabolic rate. */
        val restCeilingMultiplier: Double = 3.0,
        /**
         * If the gap between two HR samples exceeds this (sensor dropout), we only credit
         * up to this many ms at the last-known HR, avoiding runaway estimates across gaps.
         */
        val maxIntervalMs: Long = 30_000L,
        /** Time uncovered by any explicit segment is attributed to this type. */
        val defaultSegmentType: SegmentType = SegmentType.ACTIVE,
        /**
         * Report energy **above resting** rather than gross expenditure. See
         * [Formulas.netKcalPerMin] — leaving resting metabolism in the total is the largest single
         * source of overcounting in HR-based trackers. Configurable so tests can check gross.
         */
        val netOfResting: Boolean = true,
        /**
         * Correction for the known positive bias of HR→energy regressions in field use.
         *
         * Keytel was fitted on steady-state cycle-ergometer and treadmill work in a lab. Resistance
         * training violates those conditions in ways that all push the same direction: heart rate
         * stays elevated between sets without corresponding oxygen uptake, the pressor response to
         * gripping and bracing raises HR independently of metabolic demand, and wrist-worn optical
         * sensors bias high under motion artefact. Validation studies consistently find the equation
         * overestimates for this kind of work, so the estimate is scaled down modestly.
         *
         * Applied to the HR-derived term only — the mechanical-work floor is physics and needs no
         * such correction.
         */
        val hrCalibration: Double = 0.90,
    )

    fun summarize(session: WorkoutSession, profile: UserProfile): SessionSummary =
        compute(session.samples, session.segments, profile, endMs = session.endMs)

    /**
     * Compute a full summary from raw HR samples + active/rest segments.
     *
     * @param endMs optional session end; when provided, a trailing interval from the last
     *              sample to [endMs] is credited at the last-known HR.
     */
    fun compute(
        samples: List<HeartRateSample>,
        segments: List<Segment>,
        profile: UserProfile,
        endMs: Long? = null,
    ): SessionSummary {
        if (samples.isEmpty()) return SessionSummary.EMPTY

        val sorted = samples.sortedBy { it.timestampMs }
        val rmrPerMin = Formulas.restingKcalPerMin(profile)
        val restCeilingPerMin = rmrPerMin * config.restCeilingMultiplier
        // Subtracted from every slice when reporting net energy; the block floor drops to zero with
        // it, since "no less than resting" means "no less than nothing" once resting is removed.
        val restingOffset = if (config.netOfResting) rmrPerMin else 0.0
        val maxHr = profile.effectiveMaxHr
        val nowRef = endMs ?: sorted.last().timestampMs

        // Energy is bucketed per segment rather than summed directly, because the exercise
        // correction and the mechanical floor apply to a whole block of work — a set — not to
        // individual integration slices.
        val segKcal = DoubleArray(segments.size)
        val segMs = LongArray(segments.size)
        var looseKcal = 0.0
        var looseMs = 0L

        var sumHr = 0.0
        var peakHr = Int.MIN_VALUE
        var minHr = Int.MAX_VALUE
        var activeHrSum = 0.0
        var activeHrCount = 0
        val zoneMs = linkedMapOf<HrZone, Long>()

        // Build the ordered list of "breakpoints" so each integration slice lives entirely
        // within one segment (we never split calories across an active/rest boundary).
        val points = buildTimeline(sorted, segments, endMs)

        for (i in 0 until points.size - 1) {
            val a = points[i]
            val b = points[i + 1]
            val rawDt = b.timeMs - a.timeMs
            if (rawDt <= 0) continue

            val dtMs = min(rawDt, config.maxIntervalMs)
            val dtMin = dtMs / 60_000.0

            // Instantaneous HR for this slice: average of the endpoints (trapezoidal).
            val hr = ((a.bpm + b.bpm) / 2.0).roundToInt()
            val segIndex = segmentIndexAt((a.timeMs + b.timeMs) / 2, segments)
            val type = if (segIndex >= 0) segments[segIndex].type else config.defaultSegmentType

            val keytel = Formulas.keytelKcalPerMin(hr, profile)
            val gross = when (type) {
                SegmentType.ACTIVE -> max(keytel, rmrPerMin)
                SegmentType.REST -> keytel.coerceIn(rmrPerMin, restCeilingPerMin)
            }
            val kcal = (gross - restingOffset).coerceAtLeast(0.0) * dtMin * config.hrCalibration

            if (segIndex >= 0) {
                segKcal[segIndex] += kcal
                segMs[segIndex] += dtMs
            } else {
                looseKcal += kcal
                looseMs += dtMs
            }
            if (type == SegmentType.ACTIVE) {
                activeHrSum += hr
                activeHrCount++
            }
            val zone = HrZone.forHr(hr, maxHr)
            zoneMs[zone] = (zoneMs[zone] ?: 0L) + dtMs
        }

        // Reconcile each block: apply the exercise correction, then take the mechanical work
        // from logged reps as a floor. Never below resting metabolism for the elapsed time.
        var activeKcal = 0.0
        var restKcal = 0.0
        var activeMs = 0L
        var restMs = 0L
        var totalReps = 0
        val breakdown = linkedMapOf<String, ExerciseBreakdown>()

        for (i in segments.indices) {
            val seg = segments[i]
            val coveredMs = segMs[i]
            if (seg.type == SegmentType.REST) {
                restKcal += segKcal[i]
                restMs += coveredMs
                continue
            }

            // Wall-clock length drives the cardiac-lag term: how long the effort lasted, not how
            // much of it the sensor happened to cover.
            val wallMs = seg.durationMs(nowRef).coerceAtLeast(0)
            val corrected = segKcal[i] * ExerciseIntensity.correctionFactor(seg.metabolics, wallMs)
            val mechanical = ExerciseIntensity.mechanicalKcal(seg.metabolics, seg.reps, profile)
            val restingFloor = (rmrPerMin - restingOffset) * coveredMs / 60_000.0
            val blockKcal = max(max(corrected, mechanical), restingFloor)

            activeKcal += blockKcal
            activeMs += coveredMs
            totalReps += seg.reps

            if (seg.exerciseName != null) {
                val key = seg.slotId ?: seg.exerciseName
                val prev = breakdown[key]
                breakdown[key] = ExerciseBreakdown(
                    slotId = seg.slotId,
                    exerciseName = seg.exerciseName,
                    kcal = (prev?.kcal ?: 0.0) + blockKcal,
                    reps = (prev?.reps ?: 0) + seg.reps,
                    sets = (prev?.sets ?: 0) + 1,
                    activeDurationMs = (prev?.activeDurationMs ?: 0L) + coveredMs,
                )
            }
        }

        // Time not covered by any explicit segment (e.g. before the first toggle).
        when (config.defaultSegmentType) {
            SegmentType.ACTIVE -> {
                activeKcal += looseKcal
                activeMs += looseMs
            }
            SegmentType.REST -> {
                restKcal += looseKcal
                restMs += looseMs
            }
        }

        for (s in sorted) {
            sumHr += s.bpm
            peakHr = max(peakHr, s.bpm)
            minHr = min(minHr, s.bpm)
        }

        return SessionSummary(
            totalKcal = activeKcal + restKcal,
            activeKcal = activeKcal,
            restKcal = restKcal,
            activeDurationMs = activeMs,
            restDurationMs = restMs,
            avgHr = (sumHr / sorted.size).roundToInt(),
            peakHr = if (peakHr == Int.MIN_VALUE) 0 else peakHr,
            avgActiveHr = if (activeHrCount == 0) 0 else (activeHrSum / activeHrCount).roundToInt(),
            minHr = if (minHr == Int.MAX_VALUE) 0 else minHr,
            timeInZonesMs = zoneMs,
            perExercise = breakdown.values.sortedByDescending { it.kcal },
            totalReps = totalReps,
        )
    }

    /** A time point with an interpolated bpm; used as an integration boundary. */
    private data class TimePoint(val timeMs: Long, val bpm: Int)

    /**
     * Produce integration boundaries: every HR sample, every segment edge (so slices don't
     * straddle an active/rest boundary), plus an optional session end. bpm at segment edges is
     * interpolated from surrounding samples.
     */
    private fun buildTimeline(
        sorted: List<HeartRateSample>,
        segments: List<Segment>,
        endMs: Long?,
    ): List<TimePoint> {
        val first = sorted.first().timestampMs
        val last = endMs?.coerceAtLeast(sorted.last().timestampMs) ?: sorted.last().timestampMs
        val times = sortedSetOf(first, last)
        sorted.forEach { times.add(it.timestampMs) }
        // Add segment edges (clamped to the sampled range) so no slice straddles a boundary.
        for (seg in segments) {
            if (seg.startMs in first..last) times.add(seg.startMs)
            seg.endMs?.let { if (it in first..last) times.add(it) }
        }
        return times.map { t -> TimePoint(t, bpmAt(t, sorted)) }
    }

    /** Linear interpolation / clamping of bpm at an arbitrary time. */
    private fun bpmAt(timeMs: Long, sorted: List<HeartRateSample>): Int {
        if (timeMs <= sorted.first().timestampMs) return sorted.first().bpm
        if (timeMs >= sorted.last().timestampMs) return sorted.last().bpm
        var lo = sorted.first()
        for (s in sorted) {
            if (s.timestampMs == timeMs) return s.bpm
            if (s.timestampMs < timeMs) lo = s else {
                val span = (s.timestampMs - lo.timestampMs).toDouble()
                if (span <= 0) return s.bpm
                val f = (timeMs - lo.timestampMs) / span
                return (lo.bpm + f * (s.bpm - lo.bpm)).roundToInt()
            }
        }
        return sorted.last().bpm
    }

    /** Index of the segment covering [timeMs], or -1 when no segment does. */
    private fun segmentIndexAt(timeMs: Long, segments: List<Segment>): Int {
        for (i in segments.indices) {
            val seg = segments[i]
            val end = seg.endMs ?: Long.MAX_VALUE
            if (timeMs in seg.startMs until end) return i
        }
        return -1
    }
}
