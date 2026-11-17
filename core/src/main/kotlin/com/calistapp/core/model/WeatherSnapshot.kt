package com.calistapp.core.model

import kotlinx.serialization.Serializable

/**
 * The weather at the moment a session started, kept with the session so anything that reads history
 * later (the cold-day medal, the coach) sees the conditions it was actually done in, not today's.
 * Deliberately small: what was felt, not a full forecast.
 */
@Serializable
data class WeatherSnapshot(
    val tempC: Double,
    /** Apparent temperature: air temperature with wind chill and humidity folded in. */
    val feelsLikeC: Double,
    val raining: Boolean = false,
)
