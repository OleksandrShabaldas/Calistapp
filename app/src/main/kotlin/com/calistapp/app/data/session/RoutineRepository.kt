package com.calistapp.app.data.session

import com.calistapp.core.model.Routine
import com.calistapp.core.model.RoutineCatalog
import com.calistapp.core.model.RoutineKind
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the curated warm-up / stretch routines.
 *
 * A thin wrapper over the seeded [RoutineCatalog] today — the seam a future DB- or network-backed
 * library slots in behind without the setup screen changing. Kept synchronous because the content is
 * static and small.
 */
@Singleton
class RoutineRepository @Inject constructor() {

    fun byKind(kind: RoutineKind): List<Routine> = RoutineCatalog.byKind(kind)

    fun byId(id: String): Routine? = RoutineCatalog.byId(id)
}
