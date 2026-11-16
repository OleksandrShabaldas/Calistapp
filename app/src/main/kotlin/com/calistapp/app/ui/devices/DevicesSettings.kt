package com.calistapp.app.ui.devices

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calistapp.app.data.device.BandState
import com.calistapp.app.data.device.FoundBand
import com.calistapp.app.ui.common.GlowIcon
import com.calistapp.app.ui.common.HelpDot
import com.calistapp.app.ui.common.SectionCard
import com.calistapp.app.ui.common.WatchStatusCard
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.Coral
import com.calistapp.app.ui.theme.Flame
import com.calistapp.app.ui.theme.FlameGlow
import com.calistapp.app.ui.theme.Mint
import com.calistapp.app.ui.theme.OnyxBorder

/**
 * Settings → Devices. Where heart rate comes from besides the Calistapp watch app:
 *
 * - a **heart-rate band** that streams live over Bluetooth (any strap, armband, screenless band or
 *   watch that broadcasts standard heart rate — nothing to install on it);
 * - **rings and other trackers** that can't stream live, filled in from Health Connect after the
 *   workout;
 * - the **watch** itself, for completeness.
 */
@Composable
fun DevicesSettings(viewModel: DevicesViewModel = hiltViewModel()) {
    val prefs by viewModel.devicePrefs.collectAsStateWithLifecycle()
    val band by viewModel.bandState.collectAsStateWithLifecycle()
    val readiness by viewModel.readiness.collectAsStateWithLifecycle()
    val found by viewModel.found.collectAsStateWithLifecycle()
    val checkResult by viewModel.checkResult.collectAsStateWithLifecycle()
    val watchLink by viewModel.watchLink.collectAsStateWithLifecycle()

    val askBluetooth = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { viewModel.refresh() }
    val turnOnBluetooth = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { viewModel.refresh() }
    val askHealth = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { viewModel.refresh() }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // ---- Live: a Bluetooth heart-rate band -----------------------------------------------------
        SectionCard(
            title = "Heart-rate band",
            help = "Streams live heart rate during a workout from anything that broadcasts standard " +
                "Bluetooth heart rate — chest straps and armbands (Polar, Garmin HRM, Wahoo, Coospo), " +
                "screenless bands with broadcast switched on (Whoop, Amazfit Helio Strap), Huawei bands " +
                "with HR data broadcasts on, Fitbit and Pixel heart-rate sharing. Nothing to install on " +
                "the band. While it streams, it's the heart-rate source — your watch can still run the " +
                "workout's controls.",
        ) {
            when {
                !readiness.hasBluetooth -> Note("This phone has no Bluetooth Low Energy.")
                !readiness.bluetoothAllowed -> Button(
                    onClick = { askBluetooth.launch(viewModel.bluetoothPermissions) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Allow Bluetooth") }
                !readiness.bluetoothOn && found == null && prefs.band == null -> Button(
                    onClick = { turnOnBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Turn on Bluetooth") }
                else -> Unit
            }

            prefs.band?.let { saved ->
                ChosenBand(name = saved.name, state = band, onTest = viewModel::test, onForget = viewModel::forget)
            }

            AnimatedContent(
                targetState = found != null,
                transitionSpec = { (fadeIn(tween(220)) + expandVertically()) togetherWith fadeOut(tween(120)) },
                label = "scan",
            ) { scanning ->
                if (scanning) {
                    ScanList(found.orEmpty(), onChoose = viewModel::choose, onStop = viewModel::stopScan)
                } else if (readiness.bluetoothAllowed && readiness.hasBluetooth) {
                    OutlinedButton(onClick = viewModel::startScan, modifier = Modifier.fillMaxWidth()) {
                        Text(if (prefs.band == null) "Find a band" else "Choose a different band")
                    }
                }
            }
        }

        // ---- After the workout: rings and trackers via Health Connect -------------------------------
        SectionCard(
            title = "Rings and other trackers",
            help = "Rings and most fitness bands can't stream heart rate live to another app. After a " +
                "workout, Calistapp fills in heart rate from Health Connect instead — where Samsung " +
                "Health (Galaxy Ring, Galaxy Fit, your watch's background readings), Oura, Garmin " +
                "Connect, Fitbit, Zepp and Mi Fitness write it. It happens when you next open the app, " +
                "once they've synced. Rings measure only every few minutes unless a workout is running " +
                "in their own app — start one there too, or the workout's calories stay estimated.",
        ) {
            when {
                !readiness.healthConnectAvailable -> Note("Health Connect isn't available on this phone.")
                else -> {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Fill in heart rate after workouts", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Switch(checked = prefs.fillInFromHealthConnect, onCheckedChange = viewModel::setFillIn)
                    }
                    AnimatedVisibility(visible = prefs.fillInFromHealthConnect) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!readiness.healthAllowed) {
                                Button(onClick = { askHealth.launch(setOf(viewModel.healthPermission)) }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Allow heart rate in Health Connect")
                                }
                            } else {
                                OutlinedButton(onClick = viewModel::checkNow, modifier = Modifier.fillMaxWidth()) { Text("Check now") }
                                AnimatedContent(targetState = checkResult, label = "check") { r ->
                                    if (r != null) Note(r)
                                }
                            }
                        }
                    }
                }
            }
        }

        // ---- The watch -------------------------------------------------------------------------------
        WatchStatusCard(state = watchLink, onReconnect = viewModel::reconnectWatch)
    }
}

