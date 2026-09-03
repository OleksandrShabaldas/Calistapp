package com.calistapp.app.data.session

import com.calistapp.app.data.local.ScheduleDao
import com.calistapp.app.data.local.ScheduleOverrideEntity
import com.calistapp.app.data.local.ScheduledWorkoutEntity
import com.calistapp.core.model.SavedWorkout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** How a this-week override changes a recurring occurrence. */
enum class ScheduleAction { SKIP, MOVE }

/** One planned occurrence of a saved workout on a given weekday of a given week. */
data class ScheduledItem(
    val workout: SavedWorkout,
    val dayOfWeek: DayOfWeek,
    /** The recurring rule behind it, or null when it exists only via a this-week move. */
    val ruleId: String?,
    /** Set when this occurrence was moved into this day *this week*. */
    val movedFrom: DayOfWeek? = null,
)

/**
 * Planned workouts — the schedule behind the dashboard's grey "planned" dots and its Next Up card,
 * and the surface the scheduling screen edits.
 *
 * Two layers: recurring weekly *rules* ("every Monday and Thursday"), plus per-week *overrides* that
 * move or skip an occurrence for one week only. Resolving a week applies its overrides on top of the
 * rules. Rules whose saved workout no longer exists simply don't resolve, so a deleted workout can't
 * leave a broken entry on the calendar.
 */
@Singleton
class ScheduleRepository @Inject constructor(
    private val dao: ScheduleDao,
    private val savedWorkouts: SavedWorkoutRepository,
) {
    /** Recurring rules with their saved workout resolved, grouped by weekday (ignores overrides). */
    val recurring: Flow<Map<DayOfWeek, List<ScheduledItem>>> =
        combine(dao.observeRules(), savedWorkouts.saved) { rules, saved ->
            val byId = saved.associateBy { it.id }
            rules.mapNotNull { r -> byId[r.savedWorkoutId]?.let { ScheduledItem(it, DayOfWeek.of(r.dayOfWeek), r.id) } }
                .groupBy { it.dayOfWeek }
        }

    /** Which weekdays each saved workout recurs on — what the schedule editor's day toggles reflect. */
    val recurringDaysByWorkout: Flow<Map<String, Set<DayOfWeek>>> =
        dao.observeRules().map { rules ->
            rules.groupBy { it.savedWorkoutId }
                .mapValues { (_, rs) -> rs.map { DayOfWeek.of(it.dayOfWeek) }.toSet() }
        }

    /** The plan for the week beginning [weekStartMs] (Monday 00:00), after its overrides. */
    fun weekPlan(weekStartMs: Long): Flow<Map<DayOfWeek, List<ScheduledItem>>> =
        combine(dao.observeRules(), dao.observeOverrides(), savedWorkouts.saved) { rules, overrides, saved ->
            resolveWeek(weekStartMs, rules, overrides, saved)
        }

    private fun resolveWeek(
        weekStartMs: Long,
        rules: List<ScheduledWorkoutEntity>,
        overrides: List<ScheduleOverrideEntity>,
        saved: List<SavedWorkout>,
    ): Map<DayOfWeek, List<ScheduledItem>> {
        val byId = saved.associateBy { it.id }
        val items = rules
            .mapNotNull { r -> byId[r.savedWorkoutId]?.let { ScheduledItem(it, DayOfWeek.of(r.dayOfWeek), r.id) } }
            .toMutableList()

        overrides.asSequence().filter { it.weekStartMs == weekStartMs }.forEach { o ->
            val action = runCatching { ScheduleAction.valueOf(o.action) }.getOrNull() ?: return@forEach
            val src = DayOfWeek.of(o.sourceDayOfWeek)
            val i = items.indexOfFirst { it.workout.id == o.savedWorkoutId && it.dayOfWeek == src && it.movedFrom == null }
            when (action) {
                ScheduleAction.SKIP -> if (i >= 0) items.removeAt(i)
                ScheduleAction.MOVE -> {
                    val target = o.targetDayOfWeek?.let { DayOfWeek.of(it) } ?: return@forEach
                    if (i >= 0) {
                        items.add(items.removeAt(i).copy(dayOfWeek = target, movedFrom = src))
                    } else byId[o.savedWorkoutId]?.let { // rule gone, but the move still stands
                        items.add(ScheduledItem(it, target, ruleId = null, movedFrom = src))
                    }
                }
            }
        }
        return items.groupBy { it.dayOfWeek }.toSortedMap()
    }

    // ---- mutations ----

    /** Replace every recurring day for [savedWorkoutId] with exactly [days]. */
    suspend fun setRecurring(savedWorkoutId: String, days: Set<DayOfWeek>) {
        dao.deleteRulesForWorkout(savedWorkoutId)
        days.forEach { dao.upsertRule(ScheduledWorkoutEntity(UUID.randomUUID().toString(), savedWorkoutId, it.value)) }
    }

    suspend fun removeRule(ruleId: String) = dao.deleteRule(ruleId)
    suspend fun removeAllForWorkout(savedWorkoutId: String) = dao.deleteRulesForWorkout(savedWorkoutId)

    suspend fun skipThisWeek(weekStartMs: Long, day: DayOfWeek, savedWorkoutId: String) =
        dao.upsertOverride(
            ScheduleOverrideEntity(
                id = UUID.randomUUID().toString(),
                weekStartMs = weekStartMs,
                action = ScheduleAction.SKIP.name,
                sourceDayOfWeek = day.value,
                targetDayOfWeek = null,
                savedWorkoutId = savedWorkoutId,
            ),
        )

    suspend fun moveThisWeek(weekStartMs: Long, from: DayOfWeek, to: DayOfWeek, savedWorkoutId: String) =
        dao.upsertOverride(
            ScheduleOverrideEntity(
                id = UUID.randomUUID().toString(),
                weekStartMs = weekStartMs,
                action = ScheduleAction.MOVE.name,
                sourceDayOfWeek = from.value,
                targetDayOfWeek = to.value,
                savedWorkoutId = savedWorkoutId,
            ),
        )

    suspend fun clearOverride(id: String) = dao.deleteOverride(id)

    /** Drop overrides for weeks now in the past. Cheap to call on app open. */
    suspend fun pruneOldOverrides(cutoffMs: Long) = dao.pruneOverridesBefore(cutoffMs)
}
