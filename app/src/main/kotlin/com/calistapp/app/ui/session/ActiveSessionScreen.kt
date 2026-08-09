package com.calistapp.app.ui.session

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calistapp.app.data.sync.WatchLinkState
import com.calistapp.app.session.LiveSession
import com.calistapp.app.ui.common.AmbientOverride
import com.calistapp.app.ui.common.GlassCard
import com.calistapp.app.ui.common.MetricBlock
import com.calistapp.app.ui.common.MiniRing
import com.calistapp.app.ui.common.NoHeartRateDialog
import com.calistapp.app.ui.common.PillChip
import com.calistapp.app.ui.common.RepeatingIconButton
import com.calistapp.app.ui.common.RestAlert
import com.calistapp.app.ui.common.SectionHeading
import com.calistapp.app.ui.common.WatchStatusCard
import com.calistapp.app.ui.common.WatchStatusStrip
import com.calistapp.app.ui.common.formatClock
import com.calistapp.app.ui.common.rememberHaptics
import kotlin.math.abs
import com.calistapp.app.ui.exercises.ExerciseImage
import com.calistapp.app.ui.theme.Amber
import com.calistapp.app.ui.theme.Capsule
import com.calistapp.app.ui.theme.CardShape
import com.calistapp.app.ui.theme.Coral
import com.calistapp.app.ui.theme.Cream
import com.calistapp.app.ui.theme.CreamMuted
import com.calistapp.app.ui.theme.Emerald
import com.calistapp.app.ui.theme.NumericLarge
import com.calistapp.app.ui.theme.Sky
import com.calistapp.app.ui.theme.Violet
import com.calistapp.core.model.ExerciseMeasure
import com.calistapp.core.model.ExerciseType
import com.calistapp.core.model.SegmentType
import com.calistapp.core.model.SessionStatus
import com.calistapp.core.model.WorkoutPlan