/** The chosen band: its name, what it's doing right now (with a live, glowing heart when streaming). */
@Composable
private fun ChosenBand(name: String, state: BandState, onTest: () -> Unit, onForget: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.04f), shape)
            .border(1.dp, OnyxBorder, shape)
            .padding(14.dp)
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Chalk)
                AnimatedContent(
                    targetState = statusLine(state),
                    transitionSpec = { (fadeIn(tween(200)) + slideInVertically { it / 2 }) togetherWith fadeOut(tween(120)) },
                    label = "bandStatus",
                ) { line -> Text(line.first, style = MaterialTheme.typography.labelMedium, color = line.second) }
            }
            if (state is BandState.Streaming) LiveHeart(state.bpm)
            else if (state is BandState.Connecting || state is BandState.Reconnecting) {
                CircularProgressIndicator(color = Flame, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onTest, modifier = Modifier.weight(1f)) { Text("Test") }
            OutlinedButton(onClick = onForget, modifier = Modifier.weight(1f)) { Text("Forget", color = Coral) }
        }
    }
}

private fun statusLine(state: BandState): Pair<String, Color> = when (state) {
    BandState.None -> "Connects when a workout starts" to Ash
    is BandState.Connecting -> "Connecting…" to Ash
    is BandState.Streaming -> (if (state.contact) "Streaming" else "No skin contact") to (if (state.contact) Mint else Coral)
    is BandState.Reconnecting -> "Lost it — reconnecting…" to FlameGlow
    is BandState.NeedsPermission -> "Needs Bluetooth permission" to Coral
    is BandState.BluetoothOff -> "Bluetooth is off" to Coral
}

/** The live reading: a heart that beats at the band's own rate, glowing — it's the thing to look at. */
@Composable
private fun LiveHeart(bpm: Int) {
    val beat = rememberInfiniteTransition(label = "beat")
    val period = (60_000 / bpm.coerceIn(40, 220))
    val pulse by beat.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(period / 2), RepeatMode.Reverse),
        label = "pulse",
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.scale(pulse)) {
            GlowIcon(imageVector = Icons.Filled.Favorite, contentDescription = null, tint = Coral, size = 18.dp, glowColor = Coral, glowRadius = 6.dp, glowAlpha = 0.6f)
        }
        AnimatedContent(targetState = bpm, transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) }, label = "bpm") { v ->
            Text("$v", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Chalk)
        }
        Text("bpm", style = MaterialTheme.typography.labelMedium, color = Ash)
    }
}

/** Bands answering the scan, strongest first, each sliding in as it's found. */
@Composable
private fun ScanList(found: List<FoundBand>, onChoose: (FoundBand) -> Unit, onStop: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CircularProgressIndicator(color = Flame, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            Text("Looking for bands…", style = MaterialTheme.typography.labelLarge, color = Chalk, modifier = Modifier.weight(1f))
            HelpDot(
                "Wear the band, and switch on its heart-rate broadcast if it has one (Whoop, Huawei, " +
                    "Amazfit and Garmin watches call it broadcast or sharing). Only devices that advertise " +
                    "standard heart rate are listed.",
            )
            Text("Stop", style = MaterialTheme.typography.labelLarge, color = Flame, modifier = Modifier.clickable(onClick = onStop).padding(4.dp))
        }
        found.forEach { band ->
            key(band.address) {
                var shown by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                LaunchedEffect(Unit) { shown = true }
                AnimatedVisibility(visible = shown, enter = fadeIn(tween(220)) + slideInVertically { it / 3 }) {
                    FoundRow(band, onClick = { onChoose(band) })
                }
            }
        }
    }
}

@Composable
private fun FoundRow(band: FoundBand, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.04f), shape)
            .border(1.dp, OnyxBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(band.name, style = MaterialTheme.typography.bodyLarge, color = Chalk, modifier = Modifier.weight(1f))
        SignalBars(band.rssi)
    }
}

/** Four bars for signal strength — closer bands first, so the one on your wrist is usually on top. */
@Composable
private fun SignalBars(rssi: Int) {
    val level = when {
        rssi >= -55 -> 4
        rssi >= -67 -> 3
        rssi >= -80 -> 2
        else -> 1
    }
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        (1..4).forEach { i ->
            Box(
                Modifier
                    .width(4.dp)
                    .height((4 + i * 3).dp)
                    .background(if (i <= level) Mint else Ash.copy(alpha = 0.3f), RoundedCornerShape(1.dp)),
            )
        }
    }
    Spacer(Modifier.width(2.dp))
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = Ash)
}
