package com.calistapp.app.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.calistapp.app.data.session.SessionPrefs
import com.calistapp.app.ui.common.formatClock
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.Capsule
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.Coral
import com.calistapp.app.ui.theme.Flame
import com.calistapp.app.ui.theme.NumericLarge
import com.calistapp.app.ui.theme.Onyx
import com.calistapp.app.ui.theme.OnyxBorder
import com.calistapp.app.ui.theme.OnyxFillStrong

/**
 * The paused takeover: a big frozen clock, the four live-workout toggles, and the way out — resume,
 * end (score and save), or discard. Fills the whole screen opaquely so the workout behind it is put
 * aside rather than peeking through.
 */
@Composable
fun PauseScreen(
    elapsedMs: Long,
    prefs: SessionPrefs,
    onSound: (Boolean) -> Unit,
    onVibration: (Boolean) -> Unit,
    onAutoplay: (Boolean) -> Unit,
    onHandsFree: (Boolean) -> Unit,
    onResume: () -> Unit,
    onEnd: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmDiscard by remember { mutableStateOf(false) }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard this workout?") },
            text = { Text("Everything recorded so far is thrown away and nothing is saved. This can't be undone.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onDiscard() }) { Text("Discard", color = Coral) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep it") } },
        )
    }

    Column(
        modifier
            .fillMaxSize()
            .background(Onyx)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(90.dp))
        Text("PAUSED", style = MaterialTheme.typography.titleMedium, color = Ash, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(formatClock(elapsedMs), style = NumericLarge, color = Chalk)

        Spacer(Modifier.weight(1f))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ToggleCard("Sound", Icons.AutoMirrored.Filled.VolumeUp, prefs.sound, Modifier.weight(1f)) { onSound(!prefs.sound) }
            ToggleCard("Vibration", Icons.Filled.Vibration, prefs.vibration, Modifier.weight(1f)) { onVibration(!prefs.vibration) }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ToggleCard("Autoplay", Icons.Filled.Videocam, prefs.autoplayVideo, Modifier.weight(1f)) { onAutoplay(!prefs.autoplayVideo) }
            ToggleCard("Hands-free", Icons.Filled.RecordVoiceOver, prefs.handsFree, Modifier.weight(1f)) { onHandsFree(!prefs.handsFree) }
        }

        Spacer(Modifier.height(24.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .clip(Capsule)
                .background(Flame)
                .clickable(onClick = onResume)
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Resume", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Onyx)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .weight(1f)
                    .clip(Capsule)
                    .border(1.dp, OnyxBorder, Capsule)
                    .clickable(onClick = onEnd)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("End now", style = MaterialTheme.typography.titleSmall, color = Chalk)
            }
            Box(
                Modifier
                    .weight(1f)
                    .clip(Capsule)
                    .border(1.dp, OnyxBorder, Capsule)
                    .clickable { confirmDiscard = true }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Discard", style = MaterialTheme.typography.titleSmall, color = Coral)
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun ToggleCard(
    label: String,
    icon: ImageVector,
    on: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(OnyxFillStrong)
            .border(1.dp, if (on) Flame.copy(alpha = 0.4f) else OnyxBorder, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (on) Chalk else Ash, modifier = Modifier.size(22.dp))
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(9.dp).clip(Capsule).background(if (on) Flame else Ash.copy(alpha = 0.4f)))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) Chalk else Ash)
    }
}
