package com.calistapp.core.model

/**
 * Merge [incoming] readings into this time-ordered list, dropping exact duplicates.
 *
 * Readings no longer arrive one at a time in order: the watch delivers them in batches while its
 * screen is off, resends a batch whose message failed, and backfills gaps on request — so each reading
 * is placed by when it was *measured*, never appended in arrival order, and a resent one is ignored.
 *
 * @return how many readings were new.
 */
fun MutableList<HeartRateSample>.mergeReadings(incoming: List<HeartRateSample>): Int {
    var added = 0
    for (sample in incoming.sortedBy { it.timestampMs }) {
        if (isEmpty() || sample.timestampMs > last().timestampMs) {
            add(sample)
            added++
            continue
        }
        val at = binarySearchBy(sample.timestampMs) { it.timestampMs }
        if (at >= 0) continue
        add(-(at + 1), sample)
        added++
    }
    return added
}

/**
 * How well the watch → phone heart-rate link is doing, for the live HR panel. Exists so the next
 * workout can *show* whether the stream is continuous, rather than leaving it to the post-workout chart.
 */
data class HrLinkHealth(
    /** Readings measured in the last minute — ~60 when the stream is healthy. */
    val readingsLastMinute: Int = 0,
    /** How old the newest reading is, or null before the first one. */
    val newestAgeMs: Long? = null,
    /** The longest stretch of the session so far with no reading at all. */
    val longestGapMs: Long = 0,
    /** Readings that arrived out of order and were slotted back in — resends and backfills. */
    val backfilled: Int = 0,
)

/** [HrLinkHealth] for a time-ordered reading list as of [nowMs]. */
fun linkHealthOf(samples: List<HeartRateSample>, nowMs: Long, backfilled: Int): HrLinkHealth {
    if (samples.isEmpty()) return HrLinkHealth(backfilled = backfilled)
    var lastMinute = 0
    for (i in samples.indices.reversed()) {
        if (samples[i].timestampMs <= nowMs - 60_000L) break
        lastMinute++
    }
    return HrLinkHealth(
        readingsLastMinute = lastMinute,
        newestAgeMs = (nowMs - samples.last().timestampMs).coerceAtLeast(0),
        longestGapMs = samples.zipWithNext { a, b -> b.timestampMs - a.timestampMs }.maxOrNull() ?: 0L,
        backfilled = backfilled,
    )
}
