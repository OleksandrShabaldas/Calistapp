package com.calistapp.app.data.recommend

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.calistapp.app.data.ai.AiModelTier
import com.calistapp.app.data.ai.AiResult
import com.calistapp.app.data.ai.GeminiClient
import com.calistapp.app.data.fitpal.StepsImportRepository
import com.calistapp.app.data.profile.ProfileRepository
import com.calistapp.app.data.session.SessionRepository
import com.calistapp.app.di.IoDispatcher
import com.calistapp.core.model.UserProfile
import com.calistapp.core.progress.TrainingLoad
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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

private val Context.recStore by preferencesDataStore("recommendations")

/**
 * Produces the dashboard's two recommendations, each on its own clock to respect a free-tier Gemini
 * quota:
 * - **Readiness** ("should I train today") — a broad, mindful read of sleep quality, autonomic
 *   recovery, load and daily activity, generated at most **once per calendar day** by the reasoning
 *   (THINKING) model.
 * - **Conditions** ("indoors or out") — generated at most **every 3 hours** by the FAST model.
 *
 * Both regenerate only on app open (via [refresh]); the conditions detail card can force a refresh.
 * Each result is persisted, so re-opening the app the same day reuses the day's readiness rather than
 * burning a call. While either is being generated its gauge shows "loading" instead of stale data.
 */
