package com.calistapp.app.data.recommend

import com.calistapp.app.data.ai.AiModelTier
import com.calistapp.app.data.ai.AiResult
import com.calistapp.app.data.ai.GeminiClient
import com.calistapp.app.data.fitpal.StepsImportRepository
import com.calistapp.app.data.profile.ProfileRepository
import com.calistapp.app.data.session.SessionRepository
import com.calistapp.app.di.IoDispatcher
import com.calistapp.core.model.UserProfile
import com.calistapp.core.progress.PerformedSession
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
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Produces the dashboard's two recommendations — **should I train today** (readiness) and **indoors
 * or out** (conditions) — by handing the whole picture to Gemini's reasoning model in ONE call and
 * letting it interpret it (the user's ask: an LLM reads it more mindfully than a formula, and one
 * pass respects the free-tier limits).
 *
 * Readiness weighs a broad set of evidence: sleep quantity *and* quality (stage split, sleeping heart
 * rate and HRV, schedule consistency), recent training load and how today's intensity compares to
 * normal, the athlete's own RPE and notes, and general daily activity. Everything degrades: missing
 * inputs are simply stated as unavailable; the AI failing falls back to a plain heuristic. Cached so
 * opening the app doesn't burn a call each time; [refresh]`(force = true)` bypasses it.
 */
