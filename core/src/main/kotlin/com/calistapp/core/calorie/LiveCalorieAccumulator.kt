package com.calistapp.core.calorie

import com.calistapp.core.model.ExerciseBreakdown
import com.calistapp.core.model.ExerciseMetabolics
import com.calistapp.core.model.HeartRateSample
import com.calistapp.core.model.HrZone
import com.calistapp.core.model.Segment
import com.calistapp.core.model.SegmentType
import com.calistapp.core.model.SessionSummary
import com.calistapp.core.model.UserProfile
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Rebuild an accumulator from a session's recorded history — the state it would be in had it
 * processed that history live.
 *
 * Needed to resume a workout that was interrupted. The accumulator is a streaming structure with no
 * persisted form, so a session read back from disk has to be fed its own history in the order it
 * originally happened. Replaying is exact by construction: it makes the same calls in the same order
 * the live path made, rather than trying to reconstitute internal totals from a finished summary.
 *
 * @param currentReps reps already logged against the still-open final segment.
 */
fun rebuildAccumulator(
    profile: UserProfile,
    segments: List<Segment>,
    samples: List<HeartRateSample>,
    currentReps: Int = 0,
    config: CalorieEngine.Config = CalorieEngine.Config(),
): LiveCalorieAccumulator {
    val accumulator = LiveCalorieAccumulator(profile, config)
    val ordered = samples.sortedBy { it.timestampMs }
    val first = segments.firstOrNull()

    if (first == null) {
        // No segments recorded yet — anchor on the first reading and take the samples as they came.
        accumulator.begin(ordered.firstOrNull()?.timestampMs ?: 0L)
        ordered.forEach(accumulator::addSample)
        accumulator.setCurrentReps(currentReps)
        return accumulator
    }

    accumulator.begin(first.startMs, first.type)
    accumulator.setCurrentExercise(first.slotId, first.exerciseName, first.metabolics)

    var next = 0
    segments.forEachIndexed { index, segment ->
        if (index == 0) return@forEachIndexed
        // Everything sampled inside the block that's ending, before the boundary is crossed.
        while (next < ordered.size && ordered[next].timestampMs < segment.startMs) {
            accumulator.addSample(ordered[next++])
        }
        // The closing block banks the reps it was sealed with, exactly as it did live.
        accumulator.setCurrentReps(segments[index - 1].reps)
        accumulator.startSegment(
            type = segment.type,
            nowMs = segment.startMs,
            slotId = segment.slotId,
            exerciseName = segment.exerciseName,
            metabolics = segment.metabolics,
        )
    }
    while (next < ordered.size) accumulator.addSample(ordered[next++])
    accumulator.setCurrentReps(currentReps)
    return accumulator
}

/**
 * Running calorie total for an **in-progress** session.
 *
 * [CalorieEngine.compute] walks a session's entire history. That's correct for scoring a finished
 * workout, but calling it on every heart-rate sample of a live one is quadratic in session length —
 * which is precisely what made the watch crawl after ten minutes. This class keeps a running total
 * instead: each sample integrates exactly one time slice, so the cost per sample is constant no
 * matter how long the workout has run.
 *
 * The slice math is deliberately identical to [CalorieEngine]'s, so the live number and the stored
 * one agree. The finished session is still scored by the engine, which stays authoritative.
 *
 * Not thread-safe; callers serialize access (the session managers own one each).
 */
