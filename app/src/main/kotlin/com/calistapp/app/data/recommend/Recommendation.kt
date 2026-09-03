package com.calistapp.app.data.recommend

/**
 * The two AI-interpreted calls the dashboard's "Training recommendations" widget shows.
 *
 * Kept in the data layer (not the UI) so the repository can produce them without depending on
 * Compose. The dashboard reads [RecommendationState] straight off the repository.
 */
data class Recommendation(
    val readiness: Readiness,
    val conditions: Conditions,
    /** When this was generated (epoch millis), for the cache and a "just now / 2h ago" note. */
    val generatedAt: Long,
)

/** One input that fed a recommendation, normalised for a progress bar in the detail card. */
data class RecFactor(
    val label: String,
    /** The human-readable value, e.g. "7.2 h", "UV 5.1", "AQI 42". */
    val value: String,
    /** 0..1 fill for the bar. */
    val progress: Float,
)

/** "Should I train today?" — 0 (prioritise rest) … 100 (perfect day to train). */
data class Readiness(
    val score: Int,
    /** A short verb the gauge shows: "Train", "Take it easy", "Rest". */
    val label: String,
    /** One or two sentences explaining the score (sleep, recent load, days off). */
    val reason: String,
    /** The inputs it weighed — sleep, recovery, load — for the detail card's bars. */
    val factors: List<RecFactor> = emptyList(),
)

/** "Indoors or out?" — from weather, UV and air quality. */
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

sealed interface RecommendationState {
    data object Loading : RecommendationState
    data class Ready(val rec: Recommendation) : RecommendationState
    /** The AI or its inputs failed; [message] is safe to show. */
    data class Failed(val message: String) : RecommendationState
}
