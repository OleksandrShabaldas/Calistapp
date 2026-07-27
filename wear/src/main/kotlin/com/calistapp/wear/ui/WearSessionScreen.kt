package com.calistapp.wear.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.calistapp.core.model.ExerciseMeasure
import com.calistapp.core.model.ExerciseType
import com.calistapp.wear.session.WearSessionState
import com.calistapp.wear.session.WearSessionViewModel
import java.util.concurrent.TimeUnit

/**
 * Background sensor access, gated behind its own permission from Android 13 onwards. Named as a
 * literal because the constant doesn't exist in older compile targets.
 */
private const val BACKGROUND_SENSORS = "android.permission.BODY_SENSORS_BACKGROUND"

private fun needsBackgroundSensors(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/** Below API 33 the foreground grant already covers background reads, so treat it as held. */
private fun hasBackgroundSensors(context: Context): Boolean =
    !needsBackgroundSensors() ||
        ContextCompat.checkSelfPermission(context, BACKGROUND_SENSORS) == PackageManager.PERMISSION_GRANTED

@Composable
fun WearApp(viewModel: WearSessionViewModel) {
    MaterialTheme {
        Scaffold(timeText = { TimeText() }) {
            val state by viewModel.state.collectAsStateWithLifecycle()
            if (state.running) {
                RunningScreen(
                    state = state,
                    onToggle = { viewModel.toggleSegment() },
                    onReps = { viewModel.adjustReps(it) },
                    onNext = { viewModel.nextExercise() },
                    onStop = { viewModel.stop() },
                    onReconnect = { viewModel.reconnect() },
                )
            } else {
                StartScreen(
                    state = state,
                    onStart = { viewModel.start(it) },
                    onReconnect = { viewModel.reconnect() },
                )
            }
        }
    }
}

/**
 * Phone-link status. Kept to one line so it never crowds the workout controls, but always present —
 * discovering mid-session that nothing reached the phone is the failure worth preventing.
 */
@Composable
private fun PhoneLinkRow(state: WearSessionState, onReconnect: () -> Unit) {
    val linked = state.phoneLinked
    val accent = if (linked) MaterialTheme.colors.primary else MaterialTheme.colors.error
    if (linked) {
        Text(
            "● Phone connected",
            style = MaterialTheme.typography.caption3,
            color = accent,
            textAlign = TextAlign.Center,
        )
    } else {
        Chip(
            modifier = Modifier.fillMaxWidth(),
            label = { Text(if (state.linkRefreshing) "Reconnecting…" else "Reconnect phone") },
            secondaryLabel = { Text("● Phone not connected") },
            onClick = onReconnect,
            colors = ChipDefaults.secondaryChipColors(contentColor = accent),
        )
    }
}

@Composable
private fun StartScreen(
    state: WearSessionState,
    onStart: (ExerciseType) -> Unit,
    onReconnect: () -> Unit,
) {
    val context = LocalContext.current
    var typeIndex by remember { mutableStateOf(0) }
    val types = ExerciseType.entries
    val type = types[typeIndex]

    // Health Services is the only heart-rate source, so the permission gate comes first.
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.BODY_SENSORS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    // Android 13+ needs background sensor access as well, or readings stop the moment the screen
    // blanks. The platform requires it be asked for *separately*, and only once the foreground
    // permission is already held — requesting both at once is silently denied.
    var backgroundGranted by remember { mutableStateOf(hasBackgroundSensors(context)) }
    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { result -> backgroundGranted = result }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { result ->
        granted = result
        if (result && !hasBackgroundSensors(context) && needsBackgroundSensors()) {
            backgroundLauncher.launch(BACKGROUND_SENSORS)
        }
    }

    val listState = rememberScalingLazyListState()
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item { Text("Calistapp", fontWeight = FontWeight.Bold, color = MaterialTheme.colors.primary) }
        item { PhoneLinkRow(state, onReconnect) }

        // A plan pushed down from the phone — start here and the watch runs the same workout.
        if (!state.plan.isEmpty) {
            item {
                Text(
                    state.plan.name.ifBlank { "Workout from phone" },
                    style = MaterialTheme.typography.caption1,
                    textAlign = TextAlign.Center,
                )
            }
            item {
                Text(
                    "${state.plan.exercises.size} exercises · ${state.plan.totalSets} sets",
                    style = MaterialTheme.typography.caption2,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = { Text(type.displayName) },
                secondaryLabel = { Text("Type") },
                onClick = { typeIndex = (typeIndex + 1) % types.size },
                colors = ChipDefaults.secondaryChipColors(),
            )
        }

        if (!granted) {
            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Allow heart rate") },
                    onClick = { permissionLauncher.launch(Manifest.permission.BODY_SENSORS) },
                    colors = ChipDefaults.secondaryChipColors(),
                )
            }
        } else if (!backgroundGranted && needsBackgroundSensors()) {
            // Tracking works, but only while you're looking at it — worth flagging, since the
            // failure mode is a workout that quietly stops recording in your pocket.
            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Allow always") },
                    secondaryLabel = { Text("For screen-off tracking") },
                    onClick = { backgroundLauncher.launch(BACKGROUND_SENSORS) },
                    colors = ChipDefaults.secondaryChipColors(),
                )
            }
        }

        item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Start") },
                onClick = { onStart(type) },
                colors = ChipDefaults.primaryChipColors(),
            )
        }
    }
}

