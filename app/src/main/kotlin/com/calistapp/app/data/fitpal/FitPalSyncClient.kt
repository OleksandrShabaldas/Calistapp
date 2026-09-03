package com.calistapp.app.data.fitpal

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** One finished Calistapp workout, flattened for the FitPal exercise provider. */
data class ExercisePayload(
    val externalId: String,
    val date: String,
    val name: String,
    val minutes: Int,
    val met: Float,
    val calories: Float,
    val detailsJson: String?,
    val startMs: Long,
)

/** One day of steps pulled back from FitPal (calories already trimmed by FitPal's reduction %). */
data class StepDay(
    val date: String,
    val steps: Int,
    val calories: Double,
    val reductionPercent: Int,
)

/** Outcome of a bridge call — lets callers distinguish "FitPal not there" from a real error. */
sealed interface SyncResult {
    data object Success : SyncResult
    /** FitPal isn't installed, or its provider couldn't be reached. Not an error to surface loudly. */
    data object Unavailable : SyncResult
    data class Error(val message: String) : SyncResult
}

/**
 * Talks to FitPal's `content://com.fitpal.app.sync` provider. Pure client, no state. Every call
 * degrades quietly when FitPal is absent (provider missing → `insert` returns null / `query`
 * returns null / a resolver throws) so the app is fully usable without FitPal installed.
 */
@Singleton
class FitPalSyncClient @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val resolver get() = context.contentResolver

    /** True if FitPal's provider is present on the device right now. */
    fun isFitPalAvailable(): Boolean = runCatching {
        resolver.acquireContentProviderClient(FitPalContract.AUTHORITY)?.also {
            @Suppress("DEPRECATION") it.release()
        } != null
    }.getOrDefault(false)

    /** Push (upsert) one finished workout into FitPal's activity log. Idempotent on [ExercisePayload.externalId]. */
    suspend fun upsertExercise(p: ExercisePayload): SyncResult = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(FitPalContract.COL_EXTERNAL_ID, p.externalId)
            put(FitPalContract.COL_DATE, p.date)
            put(FitPalContract.COL_NAME, p.name)
            put(FitPalContract.COL_MINUTES, p.minutes)
            put(FitPalContract.COL_MET, p.met)
            put(FitPalContract.COL_CALORIES, p.calories)
            put(FitPalContract.COL_SOURCE, FitPalContract.SOURCE_CALISTAPP)
            put(FitPalContract.COL_DETAILS_JSON, p.detailsJson)
            put(FitPalContract.COL_START_MS, p.startMs)
        }
        runCatching { resolver.insert(FitPalContract.EXERCISE_URI, values) }
            .fold(
                onSuccess = { uri: Uri? -> if (uri != null) SyncResult.Success else SyncResult.Unavailable },
                onFailure = { e ->
                    Log.w(TAG, "upsertExercise failed: ${e.message}")
                    if (e is SecurityException) SyncResult.Error("Rejected by FitPal")
                    else SyncResult.Unavailable
                }
            )
    }

    /**
     * Pull daily steps + FitPal's trimmed step-calories for an inclusive date range. Returns null
     * when FitPal is unavailable (so callers can tell "no bridge" from "no steps" = empty list).
     */
    suspend fun querySteps(from: String, to: String): List<StepDay>? = withContext(Dispatchers.IO) {
        val uri = FitPalContract.STEPS_URI.buildUpon()
            .appendQueryParameter(FitPalContract.QUERY_FROM, from)
            .appendQueryParameter(FitPalContract.QUERY_TO, to)
            .build()
        runCatching {
            resolver.query(uri, null, null, null, null)?.use { c ->
                val iDate = c.getColumnIndexOrThrow(FitPalContract.COL_DATE)
                val iSteps = c.getColumnIndexOrThrow(FitPalContract.COL_STEPS)
                val iCal = c.getColumnIndexOrThrow(FitPalContract.COL_CALORIES)
                val iPct = c.getColumnIndexOrThrow(FitPalContract.COL_REDUCTION_PERCENT)
                buildList {
                    while (c.moveToNext()) {
                        add(
                            StepDay(
                                date = c.getString(iDate),
                                steps = c.getInt(iSteps),
                                calories = c.getDouble(iCal),
                                reductionPercent = c.getInt(iPct),
                            )
                        )
                    }
                }
            }
        }.getOrElse {
            Log.w(TAG, "querySteps failed: ${it.message}")
            null
        }
    }

    private companion object {
        const val TAG = "FitPalSyncClient"
    }
}
