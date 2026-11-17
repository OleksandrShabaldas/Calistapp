package com.calistapp.app.data.insights

import com.calistapp.app.data.exercise.ExerciseRepository
import com.calistapp.app.data.session.ScheduleRepository
import com.calistapp.app.data.session.SessionRepository
import com.calistapp.app.di.ApplicationScope
import com.calistapp.core.medals.MedalAward
import com.calistapp.core.medals.MedalEngine
import com.calistapp.core.medals.MedalReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The medals, as they stand for the history on this phone.
 *
 * Nothing here stores an award: [report] is [MedalEngine] run over the sessions, so an edited, imported
 * or deleted session moves its medals with it. The only memory is which awards were already shown
 * ([InsightsPrefsRepository.seenMedals]), so each unlock plays once.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class MedalsRepository @Inject constructor(
    sessionRepository: SessionRepository,
    exerciseRepository: ExerciseRepository,
    private val scheduleRepository: ScheduleRepository,
    private val prefs: InsightsPrefsRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val zone: ZoneId = ZoneId.systemDefault()

    /**
     * Planned training days, counted only from the first day the app saw a schedule: rules carry no
     * creation date, and today's plan applied to the weeks before it existed would mark them all missed.
     * Shared with Insights so "consistent" and Perfect month read the same plan.
     */
    val plannedDays: Flow<Set<LocalDate>> = combine(
        scheduleRepository.plannedDays(LocalDate.now(zone).minusDays(PLAN_LOOKBACK_DAYS), LocalDate.now(zone), zone),
        prefs.scheduleSince,
    ) { days, since -> if (since == null) emptySet() else days.filterTo(HashSet()) { !it.isBefore(since) } }

    val report: StateFlow<MedalReport?> = combine(
        sessionRepository.observePerformed(),
        exerciseRepository.observeAll().map { list -> list.associateBy { it.id } },
        plannedDays,
    ) { sessions, exercises, planned -> Triple(sessions, exercises, planned) }
        .mapLatest { (sessions, exercises, planned) ->
            MedalEngine.evaluate(sessions, exercises, planned, zone, nowMs = System.currentTimeMillis())
        }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.WhileSubscribed(STOP_AFTER_MS), null)

    /** Award keys already shown. Empty until the first evaluation sets the baseline. */
    val seen: Flow<Set<String>> = prefs.seenMedals.map { it.orEmpty() }

    /** Awards not shown yet, oldest first (the order they're struck in). Empty before the baseline exists. */
    val unseen: Flow<List<MedalAward>> = combine(report.filterNotNull(), prefs.seenMedals) { r, seen ->
        if (seen == null) emptyList() else r.awards.filter { it.key !in seen }
    }

    suspend fun markSeen(keys: Collection<String>) {
        if (keys.isNotEmpty()) prefs.markMedalsSeen(keys)
    }

    /**
     * Called once per process from the application: records the first day a schedule exists, and on the
     * very first run marks every medal the history already holds as seen, so an update or an import
     * doesn't open with a ceremony for medals earned months ago.
     */
    fun start() {
        scope.launch {
            scheduleRepository.hasSchedule.collect { has -> if (has) prefs.markScheduleSeen(LocalDate.now(zone)) }
        }
        scope.launch {
            if (prefs.seenMedals.first() != null) return@launch
            val r = runCatching { report.filterNotNull().first() }.getOrNull() ?: return@launch
            prefs.baselineMedalsSeen(r.awards.map { it.key })
        }
    }

    private companion object {
        /** About three years: a Year issue, the one before it, and Perfect months well back. */
        const val PLAN_LOOKBACK_DAYS = 1_100L
        const val STOP_AFTER_MS = 10_000L
    }
}
