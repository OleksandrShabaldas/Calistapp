package com.calistapp.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A day's step data imported FROM FitPal. Calistapp never computes step-calories itself — FitPal
 * owns the formula (steps × 0.04 × weight/70) and its user-set over-count trim, so we store the
 * numbers FitPal hands over verbatim ([calories] is already post-trim). One row per day; a re-import
 * replaces the row (upsert on the [date] key).
 */
@Entity(tableName = "step_days")
data class StepDayEntity(
    @PrimaryKey val date: String,          // "YYYY-MM-DD"
    val steps: Int,
    /** Calories from steps, already trimmed by FitPal's reduction %. Stored as-is, never recomputed. */
    val calories: Double,
    /** The reduction % FitPal had applied when this was imported (for display/transparency). */
    val reductionPercent: Int,
    val importedAtMs: Long = System.currentTimeMillis(),
)