@Singleton
class RecommendationsRepository @Inject constructor(
    private val gemini: GeminiClient,
    private val weather: WeatherRepository,
    private val sleep: SleepRepository,
    private val location: LocationProvider,
    private val sessionRepository: SessionRepository,
    private val profileRepository: ProfileRepository,
    private val stepsImportRepository: StepsImportRepository,
    private val json: Json,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val zone: ZoneId = ZoneId.systemDefault()
    private val iso = DateTimeFormatter.ISO_LOCAL_DATE
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
            val exercise = gatherExercise()
            val activity = gatherActivity()

            val rec = generate(sleepSnap, weatherSnap, exercise, activity)
            cached = rec
            _state.value = RecommendationState.Ready(rec)
        }
    }

    // ---- context gathering ----

    private data class ExerciseContext(
        val daysSinceLast: Long?,
        val acute7: Double,
        val ramp: TrainingLoad.Ramp?,
        val sessionsLast28: Int,
        val lastIntensityPct: Int?,
        val usualIntensityPct: Int?,
        val lastRpe: Int?,
        val recentNote: String?,
        val avgSessionMinutes: Int?,
    )

    private data class ActivityContext(
        val todaySteps: Int?,
        val avg7dSteps: Int?,
        val activeDays7: Int?,
        val stepCv: Double?,
    )

    private suspend fun gatherExercise(): ExerciseContext {
        val performed = sessionRepository.observePerformed().first() // newest first, no HR stream
        val profile = profileRepository.profile.first()
        val today = LocalDate.now(zone)
        val perDay = DoubleArray(TrainingLoad.CHRONIC_DAYS)
        var sessions28 = 0
        performed.forEach { p ->
            val day = Instant.ofEpochMilli(p.startMs).atZone(zone).toLocalDate()
            val idx = ChronoUnit.DAYS.between(day, today).toInt()
            if (idx in perDay.indices) {
                perDay[idx] += TrainingLoad.trimp(p.avgHr, p.totalDurationMs, profile)
                sessions28++
            }
        }
        val daysSinceLast = performed.maxOfOrNull { it.startMs }?.let {
            ChronoUnit.DAYS.between(Instant.ofEpochMilli(it).atZone(zone).toLocalDate(), today)
        }
        val recent = performed.take(8)
        val last = performed.firstOrNull()
        return ExerciseContext(
            daysSinceLast = daysSinceLast,
            acute7 = perDay.toList().take(TrainingLoad.ACUTE_DAYS).sum(),
            ramp = TrainingLoad.ramp(perDay.toList()),
            sessionsLast28 = sessions28,
            lastIntensityPct = last?.let { intensityPct(it.avgHr, profile) },
            usualIntensityPct = recent.drop(1).mapNotNull { intensityPct(it.avgHr, profile) }.avgOrNull()?.roundToInt(),
            lastRpe = last?.rpe,
            recentNote = performed.firstOrNull { it.notes.isNotBlank() }?.notes?.trim()?.take(160),
            avgSessionMinutes = recent.mapNotNull { (it.totalDurationMs / 60_000L).toInt().takeIf { m -> m > 0 } }.avgOrNull()?.roundToInt(),
        )
    }

    private fun intensityPct(avgHr: Int, profile: UserProfile): Int? {
        val reserve = profile.effectiveMaxHr - profile.restingHr
        if (reserve <= 0 || avgHr <= 0) return null
        return (((avgHr - profile.restingHr).toDouble() / reserve) * 100).roundToInt().coerceIn(0, 100)
    }

    private suspend fun gatherActivity(): ActivityContext {
        val today = LocalDate.now(zone)
        val steps = runCatching {
            stepsImportRepository.observeRange(today.minusDays(8).format(iso), today.format(iso)).first()
        }.getOrDefault(emptyList())
        val byDate = steps.associateBy { it.date }
        val last7 = (1..7).mapNotNull { byDate[today.minusDays(it.toLong()).format(iso)]?.steps }
        val avg7 = last7.avgOrNull()
        return ActivityContext(
            todaySteps = byDate[today.format(iso)]?.steps,
            avg7dSteps = avg7?.roundToInt(),
            activeDays7 = if (last7.isEmpty()) null else last7.count { it >= 5_000 },
            stepCv = if (last7.size >= 3 && avg7 != null && avg7 > 0) stdev(last7.map { it.toDouble() }) / avg7 else null,
        )
    }

    // ---- generation ----

    private suspend fun generate(
        sleepSnap: SleepSnapshot?,
        weatherSnap: WeatherSnapshot?,
        exercise: ExerciseContext,
        activity: ActivityContext,
    ): Recommendation {
        val profile = profileRepository.profile.first()
        val prompt = buildPrompt(sleepSnap, weatherSnap, exercise, activity, profile)
        val parsed = when (val r = gemini.generate(prompt, AiModelTier.THINKING)) {
            is AiResult.Success -> parse(r.text)
            is AiResult.Failure -> null
        }
        val readiness = (parsed?.readiness?.let {
            Readiness(it.score.coerceIn(0, 100), it.label.ifBlank { labelFor(it.score) }, it.reason.ifBlank { "" })
        } ?: heuristicReadiness(sleepSnap, exercise)).copy(factors = buildReadinessFactors(sleepSnap, exercise, activity))

        val conditions = when {
            weatherSnap == null -> noLocationConditions()
            parsed?.conditions != null && parsed.conditions.label.isNotBlank() ->
                Conditions(parsed.conditions.label, parsed.conditions.detail.ifBlank { weatherSnap.glance }, parsed.conditions.reason)
            else -> heuristicConditions(weatherSnap)
        }.copy(factors = weatherSnap?.let { buildConditionsFactors(it) } ?: emptyList())
        return Recommendation(readiness, conditions, System.currentTimeMillis())
    }

    private fun buildPrompt(
        s: SleepSnapshot?,
        weatherSnap: WeatherSnapshot?,
        e: ExerciseContext,
        a: ActivityContext,
        p: UserProfile,
    ): String = buildString {
        appendLine("You are an expert sports scientist and recovery coach giving ONE athlete a short, mindful daily check-in.")
        appendLine("Weigh ALL the evidence together — sleep quality and quantity, autonomic recovery (sleeping HR and HRV), schedule consistency, recent load and how today compares to their normal, RPE and notes, and daily activity. Prefer nuance over a formula.")
        appendLine("Respond with ONLY minified JSON, no markdown:")
        if (weatherSnap != null) {
            appendLine("""{"readiness":{"score":<0-100 int>,"label":"<Train|Take it easy|Rest>","reason":"<max 120 chars, specific>"},"conditions":{"label":"<Indoor|Outdoor|Outdoor · SPF>","detail":"<glanceable e.g. 12°·rain>","reason":"<max 110 chars>"}}""")
        } else {
            appendLine("""{"readiness":{"score":<0-100 int>,"label":"<Train|Take it easy|Rest>","reason":"<max 120 chars, specific>"},"conditions":{"label":"","detail":"","reason":""}}""")
        }
        appendLine()
        appendLine("ATHLETE: ${p.sex}, ${p.ageYears}y, ${p.weightKg.roundToInt()}kg, resting HR ${p.restingHr}, max HR ${p.effectiveMaxHr}")
        appendLine()
        appendLine("SLEEP (last night):")
        if (s == null) {
            appendLine("- not available (Health Connect not connected)")
        } else {
            append("- Duration: ${"%.1f".format(s.hours)} h")
            if (s.deepHours != null) append(" (deep ${"%.1f".format(s.deepHours)}h, REM ${"%.1f".format(s.remHours ?: 0.0)}h, light ${"%.1f".format(s.lightHours ?: 0.0)}h)")
            appendLine()
            s.deepRemFraction?.let { appendLine("- Restorative (deep+REM) share: ${(it * 100).roundToInt()}%") }
            s.avgHrBpm?.let { appendLine("- Sleeping heart rate: $it bpm (waking resting baseline ${p.restingHr})") }
            s.avgHrvMs?.let { appendLine("- Sleeping HRV (RMSSD): ${it.roundToInt()} ms") }
            if (s.durationStdevH != null) appendLine("- Recent-week consistency: sleep length ±${"%.1f".format(s.durationStdevH)} h, bedtime ±${(s.bedtimeStdevMin ?: 0.0).roundToInt()} min")
        }
        appendLine()
        appendLine("TRAINING:")
        appendLine("- Days since last workout: ${e.daysSinceLast?.toString() ?: "no recent history"}")
        if (e.lastIntensityPct != null) {
            append("- Last session intensity: ${e.lastIntensityPct}% of HR reserve")
            e.usualIntensityPct?.let { append(" (usual ${it}%)") }
            appendLine()
        }
        e.lastRpe?.let { appendLine("- Last session RPE: $it/10") }
        e.avgSessionMinutes?.let { appendLine("- Typical session length: $it min") }
        appendLine("- Load: 7d TRIMP ${e.acute7.roundToInt()}, " + (e.ramp?.let { "28d avg-week ${it.chronicLoad.roundToInt()}, trend ${it.band.label} (${it.band.detail})" } ?: "not enough history for a trend"))
        appendLine("- Sessions in the last 4 weeks: ${e.sessionsLast28}")
        e.recentNote?.let { appendLine("- Athlete's recent note: \"$it\"") }
        appendLine()
        appendLine("DAILY ACTIVITY:")
        appendLine("- Steps today: ${a.todaySteps ?: "n/a"}; 7-day average: ${a.avg7dSteps ?: "n/a"}; active days (≥5k) last week: ${a.activeDays7?.let { "$it/7" } ?: "n/a"}" +
            (a.stepCv?.let { "; consistency: ${if (it < 0.35) "steady" else "variable"}" } ?: ""))
        appendLine()
        appendLine("Guidance: short/poor sleep, low HRV or an elevated sleeping HR, a sharp load jump, or unusually high recent intensity all lower readiness; deep restful sleep, good HRV, adequate rest and steady habits raise it. Regular trainers need only 1-2 rest days; returning after a layoff needs more caution. A high RPE or a 'sore/tired' note is a strong caution even when the numbers look fine.")
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

    private fun heuristicReadiness(s: SleepSnapshot?, e: ExerciseContext): Readiness {
        var score = 65
        s?.let {
            score += ((it.hours - 7.0) * 8).roundToInt().coerceIn(-30, 20)
            it.deepRemFraction?.let { f -> score += ((f - 0.4) * 40).roundToInt().coerceIn(-10, 10) }
        }
        when (e.daysSinceLast) {
            null -> {}
            0L -> score -= 20
            1L -> score -= 5
            in 2..3 -> score += 10
            else -> score += 5
        }
        e.ramp?.let { if (it.band == TrainingLoad.Band.SHARP_JUMP) score -= 15 }
        e.lastRpe?.let { if (it >= 8 && (e.daysSinceLast ?: 9) <= 1) score -= 10 }
        score = score.coerceIn(5, 98)
        val reason = when {
            s == null -> "Based on recent training; connect sleep for a sharper read."
            s.hours < 6 -> "Short sleep last night — keep it light or rest."
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

    // ---- factors: the inputs readiness weighed, for the detail card's bars (top few) ----

    private fun buildReadinessFactors(s: SleepSnapshot?, e: ExerciseContext, a: ActivityContext): List<RecFactor> = buildList {
        if (s != null) {
            add(RecFactor("Sleep", "%.1f h".format(s.hours), (s.hours / 8.0).toFloat().coerceIn(0f, 1f)))
            s.deepRemFraction?.let { add(RecFactor("Deep + REM", "${(it * 100).roundToInt()}%", it.toFloat().coerceIn(0f, 1f))) }
            s.avgHrvMs?.let { add(RecFactor("Sleeping HRV", "${it.roundToInt()} ms", (it / 80.0).toFloat().coerceIn(0f, 1f))) }
            s.avgHrBpm?.let { add(RecFactor("Sleeping HR", "$it bpm", ((70 - it) / 30.0).toFloat().coerceIn(0f, 1f))) }
            s.durationStdevH?.let { add(RecFactor("Sleep consistency", if (it < 0.75) "steady" else "variable", (1 - it / 2.0).toFloat().coerceIn(0f, 1f))) }
        } else {
            add(RecFactor("Sleep", "not connected", 0f))
        }
        e.daysSinceLast?.let {
            add(RecFactor("Recovery", if (it == 0L) "trained today" else "$it day${if (it == 1L) "" else "s"} rest", (it / 3.0).toFloat().coerceIn(0f, 1f)))
        }
        e.ramp?.let {
            val fill = when (it.band) {
                TrainingLoad.Band.EASING_OFF -> 0.3f
                TrainingLoad.Band.STEADY -> 0.55f
                TrainingLoad.Band.RAMPING -> 0.8f
                TrainingLoad.Band.SHARP_JUMP -> 1f
            }
            add(RecFactor("Load trend", it.band.label, fill))
        }
        e.lastIntensityPct?.let { last ->
            val label = e.usualIntensityPct?.let { "$last% vs $it% usual" } ?: "$last% of max"
            add(RecFactor("Last intensity", label, (last / 100f).coerceIn(0f, 1f)))
        }
        a.avg7dSteps?.let { add(RecFactor("Daily activity", "%,d steps/day".format(it), (it / 10_000f).coerceIn(0f, 1f))) }
    }.take(6)

    private fun buildConditionsFactors(w: WeatherSnapshot): List<RecFactor> = buildList {
        add(RecFactor("Temperature", "${w.tempC.roundToInt()}°C", ((w.tempC + 10) / 45.0).toFloat().coerceIn(0f, 1f)))
        w.uvIndex?.let { add(RecFactor("UV index", "%.1f".format(it), (it / 11.0).toFloat().coerceIn(0f, 1f))) }
        w.usAqi?.let { add(RecFactor("Air quality", "AQI $it", (it / 200f).coerceIn(0f, 1f))) }
        add(RecFactor("Wind", "${w.windKph.roundToInt()} km/h", (w.windKph / 40.0).toFloat().coerceIn(0f, 1f)))
        if (w.precipMm > 0.0) add(RecFactor("Rain", "%.1f mm".format(w.precipMm), (w.precipMm / 10.0).toFloat().coerceIn(0f, 1f)))
    }

    private fun labelFor(score: Int): String = when {
        score >= 70 -> "Train"
        score >= 40 -> "Take it easy"
        else -> "Rest"
    }

    private fun List<Int>.avgOrNull(): Double? = if (isEmpty()) null else average()

    private fun stdev(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
    }

    private companion object {
        /** Weather moves hourly and sleep nightly, so a two-hour cache is plenty. */
        const val CACHE_MS = 2 * 60 * 60 * 1000L
    }
}
