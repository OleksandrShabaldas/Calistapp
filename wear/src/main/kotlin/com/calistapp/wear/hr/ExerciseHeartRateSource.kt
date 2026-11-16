package com.calistapp.wear.hr

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.clearUpdateCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.BatchingMode
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.ExerciseTrackedStatus
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseUpdate
import androidx.health.services.client.endExercise
import androidx.health.services.client.getCapabilities
import androidx.health.services.client.getCurrentExerciseInfo
import androidx.health.services.client.startExercise
import com.calistapp.core.model.HeartRateSample
import com.calistapp.core.model.HrConfidence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import java.time.Instant
import kotlin.math.roundToInt

/**
 * When the watch booted, as wall-clock time. Health Services stamps every reading with time since boot,
 * which is what turns it into the moment it was *measured* rather than the moment it was delivered.
 */
internal fun bootInstant(): Instant = Instant.ofEpochMilli(System.currentTimeMillis() - SystemClock.elapsedRealtime())

/** Another app is already running a Health Services exercise; taking it over would end theirs. */
class ExerciseOwnedElsewhere : IllegalStateException("Another app is tracking an exercise")

/**
 * Heart rate from a Health Services **exercise** — the API Google provides for workout tracking.
 *
 * The watch used `MeasureClient`, which Health Services documents as "not intended for background
 * capture or workout tracking": with the screen off its readings stall or arrive in late batches, and
 * those batches were stamped with their *arrival* time — together that is what drew minutes-long
 * straight lines through the heart-rate chart. An exercise keeps the sensor in workout mode with the
 * screen off, and with [BatchingMode.HEART_RATE_5_SECONDS] (where the watch supports it) delivers
 * readings every ~5 s even then. Each reading carries the time it was measured.
 */
class ExerciseHeartRateSource(private val context: Context) : HeartRateSource {

    override val label = "Watch sensor"

    private companion object {
        /**
         * Starting and ending the one Health Services exercise must take turns, and only the newest
         * collector may end it. A workout that is replaced (a saved one resumed at launch, then the
         * phone's new START) cancels the old collector, whose clean-up ends the exercise from a
         * separate coroutine — which used to land just after the new collector had started its own
         * exercise, silently ending it a second in and leaving the workout with no heart rate.
         */
        val exerciseLock = Mutex()
        val newest = AtomicInteger(0)
    }

    override fun samples(): Flow<HeartRateSample> = callbackFlow {
        val mine = newest.incrementAndGet()
        val client = HealthServices.getClient(context).exerciseClient

        // Never take a workout over from another app — fall back instead (see ResilientHeartRateSource).
        val tracked = exerciseLock.withLock { client.getCurrentExerciseInfo().exerciseTrackedStatus }
        if (tracked == ExerciseTrackedStatus.OTHER_APP_IN_PROGRESS) throw ExerciseOwnedElsewhere()

        val capabilities = client.getCapabilities()
        val type = listOf(ExerciseType.CALISTHENICS, ExerciseType.STRENGTH_TRAINING, ExerciseType.WORKOUT)
            .firstOrNull { t ->
                t in capabilities.supportedExerciseTypes &&
                    DataType.HEART_RATE_BPM in capabilities.getExerciseTypeCapabilities(t).supportedDataTypes
            } ?: throw IllegalStateException("No exercise type on this watch reports heart rate")
        // Readings every ~5 s with the screen off, instead of a batch every minute or two. It costs
        // battery — Google says so — but only while a workout runs, and a live heart-rate stream is the
        // point of the app.
        val batching = setOf(BatchingMode.HEART_RATE_5_SECONDS)
            .intersect(capabilities.supportedBatchingModeOverrides)

        val callback = object : ExerciseUpdateCallback {
            override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
                // Ended from outside (another app started one, or the system stopped it): let the
                // caller fall back rather than sit on a silent stream.
                if (update.exerciseStateInfo.state.isEnded) {
                    close(IllegalStateException("Exercise ended outside the app"))
                    return
                }
                val boot = bootInstant()
                update.latestMetrics.getData(DataType.HEART_RATE_BPM).forEach { point ->
                    val bpm = point.value.roundToInt()
                    if (bpm > 0) trySend(HeartRateSample(point.getTimeInstant(boot).toEpochMilli(), bpm, HrConfidence.HIGH))
                }
            }

            override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) = Unit
            override fun onRegistered() = Unit
            override fun onRegistrationFailed(throwable: Throwable) { close(throwable) }
            override fun onAvailabilityChanged(dataType: DataType<*, *>, availability: Availability) = Unit
        }

        exerciseLock.withLock {
            client.setUpdateCallback(callback)
            // Look again under the lock: a collector being replaced may have ended its exercise since.
            val now = client.getCurrentExerciseInfo().exerciseTrackedStatus
            if (now == ExerciseTrackedStatus.OTHER_APP_IN_PROGRESS) throw ExerciseOwnedElsewhere()
            if (now != ExerciseTrackedStatus.OWNED_EXERCISE_IN_PROGRESS) {
                client.startExercise(
                    ExerciseConfig.Builder(type)
                        .setDataTypes(setOf(DataType.HEART_RATE_BPM))
                        .setIsAutoPauseAndResumeEnabled(false)
                        .setIsGpsEnabled(false)
                        .setBatchingModeOverrides(batching)
                        .build(),
                )
            }
        }

        awaitClose {
            // The flow is being cancelled; ending the exercise must still happen, off this thread.
            CoroutineScope(Dispatchers.Default).launch {
                withContext(NonCancellable) {
                    exerciseLock.withLock {
                        Log.i("CalWear", "exercise collector $mine closing; newest=${newest.get()}")
                        // A newer collector has taken over the exercise (and the callback): leave both.
                        if (newest.get() == mine) {
                            runCatching { client.endExercise() }
                            runCatching { client.clearUpdateCallback(callback) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The exercise source, falling back to the foreground-only `MeasureClient` when an exercise can't run —
 * the watch has no suitable exercise type, a permission is missing, or another app owns the exercise.
 * Heart rate then still arrives while the screen is on, as before, rather than not at all.
 */
class ResilientHeartRateSource(private val context: Context) : HeartRateSource {

    override val label = "Watch sensor"

    override fun samples(): Flow<HeartRateSample> =
        ExerciseHeartRateSource(context).samples().catch { emitAll(HealthServicesHeartRateSource(context).samples()) }
}
