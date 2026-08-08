package com.calistapp.app.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calistapp.app.ui.common.GlassCard
import com.calistapp.app.ui.common.SectionHeading
import com.calistapp.app.ui.theme.Amber
import com.calistapp.app.ui.theme.Capsule
import com.calistapp.app.ui.theme.Coral
import com.calistapp.app.ui.theme.Cream
import com.calistapp.app.ui.theme.CreamMuted
import com.calistapp.app.ui.theme.Emerald
import com.calistapp.core.update.ReleaseNotes
import com.calistapp.updater.UpdateState

/**
 * Check for, download and install a new build, plus a nudge for the watch to do the same.
 *
 * Calistapp is sideloaded, so nothing updates it on its own. Every install still goes through
 * Android's own confirmation dialog — this just removes the trip to the releases page.
 */
@Composable
fun UpdateCard(viewModel: UpdateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val watchNotice by viewModel.watchUpdate.collectAsStateWithLifecycle()
    val watchLink by viewModel.watchLink.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Check once when the screen opens, so an available update is visible without being asked for.
    LaunchedEffect(Unit) {
        if (state is UpdateState.Idle) viewModel.check()
    }

    GlassCard(accent = if (state is UpdateState.Available) Emerald else null) {
        SectionHeading("App version")
        Text(
            "Installed: ${viewModel.currentVersion}",
            style = MaterialTheme.typography.bodyMedium,
            color = CreamMuted,
        )

        when (val s = state) {
            is UpdateState.Idle -> {
                OutlinedButton(onClick = viewModel::check, modifier = Modifier.fillMaxWidth(), shape = Capsule) {
                    Text("Check for updates")
                }
            }

            is UpdateState.Checking -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("Checking…", style = MaterialTheme.typography.bodyMedium, color = CreamMuted)
                }
            }

            is UpdateState.UpToDate -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Emerald, modifier = Modifier.size(18.dp))
                    Text("You're on the latest version.", style = MaterialTheme.typography.bodyMedium, color = Cream)
                }
                TextButton(onClick = viewModel::check) { Text("Check again") }
            }

            is UpdateState.Available -> {
                Text(
                    "Version ${s.update.version.name} is available",
                    style = MaterialTheme.typography.titleMedium,
                    color = Emerald,
                    fontWeight = FontWeight.Bold,
                )
                val notes = ReleaseNotes.toPlainText(s.update.notes)
                if (notes.isNotBlank()) {
                    Text(notes, style = MaterialTheme.typography.bodySmall, color = CreamMuted)
                }
                // Sideloaded installs need this granted once; without it the installer silently
                // refuses, which looks like the update simply not working.
                if (!viewModel.canInstall()) {
                    Text(
                        "Android needs permission to install apps from Calistapp.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Amber,
                    )
                    OutlinedButton(
                        onClick = { context.startActivity(viewModel.unknownSourcesIntent()) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = Capsule,
                    ) {
                        Text("Allow installs")
                    }
                }
                Button(
                    onClick = { viewModel.download(s.update) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = Capsule,
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald),
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Download ${formatSize(s.update.asset.size)}", fontWeight = FontWeight.Bold)
                }
            }

            is UpdateState.Downloading -> {
                Text(
                    // Interrupted downloads resume rather than restart, so a blip is reported as
                    // what it is instead of throwing the whole update away.
                    if (s.reconnecting) "Reconnecting…" else "Downloading ${s.version.name}…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Cream,
                )
                // Bound locally: a property from another module can't be smart-cast after a null
                // check, since that module could in principle change it between the two reads.
                val progress = s.progress
                if (progress != null) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }

            is UpdateState.ReadyToInstall -> {
                Text(
                    "Version ${s.version.name} is ready.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Cream,
                )
                Text(
                    "Android will ask you to confirm.",
                    style = MaterialTheme.typography.bodySmall,
                    color = CreamMuted,
                )
                Button(
                    onClick = viewModel::install,
                    modifier = Modifier.fillMaxWidth(),
                    shape = Capsule,
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald),
                ) {
                    Text("Install", fontWeight = FontWeight.Bold)
                }
            }

            is UpdateState.Failed -> {
                Text(s.message, style = MaterialTheme.typography.bodySmall, color = Coral)
                OutlinedButton(onClick = viewModel::check, modifier = Modifier.fillMaxWidth(), shape = Capsule) {
                    Text("Try again")
                }
            }
        }

        // ---- The watch ---------------------------------------------------------------------------
        // Separate, because the phone genuinely cannot install anything over there — it can only
        // ask the watch to update itself, and the confirmation happens on the wrist.
        SectionHeading("Watch app")
        when (watchNotice) {
            WatchUpdateState.SENT -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.Watch, contentDescription = null, tint = Emerald, modifier = Modifier.size(18.dp))
                Text(
                    "Asked your watch to update. Confirm it on the watch.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Cream,
                )
            }

            WatchUpdateState.UNREACHABLE -> Text(
                "Watch app isn't reachable. Open Calistapp on your watch, then try again.",
                style = MaterialTheme.typography.bodySmall,
                color = Coral,
            )

            WatchUpdateState.IDLE -> Text(
                "The watch installs its own update — the phone can't do it for it.",
                style = MaterialTheme.typography.bodySmall,
                color = CreamMuted,
            )
        }
        OutlinedButton(
            onClick = viewModel::updateWatch,
            enabled = watchLink.isUsable,
            modifier = Modifier.fillMaxWidth(),
            shape = Capsule,
        ) {
            Text(if (watchLink.isUsable) "Update watch app" else "Watch not connected")
        }
    }
}

private fun formatSize(bytes: Long): String =
    if (bytes <= 0) "" else "(%.1f MB)".format(bytes / 1_048_576.0)
