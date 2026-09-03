package com.calistapp.app.data.recommend

import com.calistapp.app.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/** Current conditions for one point, assembled from Open-Meteo's forecast + air-quality feeds. */
data class WeatherSnapshot(
    val tempC: Double,
    val apparentTempC: Double,
    val weatherCode: Int,
    val weatherText: String,
    val windKph: Double,
    val precipMm: Double,
    val uvIndex: Double?,
    val pm25: Double?,
    val usAqi: Int?,
) {
    /** "12°·rain" — the glanceable line under the conditions gauge. */
    val glance: String get() = "${tempC.roundToIntSafe()}°·$weatherText"
}

private fun Double.roundToIntSafe(): Int = Math.round(this).toInt()

/**
 * Weather, UV and air quality from **Open-Meteo** — free, no API key, no rate limit. Two calls: the
 * forecast feed for temperature/wind/precip/weather-code, and the air-quality feed for UV index,
 * PM2.5 and the US AQI. The forecast is required; air quality is best-effort (its fields stay null if
 * that call fails), because "it's raining" is still useful guidance without an AQI.
 */
@Singleton
class WeatherRepository @Inject constructor(
    private val okHttp: OkHttpClient,
    private val json: Json,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend fun current(latitude: Double, longitude: Double): WeatherSnapshot? = withContext(io) {
        val forecast = get(
            "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$latitude&longitude=$longitude" +
                "&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m,precipitation" +
                "&wind_speed_unit=kmh&timezone=auto",
        )?.let { runCatching { json.decodeFromString(ForecastDto.serializer(), it) }.getOrNull() }
            ?.current ?: return@withContext null

        val air = get(
            "https://air-quality-api.open-meteo.com/v1/air-quality" +
                "?latitude=$latitude&longitude=$longitude&current=pm2_5,us_aqi,uv_index&timezone=auto",
        )?.let { runCatching { json.decodeFromString(AirDto.serializer(), it) }.getOrNull() }?.current

        WeatherSnapshot(
            tempC = forecast.temp,
            apparentTempC = forecast.apparent,
            weatherCode = forecast.weatherCode,
            weatherText = wmoText(forecast.weatherCode),
            windKph = forecast.wind,
            precipMm = forecast.precipitation,
            uvIndex = air?.uv,
            pm25 = air?.pm25,
            usAqi = air?.usAqi,
        )
    }

    private fun get(url: String): String? = runCatching {
        okHttp.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
            if (resp.isSuccessful) resp.body?.string() else null
        }
    }.getOrNull()

    @Serializable
    private data class ForecastDto(val current: Cur? = null) {
        @Serializable
        data class Cur(
            @SerialName("temperature_2m") val temp: Double = 0.0,
            @SerialName("apparent_temperature") val apparent: Double = 0.0,
            @SerialName("weather_code") val weatherCode: Int = 0,
            @SerialName("wind_speed_10m") val wind: Double = 0.0,
            val precipitation: Double = 0.0,
        )
    }

    @Serializable
    private data class AirDto(val current: Cur? = null) {
        @Serializable
        data class Cur(
            @SerialName("pm2_5") val pm25: Double? = null,
            @SerialName("us_aqi") val usAqi: Int? = null,
            @SerialName("uv_index") val uv: Double? = null,
        )
    }

    private companion object {
        /** WMO weather-interpretation codes → a one-word gloss. */
        fun wmoText(code: Int): String = when (code) {
            0 -> "clear"
            1, 2 -> "partly cloudy"
            3 -> "overcast"
            45, 48 -> "fog"
            51, 53, 55, 56, 57 -> "drizzle"
            61, 63, 65, 66, 67 -> "rain"
            71, 73, 75, 77 -> "snow"
            80, 81, 82 -> "showers"
            85, 86 -> "snow showers"
            95, 96, 99 -> "storm"
            else -> "cloudy"
        }
    }
}
