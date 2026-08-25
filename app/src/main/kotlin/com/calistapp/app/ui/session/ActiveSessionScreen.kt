package com.calistapp.app.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calistapp.app.data.sync.WatchLinkState
import com.calistapp.app.session.LiveSession
import com.calistapp.app.ui.common.GlassCard
import com.calistapp.app.ui.common.NoHeartRateDialog
import com.calistapp.app.ui.common.PillChip
import com.calistapp.app.ui.common.RestAlert
import com.calistapp.app.ui.common.SectionHeading
import com.calistapp.app.ui.common.WatchStatusCard
import com.calistapp.app.ui.common.formatClock
import com.calistapp.app.ui.common.rememberHaptics
import com.calistapp.app.ui.common.rememberSoundEffects
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.Capsule
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.Coral
import com.calistapp.app.ui.theme.Flame
import com.calistapp.app.ui.theme.NumericLarge
import com.calistapp.app.ui.theme.Onyx
import com.calistapp.app.ui.theme.TitleSans
import com.calistapp.core.model.Exercise
import com.calistapp.core.model.ExerciseMeasure
import com.calistapp.core.model.ExerciseType
import com.calistapp.core.model.PlannedExercise
import com.calistapp.core.model.SegmentType
import com.calistapp.core.model.SessionStatus
import com.calistapp.core.model.WorkoutPlan

@Composable
fun ActiveSessionScreen(
    onFinished: (String) -> Unit,
    onDiscarded: () -> Unit,
    onBuildWorkout: () -> Unit,
    onCollapse: () -> Unit,
    onOpenExercise: (String) -> Unit,
    viewModel: ActiveSessionViewModel = hiltViewModel(),
) {
    val live by viewModel.live.collectAsStateWithLifecycle()
    val planned by viewModel.plannedExercise.collectAsStateWithLifecycle()
    val plan by viewModel.plan.collectAsStateWithLifecycle()
    val watchLink by viewModel.watchLink.collectAsStateWithLifecycle()
    val heroExercise by viewModel.heroExercise.collectAsStateWithLifecycle()
    val heroHistory by viewModel.heroHistory.collectAsStateWithLifecycle()
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val thumbnails by viewModel.thumbnails.collectAsStateWithLifecycle()

    val session = live
    KeepScreenOn(enabled = session != null)

    if (session == null) {
        StartControls(
            watchLink = watchLink,
            onReconnect = viewModel::reconnectWatch,
            plan = plan,
            plannedName = planned?.name,
            hasRequestedExercise = viewModel.hasRequestedExercise,
            defaultType = planned?.let(::exerciseTypeFor) ?: ExerciseType.CALISTHENICS,
            onBuildWorkout = onBuildWorkout,
            onStart = viewModel::start,
        )
    } else {
        LiveControls(
            vm = viewModel,
            live = session,
            heroExercise = heroExercise,
            heroHistory = heroHistory,
            prefs = prefs,
            watchLink = watchLink,
            imageUrlsFor = { id -> thumbnails[id].orEmpty() },
            onFinished = onFinished,
            onDiscarded = onDiscarded,
            onCollapse = onCollapse,
            onOpenExercise = onOpenExercise,
        )
    }
}

/** Holds the display awake for as long as [enabled], and gives it back on the way out. */
@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

/** An action held back by the zero-rep confirmation, with the wording for its confirm button. */
private class PendingAction(val label: String, val run: () -> Unit)

