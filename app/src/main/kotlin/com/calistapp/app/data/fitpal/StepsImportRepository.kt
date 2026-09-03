package com.calistapp.app.data.fitpal

import com.calistapp.app.data.local.StepDayDao
import com.calistapp.app.data.local.StepDayEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/** Result of a steps import attempt. */
sealed interface ImportOutcome {
    data class Imported(val days: Int) : ImportOutcome
    /** FitPal wasn't reachable — the caller should stay quiet and try again later. */
    data object Unavailable : ImportOutcome
}

/**
 * Owns the FitPal → Calistapp step flow. Pulls the day's steps + FitPal's already-trimmed
 * step-calories through [FitPalSyncClient] and stores them verbatim in `step_days` (Calistapp never
 * recomputes step calories). Reconciliation just re-imports a recent window — idempotent upserts, so
 * it fills gaps AND refreshes days whose numbers changed (e.g. FitPal's reduction % was edited).
 */
@Singleton
class StepsImportRepository @Inject constructor(
    private val stepDayDao: StepDayDao,
    private val client: FitPalSyncClient,
) {
    private val fmt = DateTimeFormatter.ISO_LOCAL_DATE

    fun observeForDate(date: String): Flow<StepDayEntity?> = stepDayDao.observeForDate(date)
    fun observeRange(from: String, to: String): Flow<List<StepDayEntity>> = stepDayDao.observeRange(from, to)
    fun observeLastImportedAt(): Flow<Long?> = stepDayDao.observeLastImportedAt()

    fun fitPalAvailable(): Boolean = client.isFitPalAvailable()

    /** Import an explicit inclusive date range from FitPal. */
    suspend fun importRange(from: String, to: String): ImportOutcome {
        val days = client.querySteps(from, to) ?: return ImportOutcome.Unavailable
        days.forEach { d ->
            stepDayDao.upsert(
                StepDayEntity(
                    date = d.date,
                    steps = d.steps,
                    calories = d.calories,
                    reductionPercent = d.reductionPercent,
                )
            )
        }
        return ImportOutcome.Imported(days.size)
    }

    /**
     * Re-import the last [daysBack] days (default a comfortable ~7-week window). Covers the "check
     * that every recent day is imported, pull the missing ones" reconciliation on app open, the
     * background pull, and the manual button — all one code path.
     */
    suspend fun reconcileRecent(daysBack: Long = DEFAULT_WINDOW_DAYS): ImportOutcome {
        val today = LocalDate.now()
        return importRange(today.minusDays(daysBack).format(fmt), today.format(fmt))
    }

    companion object {
        const val DEFAULT_WINDOW_DAYS = 45L
    }
}
