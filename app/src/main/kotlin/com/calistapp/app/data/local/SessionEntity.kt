package com.calistapp.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted workout. HR samples, segments, and the computed summary are stored as JSON
 * columns (serialized in the mapper). At this app's scale that's simpler and fully queryable
 * enough; if per-sample SQL queries are ever needed we can normalize into child tables without
 * touching the domain model.
 */
/**
 * The columns a list row needs, and no more.
 *
 * Room projects straight into this, so the heart-rate JSON never leaves the database for a screen
 * that only shows a date and a calorie count. See [SessionDao.observeOverviews].
 */
data class SessionOverviewRow(
    val id: String,
    val exerciseType: String,
    val startMs: Long,
    val endMs: Long?,
    val exerciseName: String?,
    val summaryJson: String?,
)

/**
 * What a trend needs: when it happened, what it scored, and what was performed.
 *
 * Still no heart-rate samples — those are the bulk of a stored session and no part of a progress
 * chart. The plan comes along because added load lives on its slots rather than on the set log.
 */
data class SessionPerformanceRow(
    val id: String,
    val startMs: Long,
    val summaryJson: String?,
    val setLogsJson: String,
    val planJson: String,
)

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val exerciseType: String,
    val startMs: Long,
    val endMs: Long?,
    val status: String,
    val exerciseName: String?,
    val notes: String,
    val samplesJson: String,
    val segmentsJson: String,
    val summaryJson: String?,
    val aiInsight: String?,
    val aiInsightAtMs: Long?,
    /** The workout this session was built from. Empty JSON object for free-form sessions. */
    val planJson: String = "",
    /** Sets actually performed, set by set. */
    val setLogsJson: String = "",
    /** Borg CR10 rating of perceived exertion, 1–10. Null when the session wasn't rated. */
    val rpe: Int? = null,
)
