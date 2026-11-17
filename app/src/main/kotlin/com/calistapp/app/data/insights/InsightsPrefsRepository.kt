package com.calistapp.app.data.insights

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.calistapp.core.insights.InsightsPeriod
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

private val Context.insightsPrefs by preferencesDataStore(name = "insights_prefs")

/**
 * What Insights remembers between visits. Nothing here is training data; it only shapes how that
 * data is shown.
 *
 * - **Phrase history**, per issue: the phrase ids each issue used, so the next issue avoids them
 *   (the phrasebook's "almost never the same sentence twice"). Keyed by issue so re-opening the
 *   same issue reads the same, rather than avoiding its own words.
 * - **Schedule seen since**: the first day the app saw a training schedule. Planned days only count
 *   from then, because schedule rules carry no creation date and applying today's plan to weeks
 *   before it existed would mark them all as missed.
 * - **Period**: the last Week / 4 weeks / Quarter / Year the user picked.
 * - **Seen medals**: the award keys ("pull-ups#1") already shown, so each unlock plays once. Absent
 *   until the first evaluation, which marks everything already earned as seen: a history that
 *   arrives in bulk (an update, an import) gets no ceremony for medals earned long ago.
 */
@Singleton
class InsightsPrefsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val historyKey = stringPreferencesKey("phrase_history")
    private val scheduleSinceKey = stringPreferencesKey("schedule_since")
    private val periodKey = stringPreferencesKey("period")
    private val seenMedalsKey = stringSetPreferencesKey("seen_medals")
    private val json = Json { ignoreUnknownKeys = true }

    /** Issue key ("FOUR_WEEKS@2026-10-19") to the phrase ids it used, oldest issue first. */
    val phraseHistory: Flow<Map<String, List<String>>> = context.insightsPrefs.data.map { p ->
        p[historyKey]?.let { runCatching { json.decodeFromString<Map<String, List<String>>>(it) }.getOrNull() } ?: emptyMap()
    }

    val scheduleSince: Flow<LocalDate?> = context.insightsPrefs.data.map { p ->
        p[scheduleSinceKey]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    }

    val period: Flow<InsightsPeriod> = context.insightsPrefs.data.map { p ->
        p[periodKey]?.let { runCatching { InsightsPeriod.valueOf(it) }.getOrNull() } ?: InsightsPeriod.FOUR_WEEKS
    }

    /** Award keys already shown; null until the first evaluation set the baseline. */
    val seenMedals: Flow<Set<String>?> = context.insightsPrefs.data.map { p -> p[seenMedalsKey] }

    /** Mark these awards shown. Also sets the baseline if there wasn't one. */
    suspend fun markMedalsSeen(keys: Collection<String>) {
        context.insightsPrefs.edit { p -> p[seenMedalsKey] = (p[seenMedalsKey] ?: emptySet()) + keys }
    }

    /** Replace the seen set outright. Debug seeding only: it re-runs the first-run baseline. */
    suspend fun replaceMedalsSeen(keys: Collection<String>) {
        context.insightsPrefs.edit { p -> p[seenMedalsKey] = keys.toSet() }
    }

    /** Set the first-run baseline to [keys]; a no-op once any baseline exists. */
    suspend fun baselineMedalsSeen(keys: Collection<String>) {
        context.insightsPrefs.edit { p -> if (p[seenMedalsKey] == null) p[seenMedalsKey] = keys.toSet() }
    }

    suspend fun setPeriod(value: InsightsPeriod) {
        context.insightsPrefs.edit { it[periodKey] = value.name }
    }

    /** Remember the phrases [issueKey] used; keeps the most recent [KEEP] issues. No-op if unchanged. */
    suspend fun remember(issueKey: String, ids: List<String>) {
        context.insightsPrefs.edit { p ->
            val current = p[historyKey]?.let { runCatching { json.decodeFromString<Map<String, List<String>>>(it) }.getOrNull() } ?: emptyMap()
            if (current[issueKey] == ids) return@edit
            val next = LinkedHashMap(current).apply { remove(issueKey); put(issueKey, ids) }
            while (next.size > KEEP) next.remove(next.keys.first())
            p[historyKey] = json.encodeToString(next)
        }
    }

    suspend fun markScheduleSeen(today: LocalDate) {
        context.insightsPrefs.edit { p -> if (p[scheduleSinceKey] == null) p[scheduleSinceKey] = today.toString() }
    }

    private companion object {
        /** About two months of daily issues across periods: plenty to never repeat a sentence soon. */
        const val KEEP = 60
    }
}
