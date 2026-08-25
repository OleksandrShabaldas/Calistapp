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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
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
import com.calistapp.updater.UpdateState
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

/** Same story: a literal, because the constant arrived with API 33. */
private const val POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"

private fun hasNotifications(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

/**
 * Hold the display on while [active].
 *
 * A window flag rather than a wake lock, deliberately: it applies only while this window is in
 * front and the platform clears it when the window goes away, so there is nothing here that can be
 * left holding the screen on after the app is gone.
 */
@Composable
private fun KeepScreenOn(active: Boolean) {
    val view = LocalView.current
    DisposableEffect(active) {
        view.keepScreenOn = active
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
fun WearApp(viewModel: WearSessionViewModel) {
    MaterialTheme {
        Scaffold(timeText = { TimeText() }) {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val update by viewModel.updateState.collectAsStateWithLifecycle()

            // While a workout runs, hold the screen on. Letting the watch doze mid-set suspends the
            // Activity, and with it the heart-rate collection and the phone sync — so the set you did
            // with the screen off never reaches the phone. Battery is the trade, and for the length
            // of a workout it's the right one. Also held through an update download for the same
            // "don't look like you crashed" reason.
            KeepScreenOn(
                state.running ||
                    update is UpdateState.Checking ||
                    update is UpdateState.Downloading,
            )

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
                    update = update,
                    onStart = { viewModel.start(it) },
                    onReconnect = { viewModel.reconnect() },
                    onCheckUpdate = { viewModel.checkForUpdate() },
                    onInstallUpdate = { viewModel.installUpdate() },
                    onDismissUpdate = { viewModel.dismissUpdate() },
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
    update: UpdateState,
    onStart: (ExerciseType) -> Unit,
    onReconnect: () -> Unit,
    onCheckUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    onDismissUpdate: () -> Unit,
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

    // An update downloads behind a foreground service so the screen can sleep through it, and the
    // notice that it finished is a notification too — neither shows without this on Android 13+.
    // Asked for here, when it starts to matter, rather than at launch alongside the sensor prompts.
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    val checkUpdate = {
        if (!hasNotifications(context)) notificationLauncher.launch(POST_NOTIFICATIONS)
        onCheckUpdate()
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

        // App updates. The watch installs its own — the phone can only ask it to, so this has to
        // be reachable here too rather than existing only as a remote trigger.
        updateItems(update, checkUpdate, onInstallUpdate, onDismissUpdate)
    }
}

/**
 * The update row(s) on the start screen.
 *
 * Written as a [ScalingLazyListScope] extension rather than a composable so each state contributes
 * its own list item — a nested column inside one item would break the list's scaling effect.
 */
private fun ScalingLazyListScope.updateItems(
    update: UpdateState,
    onCheck: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (update) {
        is UpdateState.Idle, is UpdateState.UpToDate -> item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Check for update") },
                secondaryLabel = if (update is UpdateState.UpToDate) {
                    { Text("On ${update.current.name}") }
                } else {
                    null
                },
                onClick = onCheck,
                colors = ChipDefaults.secondaryChipColors(),
            )
        }

        is UpdateState.Checking -> item {
            Text("Checking for update…", style = MaterialTheme.typography.caption2, textAlign = TextAlign.Center)
        }

        is UpdateState.Available -> item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Download ${update.update.version.name}") },
                onClick = onCheck,
                colors = ChipDefaults.secondaryChipColors(),
            )
        }

        is UpdateState.Downloading -> item {
            val percent = update.progress?.let { " ${(it * 100).toInt()}%" }.orEmpty()
            Text(
                // A dropped connection is picked back up from where it stopped, so this says so
                // rather than showing an error — and the screen can be left to sleep through it.
                if (update.reconnecting) {
                    "Reconnecting…$percent"
                } else {
                    "Downloading ${update.version.name}…$percent"
                },
                style = MaterialTheme.typography.caption2,
                textAlign = TextAlign.Center,
            )
        }

        is UpdateState.ReadyToInstall -> item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Install ${update.version.name}") },
                secondaryLabel = { Text("Confirm on watch") },
                onClick = onInstall,
                colors = ChipDefaults.primaryChipColors(),
            )
        }

        is UpdateState.Failed -> item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Update failed") },
                secondaryLabel = { Text(update.message, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                onClick = onDismiss,
                colors = ChipDefaults.secondaryChipColors(),
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
                        // Per-set target when the plan carries one; the single value serves for reps
                        // or seconds, since a timed set overloads "reps" to hold its seconds.
                        val setValue = exercise.sets().getOrNull(state.setIndex - 1)?.reps
                        append(
                            targetOf(
                                exercise.measure,
                                setValue ?: exercise.targetReps,
                                setValue ?: exercise.targetSeconds,
                            ),
                        )
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
