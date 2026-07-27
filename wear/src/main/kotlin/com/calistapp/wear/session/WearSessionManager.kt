package com.calistapp.wear.session

import android.content.Context
import com.calistapp.core.calorie.ExerciseIntensity
import com.calistapp.core.calorie.LiveCalorieAccumulator
import com.calistapp.core.model.ExerciseType
import com.calistapp.core.model.HeartRateSample
import com.calistapp.core.model.PlannedExercise
import com.calistapp.core.model.SegmentType
import com.calistapp.core.model.SessionStatus
import com.calistapp.core.model.SessionSummary
import com.calistapp.core.model.WorkoutPlan
import com.calistapp.core.sync.ControlCommand
import com.calistapp.core.sync.ControlPayload
import com.calistapp.core.sync.DeviceOrigin
import com.calistapp.core.sync.HrBatchPayload
import com.calistapp.core.sync.SessionStatePayload
import com.calistapp.core.sync.WearJson
import com.calistapp.core.sync.WearSync
import com.calistapp.wear.hr.HealthServicesHeartRateSource
import com.calistapp.wear.hr.HeartRateSource
import com.calistapp.wear.sync.PhoneSender
import com.calistapp.wear.sync.WearProfileHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.Executors

/** Everything the watch UI needs to render a live workout. */
data class WearSessionState(
    val running: Boolean = false,
    val sessionId: String = "",
    val exerciseType: ExerciseType = ExerciseType.CALISTHENICS,
    val status: SessionStatus = SessionStatus.COMPLETED,
    val currentSegment: SegmentType = SegmentType.ACTIVE,
    val plan: WorkoutPlan = WorkoutPlan.EMPTY,
    val currentSlotId: String? = null,
    /** Reps counted for the block in progress. */
    val currentReps: Int = 0,
    /** Which set of the current exercise this is, 1-based. */
    val setIndex: Int = 1,
    /** Sets already completed, per slot. */
    val completedSets: Map<String, Int> = emptyMap(),
    val lastBpm: Int = 0,
    val startMs: Long = 0,
    val elapsedMs: Long = 0,
    val summary: SessionSummary = SessionSummary.EMPTY,
    /** Whether the phone is currently reachable. */
    val phoneLinked: Boolean = false,
    /** A manual reconnect is in flight. */
    val linkRefreshing: Boolean = false,
    /** Seconds left on the lead-in before work starts, or null when not counting down. */
    val countdownSeconds: Int? = null,
) {
    val currentExercise: PlannedExercise? get() = plan.slot(currentSlotId)
    val nextExercise: PlannedExercise? get() = plan.next(currentSlotId)
    val isWorking: Boolean get() = currentSegment == SegmentType.ACTIVE
    val currentRound: Int get() = plan.roundOf(completedSets)
}

/**
 * Owns the live workout on the watch.
 *
 * Deliberately **application-scoped rather than held by a ViewModel**. Two things fall out of that
 * which the previous ViewModel-owned design couldn't do: a workout keeps tracking when the screen
 * turns off and the Activity is destroyed (essential — you don't stare at your wrist mid-set), and a
 * control message from the phone can start or steer a session while the watch UI isn't even open.
 *
 * All state mutation is confined to [scope], a single-threaded dispatcher, so the HR stream, the
 * tick loop, the UI and the inbound-message service can all poke at it without locks.
 */
object WearSessionManager {

