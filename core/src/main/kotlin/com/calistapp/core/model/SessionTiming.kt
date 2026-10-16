package com.calistapp.core.model

/** The opening preparation ends at the first working segment, even across pauses or skipped slots. */
fun List<Segment>.openingWarmupSegments(): List<Segment> {
    val firstWork = filter { it.type == SegmentType.ACTIVE }.minOfOrNull { it.startMs } ?: Long.MAX_VALUE
    return filter { it.type == SegmentType.REST && it.startMs < firstWork }
}

/** Wall-clock phase durations, independent of HR coverage. Gaps between segments are pauses. */
fun SessionSummary.withSessionTiming(segments: List<Segment>, endMs: Long?): SessionSummary {
    if (segments.isEmpty()) return this
    val end = endMs ?: segments.mapNotNull { it.endMs }.maxOrNull() ?: return this
    val warmup = segments.openingWarmupSegments().toSet()
    fun duration(s: Segment) = (minOf(s.endMs ?: end, end) - s.startMs).coerceAtLeast(0)
    return copy(
        activeDurationMs = segments.filter { it.type == SegmentType.ACTIVE }.sumOf(::duration),
        restDurationMs = segments.filter { it.type == SegmentType.REST && it !in warmup }.sumOf(::duration),
        warmupDurationMs = warmup.sumOf(::duration),
    )
}