@Composable
private fun LiveControls(
    vm: ActiveSessionViewModel,
    live: LiveSession,
    heroExercise: Exercise?,
    heroHistory: ExerciseHistoryStat?,
    prefs: com.calistapp.app.data.session.SessionPrefs,
    watchLink: WatchLinkState,
    imageUrlsFor: (String) -> List<String>,
    onFinished: (String) -> Unit,
    onDiscarded: () -> Unit,
    onCollapse: () -> Unit,
    onOpenExercise: (String) -> Unit,
) {
    val isActive = live.currentSegment == SegmentType.ACTIVE
    val exercise = live.currentExercise
    val heroPlanned = live.heroExercise
    val countdown = live.countdownSeconds
    val s = live.summary
    val isHold = exercise?.measure == ExerciseMeasure.SECONDS

    // Cue tones and haptics, each gated by the pause-screen toggle.
    val haptics = rememberHaptics()
    val fx = rememberSoundEffects()
    RestAlert(
        remainingSeconds = if (live.isOpeningWarmup) null else live.restRemainingSeconds,
        resetKey = live.segmentStartMs,
        onElapsed = { if (prefs.vibration) haptics.restOver() },
    )
    LaunchedEffect(countdown) { countdown?.let { if (it in 1..3 && prefs.sound) fx.tick() } }
    val wasActive = remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(isActive) {
        val prev = wasActive.value
        wasActive.value = isActive
        if (prev == false && isActive && prefs.sound) fx.go()
    }
    if (exercise != null && isHold && isActive) {
        val heldSeconds = (live.segmentElapsedMs / 1000).toInt()
        LaunchedEffect(heldSeconds) {
            when (exercise.targetSeconds - heldSeconds) {
                in 1..3 -> if (prefs.sound) fx.tick()
                0 -> if (prefs.sound) fx.go()
            }
        }
    }

    // The rep counter opens on the plan's target as a faint ghost; the first tap makes it solid.
    val counterKey = "${live.currentSlotId}|${live.setIndex}|${live.currentSegment}"
    var counterTouched by remember(counterKey) { mutableStateOf(false) }

    // Guards against banking an empty set.
    var confirming by remember { mutableStateOf<PendingAction?>(null) }
    fun guard(action: PendingAction, run: () -> Unit) {
        if (isActive && exercise != null && live.currentReps == 0) confirming = action else run()
    }

    var showJournal by remember { mutableStateOf(false) }
    var showThisExercise by remember { mutableStateOf(false) }
    var hudExpanded by remember { mutableStateOf(false) }
    var repsNumpad by remember { mutableStateOf(false) }
    var weightNumpad by remember { mutableStateOf(false) }

    if (confirming != null) {
        val pending = confirming!!
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text("No reps logged") },
            text = {
                Text(
                    "This set is recorded as 0 ${if (isHold) "seconds" else "reps"}. Log them first if " +
                        "you forgot — they feed the calorie estimate.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirming = null; pending.run() }) { Text(pending.label, color = Coral) }
            },
            dismissButton = { TextButton(onClick = { confirming = null }) { Text("Go back") } },
        )
    }

    // Primary action — label and behaviour follow the phase.
    val startLabel = when {
        live.isOpeningWarmup -> "Start first set"
        live.nextIsNewExercise -> "Start ${live.upNextExercise?.name ?: "next exercise"}"
        else -> "Start set ${live.upNext?.setIndex ?: live.setIndex}"
    }
    val primaryLabel: String
    val primaryClick: () -> Unit
    when {
        countdown != null -> {
            primaryLabel = "Start now"; primaryClick = { vm.startWorkNow() }
        }
        isActive -> {
            primaryLabel = if (live.bankingEndsWorkout) "Done — log last set" else "Done — log & rest"
            primaryClick = { guard(PendingAction("Bank it anyway") { vm.toggleSegment() }) { vm.toggleSegment() } }
        }
        live.allSetsDone -> {
            primaryLabel = "Finish & save workout"; primaryClick = { vm.finish(onFinished) }
        }
        else -> {
            primaryLabel = startLabel; primaryClick = { vm.toggleSegment() }
        }
    }

    val canSkip = live.nextExercise != null && !live.allSetsDone && !live.isOpeningWarmup &&
        (isActive || !live.nextIsNewExercise)
    val kcalInt = s.totalKcal.toInt()
    val elapsedMin = live.elapsedMs / 60_000.0
    val kcalPerMin = if (elapsedMin > 0.1) s.totalKcal / elapsedMin else 0.0

    Box(Modifier.fillMaxSize().background(Onyx)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            LiveTopBar(
                roundLabel = if (live.plan.isCircuit) "Round ${live.currentRound} / ${live.plan.rounds}" else "",
                onCollapse = onCollapse,
                onPause = { vm.pause() },
            )

            SegmentBar(
                states = segmentStates(live),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            Box(Modifier.weight(1f).fillMaxWidth()) {
                LiveExerciseHero(heroExercise, autoplay = prefs.autoplayVideo, modifier = Modifier.fillMaxSize())

                // Legibility scrim so overlaid text reads over any frame.
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(0.45f to Color.Transparent, 1f to Onyx.copy(alpha = 0.9f)),
                    ),
                )

                Text(
                    formatClock(live.elapsedMs),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Chalk,
                    modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
                )

                LiveHudChip(
                    bpm = live.lastBpm,
                    kcal = kcalInt,
                    onClick = { hudExpanded = true },
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                )

                Column(
                    Modifier.align(Alignment.BottomStart).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    heroPlanned?.let { Text(heroTargetLabel(it), style = MaterialTheme.typography.titleMedium, color = Flame, fontWeight = FontWeight.Bold) }
                    Text(heroPlanned?.displayName ?: exercise?.displayName ?: "", style = TitleSans, color = Chalk)
                    heroPlanned?.exerciseId?.let { exId ->
                        Row(
                            Modifier.clip(Capsule).clickable { onOpenExercise(exId) }.padding(vertical = 3.dp, horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(Icons.Filled.Info, null, tint = Flame, modifier = Modifier.size(15.dp))
                            Text("Exercise info", style = MaterialTheme.typography.labelLarge, color = Flame)
                        }
                    }
                }

                if (countdown != null) {
                    Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("GET READY", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Chalk)
                        Text("$countdown", style = NumericLarge, color = Flame)
                    }
                }

                if (hudExpanded) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).clickable { hudExpanded = false })
                    LiveHudPanel(
                        bpm = live.lastBpm,
                        avgHr = s.avgHr,
                        peakHr = s.peakHr,
                        maxHr = live.maxHr,
                        recentBpm = live.recentBpm,
                        kcal = kcalInt,
                        kcalPerMin = kcalPerMin,
                        elapsedMs = live.elapsedMs,
                        watchLabel = if (live.receivingHr) "Watch streaming" else if (watchLink.isUsable) "Watch connected" else "Watch offline",
                        showReconnect = !live.receivingHr,
                        onReconnect = vm::reconnectWatch,
                        onClose = { hudExpanded = false },
                        modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                    )
                }
            }

            Column(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when {
                    countdown != null -> Unit
                    isActive && exercise != null -> RepCounterDock(
                        reps = live.currentReps,
                        target = live.currentSet?.reps ?: if (isHold) exercise.targetSeconds else exercise.targetReps,
                        isHold = isHold,
                        touched = counterTouched,
                        onDelta = { counterTouched = true; vm.adjustReps(it) },
                        onOpenNumpad = { repsNumpad = true },
                        weightKg = live.currentSetWeightKg,
                        onOpenWeight = { weightNumpad = true },
                        onSwipeUp = { showThisExercise = true },
                    )
                    live.allSetsDone -> AllDoneDock()
                    else -> {
                        val restElapsed = live.restElapsedSeconds ?: 0
                        val restTarget = exercise?.takeIf { it.isRestTimed }?.restSeconds
                        val reached = restTarget != null && restElapsed >= restTarget
                        RestDock(
                            elapsedSeconds = restElapsed,
                            statusText = when {
                                live.isOpeningWarmup -> "warm-up — start the first set when ready"
                                reached -> "rested ${exercise?.restLabel} — back to it"
                                restTarget != null -> "resting · target ${exercise?.restLabel}"
                                else -> "resting"
                            },
                            upNextText = live.upNextExercise?.let { "Up next: ${it.displayName}" },
                            reached = reached,
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (isActive) TextButton(onClick = { vm.restartCurrentSet(); counterTouched = false }) { Text("Restart set", color = Ash) }
                    if (countdown != null) TextButton(onClick = { vm.toggleSegment() }) { Text("Cancel", color = Ash) }
                    if (canSkip) live.nextExercise?.let { next ->
                        TextButton(onClick = { guard(PendingAction("Skip anyway") { vm.advanceToNext() }) { vm.advanceToNext() } }) {
                            Text("Skip to ${next.name}", color = Ash)
                        }
                    }
                }

                Button(
                    onClick = primaryClick,
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    shape = Capsule,
                    colors = ButtonDefaults.buttonColors(containerColor = Flame, contentColor = Onyx),
                ) {
                    Text(primaryLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                Row(
                    Modifier.fillMaxWidth().clip(Capsule).clickable { showJournal = true }.padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Journal", style = MaterialTheme.typography.labelLarge, color = Ash)
                }
                Box(Modifier.navigationBarsPadding())
            }
        }

        if (live.status == SessionStatus.PAUSED) {
            PauseScreen(
                elapsedMs = live.elapsedMs,
                prefs = prefs,
                onSound = vm::setSound,
                onVibration = vm::setVibration,
                onAutoplay = vm::setAutoplay,
                onResume = { vm.resume() },
                onEnd = { vm.finish(onFinished) },
                onDiscard = { vm.discard(); onDiscarded() },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    if (showJournal) {
        JournalSheet(
            live = live,
            imageUrlsFor = imageUrlsFor,
            onSetReps = vm::setSetReps,
            onSetWeight = vm::setSetWeight,
            onSetEffort = { slot, idx, scale, value -> vm.setSetEffort(slot, idx, scale, value.toDouble()) },
            onSetNote = vm::setSetNote,
            onDismiss = { showJournal = false },
        )
    }
    if (showThisExercise) {
        ThisExercisePanel(
            exercise = heroExercise,
            planned = heroPlanned,
            history = heroHistory,
            nowMs = live.nowMs,
            onDismiss = { showThisExercise = false },
        )
    }
    if (repsNumpad && exercise != null) {
        com.calistapp.app.ui.common.NumberPadSheet(
            title = if (isHold) "Seconds" else "Reps",
            initial = live.currentReps,
            onConfirm = { counterTouched = true; vm.setReps(it); repsNumpad = false },
            onDismiss = { repsNumpad = false },
        )
    }
    if (weightNumpad && exercise != null) {
        com.calistapp.app.ui.common.NumberPadSheet(
            title = "Added weight",
            initial = live.currentSetWeightKg.toInt(),
            unit = "kg",
            onConfirm = { vm.setAddedWeight(it.toDouble()); weightNumpad = false },
            onDismiss = { weightNumpad = false },
        )
    }
}

@Composable
private fun LiveTopBar(roundLabel: String, onCollapse: () -> Unit, onPause: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.KeyboardArrowDown,
            "Minimise",
            tint = Ash,
            modifier = Modifier.size(28.dp).clip(Capsule).clickable(onClick = onCollapse).padding(2.dp),
        )
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (roundLabel.isNotEmpty()) {
                Text(roundLabel, style = MaterialTheme.typography.titleSmall, color = Chalk)
            }
        }
        Icon(
            Icons.Filled.Pause,
            "Pause",
            tint = Chalk,
            modifier = Modifier.size(28.dp).clip(Capsule).clickable(onClick = onPause).padding(2.dp),
        )
    }
}

/** "8 reps" / "45s" for the hero label. */
private fun heroTargetLabel(p: PlannedExercise): String =
    if (p.measure == ExerciseMeasure.SECONDS) "${p.targetSeconds}s" else "${p.targetReps} reps"

/** One segment per plan exercise: done, the current one, or still to come. */
private fun segmentStates(live: LiveSession): List<SegState> = live.plan.exercises.map { slot ->
    val done = live.completedSets[slot.slotId] ?: 0
    val total = live.plan.targetSetsFor(slot.slotId)
    when {
        slot.slotId == live.currentSlotId && !live.allSetsDone -> SegState.CURRENT
        done >= total && total > 0 -> SegState.DONE
        else -> SegState.UPCOMING
    }
}

@Composable
private fun StartControls(
    watchLink: WatchLinkState,
    onReconnect: () -> Unit,
    plan: WorkoutPlan,
    plannedName: String?,
    hasRequestedExercise: Boolean,
    defaultType: ExerciseType,
    onBuildWorkout: () -> Unit,
    onStart: (ExerciseType) -> Unit,
) {
    var type by remember(defaultType) { mutableStateOf(defaultType) }
    var confirmNoWatch by remember { mutableStateOf(false) }

    if (confirmNoWatch) {
        NoHeartRateDialog(
            onDismiss = { confirmNoWatch = false },
            onStartAnyway = { onStart(type) },
            onReconnect = onReconnect,
        )
    }

    val hasWorkout = !plan.isEmpty || plannedName != null || hasRequestedExercise

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "New workout",
            style = MaterialTheme.typography.headlineLarge,
            color = Chalk,
            modifier = Modifier.padding(top = 12.dp),
        )

        when {
            !plan.isEmpty -> GlassCard(accent = Flame) {
                SectionHeading(plan.name.ifBlank { "Workout ready" }, count = plan.exercises.size)
                Text("${plan.totalSets} sets planned", style = MaterialTheme.typography.labelMedium, color = Flame)
                plan.exercises.forEach { slot ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(slot.name, style = MaterialTheme.typography.bodyMedium, color = Chalk, modifier = Modifier.weight(1f))
                        Text(slot.targetLabel, style = MaterialTheme.typography.bodySmall, color = Ash)
                    }
                }
            }

            plannedName != null -> GlassCard(accent = Flame) {
                SectionHeading("Single exercise")
                Text(plannedName, style = MaterialTheme.typography.displaySmall, color = Chalk)
            }

            !hasWorkout -> GlassCard {
                SectionHeading("Nothing planned yet")
                Text(
                    "Pick the movements first — the tracker scores every set against the exercise " +
                        "you're doing, so a workout needs at least one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ash,
                )
            }
        }

        if (hasWorkout) {
            OutlinedButton(onClick = onBuildWorkout, modifier = Modifier.fillMaxWidth(), shape = Capsule) {
                Text(if (plan.isEmpty) "Build a workout" else "Edit workout")
            }
        } else {
            Button(
                onClick = onBuildWorkout,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = Capsule,
                colors = ButtonDefaults.buttonColors(containerColor = Flame),
            ) {
                Text("Build a workout", fontWeight = FontWeight.Bold)
            }
        }

        SectionHeading("Type")
        FlowChips(
            options = ExerciseType.entries.map { it to it.displayName },
            selected = type,
            onSelect = { type = it },
        )

        WatchStatusCard(state = watchLink, onReconnect = onReconnect)

        Button(
            onClick = { if (watchLink.isUsable) onStart(type) else confirmNoWatch = true },
            enabled = hasWorkout,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = Capsule,
            colors = ButtonDefaults.buttonColors(containerColor = Flame),
        ) {
            Text("Start", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (!hasWorkout) {
            Text(
                "Add at least one exercise to start.",
                style = MaterialTheme.typography.labelMedium,
                color = Ash,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Box(Modifier.height(8.dp))
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun <T> FlowChips(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            PillChip(label = label, selected = selected == value, onClick = { onSelect(value) })
        }
    }
}
