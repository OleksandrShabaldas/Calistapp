package com.calistapp.core.analysis

import com.calistapp.core.model.HeartRateSample
import com.calistapp.core.model.HrRecovery
import com.calistapp.core.model.RestDrop
import com.calistapp.core.model.Segment
import com.calistapp.core.model.SegmentType

/**
 * How fast your heart rate falls when a set ends.
 *
 * One of the better-established markers of aerobic fitness and autonomic recovery, and one this app
 * gets almost for free: it already segments a workout into work and rest at the exact instant the
 * user stops, which is the measurement other apps have to infer. A drop of under ~12 bpm in the
 * first minute after effort is the threshold the clinical literature treats as blunted; trained
 * people typically see 20–40.
 *
 * Measured per rest block and averaged, because one rest tells you very little — you might have
 * walked to get water.
 */
object HeartRateRecovery {

    /** The standard window after effort ends over which recovery is measured — one minute. */
    const val WINDOW_MS = 60_000L

    /**
     * Shortest rest that still yields a recovery reading. Below this the drop is too brief to mean
     * much; between here and a full minute it's measured over the actual rest and reported per-minute,
     * so a workout with brisk rests still gives a reading for every exercise rather than only the one
     * rest that happened to run a full minute (which read as "recovery data for one exercise only").
     */
    private const val MIN_REST_MS = 30_000L

    /** How far back before the transition to look for the peak the recovery is measured from. */
    private const val PEAK_LOOKBACK_MS = 30_000L

    /** A reading this far from the target instant is too stale to anchor on. */
    private const val TOLERANCE_MS = 12_000L

    fun analyze(samples: List<HeartRateSample>, segments: List<Segment>): HrRecovery? {
        if (samples.isEmpty() || segments.isEmpty()) return null
        val ordered = samples.sortedBy { it.timestampMs }
        val drops = mutableListOf<RestDrop>()

        segments.forEachIndexed { index, segment ->
            if (segment.type != SegmentType.REST) return@forEachIndexed
            // Only rests that follow actual work: the opening rest before a session starts is not a
            // recovery from anything.
            val previous = segments.getOrNull(index - 1) ?: return@forEachIndexed
            if (previous.type != SegmentType.ACTIVE) return@forEachIndexed

            val restStart = segment.startMs
            val restEnd = segment.endMs ?: return@forEachIndexed
            val restLen = restEnd - restStart
            // Too short to read anything into.
            if (restLen < MIN_REST_MS) return@forEachIndexed
            // Measure over a full minute when the rest ran that long, otherwise over the whole rest.
            val window = minOf(restLen, WINDOW_MS)

            val peak = peakBefore(ordered, restStart) ?: return@forEachIndexed
            val after = nearest(ordered, restStart + window) ?: return@forEachIndexed

            val drop = peak - after.bpm
            // A rise isn't a recovery measurement — it's a sensor artefact or you kept moving.
            if (drop > 0) {
                drops += RestDrop(
                    afterExercise = previous.exerciseName,
                    peakBpm = peak,
                    endBpm = after.bpm,
                    dropBpm = drop,
                    atMs = restStart,
                    windowSeconds = (window / 1000).toInt(),
                )
            }
        }

        if (drops.isEmpty()) return null
        // Normalise to bpm/min before averaging so a 40-second rest and a full-minute one compare on
        // the same scale.
        val rates = drops.map { it.perMinuteDrop }
        return HrRecovery(
            meanDropBpm = rates.average().toInt(),
            bestDropBpm = rates.max(),
            measuredRests = drops.size,
            drops = drops,
        )
    }

    /** Highest reading in the run-up to [atMs] — what the heart was actually recovering from. */
    private fun peakBefore(ordered: List<HeartRateSample>, atMs: Long): Int? =
        ordered.filter { it.timestampMs in (atMs - PEAK_LOOKBACK_MS)..atMs }
            .maxOfOrNull { it.bpm }

    /** The reading closest to [atMs], provided one landed near enough to mean anything. */
    private fun nearest(ordered: List<HeartRateSample>, atMs: Long): HeartRateSample? =
        ordered.minByOrNull { kotlin.math.abs(it.timestampMs - atMs) }
            ?.takeIf { kotlin.math.abs(it.timestampMs - atMs) <= TOLERANCE_MS }
}