@Composable
private fun RunningScreen(
    state: WearSessionState,
    onToggle: () -> Unit,
    onReps: (Int) -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
    onReconnect: () -> Unit,
) {
    val isActive = state.isWorking
    val exercise = state.currentExercise
    val countdown = state.countdownSeconds
    val listState = rememberScalingLazyListState()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Text(
                when {
                    countdown != null -> "● GET READY"
                    isActive -> "● WORKING"
                    else -> "● RESTING"
                },
                color = if (isActive) MaterialTheme.colors.primary else MaterialTheme.colors.secondary,
                fontWeight = FontWeight.Bold,
            )
        }

        // What you're actually doing right now — the thing the watch was missing.
        if (exercise != null) {
            item {
                Text(
                    exercise.displayName,
                    style = MaterialTheme.typography.title3,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            item {
                Text(
                    buildString {
                        if (state.plan.isCircuit) {
                            append("Round ${state.currentRound}/${state.plan.rounds} · ")
                        }
                        append("Set ${state.setIndex} of ${state.plan.targetSetsFor(exercise.slotId)}")
                        append(" · ")
                        append(targetOf(exercise.measure, exercise.targetReps, exercise.targetSeconds))
                    },
                    style = MaterialTheme.typography.caption2,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // The lead-in, big enough to read at arm's length while you get into position.
        if (countdown != null) {
            item {
                Text(
                    "$countdown",
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.primary,
                )
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("♥ ", color = MaterialTheme.colors.error)
                Text("${state.lastBpm}", fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text(" bpm", style = MaterialTheme.typography.caption2)
            }
        }

        // Rep counter — only meaningful while working, and only when there's an exercise to count.
        if (isActive && exercise != null) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = { onReps(-1) },
                        modifier = Modifier.size(40.dp),
                        colors = ButtonDefaults.secondaryButtonColors(),
                    ) { Text("−", fontSize = 20.sp) }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${state.currentReps}", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (exercise.measure == ExerciseMeasure.SECONDS) "sec" else "reps",
                            style = MaterialTheme.typography.caption3,
                            color = MaterialTheme.colors.onSurfaceVariant,
                        )
                    }

                    Button(
                        onClick = { onReps(1) },
                        modifier = Modifier.size(40.dp),
                        colors = ButtonDefaults.primaryButtonColors(),
                    ) { Text("+", fontSize = 20.sp) }
                }
            }
        }

        item {
            Text(
                "${clock(state.elapsedMs)} · ${state.summary.totalKcal.toInt()} kcal",
                style = MaterialTheme.typography.caption1,
                color = MaterialTheme.colors.primary,
                fontWeight = FontWeight.Bold,
            )
        }

        // The core interaction. Leaving WORKING logs the reps just counted.
        item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (isActive) "Rest" else "Work") },
                onClick = onToggle,
                colors = ChipDefaults.primaryChipColors(),
            )
        }

        state.nextExercise?.let { next ->
            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(next.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    secondaryLabel = { Text("Next · ${next.targetLabel}") },
                    onClick = onNext,
                    colors = ChipDefaults.secondaryChipColors(),
                )
            }
        }

        item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Finish") },
                onClick = onStop,
                colors = ChipDefaults.secondaryChipColors(),
            )
        }

        item { PhoneLinkRow(state, onReconnect) }
    }
}

private fun targetOf(measure: ExerciseMeasure, reps: Int, seconds: Int): String =
    if (measure == ExerciseMeasure.SECONDS) "${seconds}s" else "$reps"

private fun clock(ms: Long): String {
    val totalSec = TimeUnit.MILLISECONDS.toSeconds(ms.coerceAtLeast(0))
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
