package com.calistapp.wear.hr

import android.content.Context
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DeltaDataType
import com.calistapp.core.model.HeartRateSample
import com.calistapp.core.model.HrConfidence
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.roundToInt

/**
 * Real heart rate from the watch's optical sensor via Health Services `MeasureClient`.
 *
 * NOTE: Health Services is still in alpha, so the `MeasureCallback` surface can shift slightly
 * between library versions. If this file fails to compile against your `health-services-client`
 * version, this is the only place to adjust (see README). The rest of the app is unaffected because
 * everything else depends only on [HeartRateSource].
 *
 * Requires the BODY_SENSORS runtime permission (requested from the UI before selecting this source).
 */
class HealthServicesHeartRateSource(
    private val context: Context,
) : HeartRateSource {

    override val label = "Watch sensor"

    override fun samples(): Flow<HeartRateSample> = callbackFlow {
        val measureClient = HealthServices.getClient(context).measureClient

        val callback = object : MeasureCallback {
            override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) {
                // Could surface "put the watch on your wrist" states here in a later iteration.
            }

            override fun onDataReceived(data: DataPointContainer) {
                data.getData(DataType.HEART_RATE_BPM).forEach { point ->
                    val bpm = point.value.roundToInt()
                    if (bpm > 0) {
                        trySend(HeartRateSample(System.currentTimeMillis(), bpm, HrConfidence.HIGH))
                    }
                }
            }
        }

        measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, callback)
        awaitClose {
            measureClient.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, callback)
        }
    }
}
