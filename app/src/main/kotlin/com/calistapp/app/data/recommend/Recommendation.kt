package com.calistapp.app.data.recommend

import kotlinx.serialization.Serializable

/**
 * The two AI-interpreted reads the dashboard shows — **should I train today** (readiness) and
 * **indoors or out** (conditions). Serializable so each can be persisted and re-used without a fresh
 * Gemini call: readiness is regenerated once per calendar day, conditions at most every few hours.
 */

/** One input that fed a recommendation, normalised for a progress bar in the detail card. */
@Serializable
data class RecFactor(
    val label: String,
    /** The human-readable value, e.g. "7.2 h", "UV 5.1", "AQI 42". */
    val value: String,
    /** 0..1 fill for the bar. */
    val progress: Float,
)

/** "Should I train today?" — 0 (prioritise rest) … 100 (perfect day to train). */
@Serializable
data class Readiness(
    val score: Int,
    /** A short verb the gauge shows: "Train", "Take it easy", "Rest". */
    val label: String,
    /** A few sentences explaining the score — the reasoning the detail card shows. */
    val reason: String,
    /** The inputs it weighed — sleep, recovery, load — for the detail card's bars. */
    val factors: List<RecFactor> = emptyList(),
)

/** "Indoors or out?" — from weather, UV and air quality. */
@Serializable
data class Conditions(
    /** "Indoor", "Outdoor", "Outdoor · SPF", or "Add location" when no coordinates. */
    val label: String,
    /** The glanceable detail under the label: "12°·rain", "23°·clear". */
    val detail: String,
    val reason: String,
    /** True only for the "location is off" prompt, so the card can offer to enable it. */
    val needsLocation: Boolean = false,
    /** The inputs it weighed — temperature, UV, air, wind — for the detail card's bars. */
    val factors: List<RecFactor> = emptyList(),
)

/**
 * What the dashboard reads. The two halves resolve independently, so each gauge can show its own
 * "loading…" while it's being generated rather than obsolete or fake data.
 */
data class RecommendationsUi(
    val readiness: Readiness? = null,
    val readinessLoading: Boolean = true,
    val conditions: Conditions? = null,
    val conditionsLoading: Boolean = true,
)
