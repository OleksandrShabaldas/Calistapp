package com.calistapp.wear.hr

import com.calistapp.core.model.HeartRateSample
import kotlinx.coroutines.flow.Flow

/**
 * Abstraction over "where heart rate comes from". [HealthServicesHeartRateSource] is the only
 * implementation — readings always come from real sensor hardware. The interface stays because it
 * quarantines the alpha Health Services API behind one seam (see [HealthServicesHeartRateSource])
 * and keeps the session layer testable with a fake.
 */
interface HeartRateSource {
    val label: String

    /** A hot-ish stream of readings; collect it to start receiving HR. */
    fun samples(): Flow<HeartRateSample>
}
