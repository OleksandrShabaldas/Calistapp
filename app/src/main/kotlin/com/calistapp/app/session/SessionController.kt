package com.calistapp.app.session

import android.content.Context
import com.calistapp.app.data.fitpal.FitPalExportManager
import com.calistapp.app.data.profile.ProfileRepository
import com.calistapp.app.data.session.SavedWorkoutRepository
import com.calistapp.app.data.session.SessionPrefsRepository
import com.calistapp.app.data.session.SessionRepository
import com.calistapp.app.data.sync.HrBatch
import com.calistapp.app.data.sync.HrSource
import com.calistapp.app.data.sync.LiveSessionBus
import com.calistapp.app.data.sync.WatchAppLauncher
import com.calistapp.app.data.sync.WatchCommandSender
import com.calistapp.app.data.sync.WatchConnectionMonitor
import com.calistapp.app.di.ApplicationScope
import com.calistapp.core.calorie.CalorieEngine
import com.calistapp.core.calorie.ExerciseIntensity
import com.calistapp.core.calorie.LiveCalorieAccumulator
import com.calistapp.core.calorie.rebuildAccumulator
import dagger.hilt.android.qualifiers.ApplicationContext
import com.calistapp.core.model.EffortScale
import com.calistapp.core.model.Exercise
import com.calistapp.core.model.ExerciseMeasure
import com.calistapp.core.model.ExerciseType
import com.calistapp.core.model.HeartRateSample
import com.calistapp.core.model.NextUp
import com.calistapp.core.model.BehindChoice
import com.calistapp.core.model.PlannedExercise
import com.calistapp.core.model.ReorderEffect
import com.calistapp.core.model.RoundOrder
import com.calistapp.core.model.reorderEffect
import com.calistapp.core.model.Segment
import com.calistapp.core.model.SegmentType
import com.calistapp.core.model.SessionStatus
import com.calistapp.core.model.SessionSummary
import com.calistapp.core.model.SetLog
import com.calistapp.core.model.SetSkip
import com.calistapp.core.model.SkipReason
import com.calistapp.core.model.SlotPhase
import com.calistapp.core.model.UserProfile
import com.calistapp.core.model.WorkoutPlan
import com.calistapp.core.model.WorkoutSession
import com.calistapp.core.model.describeChanges
import com.calistapp.core.model.linkHealthOf
import com.calistapp.core.model.mergeReadings
import com.calistapp.core.model.resolvedSets
import com.calistapp.core.model.savableOver
import com.calistapp.core.model.withAddedSlot
import com.calistapp.core.model.withSlotMoved
import com.calistapp.core.model.withTarget
import com.calistapp.core.model.withoutSlot
import com.calistapp.core.sync.ControlCommand
import com.calistapp.core.sync.ControlPayload
import com.calistapp.core.sync.DeviceOrigin
import com.calistapp.core.sync.SessionStatePayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Edits made to today's session that could be written back to the workout it was started from. */
data class WorkoutChanges(
    val workoutId: String,
    val workoutName: String,
    val plan: WorkoutPlan,
    /** One line per change — "Removed Parallel Bar Dips", "Push-Up: 1 × 12 → 1 × 15". */
    val lines: List<String>,
)

/**
 * The single source of truth for the in-progress workout on the phone. It owns the heart-rate
 * stream arriving from the watch, the active/rest segmentation, which exercise is being performed,
 * and the reps logged against it.
 *
 * **Calories are accumulated incrementally** via [LiveCalorieAccumulator] rather than by re-running
 * [CalorieEngine] over the whole history on every sample — the latter was quadratic in session
 * length. The engine still scores the finished session, so what gets stored stays authoritative.
 *
 * **Control is symmetric with the watch.** Local actions broadcast a [ControlPayload]; commands
 * arriving from the watch are applied without re-broadcasting, which is what stops the two devices
 * echoing each other. Skips, deferrals and edits are the exception that proves the rule: after one,
 * the phone sends its whole progress ([syncProgress]) and the watch adopts it, so neither device can
 * end up a set out of step.
 *
 * Kept as an app-scoped singleton so a session survives rotation/navigation and so a workout started
 * on the *watch* has somewhere to land even when no screen is open.
 */
