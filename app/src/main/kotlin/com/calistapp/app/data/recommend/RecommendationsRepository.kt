package com.calistapp.app.data.recommend

import com.calistapp.app.data.ai.AiModelTier
import com.calistapp.app.data.ai.AiResult
import com.calistapp.app.data.ai.GeminiClient
import com.calistapp.app.data.profile.ProfileRepository
import com.calistapp.app.data.session.SessionRepository
import com.calistapp.app.di.IoDispatcher
import com.calistapp.core.progress.TrainingLoad
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Produces the dashboard's two recommendations — **should I train today** (readiness) and **indoors
 * or out** (conditions) — by feeding sleep, recent training load and live weather to Gemini and
 * letting it interpret them (the user's explicit ask: an LLM reads the data better than a hard-coded
 * formula). Everything degrades: no sleep permission → readiness from load alone; no location →
 * conditions become a prompt to enable it; the AI failing → a plain heuristic, never an empty card.
 *
 * The result is cached (weather moves hourly, sleep nightly) so opening the app doesn't burn a Gemini
 * call every time; [refresh] with `force = true` bypasses the cache.
 */
@Singleton
class RecommendationsRepository @Inject constructor(
    private val gemini: GeminiClient,
    private val weather: WeatherRepository,
    private val sleep: SleepRepository,
    private val location: LocationProvider,
    private val sessionRepository: SessionRepository,
    private val profileRepository: ProfileRepository,
    private val json: Json,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val zone: ZoneId = ZoneId.systemDefault()
    private val _state = MutableStateFlow<RecommendationState>(RecommendationState.Loading)
    val state: StateFlow<RecommendationState> = _state.asStateFlow()

    private var cached: Recommendation? = null
    private val mutex = Mutex()

    suspend fun refresh(force: Boolean = false): Unit = withContext(io) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val fresh = cached?.takeIf { !force && now - it.generatedAt < CACHE_MS }
            if (fresh != null) {
                _state.value = RecommendationState.Ready(fresh)
                return@withLock
            }
            if (_state.value !is RecommendationState.Ready) _state.value = RecommendationState.Loading

            val sleepSnap = runCatching { sleep.lastNight() }.getOrNull()
            val coords = runCatching { location.currentOrLast() }.getOrNull()
            val weatherSnap = coords?.let { runCatching { weather.current(it.latitude, it.longitude) }.getOrNull() }
            val load = gatherLoad()

            val rec = generate(sleepSnap, weatherSnap, load)
            cached = rec
            _state.value = RecommendationState.Ready(rec)
        }
    }

    // ---- training load ----

    private data class LoadSummary(
        val daysSinceLast: Long?,
        val acute7: Double,
        val ramp: TrainingLoad.Ramp?,
        val sessionsLast28: Int,
    )

    private suspend fun gatherLoad(): LoadSummary {
        val sessions = sessionRepository.observeSessions().first()
        val profile = profileRepository.profile.first()
        val today = LocalDate.now(zone)
        val perDay = DoubleArray(TrainingLoad.CHRONIC_DAYS)
        var sessionsLast28 = 0
        sessions.forEach { s ->
            val day = Instant.ofEpochMilli(s.startMs).atZone(zone).toLocalDate()
            val idx = ChronoUnit.DAYS.between(day, today).toInt()
            if (idx in perDay.indices) {
                perDay[idx] += TrainingLoad.trimp(s.avgHr, s.activeDurationMs, profile)
                sessionsLast28++
            }
        }
        val loadsDesc = perDay.toList()
        val daysSinceLast = sessions.maxOfOrNull { it.startMs }?.let {
            ChronoUnit.DAYS.between(Instant.ofEpochMilli(it).atZone(zone).toLocalDate(), today)
        }
        return LoadSummary(daysSinceLast, loadsDesc.take(TrainingLoad.ACUTE_DAYS).sum(), TrainingLoad.ramp(loadsDesc), sessionsLast28)
    }

    // ---- generation ----

    private suspend fun generate(sleepSnap: SleepSnapshot?, weatherSnap: WeatherSnapshot?, load: LoadSummary): Recommendation {
        val prompt = buildPrompt(sleepSnap, weatherSnap, load)
        val parsed = when (val r = gemini.generate(prompt, AiModelTier.THINKING)) {
            is AiResult.Success -> parse(r.text)
            is AiResult.Failure -> null
        }
        val readiness = parsed?.readiness?.let {
            Readiness(it.score.coerceIn(0, 100), it.label.ifBlank { labelFor(it.score) }, it.reason.ifBlank { "" })
        } ?: heuristicReadiness(sleepSnap, load)

        val conditions = when {
            weatherSnap == null -> noLocationConditions()
            parsed?.conditions != null && parsed.conditions.label.isNotBlank() ->
                Conditions(parsed.conditions.label, parsed.conditions.detail.ifBlank { weatherSnap.glance }, parsed.conditions.reason)
            else -> heuristicConditions(weatherSnap)
        }
        return Recommendation(readiness, conditions, System.currentTimeMillis())
    }

    private fun buildPrompt(sleepSnap: SleepSnapshot?, weatherSnap: WeatherSnapshot?, load: LoadSummary): String = buildString {
        appendLine("You are a sports scientist giving a short daily check-in.")
        appendLine("Respond with ONLY minified JSON, no markdown:")
        appendLine("""{"readiness":{"score":<0-100 int>,"label":"<Train|Take it easy|Rest>","reason":"<max 110 chars>"},""")
        if (weatherSnap != null) {
            appendLine(""" "conditions":{"label":"<Indoor|Outdoor|Outdoor · SPF>","detail":"<glanceable e.g. 12°·rain>","reason":"<max 110 chars>"}}""")
        } else {
            appendLine(""" "conditions":{"label":"","detail":"","reason":""}}""")
        }
        appendLine()
        appendLine("READINESS — how ready is this person to train hard today, 0 (rest) to 100 (great day)?")
        appendLine("- Sleep last night: " + (sleepSnap?.let {
            "%.1f h".format(it.hours) + (it.deepRemFraction?.let { f -> ", ${(f * 100).roundToInt()}% deep+REM" } ?: "")
        } ?: "not available"))
        appendLine("- Days since last workout: " + (load.daysSinceLast?.toString() ?: "no recent history"))
        appendLine("- Training load: last 7d TRIMP ${load.acute7.roundToInt()}, " + (load.ramp?.let {
            "28d average week ${it.chronicLoad.roundToInt()}, trend ${it.band.label} (${it.band.detail})"
        } ?: "not enough history for a trend"))
        appendLine("- Sessions in the last 4 weeks: ${load.sessionsLast28}")
        appendLine("Interpret holistically: short/poor sleep or a sharp load jump lowers it; well-rested after adequate rest raises it. Regular trainers need only 1-2 rest days; someone returning after a long layoff needs more caution.")
        if (weatherSnap != null) {
            appendLine()
            appendLine("CONDITIONS — indoor vs outdoor training right now:")
            appendLine("- ${weatherSnap.tempC.roundToInt()}°C (feels ${weatherSnap.apparentTempC.roundToInt()}°C), ${weatherSnap.weatherText}, wind ${weatherSnap.windKph.roundToInt()} km/h, precip ${"%.1f".format(weatherSnap.precipMm)} mm")
            appendLine("- UV index ${weatherSnap.uvIndex?.let { "%.1f".format(it) } ?: "n/a"}, PM2.5 ${weatherSnap.pm25?.let { "${it.roundToInt()} µg/m³" } ?: "n/a"}, US AQI ${weatherSnap.usAqi ?: "n/a"}")
            appendLine("Recommend Indoor when rain/storm/poor air (AQI>100)/temperature extremes; Outdoor when pleasant. When UV index >= 6 use label \"Outdoor · SPF\" and mention sunscreen in the reason. Keep detail short like \"23°·clear\".")
        }
    }

    @Serializable
    private data class RecDto(val readiness: ReadinessDto? = null, val conditions: ConditionsDto? = null) {
        @Serializable data class ReadinessDto(val score: Int = 0, val label: String = "", val reason: String = "")
        @Serializable data class ConditionsDto(val label: String = "", val detail: String = "", val reason: String = "")
    }

    private fun parse(text: String): RecDto? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { json.decodeFromString(RecDto.serializer(), text.substring(start, end + 1)) }.getOrNull()
    }

    // ---- fallbacks (used only when the AI is unavailable) ----

    private fun heuristicReadiness(sleepSnap: SleepSnapshot?, load: LoadSummary): Readiness {
        var score = 65
        sleepSnap?.let { score += ((it.hours - 7.0) * 8).roundToInt().coerceIn(-30, 20) }
        when (load.daysSinceLast) {
            null -> {}
            0L -> score -= 20
            1L -> score -= 5
            in 2..3 -> score += 10
            else -> score += 5
        }
        load.ramp?.let { if (it.band == TrainingLoad.Band.SHARP_JUMP) score -= 15 }
        score = score.coerceIn(5, 98)
        val reason = when {
            sleepSnap == null -> "Based on recent training; connect sleep for a sharper read."
            sleepSnap.hours < 6 -> "Short sleep last night — keep it light or rest."
            else -> "Sleep and recent load both look reasonable."
        }
        return Readiness(score, labelFor(score), reason)
    }

    private fun heuristicConditions(w: WeatherSnapshot): Conditions {
        val poorAir = (w.usAqi ?: 0) > 100
        val wet = w.precipMm > 0.2 || w.weatherCode in setOf(61, 63, 65, 80, 81, 82, 95, 96, 99)
        val highUv = (w.uvIndex ?: 0.0) >= 6.0
        return when {
            wet -> Conditions("Indoor", w.glance, "Wet out there — better indoors today.")
            poorAir -> Conditions("Indoor", w.glance, "Air quality is poor right now (AQI ${w.usAqi}).")
            highUv -> Conditions("Outdoor · SPF", w.glance, "Great for outdoors — UV is high, wear sunscreen.")
            else -> Conditions("Outdoor", w.glance, "Pleasant conditions for training outside.")
        }
    }

    private fun noLocationConditions(): Conditions = Conditions(
        label = "Add location",
        detail = "",
        reason = if (location.hasPermission()) "Couldn't get a location fix — tap to retry."
        else "Turn on location for indoor/outdoor guidance.",
        needsLocation = true,
    )

    private fun labelFor(score: Int): String = when {
        score >= 70 -> "Train"
        score >= 40 -> "Take it easy"
        else -> "Rest"
    }

    private companion object {
        /** Weather moves hourly and sleep nightly, so a two-hour cache is plenty. */
        const val CACHE_MS = 2 * 60 * 60 * 1000L
    }
}
