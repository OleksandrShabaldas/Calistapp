package com.calistapp.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class SessionTimingTest {
    @Test fun `old opening rest is warmup and gaps stay paused`() {
        val segments = listOf(
            Segment(SegmentType.REST, 0, 60_000),
            Segment(SegmentType.REST, 90_000, 150_000),
            Segment(SegmentType.ACTIVE, 150_000, 180_000),
            Segment(SegmentType.REST, 180_000, 210_000),
        )
        val original = SessionSummary.EMPTY.copy(totalKcal = 42.0, activeKcal = 30.0, restKcal = 12.0)
        val summary = original.withSessionTiming(segments.reversed(), 210_000)
        assertEquals(120_000L, summary.warmupDurationMs)
        assertEquals(30_000L, summary.restDurationMs)
        assertEquals(30_000L, summary.activeDurationMs)
        assertEquals(180_000L, summary.totalDurationMs)
        assertEquals(original.totalKcal, summary.totalKcal, 0.0)
        assertEquals(summary, summary.withSessionTiming(segments, 210_000))
        assertEquals(summary, Json.decodeFromString<SessionSummary>(Json.encodeToString(SessionSummary.serializer(), summary)))
    }

    @Test fun `warmup only and no sensor session still has duration`() {
        val summary = SessionSummary.EMPTY.withSessionTiming(listOf(Segment(SegmentType.REST, 0)), 60_000)
        assertEquals(60_000L, summary.warmupDurationMs)
        assertEquals(0L, summary.restDurationMs)
    }

    @Test fun `session starting with work has no opening warmup`() {
        val summary = SessionSummary.EMPTY.withSessionTiming(listOf(
            Segment(SegmentType.ACTIVE, 0, 30_000), Segment(SegmentType.REST, 30_000, 90_000),
        ), 90_000)
        assertEquals(0L, summary.warmupDurationMs)
        assertEquals(60_000L, summary.restDurationMs)
    }
}
