package com.calistapp.app.ui.session

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
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
import com.calistapp.app.ui.theme.Onyx
import com.calistapp.app.ui.theme.TitleSans
import com.calistapp.core.model.Exercise
import com.calistapp.core.model.ExerciseMeasure
import com.calistapp.core.model.ExerciseType
import com.calistapp.core.model.PlannedExercise
import com.calistapp.core.model.SegmentType
import com.calistapp.core.model.SessionStatus
import com.calistapp.core.model.SetLog
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

    // Cue tones and haptics, each gated by the pause-screen toggle. Rest is a count-up stopwatch with
    // no target now, so there's no "rest over" buzz — which also puts paid to the phantom buzz that
    // used to fire on rotate.
    val haptics = rememberHaptics()
    val fx = rememberSoundEffects()
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
    var hudExpanded by remember { mutableStateOf(false) }
    var repsNumpad by remember { mutableStateOf(false) }
    var weightNumpad by remember { mutableStateOf(false) }
    // The set being rated from the rest-state "Rate that set" chip (the set you just banked).
    var ratingSet by remember { mutableStateOf<SetLog?>(null) }

    // How far the sheet is pulled up (0 = resting low, 1 = detail open). Drives both the sheet and
    // the video fading down behind it. Owned here so the two stay in lockstep frame-for-frame.
    val sheetExpand = remember { Animatable(0f) }
    val density = LocalDensity.current
    val heroShiftPx = with(density) { 36.dp.toPx() }
    // The lead-in should read on the video, not through a half-open detail sheet — tuck it away.
    LaunchedEffect(countdown != null) { if (countdown != null) sheetExpand.animateTo(0f) }

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
            val bank = { if (prefs.vibration) haptics.setBanked(); vm.toggleSegment(); Unit }
            primaryClick = { guard(PendingAction("Bank it anyway", bank)) { bank() } }
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
    val canReveal = (heroExercise != null || heroPlanned != null) && countdown == null
    // The set you just banked — offered for a quick effort rating while you rest, so logging effort
    // is a natural one-tap step here rather than something hidden away in the Journal.
    val justBankedSet = if (!isActive && countdown == null && !live.isOpeningWarmup) live.setLogs.lastOrNull() else null
    val kcalInt = s.totalKcal.toInt()
    val elapsedMin = live.elapsedMs / 60_000.0
    val kcalPerMin = if (elapsedMin > 0.1) s.totalKcal / elapsedMin else 0.0

    Box(Modifier.fillMaxSize().background(Onyx)) {
        // Full-bleed demonstration behind everything; it fades and sinks as the sheet rises.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = 1f - 0.5f * sheetExpand.value
                    translationY = sheetExpand.value * heroShiftPx
                },
        ) {
            LiveExerciseHero(heroExercise, autoplay = prefs.autoplayVideo, modifier = Modifier.fillMaxSize())
        }

        // Base legibility scrim so overlaid text reads over any frame.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.35f to Color.Transparent, 1f to Onyx.copy(alpha = 0.92f)),
            ),
        )
        // Extra darkening that fades in as the detail opens, so the reading surface wins over the video.
        Box(Modifier.fillMaxSize().background(Onyx).graphicsLayer { alpha = 0.6f * sheetExpand.value })

        // Top chrome: minimise / round / pause, then the progress dashes, then clock + HUD chip.
        Column(Modifier.fillMaxWidth().statusBarsPadding()) {
            LiveTopBar(
                roundLabel = if (live.plan.isCircuit) "Round ${live.currentRound} / ${live.plan.rounds}" else "",
                onCollapse = onCollapse,
                onPause = { vm.pause() },
            )
            SegmentBar(
                states = segmentStates(live),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    formatClock(live.elapsedMs),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Chalk,
                )
                LiveHudChip(bpm = live.lastBpm, kcal = kcalInt, onClick = { hudExpanded = true })
            }
        }

        // The lead-in number, front and centre on the video.
        if (countdown != null) {
            CountdownOverlay(countdown, Modifier.align(Alignment.Center))
        }

        // Hero labels + the draggable sheet, anchored to the bottom. The labels ride just above the
        // card and fade as it opens (the detail repeats them).
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp)
                    .graphicsLayer { alpha = 1f - sheetExpand.value },
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                heroPlanned?.let {
                    Text(
                        heroTargetLabel(it),
                        style = MaterialTheme.typography.titleMedium,
                        color = Flame,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    heroPlanned?.displayName ?: exercise?.displayName ?: "",
                    style = TitleSans,
                    color = Chalk,
                )
            }

            LiveSheet(
                expand = sheetExpand,
                canReveal = canReveal,
                reveal = {
                    ThisExerciseContent(
                        exercise = heroExercise,
                        planned = heroPlanned,
                        history = heroHistory,
                        nowMs = live.nowMs,
                    )
                    heroPlanned?.exerciseId?.let { exId ->
                        TextButton(onClick = { onOpenExercise(exId) }) {
                            Text("Open full exercise details", color = Flame)
                        }
                    }
                },
                peek = {
                    when {
                        countdown != null -> Unit
                        isActive && exercise != null -> RepCounterContent(
                            reps = live.currentReps,
                            target = live.currentSet?.reps ?: if (isHold) exercise.targetSeconds else exercise.targetReps,
                            isHold = isHold,
                            touched = counterTouched,
                            onDelta = { counterTouched = true; vm.adjustReps(it) },
                            onOpenNumpad = { repsNumpad = true },
                            weightKg = live.currentSetWeightKg,
                            onOpenWeight = { weightNumpad = true },
                        )
                        live.allSetsDone -> AllDoneContent()
                        else -> RestContent(
                            elapsedSeconds = live.restElapsedSeconds ?: 0,
                            isWarmup = live.isOpeningWarmup,
                            upNextName = live.upNextExercise?.displayName,
                            upNextIsNew = live.nextIsNewExercise,
                        )
                    }

                    // Rate the set you just did — effort logging where it's natural, not in the Journal.
                    justBankedSet?.let { set ->
                        RateSetChip(effortLabel = set.effortLabel, onClick = { ratingSet = set })
                    }

                    if (isActive || countdown != null || canSkip) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (isActive) {
                                TextButton(onClick = { vm.restartCurrentSet(); counterTouched = false }) {
                                    Text("Restart set", color = Ash)
                                }
                            }
                            if (countdown != null) {
                                TextButton(onClick = { vm.toggleSegment() }) { Text("Cancel", color = Ash) }
                            }
                            if (canSkip) live.nextExercise?.let { next ->
                                TextButton(
                                    onClick = { guard(PendingAction("Skip anyway") { vm.advanceToNext() }) { vm.advanceToNext() } },
                                ) {
                                    Text("Skip to ${next.name}", color = Ash)
                                }
                            }
                        }
                    }

                    Button(
                        onClick = primaryClick,
                        modifier = Modifier.fillMaxWidth().height(58.dp),
                        shape = Capsule,
                        colors = ButtonDefaults.buttonColors(containerColor = Flame, contentColor = Onyx),
                    ) {
                        Text(primaryLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }

                    Row(
                        Modifier.fillMaxWidth().clip(Capsule).clickable { showJournal = true }.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Journal", style = MaterialTheme.typography.labelLarge, color = Ash)
                    }
                    Box(Modifier.navigationBarsPadding())
                },
            )
        }

        // HUD detail — dims the screen and springs open from the chip's corner.
        AnimatedVisibility(
            visible = hudExpanded,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { hudExpanded = false })
        }
        AnimatedVisibility(
            visible = hudExpanded,
            enter = fadeIn(tween(120)) +
                scaleIn(initialScale = 0.85f, transformOrigin = TransformOrigin(1f, 0f), animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)),
            exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.85f, transformOrigin = TransformOrigin(1f, 0f)),
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 92.dp, end = 12.dp),
        ) {
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
            )
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
    if (repsNumpad && exercise != null) {
        com.calistapp.app.ui.common.NumberPadSheet(
            title = if (isHold) "Seconds" else "Reps",
            initial = live.currentReps,
            onConfirm = { counterTouched = true; vm.setReps(it); repsNumpad = false },
            onDismiss = { repsNumpad = false },
        )
    }
    if (weightNumpad && exercise != null) {
        com.calistapp.app.ui.common.DecimalPadSheet(
            title = "Added weight",
            initial = live.currentSetWeightKg,
            onConfirm = { vm.setAddedWeight(it); weightNumpad = false },
            onDismiss = { weightNumpad = false },
        )
    }
    ratingSet?.let { rating ->
        // Pre-fill from what's already logged, else the plan's target effort for that set.
        val planTarget = live.plan.slot(rating.slotId)?.sets()?.getOrNull(rating.setIndex - 1)?.effort
        EffortInputSheet(
            initialScale = rating.effortScale ?: planTarget?.scale,
            initialValue = rating.effortValue?.toInt() ?: planTarget?.value?.toInt(),
            onConfirm = { scale, value ->
                vm.setSetEffort(rating.slotId, rating.setIndex, scale, value.toDouble())
                ratingSet = null
            },
            onDismiss = { ratingSet = null },
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