class LiveCalorieAccumulator(
    private var profile: UserProfile,
    private val config: CalorieEngine.Config = CalorieEngine.Config(),
) {
    private var rmrPerMin = Formulas.restingKcalPerMin(profile)
    private var restCeilingPerMin = rmrPerMin * config.restCeilingMultiplier
    private var restingOffset = if (config.netOfResting) rmrPerMin else 0.0

    // Committed totals from segments that have already been closed.
    private var finalizedActiveKcal = 0.0
    private var finalizedRestKcal = 0.0
    private var finalizedActiveMs = 0L
    private var finalizedRestMs = 0L
    private var finalizedReps = 0
    private val breakdown = linkedMapOf<String, ExerciseBreakdown>()

    // The segment currently being accumulated.
    private var currentType = SegmentType.ACTIVE
    private var currentStartMs = 0L
    private var currentSlotId: String? = null
    private var currentExerciseName: String? = null
    private var currentMetabolics: ExerciseMetabolics? = null
    private var currentReps = 0
    private var currentRawKcal = 0.0
    private var currentMs = 0L

    // Last integration point.
    private var lastTimeMs = 0L
    private var lastBpm = 0
    private var hasPoint = false

    // Heart-rate statistics.
    private var sumHr = 0.0
    private var sampleCount = 0
    private var peakHr = 0
    private var minHr = Int.MAX_VALUE
    private var activeHrSum = 0.0
    private var activeHrCount = 0
    private val zoneMs = linkedMapOf<HrZone, Long>()

    var lastKnownBpm: Int = 0
        private set

    /** Re-derive the resting floor when the profile syncs in mid-session. */
    fun updateProfile(updated: UserProfile) {
        profile = updated
        rmrPerMin = Formulas.restingKcalPerMin(updated)
        restCeilingPerMin = rmrPerMin * config.restCeilingMultiplier
        restingOffset = if (config.netOfResting) rmrPerMin else 0.0
    }

    /** Begin a session with its first segment. Resets all state. */
    fun begin(startMs: Long, type: SegmentType = SegmentType.ACTIVE) {
        finalizedActiveKcal = 0.0
        finalizedRestKcal = 0.0
        finalizedActiveMs = 0L
        finalizedRestMs = 0L
        finalizedReps = 0
        breakdown.clear()
        currentType = type
        currentStartMs = startMs
        currentSlotId = null
        currentExerciseName = null
        currentMetabolics = null
        currentReps = 0
        currentRawKcal = 0.0
        currentMs = 0L
        lastTimeMs = startMs
        lastBpm = 0
        hasPoint = false
        sumHr = 0.0
        sampleCount = 0
        peakHr = 0
        minHr = Int.MAX_VALUE
        activeHrSum = 0.0
        activeHrCount = 0
        zoneMs.clear()
        lastKnownBpm = 0
    }

    /**
     * Integrate the slice between the previous point and this sample. O(1) — this is the whole
     * point of the class.
     */
    fun addSample(sample: HeartRateSample) {
        lastKnownBpm = sample.bpm
        sumHr += sample.bpm
        sampleCount++
        peakHr = max(peakHr, sample.bpm)
        minHr = min(minHr, sample.bpm)

        if (!hasPoint) {
            // First reading: nothing to integrate against yet, just anchor.
            hasPoint = true
            lastTimeMs = sample.timestampMs
            lastBpm = sample.bpm
            return
        }
        integrateTo(sample.timestampMs, sample.bpm)
    }

    /**
     * Advance the integration to [nowMs] at the last known heart rate, without recording a new
     * sample. Lets the live total keep climbing between readings.
     */
    fun advanceTo(nowMs: Long) {
        if (!hasPoint) return
        integrateTo(nowMs, lastBpm)
    }

    private fun integrateTo(timeMs: Long, bpm: Int) {
        val rawDt = timeMs - lastTimeMs
        if (rawDt <= 0) return

        val dtMs = min(rawDt, config.maxIntervalMs)
        val hr = ((lastBpm + bpm) / 2.0).roundToInt()
        val keytel = Formulas.keytelKcalPerMin(hr, profile)
        val gross = when (currentType) {
            SegmentType.ACTIVE -> max(keytel, rmrPerMin)
            SegmentType.REST -> keytel.coerceIn(rmrPerMin, restCeilingPerMin)
        }

        currentRawKcal += (gross - restingOffset).coerceAtLeast(0.0) *
            (dtMs / 60_000.0) * config.hrCalibration
        currentMs += dtMs
        if (currentType == SegmentType.ACTIVE) {
            activeHrSum += hr
            activeHrCount++
        }
        val zone = HrZone.forHr(hr, profile.effectiveMaxHr)
        zoneMs[zone] = (zoneMs[zone] ?: 0L) + dtMs

        lastTimeMs = timeMs
        lastBpm = bpm
    }

    /** Reps completed so far in the current block — feeds the mechanical-work floor. */
    fun setCurrentReps(reps: Int) {
        currentReps = reps.coerceAtLeast(0)
    }

    /** Attach (or change) the exercise being performed in the current block. */
    fun setCurrentExercise(slotId: String?, name: String?, metabolics: ExerciseMetabolics?) {
        currentSlotId = slotId
        currentExerciseName = name
        currentMetabolics = metabolics
    }

    /**
     * Close the current block and open a new one. Everything accumulated so far is committed
     * through the same correction + mechanical-floor reconciliation the engine applies.
     */
    fun startSegment(
        type: SegmentType,
        nowMs: Long,
        slotId: String? = null,
        exerciseName: String? = null,
        metabolics: ExerciseMetabolics? = null,
        reps: Int = 0,
    ) {
        advanceTo(nowMs)
        commitCurrent(nowMs)

        currentType = type
        currentStartMs = nowMs
        currentSlotId = slotId
        currentExerciseName = exerciseName
        currentMetabolics = metabolics
        currentReps = reps
        currentRawKcal = 0.0
        currentMs = 0L
    }

    private fun commitCurrent(nowMs: Long) {
        if (currentType == SegmentType.REST) {
            finalizedRestKcal += currentRawKcal
            finalizedRestMs += currentMs
            return
        }
        val blockKcal = reconcileActive(nowMs)
        finalizedActiveKcal += blockKcal
        finalizedActiveMs += currentMs
        finalizedReps += currentReps

        val name = currentExerciseName ?: return
        val key = currentSlotId ?: name
        val prev = breakdown[key]
        breakdown[key] = ExerciseBreakdown(
            slotId = currentSlotId,
            exerciseName = name,
            kcal = (prev?.kcal ?: 0.0) + blockKcal,
            reps = (prev?.reps ?: 0) + currentReps,
            sets = (prev?.sets ?: 0) + 1,
            activeDurationMs = (prev?.activeDurationMs ?: 0L) + currentMs,
        )
    }

    /** Apply the exercise correction and the reps-derived mechanical floor to the open block. */
    private fun reconcileActive(nowMs: Long): Double {
        val wallMs = (nowMs - currentStartMs).coerceAtLeast(0)
        val corrected = currentRawKcal * ExerciseIntensity.correctionFactor(currentMetabolics, wallMs)
        val mechanical = ExerciseIntensity.mechanicalKcal(currentMetabolics, currentReps, profile)
        val restingFloor = (rmrPerMin - restingOffset) * currentMs / 60_000.0
        return max(max(corrected, mechanical), restingFloor)
    }

    /**
     * The live summary: committed totals plus the open block scored provisionally. Cheap enough to
     * call on every UI frame.
     */
    fun snapshot(nowMs: Long): SessionSummary {
        val openIsRest = currentType == SegmentType.REST
        val openKcal = if (openIsRest) currentRawKcal else reconcileActive(nowMs)

        val activeKcal = finalizedActiveKcal + if (openIsRest) 0.0 else openKcal
        val restKcal = finalizedRestKcal + if (openIsRest) openKcal else 0.0
        val activeMs = finalizedActiveMs + if (openIsRest) 0L else currentMs
        val restMs = finalizedRestMs + if (openIsRest) currentMs else 0L

        // Fold the open block into the breakdown for display without committing it.
        val live = LinkedHashMap(breakdown)
        val name = currentExerciseName
        if (!openIsRest && name != null) {
            val key = currentSlotId ?: name
            val prev = live[key]
            live[key] = ExerciseBreakdown(
                slotId = currentSlotId,
                exerciseName = name,
                kcal = (prev?.kcal ?: 0.0) + openKcal,
                reps = (prev?.reps ?: 0) + currentReps,
                sets = (prev?.sets ?: 0) + 1,
                activeDurationMs = (prev?.activeDurationMs ?: 0L) + currentMs,
            )
        }

        return SessionSummary(
            totalKcal = activeKcal + restKcal,
            activeKcal = activeKcal,
            restKcal = restKcal,
            activeDurationMs = activeMs,
            restDurationMs = restMs,
            avgHr = if (sampleCount == 0) 0 else (sumHr / sampleCount).roundToInt(),
            peakHr = peakHr,
            avgActiveHr = if (activeHrCount == 0) 0 else (activeHrSum / activeHrCount).roundToInt(),
            minHr = if (minHr == Int.MAX_VALUE) 0 else minHr,
            timeInZonesMs = LinkedHashMap(zoneMs),
            perExercise = live.values.sortedByDescending { it.kcal },
            totalReps = finalizedReps + if (openIsRest) 0 else currentReps,
        )
    }
}
