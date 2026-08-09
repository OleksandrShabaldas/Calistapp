package com.calistapp.app.session

import android.content.Context
import com.calistapp.app.data.profile.ProfileRepository
import com.calistapp.app.data.session.SessionRepository
import com.calistapp.app.data.sync.LiveSessionBus
import com.calistapp.app.data.sync.WatchAppLauncher
import com.calistapp.app.data.sync.WatchCommandSender
import com.calistapp.app.di.ApplicationScope
import com.calistapp.core.calorie.CalorieEngine
import com.calistapp.core.calorie.LiveCalorieAccumulator
import com.calistapp.core.calorie.rebuildAccumulator
import dagger.hilt.android.qualifiers.ApplicationContext
import com.calistapp.core.model.ExerciseType
import com.calistapp.core.model.HeartRateSample
import com.calistapp.core.model.Segment
import com.calistapp.core.model.SegmentType
import com.calistapp.core.model.SessionStatus
import com.calistapp.core.model.SessionSummary
import com.calistapp.core.model.SetLog
import com.calistapp.core.model.UserProfile
import com.calistapp.core.model.WorkoutPlan
import com.calistapp.core.model.WorkoutSession
import com.calistapp.core.sync.ControlCommand
import com.calistapp.core.sync.ControlPayload
import com.calistapp.core.sync.DeviceOrigin
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
 * echoing each other.
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
    private val profileRepository: ProfileRepository,
    private val bus: LiveSessionBus,
    private val watch: WatchCommandSender,
    private val watchLauncher: WatchAppLauncher,
) {
    private val _live = MutableStateFlow<LiveSession?>(null)
    val live: StateFlow<LiveSession?> = _live.asStateFlow()

    private val mutex = Mutex()
    private var loopJob: Job? = null
    private var countdownJob: Job? = null
    private var profile: UserProfile = UserProfile()
    private var accumulator: LiveCalorieAccumulator? = null

    // Retained privately for persistence; deliberately kept out of [LiveSession].
    private val samples = mutableListOf<HeartRateSample>()
    private val segments = mutableListOf<Segment>()
    private val setLogs = mutableListOf<SetLog>()
    private var lastSampleAtMs = 0L
    private var lastCheckpointMs = 0L

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
        } }
        scope.launch {
            bus.hrSamples.collect { sample ->
                if (_live.value?.status == SessionStatus.ACTIVE) onSample(sample)
            }
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

    fun advanceToNext() = scope.launch {
        _live.value?.nextExercise?.let { selectSlotInternal(it.slotId, broadcast = true) }
    }

    fun adjustReps(delta: Int) = scope.launch {
        mutex.withLock {
            val cur = _live.value ?: return@withLock
            val updated = (cur.currentReps + delta).coerceAtLeast(0)
            accumulator?.setCurrentReps(updated)
            _live.update { it?.copy(currentReps = updated) }
        }
        refresh()
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
            ControlCommand.SET_ACTIVE -> changeSegment(SegmentType.ACTIVE, broadcast = false)
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
            ControlCommand.PAUSE -> _live.update { it?.copy(status = SessionStatus.PAUSED) }
            ControlCommand.RESUME -> _live.update { it?.copy(status = SessionStatus.ACTIVE) }
            // Liveness handshake — owned by WatchConnectionMonitor, nothing to do to the session.
            ControlCommand.PING, ControlCommand.HELLO -> Unit
            // Phone→watch only; the phone updates itself from the settings screen.
            ControlCommand.CHECK_UPDATE -> Unit
        }
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

        val completed = completedSetsFrom(segments)
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
            setIndex = (completed[slotId] ?: 0) + 1,
            completedSets = completed,
            summary = accumulator?.snapshot(now) ?: SessionSummary.EMPTY,
            lastBpm = saved.samples.lastOrNull()?.bpm ?: 0,
            nowMs = now,
            segmentStartMs = open?.startMs ?: saved.startMs,
            receivingHr = false,
        )

        WorkoutSessionService.start(context)
        startTicking()
    }

    /** Score what was recorded and file it, using the last real activity as the end. */
    private suspend fun bankAbandonedSession(saved: WorkoutSession, endMs: Long) {
        val summary = engine.compute(saved.samples, saved.segments, profile, endMs = endMs)
        runCatching {
            sessionRepository.saveSession(
                saved.copy(endMs = endMs, status = SessionStatus.COMPLETED, summary = summary),
            )
        }
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
                exerciseName = cur.plan.exercises.firstOrNull()?.name,
                summary = cur.summary,
            )
        }
        // Outside the lock: a database write must never stall the incoming heart-rate stream.
        runCatching { sessionRepository.saveSession(snapshot) }
    }

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
        // Sessions open in REST. You start the app before you start the set — walking to the bar,
        // finding the timer — and counting that as work inflates both the clock and the calories.
        accumulator = LiveCalorieAccumulator(profile).apply { begin(now, SegmentType.REST) }
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
        val summary = engine.compute(samples.toList(), segments.toList(), profile, endMs = now)
        if (persist) {
            sessionRepository.saveSession(
                WorkoutSession(
                    id = cur.id,
                    exerciseType = cur.exerciseType,
                    startMs = cur.startMs,
                    endMs = now,
                    status = SessionStatus.COMPLETED,
                    samples = samples.toList(),
                    segments = segments.toList(),
                    plan = cur.plan,
                    setLogs = setLogs.toList(),
                    exerciseName = cur.plan.exercises.firstOrNull()?.name,
                    summary = summary,
                ),
            )
        }
        clearBuffers()
        _live.value = null
        return cur.id
    }

    /**
     * Close the current block and open the next. Leaving ACTIVE records the set that was just
     * performed — the reps are what feed the mechanical-work term of the calorie estimate.
     */
    private suspend fun changeSegment(type: SegmentType, broadcast: Boolean) {
        // Messages are built under the lock but sent outside it: a Data Layer round trip is slow
        // enough that holding the mutex across it would stall incoming heart-rate samples.
        val outbound = mutex.withLock { changeSegmentLocked(type, broadcast) }
        outbound.forEach { watch.send(it) }
        refresh()
        // A banked set is exactly the state worth not losing.
        checkpoint(force = true)
    }

    private fun changeSegmentLocked(type: SegmentType, broadcast: Boolean): List<ControlPayload> {
        val cur = _live.value ?: return emptyList()
        if (cur.currentSegment == type) return emptyList()
        val now = System.currentTimeMillis()

        val repsJustDone = cur.currentReps
        closeOpenSegment(now, cur)

        val finishedSlot = cur.currentSlotId
        val completed = if (cur.currentSegment == SegmentType.ACTIVE && finishedSlot != null) {
            cur.completedSets + (finishedSlot to (cur.completedSets[finishedSlot] ?: 0) + 1)
        } else {
            cur.completedSets
        }

        // Returning to work: the plan decides what's next, so a circuit rotates through the list
        // while a split finishes each exercise first. Both devices ask the same function.
        var slotId = cur.currentSlotId
        var setIndex = cur.setIndex
        if (type == SegmentType.ACTIVE) {
            cur.plan.nextUp(slotId, completed)?.let { slotId = it.slotId; setIndex = it.setIndex }
        }

        val slot = cur.plan.slot(slotId)
        accumulator?.startSegment(type, now, slot?.slotId, slot?.name, slot?.metabolics)
        segments += Segment(
            type = type,
            startMs = now,
            slotId = slot?.slotId,
            exerciseName = slot?.name,
            metabolics = slot?.metabolics,
        )
        _live.update {
            it?.copy(
                currentSegment = type,
                completedSets = completed,
                currentSlotId = slotId,
                setIndex = setIndex,
                currentReps = 0,
                segmentStartMs = now,
            )
        }

        if (!broadcast) return emptyList()
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
                ),
            )
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
            setLogs += SetLog(
                slotId = slotId,
                exerciseId = cur.plan.slot(slotId)?.exerciseId.orEmpty(),
                exerciseName = sealed.exerciseName.orEmpty(),
                setIndex = cur.setIndex,
                reps = cur.currentReps,
                startMs = sealed.startMs,
                endMs = now,
            )
        }
    }

    // ---- Streams -------------------------------------------------------------------------------

    private suspend fun onSample(sample: HeartRateSample) {
        mutex.withLock {
            samples += sample
            accumulator?.addSample(sample)
            lastSampleAtMs = System.currentTimeMillis()
            _live.update { it?.copy(lastBpm = sample.bpm, receivingHr = true) }
        }
        refresh()
        checkpoint()
    }

    private fun startTicking() {
        loopJob?.cancel()
        loopJob = scope.launch {
            while (true) {
                delay(1000)
                val cur = _live.value ?: break
                if (cur.status != SessionStatus.ACTIVE) continue
                val now = System.currentTimeMillis()
                mutex.withLock { accumulator?.advanceTo(now) }
                refresh()
            }
        }
    }

    /** Publish a fresh snapshot. O(1) — this is what the quadratic recompute was replaced with. */
    private fun refresh() {
        val now = System.currentTimeMillis()
        val snap = accumulator?.snapshot(now) ?: return
        _live.update {
            it?.copy(
                summary = snap,
                nowMs = now,
                receivingHr = lastSampleAtMs > 0 && now - lastSampleAtMs < HR_STALE_MS,
            )
        }
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

    private fun clearBuffers() {
        samples.clear()
        segments.clear()
        setLogs.clear()
        lastSampleAtMs = 0L
        lastCheckpointMs = 0L
        accumulator = null
    }

    private companion object {
        /** No reading for this long means the watch link has gone quiet. */
        const val HR_STALE_MS = 10_000L

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
