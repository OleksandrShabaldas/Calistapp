package com.calistapp.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted workout. HR samples, segments, and the computed summary are stored as JSON
 * columns (serialized in the mapper). At this app's scale that's simpler and fully queryable
 * enough; if per-sample SQL queries are ever needed we can normalize into child tables without
 * touching the domain model.
 */
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
)
