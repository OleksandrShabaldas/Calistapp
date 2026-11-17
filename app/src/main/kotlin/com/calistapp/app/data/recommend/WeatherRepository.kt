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
    val gustKph: Double? = null,
    /** Rain over the previous ~3 hours — bars stay wet after it stops. */
    val recentPrecipMm: Double? = null,
    val isDay: Boolean? = null,
) {
    /** What a session keeps of these conditions: what was felt, and whether it was raining. */
    fun toSessionWeather() = com.calistapp.core.model.WeatherSnapshot(
        tempC = tempC,
        feelsLikeC = apparentTempC,
        raining = precipMm >= 0.1,
    )

    /** "12° · rain" — the glanceable line under the conditions gauge. */
    val glance: String get() = "${tempC.roundToIntSafe()}° · $weatherText"

    fun toAdviceInput() = com.calistapp.core.weather.WeatherInput(
        tempC = tempC,
        feelsLikeC = apparentTempC,
        windKph = windKph,
        precipMm = precipMm,
        weatherCode = weatherCode,
        recentPrecipMm = recentPrecipMm,
        gustKph = gustKph,
        uvIndex = uvIndex,
        usAqi = usAqi,
        isDay = isDay,
    )
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
    @Volatile private var last: Pair<Long, WeatherSnapshot>? = null

    /**
     * The conditions fetched most recently, when they are no older than [maxAgeMs]. A workout started now
     * keeps these as the weather it was done in — the dashboard fetches them on every open, so starting
     * a session never waits on the network.
     */
    fun recent(maxAgeMs: Long = RECENT_MAX_AGE_MS): WeatherSnapshot? =
        last?.takeIf { System.currentTimeMillis() - it.first in 0..maxAgeMs }?.second

    suspend fun current(latitude: Double, longitude: Double): WeatherSnapshot? = withContext(io) {
        val forecastDto = get(
            "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$latitude&longitude=$longitude" +
                "&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m,wind_gusts_10m,precipitation,is_day" +
                "&hourly=precipitation&past_hours=3&forecast_hours=1" +
                "&wind_speed_unit=kmh&timezone=auto",
        )?.let { runCatching { json.decodeFromString(ForecastDto.serializer(), it) }.getOrNull() }
        val forecast = forecastDto?.current ?: return@withContext null
        // past_hours=3 + forecast_hours=1: the first three hourly values are the hours before this one.
        val recentPrecip = forecastDto.hourly?.precipitation?.let { hours ->
            if (hours.size > 1) hours.dropLast(1).filterNotNull().sum() else null
        }

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
            gustKph = forecast.gust,
            recentPrecipMm = recentPrecip,
            isDay = forecast.isDay?.let { it == 1 },
        ).also { last = System.currentTimeMillis() to it }
    }

    private fun get(url: String): String? = runCatching {
        okHttp.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
            if (resp.isSuccessful) resp.body?.string() else null
        }
    }.getOrNull()

    @Serializable
    private data class ForecastDto(val current: Cur? = null, val hourly: Hourly? = null) {
        @Serializable
        data class Cur(
            @SerialName("temperature_2m") val temp: Double = 0.0,
            @SerialName("apparent_temperature") val apparent: Double = 0.0,
            @SerialName("weather_code") val weatherCode: Int = 0,
            @SerialName("wind_speed_10m") val wind: Double = 0.0,
            @SerialName("wind_gusts_10m") val gust: Double? = null,
            val precipitation: Double = 0.0,
            @SerialName("is_day") val isDay: Int? = null,
        )

        @Serializable
        data class Hourly(val precipitation: List<Double?>? = null)
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

/** Older than this, the last fetched conditions no longer describe a session starting now. */
private const val RECENT_MAX_AGE_MS = 3 * 60 * 60_000L