@Composable
fun ActiveSessionScreen(
    onFinished: (String) -> Unit,
    onDiscarded: () -> Unit,
    onBuildWorkout: () -> Unit,
    viewModel: ActiveSessionViewModel = hiltViewModel(),
) {
    val live by viewModel.live.collectAsStateWithLifecycle()
    val planned by viewModel.plannedExercise.collectAsStateWithLifecycle()
    val plan by viewModel.plan.collectAsStateWithLifecycle()
    val watchLink by viewModel.watchLink.collectAsStateWithLifecycle()
    val thumbnails by viewModel.thumbnails.collectAsStateWithLifecycle()

    val session = live
    // Mid-set you glance at the phone, you don't tap it. A screen that blanks every 30 seconds makes
    // the rep counter unusable exactly when it's needed.
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
            live = session,
            watchLink = watchLink,
            imageUrls = session.currentExercise
                ?.let { thumbnails[it.exerciseId] }
                .orEmpty(),
            onReconnect = viewModel::reconnectWatch,
            onToggle = viewModel::toggleSegment,
            onStartNow = viewModel::startWorkNow,
            onReps = viewModel::adjustReps,
            onNext = viewModel::advanceToNext,
            onPause = viewModel::pause,
            onResume = viewModel::resume,
            onFinish = { viewModel.finish(onFinished) },
            onDiscard = { viewModel.discard(); onDiscarded() },
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
    // Heart rate only ever comes from the watch. Without it a session records no samples at all, the
    // engine has nothing to integrate, and the workout is filed at zero calories — so this asks
    // first rather than letting someone find out afterwards.
    var confirmNoWatch by remember { mutableStateOf(false) }

    if (confirmNoWatch) {
        NoHeartRateDialog(
            onDismiss = { confirmNoWatch = false },
            onStartAnyway = { onStart(type) },
            onReconnect = onReconnect,
        )
    }

    // Nothing to run means nothing to score: every set is measured against the movement being
    // performed, and a session with no exercises can't advance, can't log a set, and gives the
    // calorie engine nothing but heart rate. Building the workout is the step, not an optional one.
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
            color = Cream,
            modifier = Modifier.padding(top = 12.dp),
        )

        when {
            !plan.isEmpty -> GlassCard(accent = Emerald) {
                SectionHeading(plan.name.ifBlank { "Workout ready" }, count = plan.exercises.size)
                Text(
                    "${plan.totalSets} sets planned",
                    style = MaterialTheme.typography.labelMedium,
                    color = Emerald,
                )
                plan.exercises.forEach { slot ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            slot.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Cream,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            slot.targetLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = CreamMuted,
                        )
                    }
                }
            }

            plannedName != null -> GlassCard(accent = Emerald) {
                SectionHeading("Single exercise")
                Text(plannedName, style = MaterialTheme.typography.displaySmall, color = Cream)
            }

            // Held back while a requested exercise is still being looked up, so the screen doesn't
            // flash "nothing planned" at someone who arrived from a gallery entry.
            !hasWorkout -> GlassCard {
                SectionHeading("Nothing planned yet")
                Text(
                    "Pick the movements first — the tracker scores every set against the exercise " +
                        "you're doing, so a workout needs at least one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CreamMuted,
                )
            }
        }

        // Promoted to the primary action while there's nothing to run, demoted to an edit once
        // there is — the screen should point at the one thing left to do.
        if (hasWorkout) {
            OutlinedButton(
                onClick = onBuildWorkout,
                modifier = Modifier.fillMaxWidth(),
                shape = Capsule,
            ) {
                Text(if (plan.isEmpty) "Build a workout" else "Edit workout")
            }
        } else {
            Button(
                onClick = onBuildWorkout,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = Capsule,
                colors = ButtonDefaults.buttonColors(containerColor = Emerald),
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
            colors = ButtonDefaults.buttonColors(containerColor = Emerald),
        ) {
            Text("Start", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (!hasWorkout) {
            Text(
                "Add at least one exercise to start.",
                style = MaterialTheme.typography.labelMedium,
                color = CreamMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Box(Modifier.height(8.dp))
    }
}

@Composable
private fun LiveControls(
    live: LiveSession,
    watchLink: WatchLinkState,
    imageUrls: List<String>,
    onReconnect: () -> Unit,
    onToggle: () -> Unit,
    onStartNow: () -> Unit,
    onReps: (Int) -> Unit,
    onNext: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    onDiscard: () -> Unit,
) {
    val isActive = live.currentSegment == SegmentType.ACTIVE
    val accent = if (isActive) Emerald else Sky
    val segColor by animateColorAsState(accent, label = "seg")
    val s = live.summary
    val exercise = live.currentExercise
    val countdown = live.countdownSeconds
    val restRemaining = live.restRemainingSeconds

    // Keyed on the block, so each rest gets exactly one alert however long you overrun it.
    val haptics = rememberHaptics()
    RestAlert(
        remainingSeconds = restRemaining,
        resetKey = live.segmentStartMs,
        onElapsed = haptics::restOver,
    )

    // Guards against banking an empty set. Reps are the one input the app can't infer, so a zero is
    // ambiguous — it might be a genuine skipped set or a forgotten tap, and the two need different
    // handling. Asking costs a second; silently recording nothing loses the set's calories.
    var confirming by remember { mutableStateOf<PendingAction?>(null) }
    fun guard(action: PendingAction, run: () -> Unit) {
        if (isActive && exercise != null && live.currentReps == 0) confirming = action else run()
    }

    // The whole screen shifts colour with the work/rest state — an ambient cue you can read from
    // across the room, without focusing on any single number.
    AmbientOverride(accent)

    if (confirming != null) {
        val pending = confirming!!
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text("No reps logged") },
            text = {
                Text(
                    "This set is recorded as 0 ${
                        if (exercise?.measure == ExerciseMeasure.SECONDS) "seconds" else "reps"
                    }. Log them first if you forgot — they feed the calorie estimate.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirming = null; pending.run() }) {
                    Text(pending.label, color = Coral)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = null }) { Text("Go back") }
            },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.height(4.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PillChip(
                label = when {
                    countdown != null -> "● GET READY"
                    isActive -> "● WORKING"
                    else -> "● RESTING"
                },
                selected = true,
                accent = segColor,
            )
            if (live.plan.isCircuit) {
                PillChip(
                    label = "Round ${live.currentRound} of ${live.plan.rounds}",
                    accent = Violet,
                )
            }
        }

        // The hero is the movement you're doing. Calories are the reason the app exists, but they
        // are not what you need to see mid-set — the exercise, the set number and the target are.
        if (exercise != null) {
            ExerciseImage(
                urls = imageUrls,
                contentDescription = exercise.name,
                animate = isActive,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .clip(CardShape),
            )
            Text(
                exercise.displayName,
                style = MaterialTheme.typography.displaySmall,
                color = Cream,
                textAlign = TextAlign.Center,
            )
            Text(
                "Set ${live.setIndex} of ${live.plan.targetSetsFor(exercise.slotId)} · target ${
                    if (exercise.measure == ExerciseMeasure.SECONDS) "${exercise.targetSeconds}s"
                    else "${exercise.targetReps} reps"
                }",
                style = MaterialTheme.typography.titleMedium,
                color = segColor,
                textAlign = TextAlign.Center,
            )
        }

        // The lead-in, in place of the rep counter — the one number that matters right now.
        if (countdown != null) {
            GlassCard(accent = segColor) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("$countdown", style = NumericLarge, color = segColor)
                    Text(
                        "Starting — get into position",
                        style = MaterialTheme.typography.labelMedium,
                        color = CreamMuted,
                    )
                    TextButton(onClick = onStartNow) { Text("Start now", color = segColor) }
                }
            }
        } else if (!isActive && restRemaining != null) {
            // Rest is the half of a workout nothing was tracking. Counting it down turns dead time
            // into something with a shape, and the buzz means you don't have to watch it.
            val over = restRemaining < 0
            val restAccent = if (over) Amber else segColor
            GlassCard(accent = restAccent) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(formatClock(abs(restRemaining) * 1000L), style = NumericLarge, color = restAccent)
                    Text(
                        if (over) "over the ${exercise?.restLabel} rest" else "rest remaining",
                        style = MaterialTheme.typography.labelMedium,
                        color = CreamMuted,
                    )
                }
            }
        } else if (isActive && exercise != null) {
            // Rep logging — the signal heart rate can't see.
            val isHold = exercise.measure == ExerciseMeasure.SECONDS
            val target = if (isHold) exercise.targetSeconds else exercise.targetReps
            val bulk = if (isHold) 10 else 5

            GlassCard(accent = segColor) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BulkRepButton("−$bulk", enabled = live.currentReps > 0) { onReps(-bulk) }
                    RepButton(Icons.Filled.Remove, "One fewer", enabled = live.currentReps > 0) { onReps(-1) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${live.currentReps}", style = NumericLarge, color = segColor)
                        Text(
                            if (isHold) "seconds held" else "reps this set",
                            style = MaterialTheme.typography.labelMedium,
                            color = CreamMuted,
                        )
                    }
                    RepButton(Icons.Filled.Add, "One more") { onReps(1) }
                    BulkRepButton("+$bulk") { onReps(bulk) }
                }

                // The overwhelmingly common outcome is "I did exactly what I planned". Making that
                // one tap instead of thirty is the difference between logging reps and not bothering.
                if (target > 0 && live.currentReps != target) {
                    OutlinedButton(
                        onClick = { onReps(target - live.currentReps) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = Capsule,
                    ) {
                        Text(if (isHold) "Hit the target — ${target}s" else "Hit the target — $target reps")
                    }
                }
            }
        }

        // The core interaction: leaving WORKING banks the set you just did.
        Button(
            onClick = { guard(PendingAction("Bank it anyway", onToggle)) { onToggle() } },
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = Capsule,
            colors = ButtonDefaults.buttonColors(containerColor = segColor),
        ) {
            Text(
                when {
                    countdown != null -> "Cancel"
                    isActive -> "Done — switch to REST"
                    else -> "Start next set"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }

        // Secondary now: the numbers still matter, they just aren't what you look at mid-set.
        GlassCard {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MiniRing(
                    label = "Plan",
                    value = "${live.completedSetCount}/${live.plan.totalSets}",
                    sub = "sets",
                    progress = planProgress(live),
                    accent = segColor,
                )
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        MetricBlock("kcal", "${s.totalKcal.toInt()}", Emerald)
                        MetricBlock("BPM", if (live.lastBpm > 0) "${live.lastBpm}" else "—", Coral)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        MetricBlock("Elapsed", formatClock(live.elapsedMs), Cream)
                        MetricBlock("Reps", "${s.totalReps}", Amber)
                    }
                }
            }
        }

        live.nextExercise?.let { next ->
            OutlinedButton(
                onClick = { guard(PendingAction("Skip anyway", onNext)) { onNext() } },
                modifier = Modifier.fillMaxWidth(),
                shape = Capsule,
            ) {
                Text("Skip to ${next.name}")
            }
        }

        WatchStatusStrip(state = watchLink, onReconnect = onReconnect)

        // Where the energy actually went.
        if (s.perExercise.isNotEmpty()) {
            GlassCard {
                SectionHeading("By exercise")
                s.perExercise.forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            row.exerciseName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Cream,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${row.sets}× · ${row.reps} reps · ${row.kcal.toInt()} kcal",
                            style = MaterialTheme.typography.bodySmall,
                            color = CreamMuted,
                        )
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = if (live.status == SessionStatus.PAUSED) onResume else onPause,
                modifier = Modifier.weight(1f),
                shape = Capsule,
            ) {
                Text(if (live.status == SessionStatus.PAUSED) "Resume" else "Pause")
            }
            Button(
                onClick = { guard(PendingAction("Finish anyway", onFinish)) { onFinish() } },
                modifier = Modifier.weight(1f),
                shape = Capsule,
                colors = ButtonDefaults.buttonColors(containerColor = Emerald),
            ) {
                Text("Finish", fontWeight = FontWeight.Bold)
            }
        }

        TextButton(onClick = onDiscard) { Text("Discard", color = Coral) }
        Box(Modifier.height(8.dp))
    }
}

/** An action held back by the zero-rep confirmation, with the wording for its confirm button. */
private class PendingAction(val label: String, val run: () -> Unit)

/** Single step, and keeps stepping while held. */
@Composable
private fun RepButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    RepeatingIconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(52.dp)) {
        Icon(
            icon,
            contentDescription = "$description — hold to repeat",
            tint = if (enabled) Cream else CreamMuted,
            modifier = Modifier.size(26.dp),
        )
    }
}

/** The five-at-a-time jump, for sets you count in handfuls rather than one by one. */
@Composable
private fun BulkRepButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    RepeatingIconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(44.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (enabled) CreamMuted else CreamMuted.copy(alpha = 0.4f),
        )
    }
}

/** How far through the planned sets we are — drives the hero ring. */
private fun planProgress(live: LiveSession): Float {
    val total = live.plan.totalSets
    return if (total <= 0) 0f else live.completedSetCount.toFloat() / total
}

/** A simple wrap of selectable chips. */
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
            PillChip(
                label = label,
                selected = selected == value,
                onClick = { onSelect(value) },
            )
        }
    }
}