@Singleton
class RecommendationsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
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

    private val _ui = MutableStateFlow(RecommendationsUi())
    val ui: StateFlow<RecommendationsUi> = _ui.asStateFlow()

    private val mutex = Mutex()
    private var loaded = false
    private var memReadiness: Readiness? = null
    private var memReadinessDay: Long = Long.MIN_VALUE
    private var memConditions: Conditions? = null
    private var memConditionsAt: Long = 0L

    /**
     * Called on app open. Regenerates only what's stale: readiness if the stored one isn't from today,
     * conditions if the stored one is older than 3 hours. [forceConditions] bypasses the conditions
     * cache (the detail card's manual refresh + a fresh location grant).
     */
    suspend fun refresh(forceConditions: Boolean = false, forceReadiness: Boolean = false): Unit = withContext(io) {
        mutex.withLock {
            loadPersistedIfNeeded()
            val todayEpoch = LocalDate.now(zone).toEpochDay()
            val now = System.currentTimeMillis()
            val needReadiness = forceReadiness || memReadiness == null || memReadinessDay != todayEpoch
            val needConditions = forceConditions || memConditions == null || now - memConditionsAt >= WEATHER_INTERVAL_MS

            _ui.value = RecommendationsUi(
                readiness = if (needReadiness) null else memReadiness,
                readinessLoading = needReadiness,
                conditions = if (needConditions) null else memConditions,
                conditionsLoading = needConditions,
            )

            coroutineScope {
                if (needReadiness) launch {
                    val r = generateReadiness()
                    memReadiness = r
                    memReadinessDay = todayEpoch
                    persistReadiness(r, todayEpoch)
                    _ui.update { it.copy(readiness = r, readinessLoading = false) }
                }
                if (needConditions) launch {
                    val c = generateConditions()
                    memConditions = c
                    // Only advance the timer on a real weather read; a "needs location" prompt should
                    // retry next open rather than lock in for 3 hours.
                    if (!c.needsLocation) {
                        memConditionsAt = now
                        persistConditions(c, now)
                    }
                    _ui.update { it.copy(conditions = c, conditionsLoading = false) }
                }
            }
        }
    }

    // ---- persistence ----

    private object Keys {
        val readinessJson = stringPreferencesKey("readiness_json")
        val readinessDay = longPreferencesKey("readiness_day")
        val conditionsJson = stringPreferencesKey("conditions_json")
        val conditionsAt = longPreferencesKey("conditions_at")
        @Suppress("unused") val schema = intPreferencesKey("schema")
    }

    private suspend fun loadPersistedIfNeeded() {
        if (loaded) return
        loaded = true
        val prefs = runCatching { context.recStore.data.first() }.getOrNull() ?: return
        prefs[Keys.readinessJson]?.let { memReadiness = runCatching { json.decodeFromString(Readiness.serializer(), it) }.getOrNull() }
        memReadinessDay = prefs[Keys.readinessDay] ?: Long.MIN_VALUE
        prefs[Keys.conditionsJson]?.let { memConditions = runCatching { json.decodeFromString(Conditions.serializer(), it) }.getOrNull() }
        memConditionsAt = prefs[Keys.conditionsAt] ?: 0L
    }

    private suspend fun persistReadiness(r: Readiness, day: Long) {
        runCatching {
            context.recStore.edit {
                it[Keys.readinessJson] = json.encodeToString(Readiness.serializer(), r)
                it[Keys.readinessDay] = day
            }
        }
    }

    private suspend fun persistConditions(c: Conditions, at: Long) {
        runCatching {
            context.recStore.edit {
                it[Keys.conditionsJson] = json.encodeToString(Conditions.serializer(), c)
                it[Keys.conditionsAt] = at
            }
        }
    }

    // ---- readiness ----

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
        /** Yesterday-and-back daily steps, most recent first — lets the AI see a hike-sized spike. */
        val recentDailySteps: List<Int>,
        val peakRecentSteps: Int?,
    )

    private suspend fun generateReadiness(): Readiness {
        val sleepSnap = runCatching { sleep.lastNight() }.getOrNull()
        val exercise = gatherExercise()
        val activity = gatherActivity()
        val profile = profileRepository.profile.first()

        val prompt = buildReadinessPrompt(sleepSnap, exercise, activity, profile)
        val parsed = when (val r = gemini.generate(prompt, AiModelTier.THINKING)) {
            is AiResult.Success -> parseReadiness(r.text)
            is AiResult.Failure -> null
        }
        val base = parsed?.let {
            Readiness(it.score.coerceIn(0, 100), it.label.ifBlank { labelFor(it.score) }, it.reason.ifBlank { "" })
        } ?: heuristicReadiness(sleepSnap, exercise, activity)
        return base.copy(factors = buildReadinessFactors(sleepSnap, exercise, activity))
    }

    private suspend fun gatherExercise(): ExerciseContext {
        val performed = sessionRepository.observePerformed().first()
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
        val recent = (0..4).mapNotNull { byDate[today.minusDays(it.toLong()).format(iso)]?.steps }
        val avg7 = last7.avgOrNull()
        return ActivityContext(
            todaySteps = byDate[today.format(iso)]?.steps,
            avg7dSteps = avg7?.roundToInt(),
            activeDays7 = if (last7.isEmpty()) null else last7.count { it >= 5_000 },
            stepCv = if (last7.size >= 3 && avg7 != null && avg7 > 0) stdev(last7.map { it.toDouble() }) / avg7 else null,
            recentDailySteps = recent,
            peakRecentSteps = (recent.drop(1).maxOrNull()),
        )
    }

    private fun buildReadinessPrompt(s: SleepSnapshot?, e: ExerciseContext, a: ActivityContext, p: UserProfile): String = buildString {
        appendLine("You are an expert sports scientist and recovery coach giving ONE athlete a mindful daily check-in.")
        appendLine("Weigh ALL the evidence together — sleep quality and quantity, autonomic recovery (sleeping HR and HRV), schedule consistency, recent training load, how today's intensity compares to their normal, RPE and notes, AND general daily activity (a huge step day like a long hike is real physical load, even with no gym workout). Prefer nuance over a formula.")
        appendLine("Respond with ONLY minified JSON, no markdown:")
        appendLine("""{"score":<0-100 int>,"label":"<Train|Take it easy|Rest>","reason":"<3-5 sentences, ~350-550 chars: name the specific factors that drove the score and why, so the athlete understands the call>"}""")
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
        appendLine("- Days since last logged workout: ${e.daysSinceLast?.toString() ?: "no recent history"}")
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
        appendLine("DAILY ACTIVITY (steps — treat a spike well above the personal average as a significant load needing recovery):")
        appendLine("- Steps today: ${a.todaySteps ?: "n/a"}; 7-day average: ${a.avg7dSteps ?: "n/a"}; active days (≥5k) last week: ${a.activeDays7?.let { "$it/7" } ?: "n/a"}")
        if (a.recentDailySteps.isNotEmpty()) appendLine("- Last few days of steps (most recent first): ${a.recentDailySteps.joinToString(", ")}")
        if (a.peakRecentSteps != null && a.avg7dSteps != null && a.avg7dSteps > 0 && a.peakRecentSteps > a.avg7dSteps * 2) {
            appendLine("- NOTE: a recent day (${a.peakRecentSteps} steps) was more than double the usual — likely a hike or long effort; the following 1-2 days warrant extra recovery.")
        }
        appendLine()
        appendLine("Guidance: short/poor sleep, low HRV, an elevated sleeping HR, a sharp load jump, unusually high recent intensity, a big recent activity spike, or a high RPE / 'sore/tired' note all LOWER readiness; deep restful sleep, good HRV, adequate rest and steady habits RAISE it. Regular trainers need only 1-2 rest days; returning after a layoff, or the day after a big effort, needs more caution.")
    }

    @Serializable
    private data class ReadinessDto(val score: Int = 0, val label: String = "", val reason: String = "")

    private fun parseReadiness(text: String): ReadinessDto? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { json.decodeFromString(ReadinessDto.serializer(), text.substring(start, end + 1)) }.getOrNull()
    }

    private fun heuristicReadiness(s: SleepSnapshot?, e: ExerciseContext, a: ActivityContext): Readiness {
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
        // A recent step spike (e.g. a long hike) is real load even without a logged workout.
        if (a.peakRecentSteps != null && a.avg7dSteps != null && a.avg7dSteps > 0) {
            val ratio = a.peakRecentSteps.toDouble() / a.avg7dSteps
            if (ratio >= 2.5) score -= 22 else if (ratio >= 1.8) score -= 12
        }
        score = score.coerceIn(5, 98)
        val reason = buildString {
            append(
                when {
                    s == null -> "This is from your recent training and daily activity — connect sleep in Settings for a sharper read. "
                    s.hours < 6 -> "You got only ${"%.1f".format(s.hours)} h of sleep, which is short and blunts recovery. "
                    else -> "Sleep of ${"%.1f".format(s.hours)} h looks reasonable. "
                },
            )
            if (a.peakRecentSteps != null && a.avg7dSteps != null && a.avg7dSteps > 0 && a.peakRecentSteps > a.avg7dSteps * 2) {
                append("A recent ${a.peakRecentSteps}-step day was well above your usual ${a.avg7dSteps}, so your legs are carrying that load — favour rest or something light. ")
            }
            append(
                when (e.daysSinceLast) {
                    null -> "No recent training on record."
                    0L -> "You already trained today."
                    in 1..3 -> "You've had a day or two of rest since your last workout."
                    else -> "It's been a while since your last workout."
                },
            )
        }
        return Readiness(score, labelFor(score), reason)
    }

    private fun buildReadinessFactors(s: SleepSnapshot?, e: ExerciseContext, a: ActivityContext): List<RecFactor> = buildList {
        if (s != null) {
            add(RecFactor("Sleep", "%.1f h".format(s.hours), (s.hours / 8.0).toFloat().coerceIn(0f, 1f)))
            s.deepRemFraction?.let { add(RecFactor("Deep + REM", "${(it * 100).roundToInt()}%", it.toFloat().coerceIn(0f, 1f))) }
            s.avgHrvMs?.let { add(RecFactor("Sleeping HRV", "${it.roundToInt()} ms", (it / 80.0).toFloat().coerceIn(0f, 1f))) }
            s.avgHrBpm?.let { add(RecFactor("Sleeping HR", "$it bpm", ((70 - it) / 30.0).toFloat().coerceIn(0f, 1f))) }
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
        a.avg7dSteps?.let { add(RecFactor("Daily activity", "%,d steps/day".format(it), (it / 10_000f).coerceIn(0f, 1f))) }
    }.take(6)

    // ---- conditions ----

    private suspend fun generateConditions(): Conditions {
        val coords = runCatching { location.currentOrLast() }.getOrNull() ?: return noLocationConditions()
        val w = runCatching { weather.current(coords.latitude, coords.longitude) }.getOrNull() ?: return noLocationConditions()

        val prompt = buildConditionsPrompt(w)
        val parsed = when (val r = gemini.generate(prompt, AiModelTier.FAST)) {
            is AiResult.Success -> parseConditions(r.text)
            is AiResult.Failure -> null
        }
        val base = if (parsed != null && parsed.label.isNotBlank()) {
            Conditions(parsed.label, parsed.detail.ifBlank { w.glance }, parsed.reason)
        } else {
            heuristicConditions(w)
        }
        return base.copy(factors = buildConditionsFactors(w))
    }

    private fun buildConditionsPrompt(w: WeatherSnapshot): String = buildString {
        appendLine("Advise whether to train indoors or outdoors right now. Respond with ONLY minified JSON, no markdown:")
        appendLine("""{"label":"<Indoor|Outdoor|Outdoor · SPF>","detail":"<glanceable, e.g. 12°·rain>","reason":"<2-3 sentences explaining the call from the specific conditions>"}""")
        appendLine()
        appendLine("- ${w.tempC.roundToInt()}°C (feels ${w.apparentTempC.roundToInt()}°C), ${w.weatherText}, wind ${w.windKph.roundToInt()} km/h, precip ${"%.1f".format(w.precipMm)} mm")
        appendLine("- UV index ${w.uvIndex?.let { "%.1f".format(it) } ?: "n/a"}, PM2.5 ${w.pm25?.let { "${it.roundToInt()} µg/m³" } ?: "n/a"}, US AQI ${w.usAqi ?: "n/a"}")
        appendLine("Recommend Indoor when rain/storm/poor air (AQI>100)/temperature extremes; Outdoor when pleasant. When UV index >= 6 use label \"Outdoor · SPF\" and mention sunscreen. Keep detail short like \"23°·clear\".")
    }

    @Serializable
    private data class ConditionsDto(val label: String = "", val detail: String = "", val reason: String = "")

    private fun parseConditions(text: String): ConditionsDto? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { json.decodeFromString(ConditionsDto.serializer(), text.substring(start, end + 1)) }.getOrNull()
    }

    private fun heuristicConditions(w: WeatherSnapshot): Conditions {
        val poorAir = (w.usAqi ?: 0) > 100
        val wet = w.precipMm > 0.2 || w.weatherCode in setOf(61, 63, 65, 80, 81, 82, 95, 96, 99)
        val highUv = (w.uvIndex ?: 0.0) >= 6.0
        return when {
            wet -> Conditions("Indoor", w.glance, "It's wet out there right now, so an indoor session is the safer, more comfortable call today.")
            poorAir -> Conditions("Indoor", w.glance, "Air quality is poor right now (AQI ${w.usAqi}). Training indoors avoids the extra respiratory load.")
            highUv -> Conditions("Outdoor · SPF", w.glance, "Conditions are good for outdoor training, but the UV index is high — wear sunscreen and a hat.")
            else -> Conditions("Outdoor", w.glance, "Pleasant, dry conditions with clean air — a good window to train outside.")
        }
    }

    private fun noLocationConditions(): Conditions = Conditions(
        label = "Add location",
        detail = "",
        reason = if (location.hasPermission()) "Couldn't get a location fix — tap to retry."
        else "Turn on location for indoor/outdoor guidance.",
        needsLocation = true,
    )

    private fun buildConditionsFactors(w: WeatherSnapshot): List<RecFactor> = buildList {
        add(RecFactor("Temperature", "${w.tempC.roundToInt()}°C", ((w.tempC + 10) / 45.0).toFloat().coerceIn(0f, 1f)))
        w.uvIndex?.let { add(RecFactor("UV index", "%.1f".format(it), (it / 11.0).toFloat().coerceIn(0f, 1f))) }
        w.usAqi?.let { add(RecFactor("Air quality", "AQI $it", (it / 200f).coerceIn(0f, 1f))) }
        add(RecFactor("Wind", "${w.windKph.roundToInt()} km/h", (w.windKph / 40.0).toFloat().coerceIn(0f, 1f)))
        if (w.precipMm > 0.0) add(RecFactor("Rain", "%.1f mm".format(w.precipMm), (w.precipMm / 10.0).toFloat().coerceIn(0f, 1f)))
    }

    // ---- shared ----

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
        /** Conditions regenerate at most this often (still only on app open). */
        const val WEATHER_INTERVAL_MS = 3 * 60 * 60 * 1000L
    }
}
