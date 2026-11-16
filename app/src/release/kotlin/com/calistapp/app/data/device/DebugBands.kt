package com.calistapp.app.data.device

import kotlinx.coroutines.flow.Flow

/** Release build: no simulated band — the debug build's lives in `src/debug`. */
object DebugBands {
    fun devices(): List<FoundBand> = emptyList()
    fun connect(address: String): Flow<BandEvent>? = null
}
