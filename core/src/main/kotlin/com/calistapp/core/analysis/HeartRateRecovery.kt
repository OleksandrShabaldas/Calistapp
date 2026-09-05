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

    /** Window after effort ends over which the drop is measured. The standard is one minute. */
    const val WINDOW_MS = 60_000L

    /** How far back before the transition to look for the peak the recovery is measured from. */
    private const val PEAK_LOOKBACK_MS = 30_000L

    /** A reading this far from the target instant is too stale to anchor on. */
    private const val TOLERANCE_MS = 15_000L

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
            // The rest has to have lasted the full window, or the drop is measured over less time
            // than it claims.
            val restEnd = segment.endMs ?: return@forEachIndexed
            if (restEnd - restStart < WINDOW_MS) return@forEachIndexed

            val peak = peakBefore(ordered, restStart) ?: return@forEachIndexed
            val after = nearest(ordered, restStart + WINDOW_MS) ?: return@forEachIndexed

            val drop = peak - after.bpm
            // A rise isn't a recovery measurement — it's a sensor artefact or you kept moving.
            if (drop > 0) {
                drops += RestDrop(
                    afterExercise = previous.exerciseName,
                    peakBpm = peak,
                    endBpm = after.bpm,
                    dropBpm = drop,
                    atMs = restStart,
                )
            }
        }

        if (drops.isEmpty()) return null
        val dropBpms = drops.map { it.dropBpm }
        return HrRecovery(
            meanDropBpm = dropBpms.average().toInt(),
            bestDropBpm = dropBpms.max(),
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