    private val dispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(r, "wear-session").apply { isDaemon = true }
    }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _state = MutableStateFlow(WearSessionState())
    val state: StateFlow<WearSessionState> = _state.asStateFlow()

    private var appContext: Context? = null
    private var sender: PhoneSender? = null
    private var accumulator: LiveCalorieAccumulator? = null

    private var collectJob: Job? = null
    private var tickJob: Job? = null
    private var countdownJob: Job? = null

    /** Samples captured but not yet shipped to the phone — batched to cut Data Layer chatter. */
    private val pendingSamples = mutableListOf<HeartRateSample>()
    private var lastHrFlushMs = 0L
    private var lastStateSendMs = 0L

    /** Swappable so tests (and a future replay mode) can drive the manager without hardware. */
    var heartRateSourceFactory: (Context) -> HeartRateSource = { HealthServicesHeartRateSource(it) }

    fun attach(context: Context) {
        if (appContext != null) return
        val app = context.applicationContext
        appContext = app
        sender = PhoneSender(app)
        scope.launch {
            WearProfileHolder.profile.collect { profile -> accumulator?.updateProfile(profile) }
        }
        // Keep the connection indicator honest even when no workout is running.
        scope.launch {
            while (true) {
                val linked = sender?.hasPhone() == true
                _state.update { it.copy(phoneLinked = linked) }
                delay(LINK_POLL_MS)
            }
        }
    }

    /** Anything received from the phone proves the link is up. */
    private fun markPhoneHeard() = _state.update { it.copy(phoneLinked = true) }

    /** Re-resolve the phone node and announce ourselves — the watch's Reconnect button. */
    fun reconnectPhone() = scope.launch {
        _state.update { it.copy(linkRefreshing = true) }
        sender?.invalidateNow()
        send(ControlPayload(ControlCommand.PING, System.currentTimeMillis(), DeviceOrigin.WATCH))
        val linked = sender?.hasPhone() == true
        if (_state.value.running) sendState(force = true)
        _state.update { it.copy(phoneLinked = linked, linkRefreshing = false) }
    }

    /** Answer a phone's PING so its indicator can turn green. */
    private suspend fun announce() {
        send(ControlPayload(ControlCommand.HELLO, System.currentTimeMillis(), DeviceOrigin.WATCH))
        if (_state.value.running) sendState(force = true)
    }

    // ---- Local actions (user tapped the watch) — these broadcast to the phone ------------------

    fun startLocal(type: ExerciseType, plan: WorkoutPlan = WorkoutPlan.EMPTY) =
        scope.launch { beginSession(UUID.randomUUID().toString(), type, plan, broadcast = true) }

    fun stopLocal() = scope.launch { endSession(broadcast = true) }

    /**
     * Work/rest toggle. Entering work runs the same short lead-in the phone uses — tapping again
     * during it cancels. Each device counts down locally and broadcasts only at the flip, so both
     * change state at the same instant without an extra protocol message.
     */
    fun toggleSegmentLocal() = scope.launch {
        val cur = _state.value
        when {
            cur.countdownSeconds != null -> cancelCountdown()
            cur.isWorking -> changeSegment(SegmentType.REST, broadcast = true)
            else -> startCountdownToWork()
        }
    }

    private fun startCountdownToWork() {
        countdownJob?.cancel()
        countdownJob = scope.launch {
            for (remaining in COUNTDOWN_SECONDS downTo 1) {
                _state.update { it.copy(countdownSeconds = remaining) }
                delay(1_000)
            }
            _state.update { it.copy(countdownSeconds = null) }
            changeSegment(SegmentType.ACTIVE, broadcast = true)
        }
    }

    private fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        _state.update { it.copy(countdownSeconds = null) }
    }

    fun selectSlotLocal(slotId: String) = scope.launch { selectSlot(slotId, broadcast = true) }

    fun advanceToNextLocal() = scope.launch {
        _state.value.nextExercise?.let { selectSlot(it.slotId, broadcast = true) }
    }

    /** Bump the rep counter for the block in progress. Synced to the phone when the set closes. */
    fun adjustReps(delta: Int) = scope.launch {
        val updated = (_state.value.currentReps + delta).coerceAtLeast(0)
        accumulator?.setCurrentReps(updated)
        _state.update { it.copy(currentReps = updated) }
        publish()
    }

    // ---- Remote commands (phone drove this) — applied WITHOUT rebroadcast ---------------------

    /**
     * Apply a command that arrived from the phone. Nothing here re-broadcasts: that one rule is what
     * stops the two devices echoing each other into an infinite toggle loop.
     */
    fun applyControl(payload: ControlPayload) = scope.launch {
        markPhoneHeard()
        when (payload.command) {
            ControlCommand.PING -> announce()
            ControlCommand.HELLO -> Unit // Already handled by markPhoneHeard above.
            ControlCommand.START -> beginSession(
                sessionId = payload.sessionId ?: UUID.randomUUID().toString(),
                type = payload.exerciseType ?: ExerciseType.CALISTHENICS,
                plan = payload.plan ?: WorkoutPlan.EMPTY,
                broadcast = false,
            )
            ControlCommand.STOP -> endSession(broadcast = false)
            // The phone already ran its own lead-in before sending these, so they apply at once.
            ControlCommand.SET_ACTIVE -> {
                cancelCountdown()
                changeSegment(SegmentType.ACTIVE, broadcast = false)
            }
            ControlCommand.SET_REST -> {
                cancelCountdown()
                changeSegment(SegmentType.REST, broadcast = false)
            }
            ControlCommand.SET_EXERCISE -> payload.slotId?.let { selectSlot(it, broadcast = false) }
            ControlCommand.LOG_SET -> {
                accumulator?.setCurrentReps(payload.reps)
                _state.update { it.copy(currentReps = payload.reps) }
                publish()
            }
            ControlCommand.SYNC_PLAN -> payload.plan?.let { plan ->
                _state.update { it.copy(plan = plan, currentSlotId = it.currentSlotId ?: plan.exercises.firstOrNull()?.slotId) }
                applyCurrentExerciseToAccumulator()
                publish()
            }
            ControlCommand.PAUSE -> _state.update { it.copy(status = SessionStatus.PAUSED) }
            ControlCommand.RESUME -> _state.update { it.copy(status = SessionStatus.ACTIVE) }
        }
    }

    // ---- Session lifecycle ---------------------------------------------------------------------

    private suspend fun beginSession(
        sessionId: String,
        type: ExerciseType,
        plan: WorkoutPlan,
        broadcast: Boolean,
    ) {
        if (_state.value.running) return
        val now = System.currentTimeMillis()
        val profile = WearProfileHolder.profile.value
        val firstSlot = plan.exercises.firstOrNull()

        // Opens in REST, matching the phone — the workout begins when you do, not when the app does.
        accumulator = LiveCalorieAccumulator(profile).apply { begin(now, SegmentType.REST) }
        pendingSamples.clear()
        lastHrFlushMs = now
        lastStateSendMs = 0L

        _state.value = WearSessionState(
            running = true,
            sessionId = sessionId,
            exerciseType = type,
            status = SessionStatus.ACTIVE,
            currentSegment = SegmentType.REST,
            plan = plan,
            currentSlotId = firstSlot?.slotId,
            currentReps = 0,
            setIndex = 1,
            startMs = now,
        )
        applyCurrentExerciseToAccumulator()

        if (broadcast) {
            send(ControlPayload(ControlCommand.START, now, DeviceOrigin.WATCH, type, sessionId, plan))
        }
        startCollecting()
        startTicking()
    }

    private suspend fun endSession(broadcast: Boolean) {
        if (!_state.value.running) return
        val now = System.currentTimeMillis()
        collectJob?.cancel()
        tickJob?.cancel()
        countdownJob?.cancel()
        accumulator?.advanceTo(now)
        flushHr(force = true)

        _state.update {
            it.copy(
                running = false,
                status = SessionStatus.COMPLETED,
                elapsedMs = now - it.startMs,
                summary = accumulator?.snapshot(now) ?: SessionSummary.EMPTY,
            )
        }
        if (broadcast) {
            send(ControlPayload(ControlCommand.STOP, now, DeviceOrigin.WATCH, sessionId = _state.value.sessionId))
        }
        sendState(force = true)
    }

    /**
     * Close the current block and open the next. Switching *out* of work carries the reps just
     * performed with it, which is what feeds the mechanical-work term of the calorie estimate.
     */
    private suspend fun changeSegment(type: SegmentType, broadcast: Boolean) {
        val cur = _state.value
        if (!cur.running || cur.currentSegment == type) return
        val now = System.currentTimeMillis()

        if (cur.currentSegment == SegmentType.ACTIVE && broadcast) {
            // Tell the phone how many reps that set was, so its record matches ours.
            send(
                ControlPayload(
                    command = ControlCommand.LOG_SET,
                    timestampMs = now,
                    origin = DeviceOrigin.WATCH,
                    sessionId = cur.sessionId,
                    slotId = cur.currentSlotId,
                    reps = cur.currentReps,
                    setIndex = cur.setIndex,
                ),
            )
        }

        val finishedSlot = cur.currentSlotId
        val completed = if (cur.currentSegment == SegmentType.ACTIVE && finishedSlot != null) {
            cur.completedSets + (finishedSlot to (cur.completedSets[finishedSlot] ?: 0) + 1)
        } else {
            cur.completedSets
        }

        // Going back to work: the plan decides what's next — same function the phone calls, so a
        // circuit rotates and a split completes each exercise identically on both devices.
        var slotId = cur.currentSlotId
        var setIndex = cur.setIndex
        if (type == SegmentType.ACTIVE) {
            cur.plan.nextUp(slotId, completed)?.let { slotId = it.slotId; setIndex = it.setIndex }
        }

        accumulator?.startSegment(type, now)
        _state.update {
            it.copy(
                currentSegment = type,
                completedSets = completed,
                currentSlotId = slotId,
                setIndex = setIndex,
                currentReps = 0,
            )
        }
        applyCurrentExerciseToAccumulator()

        if (broadcast) {
            val cmd = if (type == SegmentType.ACTIVE) ControlCommand.SET_ACTIVE else ControlCommand.SET_REST
            send(ControlPayload(cmd, now, DeviceOrigin.WATCH, sessionId = cur.sessionId, slotId = slotId))
        }
        publish()
        sendState(force = true)
    }

    private suspend fun selectSlot(slotId: String, broadcast: Boolean) {
        val cur = _state.value
        if (!cur.running || cur.currentSlotId == slotId) return
        val now = System.currentTimeMillis()

        // Selecting a different exercise closes the current block — it's a different movement, so it
        // must be scored separately rather than smeared into the previous one.
        accumulator?.startSegment(cur.currentSegment, now)
        _state.update {
            it.copy(
                currentSlotId = slotId,
                setIndex = (it.completedSets[slotId] ?: 0) + 1,
                currentReps = 0,
            )
        }
        applyCurrentExerciseToAccumulator()

        if (broadcast) {
            send(
                ControlPayload(
                    ControlCommand.SET_EXERCISE, now, DeviceOrigin.WATCH,
                    sessionId = cur.sessionId, slotId = slotId,
                ),
            )
        }
        publish()
        sendState(force = true)
    }

    /** Push the current exercise's physical profile into the accumulator so scoring reflects it. */
    private fun applyCurrentExerciseToAccumulator() {
        val cur = _state.value
        val exercise = cur.currentExercise
        accumulator?.setCurrentExercise(exercise?.slotId, exercise?.name, exercise?.metabolics)
        accumulator?.setCurrentReps(cur.currentReps)
    }

    // ---- Streams ---------------------------------------------------------------------------------

    private fun startCollecting() {
        val ctx = appContext ?: return
        collectJob?.cancel()
        collectJob = scope.launch {
            runCatching {
                heartRateSourceFactory(ctx).samples().collect { sample ->
                    if (_state.value.status != SessionStatus.ACTIVE) return@collect
                    accumulator?.addSample(sample)
                    pendingSamples += sample
                    _state.update { it.copy(lastBpm = sample.bpm) }
                    flushHr(force = false)
                }
            }
        }
    }

    private fun startTicking() {
        tickJob?.cancel()
        tickJob = scope.launch {
            while (true) {
                delay(TICK_MS)
                if (!_state.value.running) break
                if (_state.value.status != SessionStatus.ACTIVE) continue
                val now = System.currentTimeMillis()
                accumulator?.advanceTo(now)
                publish()
                flushHr(force = false)
                sendState(force = false)
            }
        }
    }

    /** Recompute the UI snapshot. O(1) now — this used to walk the whole session every tick. */
    private fun publish() {
        val now = System.currentTimeMillis()
        val snap = accumulator?.snapshot(now) ?: return
        _state.update { it.copy(elapsedMs = now - it.startMs, summary = snap) }
    }

    private suspend fun flushHr(force: Boolean) {
        if (pendingSamples.isEmpty()) return
        val now = System.currentTimeMillis()
        if (!force && pendingSamples.size < HR_BATCH_SIZE && now - lastHrFlushMs < HR_FLUSH_MS) return

        val batch = pendingSamples.toList()
        pendingSamples.clear()
        lastHrFlushMs = now
        val payload = HrBatchPayload(_state.value.sessionId, batch)
        sender?.send(WearSync.PATH_HR_BATCH, WearJson.encodeToString(HrBatchPayload.serializer(), payload).toByteArray())
    }

    private suspend fun sendState(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastStateSendMs < STATE_SEND_MS) return
        lastStateSendMs = now

        val st = _state.value
        val payload = SessionStatePayload(
            sessionId = st.sessionId,
            exerciseType = st.exerciseType,
            status = st.status,
            currentSegment = st.currentSegment,
            startMs = st.startMs,
            elapsedActiveMs = st.summary.activeDurationMs,
            elapsedRestMs = st.summary.restDurationMs,
            liveKcal = st.summary.totalKcal,
            lastBpm = st.lastBpm,
            avgHr = st.summary.avgHr,
            slotId = st.currentSlotId,
            exerciseName = st.currentExercise?.name,
            currentReps = st.currentReps,
            setIndex = st.setIndex,
        )
        sender?.send(
            WearSync.PATH_SESSION_STATE,
            WearJson.encodeToString(SessionStatePayload.serializer(), payload).toByteArray(),
        )
    }

    private suspend fun send(payload: ControlPayload) {
        sender?.send(
            WearSync.PATH_CONTROL,
            WearJson.encodeToString(ControlPayload.serializer(), payload).toByteArray(),
        )
    }

    private const val TICK_MS = 1_000L

    /**
     * Heart rate is forwarded as soon as it arrives.
     *
     * Batching five readings over three seconds cut Data Layer chatter, but it also meant the phone's
     * BPM lagged the wrist by up to three seconds — during a 40-second set that's most of the useful
     * signal arriving after the set is over. Health Services emits roughly one reading a second, so
     * per-sample sending is about one small message per second: cheap enough, and the number on the
     * phone now tracks the watch.
     */
    private const val HR_FLUSH_MS = 0L
    private const val HR_BATCH_SIZE = 1

    /** Session state follows heart rate closely enough that the phone's other figures stay live. */
    private const val STATE_SEND_MS = 1_000L
    private const val LINK_POLL_MS = 10_000L
    private const val COUNTDOWN_SECONDS = 3
}