@Singleton
class SessionController @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    private val engine: CalorieEngine,
    private val sessionRepository: SessionRepository,
    private val sessionPrefs: SessionPrefsRepository,
    private val profileRepository: ProfileRepository,
    private val savedWorkouts: SavedWorkoutRepository,
    private val bus: LiveSessionBus,
    private val watch: WatchCommandSender,
    private val watchLauncher: WatchAppLauncher,
    private val fitPalExportManager: FitPalExportManager,
    private val watchConnection: WatchConnectionMonitor,
    private val devicePrefs: com.calistapp.app.data.device.DevicePrefsRepository,
) {
    private val _live = MutableStateFlow<LiveSession?>(null)
    val live: StateFlow<LiveSession?> = _live.asStateFlow()

    private val mutex = Mutex()
    private var loopJob: Job? = null
    private var countdownJob: Job? = null
    private var profile: UserProfile = UserProfile()
    private var accumulator: LiveCalorieAccumulator? = null
    private var autoAdvanceRoutines = true

    // Retained privately for persistence; deliberately kept out of [LiveSession].
    private val samples = mutableListOf<HeartRateSample>()
    private val segments = mutableListOf<Segment>()
    private val setLogs = mutableListOf<SetLog>()
    private val skips = mutableListOf<SetSkip>()
    private var lastSampleAtMs = 0L
    private var lastCheckpointMs = 0L
    /** When the accumulator was last integrated forward — readings timestamped before it need a rebuild. */
    private var integratedToMs = 0L
    /** Readings that arrived out of order and were slotted back in — the link-health read-out's backfill count. */
    private var backfilled = 0
    private var lastHrRequestMs = 0L

    /**
     * The session that just finished. The watch resends its whole reading log for it after the stop,
     * and anything that never made it during the workout is merged into the saved record here.
     */
    private var finishedSessionId: String? = null
    private var finishedAtMs = 0L
    private val lateReadings = mutableListOf<HeartRateSample>()
    private var lateMergeJob: Job? = null

    /**
     * Reps the same workout was performed with last time, keyed slotId → (setIndex → reps). When the
     * "start from last time" setting is on, a work block opens on these instead of the plan's target —
     * so a workout you've done before pre-fills the counter with what you actually did, ready to match
     * or beat. Scoped by slotId, which only exists in this one saved workout, so the same movement in a
     * different workout never leaks its reps in. Empty when the setting is off or it's a new workout.
     * Exposed so the live screen can label the counter "last time" instead of "target" for these sets.
     */
    private val _previousReps = MutableStateFlow<Map<String, Map<Int, Int>>>(emptyMap())
    val previousReps: StateFlow<Map<String, Map<Int, Int>>> = _previousReps.asStateFlow()

    val isRunning: Boolean
        get() = _live.value?.status.let { it == SessionStatus.ACTIVE || it == SessionStatus.PAUSED }

    init {
        // Before anything else: a workout the process died in the middle of is still on disk.
        scope.launch { recoverUnfinished() }
        // Always listening: a workout started on the watch must create one here too.
        scope.launch { bus.commands.collect { applyRemote(it) } }
        scope.launch { profileRepository.profile.collect { updated ->
            profile = updated
            accumulator?.updateProfile(updated)
            _live.update { it?.copy(maxHr = updated.effectiveMaxHr) }
        } }
        scope.launch { sessionPrefs.prefs.collect { autoAdvanceRoutines = it.autoAdvanceRoutines } }
        scope.launch { bus.hrBatches.collect { onHrBatch(it) } }
        scope.launch { bus.watchState.collect { it?.let(::onWatchState) } }
        scope.launch { devicePrefs.prefs.collect { bandChosen = it.band != null } }
    }

    // ---- Bringing the watch back into the workout ----------------------------------------------------

    private val _rejoin = MutableStateFlow<WatchRejoin>(WatchRejoin.Idle)
    /** Where a "bring the watch back" attempt stands — the HR panel's and HUD chip's feedback. */
    val rejoin: StateFlow<WatchRejoin> = _rejoin.asStateFlow()

    private var rejoinJob: Job? = null
    /** When the watch last reported its state for *this* workout — proof its copy of it is alive. */
    private var lastWatchStateAtMs = 0L
    private var lastAutoRejoinMs = 0L
    private var autoRejoinFailures = 0
    /** When a Bluetooth band last delivered a reading — while it's streaming, it's the heart-rate source. */
    private var lastBandSampleAtMs = 0L
    /** A band is chosen in Settings → Devices: heart rate comes from it, so the watch isn't chased. */
    private var bandChosen = false
    private var lastGhostStopMs = 0L

    /**
     * Reconnect: bring the watch back into this workout. Pings it, re-sends the workout itself (plan,
     * progress, start time) and raises the watch app, then waits for heart rate — so it also recovers a
     * watch whose copy of the workout was lost, which the old ping-only reconnect couldn't.
     */
    fun rejoinWatch() = startRejoin(auto = false)

    private fun startRejoin(auto: Boolean) {
        if (rejoinJob?.isActive == true) return
        val cur = _live.value ?: return
        rejoinJob = scope.launch {
            _rejoin.value = WatchRejoin.Working(auto)
            val attemptAt = System.currentTimeMillis()
            watchConnection.reconnect().join()
            if (watchConnection.state.value.status == com.calistapp.app.data.sync.WatchLinkStatus.NO_DEVICE) {
                finishRejoin(WatchRejoin.Failed(WatchRejoin.Reason.NO_WATCH), auto)
                return@launch
            }
            watch.send(
                ControlPayload(
                    command = ControlCommand.START,
                    timestampMs = attemptAt,
                    origin = DeviceOrigin.PHONE,
                    exerciseType = cur.exerciseType,
                    sessionId = cur.id,
                    plan = cur.plan,
                    sessionStartMs = cur.startMs,
                ),
            )
            syncProgress()
            watchLauncher.launch()
            // Heart rate arriving is the only real proof; the watch's own state report proves it rejoined.
            val deadline = attemptAt + REJOIN_WAIT_MS
            while (System.currentTimeMillis() < deadline) {
                if (lastSampleAtMs > attemptAt) {
                    finishRejoin(WatchRejoin.Back, auto)
                    return@launch
                }
                delay(500)
            }
            val reason = if (lastWatchStateAtMs > attemptAt) WatchRejoin.Reason.NO_HEART_RATE else WatchRejoin.Reason.NO_ANSWER
            finishRejoin(WatchRejoin.Failed(reason), auto)
        }
    }

    private suspend fun finishRejoin(result: WatchRejoin, auto: Boolean) {
        _rejoin.value = result
        if (result == WatchRejoin.Back) {
            autoRejoinFailures = 0
            delay(REJOIN_BACK_SHOWN_MS)
            if (_rejoin.value == WatchRejoin.Back) _rejoin.value = WatchRejoin.Idle
        } else if (auto) {
            autoRejoinFailures++
        }
    }

    /**
     * The phone noticing silence by itself: no heart rate and no word from the watch's copy of this
     * workout for a while, though a watch is paired — rejoin without waiting for a tap. Throttled, and
     * it stops trying after a few misses (Reconnect still works by hand).
     */
    private fun maybeAutoRejoin(cur: LiveSession, now: Long) {
        if (rejoinJob?.isActive == true || autoRejoinFailures >= AUTO_REJOIN_MAX_FAILURES) return
        // Heart rate is the band's job; its own manager reconnects it. Don't wake a watch nobody asked for.
        if (bandChosen) return
        val status = watchConnection.state.value.status
        if (status == com.calistapp.app.data.sync.WatchLinkStatus.NO_DEVICE ||
            status == com.calistapp.app.data.sync.WatchLinkStatus.CHECKING
        ) return
        // Only once the watch has been part of this workout — a session started without a watch on
        // purpose shouldn't keep poking one that's merely paired.
        if (lastSampleAtMs == 0L && lastWatchStateAtMs == 0L && status != com.calistapp.app.data.sync.WatchLinkStatus.READY &&
            status != com.calistapp.app.data.sync.WatchLinkStatus.STREAMING
        ) return
        val lastSign = maxOf(lastSampleAtMs, lastWatchStateAtMs, cur.startMs)
        if (now - lastSign < AUTO_REJOIN_AFTER_MS || now - lastAutoRejoinMs < AUTO_REJOIN_EVERY_MS) return
        lastAutoRejoinMs = now
        startRejoin(auto = true)
    }

    /**
     * A state report from the watch. For this workout it's a sign of life. For a workout the phone isn't
     * running — one the watch resumed after a restart though the phone had finished it — it's a ghost
     * draining the battery, so the watch is told to stop (unless it's brand new: a workout the watch just
     * started may simply not have reached the phone yet).
     */
    private fun onWatchState(state: SessionStatePayload) {
        val now = System.currentTimeMillis()
        val cur = _live.value
        if (cur != null && state.sessionId == cur.id) {
            lastWatchStateAtMs = now
            return
        }
        val running = state.status == SessionStatus.ACTIVE || state.status == SessionStatus.PAUSED
        if (!running || now - state.startMs < GHOST_GRACE_MS || now - lastGhostStopMs < GHOST_GRACE_MS) return
        lastGhostStopMs = now
        scope.launch {
            watch.send(ControlPayload(ControlCommand.STOP, now, DeviceOrigin.PHONE, sessionId = state.sessionId))
            // The phone has a workout of its own running: put the watch on that one instead.
            if (cur != null) startRejoin(auto = true)
        }
    }

    // ---- Local actions — these broadcast to the watch -----------------------------------------

    fun start(type: ExerciseType, plan: WorkoutPlan = WorkoutPlan.EMPTY) = scope.launch {
        beginSession(UUID.randomUUID().toString(), type, plan, broadcast = true)
    }

    /**
     * Work/rest toggle. Going *into* work runs a short lead-in first (see [LiveSession.countdownUntilMs]);
     * tapping again during it cancels, so a mistaken tap doesn't force you to work a set.
     */
    fun toggleSegment() = scope.launch {
        val cur = _live.value ?: return@launch
        when {
            cur.countdownUntilMs != null -> cancelCountdown()
            cur.isWorking -> changeSegment(SegmentType.REST, broadcast = true)
            else -> startCountdownToWork()
        }
    }

    /** Skip the remaining lead-in and start working now. */
    fun startWorkNow() = scope.launch {
        if (_live.value?.countdownUntilMs == null) return@launch
        countdownJob?.cancel()
        _live.update { it?.copy(countdownUntilMs = null) }
        changeSegment(SegmentType.ACTIVE, broadcast = true)
    }

    private fun startCountdownToWork() {
        countdownJob?.cancel()
        val until = System.currentTimeMillis() + COUNTDOWN_MS
        _live.update { it?.copy(countdownUntilMs = until) }
        countdownJob = scope.launch {
            delay(COUNTDOWN_MS)
            _live.update { it?.copy(countdownUntilMs = null) }
            // Broadcast only now: the watch flips at the same instant we do, so a countdown running
            // on either device keeps the two in step without a second protocol message.
            changeSegment(SegmentType.ACTIVE, broadcast = true)
        }
    }

    private fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        _live.update { it?.copy(countdownUntilMs = null) }
    }

    fun selectSlot(slotId: String) = scope.launch { selectSlotInternal(slotId, broadcast = true) }

    fun adjustReps(delta: Int) = scope.launch {
        mutex.withLock {
            val cur = _live.value ?: return@withLock
            val updated = (cur.currentReps + delta).coerceAtLeast(0)
            accumulator?.setCurrentReps(updated)
            _live.update { it?.copy(currentReps = updated) }
        }
        refresh()
    }

    /**
     * Rate how the set in progress felt, on the exercise screen while it's fresh. Held as pending and
     * copied onto the set's log when it's banked (see [closeOpenSegment]); effort never enters the
     * calorie estimate, only history and the coach. Only meaningful during a work block.
     */
    fun setCurrentEffort(scale: EffortScale?, value: Double?) = scope.launch {
        mutex.withLock {
            val cur = _live.value ?: return@withLock
            if (cur.currentSegment != SegmentType.ACTIVE) return@withLock
            _live.update { it?.copy(pendingEffortScale = scale, pendingEffortValue = value) }
        }
        refresh()
    }

    /**
     * Change the load on the exercise in progress — you added a plate, or forgot to set it when
     * building the plan. Rewrites the live plan slot and the open segment so both the running score
     * and the stored record reflect the weight actually lifted, and syncs the plan to the watch.
     */
    fun setAddedWeight(kg: Double) = scope.launch {
        val updatedPlan = mutex.withLock {
            val cur = _live.value ?: return@withLock null
            val slotId = cur.currentSlotId ?: return@withLock null
            val clamped = kg.coerceIn(0.0, 300.0)
            val setIndex = cur.setIndex
            val plan = cur.plan.copy(
                exercises = cur.plan.exercises.map { slot ->
                    if (slot.slotId != slotId) {
                        slot
                    } else {
                        // On a per-set plan the change lands on the set being performed now; on a
                        // uniform one it moves the slot's nominal load. Either way [weightForSet]
                        // reads the value back at bank time.
                        val updatedSets = slot.plannedSets.takeIf { it.isNotEmpty() }
                            ?.mapIndexed { i, s -> if (i == setIndex - 1) s.copy(weightKg = clamped) else s }
                            ?: slot.plannedSets
                        slot.copy(
                            plannedSets = updatedSets,
                            metabolics = slot.metabolics.copy(externalLoadKg = clamped),
                        )
                    }
                },
            )
            val metabolics = plan.slot(slotId)?.metabolics
            // Re-tag the open segment, so the load counts toward the block being performed now.
            segments.lastOrNull()?.takeIf { it.endMs == null }?.let {
                segments[segments.lastIndex] = it.copy(metabolics = metabolics)
            }
            accumulator?.setCurrentExercise(slotId, plan.slot(slotId)?.name, metabolics)
            _live.update { it?.copy(plan = plan) }
            plan
        } ?: return@launch
        refresh()
        checkpoint(force = true)
        // Keep the watch's copy of the plan (and the weight it shows) in step.
        watch.send(
            ControlPayload(
                command = ControlCommand.SYNC_PLAN,
                timestampMs = System.currentTimeMillis(),
                origin = DeviceOrigin.PHONE,
                sessionId = _live.value?.id,
                plan = updatedPlan,
            ),
        )
    }

    /**
     * Redo the set in progress: reset its counter to the plan's target and restart the block clock,
     * so a set you started wrong (or want to run again) doesn't bank a half-count. Only meaningful
     * while a work block is open.
     */
    fun restartCurrentSet() = scope.launch {
        mutex.withLock {
            val cur = _live.value ?: return@withLock
            if (cur.currentSegment != SegmentType.ACTIVE) return@withLock
            val target = defaultRepsFor(cur.plan.slot(cur.currentSlotId), cur.setIndex)
            accumulator?.setCurrentReps(target)
            _live.update {
                it?.copy(
                    currentReps = target,
                    segmentStartMs = System.currentTimeMillis(),
                    pendingEffortScale = null,
                    pendingEffortValue = null,
                )
            }
        }
        refresh()
    }

    // ---- Skip, "do it later", remove — the three answers to "not this one" ----------------------

    /**
     * Skip the set in focus — the one being worked, or the one coming up while resting. It's recorded
     * as skipped and counts as resolved, so the session moves on and never comes back to it this
     * round; a circuit with more rounds still has it in the next one. A work block that was open is
     * turned back into recovery: nothing is banked for it (the old skip banked the counter's
     * pre-filled target as if the set had been done).
     */
    fun skipFocus() = scope.launch {
        val focus = _live.value?.focus ?: return@launch
        applySkip(focus.slotId, focus.setIndex, SkipReason.SKIPPED)
    }

    /** "Do it later": the set in focus moves to the end of its round (or, in a split, the workout). */
    fun deferFocus() = scope.launch {
        val focus = _live.value?.focus ?: return@launch
        applyDefer(focus.slotId)
    }

    /**
     * "Remove from today": drop [slotId] from this session — every set it still owed, in every round.
     * An exercise that hasn't been touched yet simply leaves the plan; one with sets already done keeps
     * them, and its remaining sets are recorded as removed. The saved workout is never touched here —
     * finishing offers to carry the change over (see [pendingWorkoutChanges]).
     */
    fun removeExercise(slotId: String) = scope.launch { applyRemove(slotId) }

    /** Undo a "remove from today" for an exercise that had sets done before it was dropped. */
    fun restoreExercise(slotId: String) = scope.launch {
        mutex.withLock {
            val cur = _live.value ?: return@withLock
            if (skips.none { it.slotId == slotId && it.reason == SkipReason.REMOVED }) return@withLock
            skips.removeAll { it.slotId == slotId && it.reason == SkipReason.REMOVED }
            _live.update { it?.copy(skips = skips.toList(), completedSets = resolvedFor(cur.plan)) }
        }
        afterProgressChange()
    }

    /** Skip whatever is left of [phase] — "Skip warm-up", or the cool-down on the way to finishing. */
    fun skipRestOfPhase(phase: SlotPhase) = scope.launch { skipPhaseInternal(phase) }

    /** [skipRestOfPhase], awaited — for finishing straight after, so the skip is in the saved record. */
    suspend fun skipRestOfPhaseNow(phase: SlotPhase) = skipPhaseInternal(phase)

    private suspend fun skipPhaseInternal(phase: SlotPhase) {
        // A move held part-way when you skip the rest of its routine still happened: keep the seconds
        // held, then skip what's left. ("Finish now" mid-stretch used to throw the stretch away.)
        val held = _live.value?.takeIf { it.isWorking && it.plan.phaseOf(it.currentSlotId) == phase && phase != SlotPhase.MAIN }
        if (held != null) {
            mutex.withLock {
                val seconds = (held.segmentElapsedMs / 1000).toInt().coerceAtLeast(1)
                accumulator?.setCurrentReps(seconds)
                _live.update { it?.copy(currentReps = seconds) }
            }
            changeSegment(SegmentType.REST, broadcast = true)
        }
        mutex.withLock {
            val cur = _live.value ?: return@withLock
            val now = System.currentTimeMillis()
            if (cur.isWorking && cur.plan.phaseOf(cur.currentSlotId) == phase) abandonOpenSet(now)
            cur.plan.slotsIn(phase).forEach { slot ->
                val resolved = cur.completedSets[slot.slotId] ?: 0
                for (i in resolved + 1..cur.plan.targetSetsFor(slot.slotId)) skips += SetSkip(slot.slotId, i, now)
            }
            _live.update { it?.copy(skips = skips.toList(), completedSets = resolvedFor(cur.plan)) }
        }
        afterProgressChange()
    }

    private suspend fun applySkip(slotId: String, setIndex: Int, reason: SkipReason) {
        mutex.withLock {
            val cur = _live.value ?: return@withLock
            if (skips.any { it.slotId == slotId && it.setIndex == setIndex }) return@withLock
            val now = System.currentTimeMillis()
            if (cur.isWorking && cur.currentSlotId == slotId) abandonOpenSet(now)
            skips += SetSkip(slotId, setIndex, now, reason)
            _live.update {
                it?.copy(
                    skips = skips.toList(),
                    completedSets = resolvedFor(cur.plan),
                    deferred = it.deferred - slotId,
                )
            }
        }
        afterProgressChange()
    }

    private suspend fun applyDefer(slotId: String) {
        mutex.withLock {
            val cur = _live.value ?: return@withLock
            val now = System.currentTimeMillis()
            if (cur.isWorking && cur.currentSlotId == slotId) abandonOpenSet(now)
            _live.update { it?.copy(deferred = it.deferred + slotId) }
        }
        afterProgressChange()
    }

    private suspend fun applyRemove(slotId: String) {
        mutex.withLock {
            val cur = _live.value ?: return@withLock
            val slot = cur.plan.slot(slotId) ?: return@withLock
            val now = System.currentTimeMillis()
            if (cur.isWorking && cur.currentSlotId == slotId) abandonOpenSet(now)
            val touched = setLogs.any { it.slotId == slotId } || skips.any { it.slotId == slotId }
            val plan = if (!touched) {
                cur.plan.withoutSlot(slotId)
            } else {
                val resolved = cur.completedSets[slotId] ?: 0
                for (i in resolved + 1..cur.plan.targetSetsFor(slot.slotId)) {
                    skips += SetSkip(slotId, i, now, SkipReason.REMOVED)
                }
                cur.plan
            }
            _live.update {
                it?.copy(
                    plan = plan,
                    skips = skips.toList(),
                    completedSets = resolvedFor(plan),
                    deferred = it.deferred - slotId,
                    currentSlotId = it.currentSlotId.takeIf { id -> plan.slot(id) != null }
                        ?: plan.upcoming(null, resolvedFor(plan), it.deferred - slotId)?.slotId,
                )
            }
        }
        afterProgressChange()
    }

    /**
     * Turn the open work block into recovery: the set never happened, so nothing is banked for it,
     * and the time it ran scores as rest. The accumulator is rebuilt so the live total agrees.
     */
    private fun abandonOpenSet(now: Long) {
        val last = segments.lastOrNull() ?: return
        if (last.endMs != null || last.type != SegmentType.ACTIVE) return
        segments[segments.lastIndex] = last.copy(type = SegmentType.REST, reps = 0)
        accumulator = rebuildAccumulator(profile, segments, samples, currentReps = 0)
        integratedToMs = now
        _live.update {
            it?.copy(
                currentSegment = SegmentType.REST,
                currentReps = 0,
                // The rest stopwatch starts now; the record keeps the abandoned stretch as recovery.
                segmentStartMs = now,
                pendingEffortScale = null,
                pendingEffortValue = null,
            )
        }
    }

    // ---- Editing today's session -------------------------------------------------------------------

    /** Add [exercise] to today's workout, after the last working exercise and ahead of any cool-down. */
    fun addExercise(exercise: Exercise) = scope.launch {
        val metabolics = ExerciseIntensity.deriveMetabolics(exercise)
        val slot = PlannedExercise(
            slotId = UUID.randomUUID().toString(),
            exerciseId = exercise.id,
            name = exercise.name,
            bodyPart = exercise.bodyPart,
            measure = if (metabolics.isometric) ExerciseMeasure.SECONDS else ExerciseMeasure.REPS,
            metabolics = metabolics,
        )
        editPlan { it.withAddedSlot(slot) }
    }

    /**
     * A reorder that's waiting on the user's answer — in a circuit it would leave exercises still owed
     * this round behind the one in progress. The editor shows its order while the question is up.
     */
    private val _pendingReorder = MutableStateFlow<PendingReorder?>(null)
    val pendingReorder: StateFlow<PendingReorder?> = _pendingReorder.asStateFlow()

    /**
     * Move [slotId] to [toIndex] within its own phase — and carry the session on in the new order. The
     * session used to keep whatever slot it had picked (the old first exercise, before set 1), so a
     * reorder looked ignored and the progress dashes lit the moved exercise in its new spot. The rules
     * live in [reorderEffect]; a circuit asks first when the new order leaves owed exercises behind you.
     */
    fun moveExercise(slotId: String, toIndex: Int) = scope.launch {
        val applied = mutex.withLock {
            val cur = _live.value ?: return@withLock false
            val after = cur.plan.withSlotMoved(slotId, toIndex)
            if (after == cur.plan) return@withLock false
            when (val effect = reorderEffect(cur.plan, after, slotId, cur.currentSlotId, cur.completedSets, cur.deferred, cur.isWorking, cur.roundOrder)) {
                is ReorderEffect.AskBehind -> {
                    _pendingReorder.value = PendingReorder(after, slotId, effect.behind)
                    false
                }
                is ReorderEffect.Apply -> {
                    applyReorderLocked(cur, after, effect)
                    true
                }
            }
        }
        if (applied) afterProgressChange()
    }

    /** The answer to [pendingReorder]; null puts the exercise back where it was. */
    fun resolveReorder(choice: BehindChoice?) = scope.launch {
        val pending = _pendingReorder.value ?: return@launch
        _pendingReorder.value = null
        if (choice == null) return@launch
        mutex.withLock {
            val cur = _live.value ?: return@withLock
            when (choice) {
                // This round keeps the order it was running in; the new one takes over next round.
                BehindChoice.NEXT_ROUND -> _live.update {
                    it?.copy(roundOrder = RoundOrder(cur.plan.roundOf(cur.completedSets), cur.plan.exercises.map { s -> s.slotId }))
                }
                BehindChoice.SKIP_THIS_ROUND -> {
                    val now = System.currentTimeMillis()
                    pending.behind.forEach { id ->
                        val setIndex = (cur.completedSets[id] ?: 0) + 1
                        if (skips.none { it.slotId == id && it.setIndex == setIndex }) skips += SetSkip(id, setIndex, now)
                    }
                    _live.update { it?.copy(skips = skips.toList()) }
                }
                // Nothing extra: the round walks on from the current exercise and comes back round to them.
                BehindChoice.LATER_THIS_ROUND -> Unit
            }
            _live.value?.let { applyReorderLocked(it, pending.plan, ReorderEffect.Apply()) }
        }
        afterProgressChange()
    }

    private fun applyReorderLocked(cur: LiveSession, after: WorkoutPlan, effect: ReorderEffect.Apply) {
        val anchor = effect.anchor?.takeIf { after.slot(it.slotId) != null }
        val opening = cur.completedSetCount == 0 && !cur.isWorking
        if (anchor != null && opening) {
            // Before set 1 the open rest is the opening warm-up of whichever exercise leads — re-point
            // it at the new first exercise, so the record and the live score agree with the screen.
            val slot = after.slot(anchor.slotId)
            segments.lastOrNull()?.takeIf { it.endMs == null }?.let {
                segments[segments.lastIndex] = it.copy(slotId = slot?.slotId, exerciseName = slot?.name, metabolics = slot?.metabolics)
            }
            accumulator?.setCurrentExercise(slot?.slotId, slot?.name, slot?.metabolics)
        }
        _live.update {
            it?.copy(
                plan = after,
                completedSets = resolvedFor(after),
                currentSlotId = anchor?.slotId ?: it.currentSlotId,
                setIndex = when {
                    anchor == null -> it.setIndex
                    opening -> 1
                    else -> (it.completedSets[anchor.slotId] ?: 0).coerceAtLeast(1)
                },
                reanchor = when {
                    effect.afterSet -> Reanchor.AFTER_SET
                    effect.afterExercise -> Reanchor.AFTER_EXERCISE
                    anchor != null -> null
                    else -> it.reanchor
                },
            )
        }
    }

    /** Change [slotId]'s remaining sets to [value] reps (or seconds) at [weightKg]. */
    fun retarget(slotId: String, value: Int, weightKg: Double) = scope.launch {
        val from = (_live.value?.completedSets?.get(slotId) ?: 0) + 1
        editPlan { it.withTarget(slotId, from, value, weightKg) }
    }

    /** A circuit's round count for today — never below the round already in progress. */
    fun setRounds(rounds: Int) = scope.launch {
        val floor = _live.value?.currentRound ?: 1
        editPlan { it.copy(rounds = rounds.coerceIn(floor, 9)) }
    }

    private suspend fun editPlan(transform: (WorkoutPlan) -> WorkoutPlan) {
        mutex.withLock {
            val cur = _live.value ?: return@withLock
            val plan = transform(cur.plan)
            if (plan == cur.plan) return@withLock
            _live.update {
                it?.copy(
                    plan = plan,
                    completedSets = resolvedFor(plan),
                    currentSlotId = it.currentSlotId.takeIf { id -> plan.slot(id) != null }
                        ?: plan.upcoming(null, resolvedFor(plan), it.deferred)?.slotId,
                )
            }
        }
        afterProgressChange()
    }

    /** After a skip, deferral or edit: refresh, persist, and hand the watch the whole picture. */
    private suspend fun afterProgressChange() {
        refresh()
        checkpoint(force = true)
        syncProgress()
    }

    /** Resolved sets for [plan]: banked sets (from the segments) plus skips, for slots still in it. */
    private fun resolvedFor(plan: WorkoutPlan): Map<String, Int> =
        resolvedSets(completedSetsFrom(segments), skips).filterKeys { plan.slot(it) != null }

    /**
     * Send the phone's progress to the watch — plan, resolved sets, deferrals, where it is and which
     * block it's in. The watch adopts it wholesale, so a skip or an edit can't leave the two devices
     * disagreeing about what comes next.
     */
    private suspend fun syncProgress() {
        progressPayload()?.let { watch.send(it) }
    }

    /** The phone's whole progress as a SYNC_PLAN message — see [syncProgress]. */
    private fun progressPayload(): ControlPayload? {
        val cur = _live.value ?: return null
        return ControlPayload(
            command = ControlCommand.SYNC_PLAN,
            timestampMs = System.currentTimeMillis(),
            origin = DeviceOrigin.PHONE,
            sessionId = cur.id,
            plan = cur.plan,
            slotId = cur.currentSlotId,
            setIndex = cur.setIndex,
            completed = cur.completedSets,
            deferred = cur.deferred.toList(),
            segment = cur.currentSegment,
            roundOrder = cur.roundOrder,
        )
    }

    /**
     * What finishing would offer to write back to the saved workout this session was started from:
     * its workout block as edited today (order, targets, exercises added or removed, rounds), never the
     * routines. Null when it wasn't started from a saved workout or nothing changed — then nothing is
     * asked.
     */
    suspend fun pendingWorkoutChanges(): WorkoutChanges? {
        val cur = _live.value ?: return null
        val sourceId = cur.plan.sourceWorkoutId ?: return null
        val saved = savedWorkouts.get(sourceId) ?: return null
        val edited = cur.plan.savableOver(saved.plan, cur.removedSlots)
        val lines = describeChanges(saved.plan, edited)
        if (lines.isEmpty()) return null
        return WorkoutChanges(sourceId, saved.name, edited.copy(sourceWorkoutId = null), lines)
    }

    suspend fun saveWorkoutChanges(changes: WorkoutChanges) =
        savedWorkouts.syncPlan(changes.workoutId, changes.plan)

    // ---- Guided routines ----------------------------------------------------------------------------

    /**
     * End the warm-up or cool-down move in progress now and go on to the next — the "Next stretch"
     * button, and what guided mode does by itself when the hold's time is up. The hold is logged as
     * the seconds actually held.
     */
    fun finishRoutineMove() = scope.launch { finishRoutineMoveInternal() }

    private suspend fun finishRoutineMoveInternal() {
        mutex.withLock {
            val cur = _live.value ?: return@withLock
            if (!cur.inRoutine) return@withLock
            val held = (cur.segmentElapsedMs / 1000).toInt().coerceAtLeast(1)
            accumulator?.setCurrentReps(held)
            _live.update { it?.copy(currentReps = held) }
        }
        changeSegment(SegmentType.REST, broadcast = true)
        // Straight on to the next move of the same routine; a phase change waits for the hand-off card.
        val next = _live.value ?: return
        if (next.upNextPhase == next.currentPhase && next.currentPhase != SlotPhase.MAIN) {
            startCountdownToWork()
        }
    }

    // ---- Journal edits — annotate banked sets. Effort feeds history/AI only, never calories. -----

    fun setSetEffort(slotId: String, setIndex: Int, scale: EffortScale?, value: Double?) =
        scope.launch { editSetLog(slotId, setIndex) { it.copy(effortScale = scale, effortValue = value) } }

    fun setSetNote(slotId: String, setIndex: Int, note: String) =
        scope.launch { editSetLog(slotId, setIndex) { it.copy(note = note) } }

    fun setSetReps(slotId: String, setIndex: Int, reps: Int) =
        scope.launch { editSetLog(slotId, setIndex) { it.copy(reps = reps.coerceAtLeast(0)) } }

    fun setSetWeight(slotId: String, setIndex: Int, kg: Double) =
        scope.launch { editSetLog(slotId, setIndex) { it.copy(weightKg = kg.coerceIn(0.0, 300.0)) } }

    private suspend fun editSetLog(slotId: String, setIndex: Int, edit: (SetLog) -> SetLog) {
        mutex.withLock {
            val i = setLogs.indexOfLast { it.slotId == slotId && it.setIndex == setIndex }
            if (i < 0) return@withLock
            setLogs[i] = edit(setLogs[i])
            _live.update { it?.copy(setLogs = setLogs.toList()) }
        }
        checkpoint(force = true)
    }

    fun pause() = scope.launch {
        _live.update { it?.copy(status = SessionStatus.PAUSED) }
        broadcast(ControlCommand.PAUSE)
        checkpoint(force = true)
    }

    fun resume() = scope.launch {
        _live.update { it?.copy(status = SessionStatus.ACTIVE) }
        broadcast(ControlCommand.RESUME)
        checkpoint(force = true)
    }

    /** Finish, compute the definitive summary, and persist. Returns the new session id. */
    suspend fun stop(): String? {
        val cur = _live.value ?: return null
        broadcast(ControlCommand.STOP)
        return finishSession(cur, persist = true)
    }

    fun discard() = scope.launch {
        broadcast(ControlCommand.STOP)
        loopJob?.cancel()
        countdownJob?.cancel()
        // Drop the checkpoint too, or the workout you just threw away comes back on next launch.
        _live.value?.id?.let { id -> runCatching { sessionRepository.deleteSession(id) } }
        clearBuffers()
        _live.value = null
    }

    // ---- Remote commands — applied WITHOUT rebroadcast ----------------------------------------

    private suspend fun applyRemote(payload: ControlPayload) {
        when (payload.command) {
            ControlCommand.START -> beginSession(
                sessionId = payload.sessionId ?: UUID.randomUUID().toString(),
                type = payload.exerciseType ?: ExerciseType.CALISTHENICS,
                plan = payload.plan ?: _live.value?.plan ?: WorkoutPlan.EMPTY,
                broadcast = false,
            )
            ControlCommand.STOP -> _live.value?.let { finishSession(it, persist = true) }
            // The watch names the set it started; follow it rather than re-deriving, so the two can't
            // pick different exercises even if one is a step behind on a skip.
            ControlCommand.SET_ACTIVE -> changeSegment(SegmentType.ACTIVE, broadcast = false, target = payload.target())
            ControlCommand.SET_REST -> changeSegment(SegmentType.REST, broadcast = false)
            ControlCommand.SET_EXERCISE -> payload.slotId?.let { selectSlotInternal(it, broadcast = false) }
            ControlCommand.LOG_SET -> {
                mutex.withLock {
                    accumulator?.setCurrentReps(payload.reps)
                    _live.update { it?.copy(currentReps = payload.reps) }
                }
                refresh()
            }
            ControlCommand.SYNC_PLAN -> payload.plan?.let { plan ->
                _live.update { it?.copy(plan = plan) }
            }
            // The watch's skip and "later" buttons: applied here, then the phone's progress goes back
            // to the watch (inside the apply), which settles any difference.
            ControlCommand.SKIP_SET -> payload.slotId?.let { slot ->
                if (payload.skipReason == SkipReason.REMOVED) {
                    applyRemove(slot)
                } else {
                    applySkip(slot, payload.setIndex.takeIf { it > 0 } ?: ((_live.value?.completedSets?.get(slot) ?: 0) + 1), SkipReason.SKIPPED)
                }
            }
            ControlCommand.DEFER -> payload.slotId?.let { applyDefer(it) }
            ControlCommand.PAUSE -> _live.update { it?.copy(status = SessionStatus.PAUSED) }
            ControlCommand.RESUME -> _live.update { it?.copy(status = SessionStatus.ACTIVE) }
            // Liveness handshake — owned by WatchConnectionMonitor, nothing to do to the session.
            ControlCommand.PING, ControlCommand.HELLO -> Unit
            // Phone→watch only; the phone updates itself from the settings screen.
            ControlCommand.CHECK_UPDATE, ControlCommand.REQUEST_HR -> Unit
        }
    }

    private fun ControlPayload.target(): NextUp? {
        val cur = _live.value ?: return null
        val slot = slotId?.takeIf { cur.plan.slot(it) != null } ?: return null
        // An older watch build sends no set number; the next unresolved one is the same thing.
        return NextUp(slot, setIndex.takeIf { it > 0 } ?: ((cur.completedSets[slot] ?: 0) + 1))
    }

    // ---- Crash recovery --------------------------------------------------------------------------

    /**
     * Pick up a workout the app didn't get to finish.
     *
     * A recent one is resumed outright — the usual cause is the process being killed mid-session
     * while the watch kept streaming, and the right answer is to carry on as if nothing happened. An
     * old one is banked as a finished session instead of resumed: an hour-old checkpoint means the
     * workout ended without a Finish tap, and reviving it would show an elapsed clock counting the
     * time the phone spent in someone's pocket. Either way the training is kept.
     */
    private suspend fun recoverUnfinished() {
        val saved = runCatching { sessionRepository.findUnfinished() }.getOrNull() ?: return
        if (isRunning) return

        profile = profileRepository.profile.first()
        val now = System.currentTimeMillis()
        val lastActivityMs = saved.samples.lastOrNull()?.timestampMs
            ?: saved.segments.lastOrNull()?.startMs
            ?: saved.startMs

        if (now - lastActivityMs <= RESUME_WINDOW_MS) {
            resumeSession(saved, now)
        } else {
            bankAbandonedSession(saved, lastActivityMs)
        }
    }

    private fun resumeSession(saved: WorkoutSession, now: Long) {
        clearBuffers()
        samples += saved.samples
        segments += saved.segments
        setLogs += saved.setLogs
        skips += saved.skips

        val open = segments.lastOrNull()
        // The in-flight rep count rides on the open segment — see [checkpoint], which stamps it
        // there precisely so it survives this round trip.
        val openReps = if (open?.type == SegmentType.ACTIVE) open.reps else 0

        accumulator = rebuildAccumulator(
            profile = profile,
            segments = segments,
            samples = samples,
            currentReps = openReps,
        )
        lastSampleAtMs = 0L
        integratedToMs = samples.lastOrNull()?.timestampMs ?: 0L

        val completed = resolvedFor(saved.plan)
        val slotId = open?.slotId
        _live.value = LiveSession(
            id = saved.id,
            exerciseType = saved.exerciseType,
            startMs = saved.startMs,
            status = saved.status,
            currentSegment = open?.type ?: SegmentType.REST,
            plan = saved.plan,
            currentSlotId = slotId,
            currentReps = openReps,
            setIndex = (completed[slotId] ?: 0) + if (open?.type == SegmentType.ACTIVE) 1 else 0,
            completedSets = completed,
            summary = accumulator?.snapshot(now) ?: SessionSummary.EMPTY,
            lastBpm = saved.samples.lastOrNull()?.bpm ?: 0,
            nowMs = now,
            segmentStartMs = open?.startMs ?: saved.startMs,
            receivingHr = false,
            setLogs = setLogs.toList(),
            skips = skips.toList(),
            recentBpm = samples.takeLast(RECENT_BPM_MAX).map { it.bpm },
            maxHr = profile.effectiveMaxHr,
        )

        WorkoutSessionService.start(context)
        startTicking()
    }

    /** Score what was recorded and file it, using the last real activity as the end. */
    private suspend fun bankAbandonedSession(saved: WorkoutSession, endMs: Long) {
        val summary = engine.compute(saved.samples, saved.segments, profile, endMs = endMs, plan = saved.plan)
        val completed = saved.copy(endMs = endMs, status = SessionStatus.COMPLETED, summary = summary)
        runCatching { sessionRepository.saveSession(completed) }
        exportToFitPal(completed)
    }

    /**
     * Push a finished workout to FitPal, fire-and-forget on the app scope. Never blocks or fails the
     * save — if FitPal is closed/not installed the session stays flagged unsynced and is retried on
     * next app open (and by the manual button). The push is an upsert keyed by session id, so a
     * session corrected afterwards (late heart rate merged in) simply updates its row.
     */
    private fun exportToFitPal(session: WorkoutSession) {
        scope.launch { runCatching { fitPalExportManager.push(session) } }
    }

    /**
     * Replay the rule [changeSegmentLocked] applies live — a set is banked when an ACTIVE block
     * gives way to a different kind of block — so a resumed session's progress matches what the
     * screen showed before the interruption.
     */
    private fun completedSetsFrom(segments: List<Segment>): Map<String, Int> {
        val completed = mutableMapOf<String, Int>()
        segments.zipWithNext { a, b ->
            if (a.type == SegmentType.ACTIVE && b.type != a.type) {
                a.slotId?.let { completed[it] = (completed[it] ?: 0) + 1 }
            }
        }
        return completed
    }

    // ---- Checkpointing ---------------------------------------------------------------------------

    /**
     * Write the session as it stands to the database, so an interruption costs seconds rather than
     * the whole workout.
     *
     * Throttled, because re-encoding a growing sample list on every reading is wasted work; forced
     * at every structural event, because those are the points where losing state actually hurts.
     * The row carries status ACTIVE/PAUSED, which is what keeps it out of history and what
     * [recoverUnfinished] looks for.
     */
    private suspend fun checkpoint(force: Boolean = false) {
        val now = System.currentTimeMillis()
        val snapshot = mutex.withLock {
            val cur = _live.value ?: return
            if (!force && now - lastCheckpointMs < CHECKPOINT_INTERVAL_MS) return
            lastCheckpointMs = now

            // Stamp the reps of the block in progress onto the open segment. Nothing else persists
            // them, and a resumed set that forgot its reps would under-count the workout.
            val openSegments = segments.toMutableList()
            openSegments.lastOrNull()
                ?.takeIf { it.endMs == null && it.type == SegmentType.ACTIVE }
                ?.let { openSegments[openSegments.lastIndex] = it.copy(reps = cur.currentReps) }

            WorkoutSession(
                id = cur.id,
                exerciseType = cur.exerciseType,
                startMs = cur.startMs,
                endMs = null,
                status = cur.status,
                samples = samples.toList(),
                segments = openSegments,
                plan = cur.plan,
                setLogs = setLogs.toList(),
                skips = skips.toList(),
                exerciseName = sessionTitle(cur.plan),
                summary = cur.summary,
            )
        }
        // Outside the lock: a database write must never stall the incoming heart-rate stream.
        runCatching { sessionRepository.saveSession(snapshot) }
    }

    /**
     * What a finished session is called in history. A named workout ("Main Calisthenics") keeps its
     * own name; only a nameless plan — a single exercise launched from the gallery — falls back to the
     * first movement's name. The old code always used the first exercise, so a five-exercise workout
     * showed up as "Pull-Up" rather than the name the user typed when they built it.
     */
    private fun sessionTitle(plan: WorkoutPlan): String? =
        plan.name.takeIf { it.isNotBlank() } ?: plan.exercises.firstOrNull()?.name

    // ---- Lifecycle -----------------------------------------------------------------------------

    private suspend fun beginSession(
        sessionId: String,
        type: ExerciseType,
        plan: WorkoutPlan,
        broadcast: Boolean,
    ) {
        if (isRunning) return
        profile = profileRepository.profile.first()
        val now = System.currentTimeMillis()
        val firstSlot = plan.exercises.firstOrNull()

        clearBuffers()
        _previousReps.value = loadPreviousReps(plan)
        // Sessions open in REST. You start the app before you start the set — walking to the bar,
        // finding the timer — and counting that as work inflates both the clock and the calories.
        accumulator = LiveCalorieAccumulator(profile).apply { begin(now, SegmentType.REST) }
        integratedToMs = now
        segments += Segment(
            type = SegmentType.REST,
            startMs = now,
            slotId = firstSlot?.slotId,
            exerciseName = firstSlot?.name,
            metabolics = firstSlot?.metabolics,
        )

        _live.value = LiveSession(
            id = sessionId,
            exerciseType = type,
            startMs = now,
            status = SessionStatus.ACTIVE,
            currentSegment = SegmentType.REST,
            plan = plan,
            currentSlotId = firstSlot?.slotId,
            currentReps = 0,
            setIndex = 1,
            completedSets = emptyMap(),
            summary = SessionSummary.EMPTY,
            lastBpm = 0,
            nowMs = now,
            segmentStartMs = now,
            receivingHr = false,
            maxHr = profile.effectiveMaxHr,
        )
        accumulator?.setCurrentExercise(firstSlot?.slotId, firstSlot?.name, firstSlot?.metabolics)

        if (broadcast) {
            watch.send(
                ControlPayload(
                    command = ControlCommand.START,
                    timestampMs = now,
                    origin = DeviceOrigin.PHONE,
                    exerciseType = type,
                    sessionId = sessionId,
                    plan = plan,
                ),
            )
            // Raise the watch UI too. The message above already starts tracking; this is what makes
            // the watch actually *show* the workout instead of sitting on the watch face.
            scope.launch { watchLauncher.launch() }
        }
        // The service holds the process; the checkpoint makes the workout survive losing it anyway.
        WorkoutSessionService.start(context)
        startTicking()
        checkpoint(force = true)
    }

    private suspend fun finishSession(cur: LiveSession, persist: Boolean): String? {
        loopJob?.cancel()
        countdownJob?.cancel()
        val now = System.currentTimeMillis()
        closeOpenSegment(now, cur)

        // The engine — not the live accumulator — produces the number we store.
        val summary = engine.compute(samples.toList(), segments.toList(), profile, endMs = now, plan = cur.plan)
        if (persist) {
            val completed = WorkoutSession(
                id = cur.id,
                exerciseType = cur.exerciseType,
                startMs = cur.startMs,
                endMs = now,
                status = SessionStatus.COMPLETED,
                samples = samples.toList(),
                segments = segments.toList(),
                plan = cur.plan,
                setLogs = setLogs.toList(),
                skips = skips.toList(),
                exerciseName = sessionTitle(cur.plan),
                summary = summary,
            )
            sessionRepository.saveSession(completed)
            // Auto-send to FitPal the moment a workout finishes.
            exportToFitPal(completed)
            // Ask the watch for its whole reading log once more: anything lost in transit during the
            // workout is merged into the saved session as it arrives (see [onHrBatch]).
            finishedSessionId = cur.id
            finishedAtMs = now
            scope.launch {
                watch.send(
                    ControlPayload(
                        command = ControlCommand.REQUEST_HR,
                        timestampMs = now,
                        origin = DeviceOrigin.PHONE,
                        sessionId = cur.id,
                        sinceMs = cur.startMs - 1,
                    ),
                )
            }
        }
        clearBuffers()
        _live.value = null
        return cur.id
    }

    /**
     * Close the current block and open the next. Leaving ACTIVE records the set that was just
     * performed — the reps are what feed the mechanical-work term of the calorie estimate.
     */
    private suspend fun changeSegment(type: SegmentType, broadcast: Boolean, target: NextUp? = null) {
        // Messages are built under the lock but sent outside it: a Data Layer round trip is slow
        // enough that holding the mutex across it would stall incoming heart-rate samples.
        val outbound = mutex.withLock { changeSegmentLocked(type, broadcast, target) }
        outbound.forEach { watch.send(it) }
        refresh()
        // A banked set is exactly the state worth not losing.
        checkpoint(force = true)
    }

    private fun changeSegmentLocked(type: SegmentType, broadcast: Boolean, target: NextUp?): List<ControlPayload> {
        val cur = _live.value ?: return emptyList()
        if (cur.currentSegment == type) return emptyList()
        val now = System.currentTimeMillis()

        val repsJustDone = cur.currentReps
        closeOpenSegment(now, cur)

        val finishedSlot = cur.currentSlotId
        val banked = cur.currentSegment == SegmentType.ACTIVE && finishedSlot != null
        val completed = if (banked) {
            cur.completedSets + (finishedSlot!! to (cur.completedSets[finishedSlot] ?: 0) + 1)
        } else {
            cur.completedSets
        }
        // A deferred set that's now been done is no longer deferred.
        val deferred = if (banked) cur.deferred - finishedSlot!! else cur.deferred

        // Returning to work: the plan decides what's next, so a circuit rotates through the list
        // while a split finishes each exercise first. Both devices ask the same function.
        var slotId = cur.currentSlotId
        var setIndex = cur.setIndex
        if (type == SegmentType.ACTIVE) {
            // `upcoming` works the on-screen slot for the opening block and asks nextUp after that —
            // the same rule the live screen's "up next" and the watch use. A remote start names its
            // set; that wins, so the devices can't diverge.
            (target ?: cur.plan.upcoming(slotId, completed, deferred, cur.roundOrder))?.let { slotId = it.slotId; setIndex = it.setIndex }
        }

        // A reorder waiting on this set (or this exercise) to finish: point the session at the first
        // unfinished exercise in the new order now. Only the live position moves — the rest block
        // itself still belongs to the set just done.
        var liveSlotId = slotId
        var liveSetIndex = setIndex
        var reanchor = cur.reanchor
        if (type == SegmentType.REST && banked && reanchor != null && !cur.plan.isCircuit) {
            val owes = cur.plan.targetSetsFor(finishedSlot) - (completed[finishedSlot] ?: 0) > 0
            if (reanchor == Reanchor.AFTER_SET || !owes) {
                cur.plan.nextUp(null, completed, deferred)?.let { next ->
                    liveSlotId = next.slotId
                    liveSetIndex = (completed[next.slotId] ?: 0).coerceAtLeast(1)
                }
                reanchor = null
            }
        }

        val slot = cur.plan.slot(slotId)
        // Score this block on *this set's* load, so a top set at +20 kg and its back-offs at bodyweight
        // aren't estimated as if they were the same. Uniform plans are unaffected — the per-set weight
        // equals the slot's nominal one.
        val setMetabolics = slot?.metabolics?.copy(externalLoadKg = weightForSet(slot, setIndex))
        accumulator?.startSegment(type, now, slot?.slotId, slot?.name, setMetabolics)
        integratedToMs = maxOf(integratedToMs, now)
        // A work block opens pre-filled with the target — the overwhelmingly common outcome is "I did
        // what I planned", so the counter starts there and you adjust only when you didn't. A rest
        // block carries no reps.
        val openingReps = if (type == SegmentType.ACTIVE) defaultRepsFor(slot, setIndex) else 0
        accumulator?.setCurrentReps(openingReps)
        segments += Segment(
            type = type,
            startMs = now,
            slotId = slot?.slotId,
            exerciseName = slot?.name,
            metabolics = setMetabolics,
        )
        _live.update {
            it?.copy(
                currentSegment = type,
                completedSets = completed,
                deferred = deferred,
                currentSlotId = liveSlotId,
                setIndex = liveSetIndex,
                currentReps = openingReps,
                segmentStartMs = now,
                // The set just banked consumed its effort; the next one starts unrated.
                pendingEffortScale = null,
                pendingEffortValue = null,
                reanchor = reanchor,
            )
        }
        val reanchored = liveSlotId != slotId

        if (!broadcast) return if (reanchored) listOfNotNull(progressPayload()) else emptyList()
        return buildList {
            // Tell the watch what the set was before telling it the state changed, so its rep
            // count matches ours.
            if (cur.currentSegment == SegmentType.ACTIVE && repsJustDone > 0) {
                add(
                    ControlPayload(
                        command = ControlCommand.LOG_SET,
                        timestampMs = now,
                        origin = DeviceOrigin.PHONE,
                        sessionId = cur.id,
                        slotId = cur.currentSlotId,
                        reps = repsJustDone,
                        setIndex = cur.setIndex,
                    ),
                )
            }
            add(
                ControlPayload(
                    command = if (type == SegmentType.ACTIVE) ControlCommand.SET_ACTIVE else ControlCommand.SET_REST,
                    timestampMs = now,
                    origin = DeviceOrigin.PHONE,
                    sessionId = cur.id,
                    slotId = slotId,
                    setIndex = setIndex,
                ),
            )
            // Re-pointed after the set: hand the watch the new position, or its next start would
            // still walk on from the exercise just finished.
            if (reanchored) progressPayload()?.let(::add)
        }
    }

    private suspend fun selectSlotInternal(slotId: String, broadcast: Boolean) {
        val outbound = mutex.withLock { selectSlotLocked(slotId, broadcast) }
        outbound?.let { watch.send(it) }
        refresh()
        checkpoint(force = true)
    }

    private fun selectSlotLocked(slotId: String, broadcast: Boolean): ControlPayload? {
        val cur = _live.value ?: return null
        if (cur.currentSlotId == slotId) return null
        val now = System.currentTimeMillis()

        // A different movement must be scored separately, so close the current block.
        closeOpenSegment(now, cur)
        val slot = cur.plan.slot(slotId)
        accumulator?.startSegment(cur.currentSegment, now, slot?.slotId, slot?.name, slot?.metabolics)
        segments += Segment(
            type = cur.currentSegment,
            startMs = now,
            slotId = slot?.slotId,
            exerciseName = slot?.name,
            metabolics = slot?.metabolics,
        )
        _live.update {
            it?.copy(
                currentSlotId = slotId,
                setIndex = (it.completedSets[slotId] ?: 0) + 1,
                currentReps = 0,
                segmentStartMs = now,
                pendingEffortScale = null,
                pendingEffortValue = null,
            )
        }

        return if (broadcast) {
            ControlPayload(
                command = ControlCommand.SET_EXERCISE,
                timestampMs = now,
                origin = DeviceOrigin.PHONE,
                sessionId = cur.id,
                slotId = slotId,
            )
        } else {
            null
        }
    }

    /** Seal the open segment with the reps performed, and record the set. */
    private fun closeOpenSegment(now: Long, cur: LiveSession) {
        val last = segments.lastOrNull() ?: return
        if (last.endMs != null) return
        val sealed = last.copy(endMs = now, reps = if (last.type == SegmentType.ACTIVE) cur.currentReps else 0)
        segments[segments.lastIndex] = sealed

        val slotId = sealed.slotId
        if (sealed.type == SegmentType.ACTIVE && slotId != null && cur.currentReps > 0) {
            val slot = cur.plan.slot(slotId)
            setLogs += SetLog(
                slotId = slotId,
                exerciseId = slot?.exerciseId.orEmpty(),
                exerciseName = sealed.exerciseName.orEmpty(),
                setIndex = cur.setIndex,
                reps = cur.currentReps,
                // A hold's count is seconds — say so on the log, not only in the reps field.
                seconds = if (slot?.measure == ExerciseMeasure.SECONDS) cur.currentReps else 0,
                startMs = sealed.startMs,
                endMs = now,
                weightKg = weightForSet(slot, cur.setIndex),
                // Effort rated on the exercise screen during the set rides along onto its log.
                effortScale = cur.pendingEffortScale,
                effortValue = cur.pendingEffortValue,
            )
        }
    }

    // ---- Streams -------------------------------------------------------------------------------

    /**
     * A batch of readings from the watch. While the workout runs they're merged into the stream by
     * measurement time — batches arrive late and out of order now that the watch delivers them every
     * few seconds with the screen off, resends what failed, and backfills on request. After the
     * workout, readings for it are merged into the saved record instead.
     */
    private suspend fun onHrBatch(batch: HrBatch) {
        val cur = _live.value
        // One heart-rate source at a time: with a band streaming, the watch's readings (if it's on too)
        // would interleave with the band's and jitter the curve — the band is the one you chose.
        val now = System.currentTimeMillis()
        if (batch.source == HrSource.BAND) lastBandSampleAtMs = now
        if (batch.source == HrSource.WATCH && now - lastBandSampleAtMs < BAND_PREFERRED_MS && cur?.id == batch.sessionId) return
        when {
            cur != null && batch.sessionId == cur.id -> if (cur.status == SessionStatus.ACTIVE) mergeLive(batch.samples)
            batch.sessionId == finishedSessionId &&
                System.currentTimeMillis() - finishedAtMs < LATE_MERGE_WINDOW_MS -> mergeLate(batch.samples)
        }
    }

    private suspend fun mergeLive(incoming: List<HeartRateSample>) {
        var requestSince: Long? = null
        mutex.withLock {
            val prevLast = samples.lastOrNull()?.timestampMs
            val (older, newer) = incoming.partition { prevLast != null && it.timestampMs < prevLast }
            val backfill = samples.mergeReadings(older)
            val fresh = samples.mergeReadings(newer)
            if (backfill + fresh == 0) return@withLock
            backfilled += backfill
            lastSampleAtMs = System.currentTimeMillis()
            // Heart rate is back on its own: a failed rejoin's warning no longer applies.
            if (_rejoin.value is WatchRejoin.Failed) _rejoin.value = WatchRejoin.Idle
            autoRejoinFailures = 0

            // A gap before this batch: ask the watch for whatever it measured in between.
            val firstNew = newer.minOfOrNull { it.timestampMs }
            if (prevLast != null && firstNew != null && firstNew - prevLast > GAP_REQUEST_MS) requestSince = prevLast

            // In order and newer than anything integrated: stream them in. Otherwise (a batch delivered
            // late, a backfill) replay the session so the live total matches what the engine will store.
            val earliestAdded = (older + newer).minOf { it.timestampMs }
            if (backfill == 0 && earliestAdded > integratedToMs) {
                newer.sortedBy { it.timestampMs }.forEach { accumulator?.addSample(it) }
            } else {
                val reps = _live.value?.currentReps ?: 0
                accumulator = rebuildAccumulator(profile, segments, samples, currentReps = reps)
            }
            integratedToMs = maxOf(integratedToMs, samples.last().timestampMs)
            _live.update {
                it?.copy(
                    lastBpm = samples.last().bpm,
                    receivingHr = true,
                    recentBpm = samples.takeLast(RECENT_BPM_MAX).map { s -> s.bpm },
                )
            }
        }
        requestSince?.let { requestReadings(it) }
        refresh()
        checkpoint()
    }

    /** Readings for the session that just ended: buffered briefly, then merged into the saved record. */
    private fun mergeLate(incoming: List<HeartRateSample>) {
        synchronized(lateReadings) { lateReadings += incoming }
        lateMergeJob?.cancel()
        lateMergeJob = scope.launch {
            delay(LATE_MERGE_DEBOUNCE_MS)
            val id = finishedSessionId ?: return@launch
            val batch = synchronized(lateReadings) { lateReadings.toList().also { lateReadings.clear() } }
            val saved = runCatching { sessionRepository.getSession(id) }.getOrNull() ?: return@launch
            val merged = saved.samples.toMutableList()
            if (merged.mergeReadings(batch) == 0) return@launch
            val summary = engine.compute(merged, saved.segments, profile, endMs = saved.endMs, plan = saved.plan)
            val updated = saved.copy(samples = merged, summary = summary)
            runCatching { sessionRepository.saveSession(updated) }
            exportToFitPal(updated)
        }
    }

    /** Ask the watch to resend what it measured after [sinceMs] — throttled, it's a repair, not a poll. */
    private suspend fun requestReadings(sinceMs: Long) {
        val now = System.currentTimeMillis()
        if (now - lastHrRequestMs < HR_REQUEST_THROTTLE_MS) return
        lastHrRequestMs = now
        val id = _live.value?.id ?: return
        watch.send(
            ControlPayload(
                command = ControlCommand.REQUEST_HR,
                timestampMs = now,
                origin = DeviceOrigin.PHONE,
                sessionId = id,
                sinceMs = sinceMs,
            ),
        )
    }

    private fun startTicking() {
        loopJob?.cancel()
        loopJob = scope.launch {
            while (true) {
                delay(1000)
                val cur = _live.value ?: break
                if (cur.status != SessionStatus.ACTIVE) continue
                val now = System.currentTimeMillis()
                mutex.withLock {
                    accumulator?.advanceTo(now)
                    integratedToMs = maxOf(integratedToMs, now)
                }
                refresh()
                // Silence from the watch while a workout runs: ask for anything it has that we don't.
                val newest = samples.lastOrNull()?.timestampMs
                if (newest != null && now - newest > STALE_REQUEST_MS) requestReadings(newest)
                maybeAutoRejoin(cur, now)
                maybeAdvanceRoutine()
            }
        }
    }

    /** Guided mode: when a warm-up or cool-down hold reaches its time, end it and start the next. */
    private suspend fun maybeAdvanceRoutine() {
        if (!autoAdvanceRoutines) return
        val cur = _live.value ?: return
        if (!cur.inRoutine || cur.countdownUntilMs != null) return
        val slot = cur.currentExercise ?: return
        if (slot.measure != ExerciseMeasure.SECONDS) return
        val target = cur.currentSet?.reps ?: slot.targetSeconds
        if (cur.segmentElapsedMs >= target * 1000L) finishRoutineMoveInternal()
    }

    /** Publish a fresh snapshot. O(1) — this is what the quadratic recompute was replaced with. */
    private fun refresh() {
        val now = System.currentTimeMillis()
        // No heart-rate source at all (no watch, no band): show the same estimate the finished
        // session will be scored with, instead of a live counter stuck near zero.
        val snap = (if (samples.isEmpty()) liveEstimate(now) else null) ?: accumulator?.snapshot(now) ?: return
        _live.update {
            it?.copy(
                summary = snap,
                nowMs = now,
                receivingHr = lastSampleAtMs > 0 && now - lastSampleAtMs < HR_STALE_MS,
                setLogs = setLogs.toList(),
                skips = skips.toList(),
                hrLink = linkHealthOf(samples, now, backfilled),
                maxHr = profile.effectiveMaxHr,
            )
        }
    }

    /** The no-heart-rate estimate for the session so far — see [com.calistapp.core.calorie.NoHeartRateEstimate]. */
    private fun liveEstimate(now: Long): SessionSummary? {
        val plan = _live.value?.plan ?: return null
        val segs = runCatching { segments.toList() }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching { engine.compute(emptyList(), segs, profile, endMs = now, plan = plan) }.getOrNull()
    }

    private suspend fun broadcast(command: ControlCommand) {
        val cur = _live.value ?: return
        watch.send(
            ControlPayload(
                command = command,
                timestampMs = System.currentTimeMillis(),
                origin = DeviceOrigin.PHONE,
                sessionId = cur.id,
            ),
        )
    }

    /**
     * The reps a work block opens on: the target for *this set* of the movement (per-set when the
     * plan carries it, else the uniform target), or 0 when free-form.
     */
    private fun defaultRepsFor(slot: PlannedExercise?, setIndex: Int): Int {
        slot ?: return 0
        // A workout done before opens each rep set on what was actually done last time, not the plan's
        // target — so you're matching or beating yourself, not chasing a number you may have set once.
        // Timed holds keep their planned duration; the map is empty when the setting is off.
        if (slot.measure == ExerciseMeasure.REPS) {
            _previousReps.value[slot.slotId]?.get(setIndex)?.let { return it }
        }
        slot.sets().getOrNull(setIndex - 1)?.let { return it.reps }
        return if (slot.measure == ExerciseMeasure.SECONDS) slot.targetSeconds else slot.targetReps
    }

    /**
     * Load, per rep-counted slot, the reps that slot was performed with in the most recent earlier
     * session — the "start from last time" data. Off (empty) unless the setting is on. Runs once at
     * session start, off the sample path; failures degrade to the plan's targets.
     */
    private suspend fun loadPreviousReps(plan: WorkoutPlan): Map<String, Map<Int, Int>> {
        if (plan.isEmpty || !sessionPrefs.prefs.first().startFromLastTime) return emptyMap()
        val performed = runCatching { sessionRepository.observePerformed().first() }.getOrDefault(emptyList())
        val bySlot = HashMap<String, Map<Int, Int>>()
        for (slot in plan.exercises) {
            if (slot.measure != ExerciseMeasure.REPS) continue
            val prior = performed
                .filter { s -> s.setLogs.any { it.slotId == slot.slotId && it.reps > 0 } }
                .maxByOrNull { it.startMs } ?: continue
            val reps = prior.setLogs
                .filter { it.slotId == slot.slotId && it.reps > 0 }
                .associate { it.setIndex to it.reps }
            if (reps.isNotEmpty()) bySlot[slot.slotId] = reps
        }
        return bySlot
    }

    /** Added load for a given set of a slot — per-set when present, else the slot's nominal weight. */
    private fun weightForSet(slot: PlannedExercise?, setIndex: Int): Double {
        slot ?: return 0.0
        return slot.sets().getOrNull(setIndex - 1)?.weightKg ?: slot.addedWeightKg
    }

    private fun clearBuffers() {
        samples.clear()
        segments.clear()
        setLogs.clear()
        skips.clear()
        _previousReps.value = emptyMap()
        lastSampleAtMs = 0L
        lastCheckpointMs = 0L
        integratedToMs = 0L
        backfilled = 0
        lastHrRequestMs = 0L
        accumulator = null
        _pendingReorder.value = null
        rejoinJob?.cancel()
        _rejoin.value = WatchRejoin.Idle
        lastWatchStateAtMs = 0L
        lastAutoRejoinMs = 0L
        autoRejoinFailures = 0
    }

    private companion object {
        /** How many recent readings the HUD sparkline keeps (~one minute at 1 Hz). */
        const val RECENT_BPM_MAX = 60

        /** No reading for this long means the watch link has gone quiet. */
        const val HR_STALE_MS = 10_000L

        /** How long a rejoin waits for heart rate before calling it failed. */
        const val REJOIN_WAIT_MS = 15_000L

        /** How long "heart rate back" stays up after a rejoin worked. */
        const val REJOIN_BACK_SHOWN_MS = 3_000L

        /** Silence (no heart rate, no watch state) that sets off an automatic rejoin… */
        const val AUTO_REJOIN_AFTER_MS = 15_000L

        /** …no more often than this… */
        const val AUTO_REJOIN_EVERY_MS = 60_000L

        /** …and not after this many misses in a row (Reconnect still works by hand). */
        const val AUTO_REJOIN_MAX_FAILURES = 3

        /** A watch workout younger than this may just not have reached the phone yet — never a ghost. */
        const val GHOST_GRACE_MS = 30_000L

        /** A band reading this recent makes the band the heart-rate source; the watch's are set aside. */
        const val BAND_PREFERRED_MS = 10_000L

        /** A gap this long between consecutive readings is worth asking the watch to fill. */
        const val GAP_REQUEST_MS = 12_000L

        /** The stream silent for this long mid-workout: ask the watch for what it has. */
        const val STALE_REQUEST_MS = 20_000L

        /** At most one resend request this often — the watch answers with everything it has anyway. */
        const val HR_REQUEST_THROTTLE_MS = 15_000L

        /** How long after finishing a workout late readings for it are still merged into the record. */
        const val LATE_MERGE_WINDOW_MS = 10 * 60_000L

        /** Late readings arrive in chunks; merge once they've stopped coming for this long. */
        const val LATE_MERGE_DEBOUNCE_MS = 3_000L

        /** Lead-in before a work block actually starts. */
        const val COUNTDOWN_MS = 3_000L

        /**
         * How often the in-progress session is written to disk between structural events. Short
         * enough that an interruption costs a few readings, long enough that re-encoding the sample
         * list isn't constant background work.
         */
        const val CHECKPOINT_INTERVAL_MS = 15_000L

        /**
         * How stale a checkpoint may be and still be resumed as a live workout. Beyond this it's
         * treated as a session that ended without a Finish tap and banked as-is.
         */
        const val RESUME_WINDOW_MS = 30 * 60_000L
    }
}
