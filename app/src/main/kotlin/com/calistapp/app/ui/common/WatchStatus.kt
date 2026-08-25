package com.calistapp.app.ui.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.calistapp.app.data.sync.WatchLinkState
import com.calistapp.app.data.sync.WatchLinkStatus
import com.calistapp.app.ui.theme.Amber
import com.calistapp.app.ui.theme.Coral
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.Flame
import com.calistapp.app.ui.theme.Sky

private fun accentFor(status: WatchLinkStatus): Color = when (status) {
    WatchLinkStatus.STREAMING -> Flame
    WatchLinkStatus.READY -> Sky
    WatchLinkStatus.APP_UNREACHABLE -> Amber
    WatchLinkStatus.NO_DEVICE -> Coral
    WatchLinkStatus.CHECKING -> Sky
}

private fun titleFor(state: WatchLinkState): String = when (state.status) {
    WatchLinkStatus.STREAMING -> "Watch connected — live"
    WatchLinkStatus.READY -> "Watch connected"
    WatchLinkStatus.APP_UNREACHABLE -> "Watch app not reachable"
    WatchLinkStatus.NO_DEVICE -> "No watch connected"
    WatchLinkStatus.CHECKING -> "Checking watch…"
}

/**
 * Says which of the three things is actually wrong, because they have three different fixes:
 * no watch paired at all, a paired watch without the app running, or a healthy link.
 */
private fun detailFor(state: WatchLinkState): String = when (state.status) {
    WatchLinkStatus.STREAMING ->
        state.deviceName?.let { "Streaming heart rate from $it." } ?: "Streaming heart rate."
    WatchLinkStatus.READY ->
        "${state.deviceName ?: "Your watch"} is paired and the app answered. Heart rate starts when a workout does."
    WatchLinkStatus.APP_UNREACHABLE ->
        "Paired, but Calistapp isn't running on it. Open the app on your watch."
    WatchLinkStatus.NO_DEVICE ->
        "Reps are still counted without one."
    WatchLinkStatus.CHECKING -> "Looking for your watch…"
}

/** Full status card for the dashboard and the pre-workout screen. */
@Composable
fun WatchStatusCard(
    state: WatchLinkState,
    onReconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = accentFor(state.status)
    GlassCard(modifier = modifier, accent = accent, contentPadding = 14) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatusDot(state.status, accent)
            Column(Modifier.weight(1f)) {
                Text(
                    titleFor(state),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
                Text(
                    detailFor(state),
                    style = MaterialTheme.typography.bodySmall,
                    color = Ash,
                )
            }
            if (state.refreshing) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = accent)
            } else if (state.status != WatchLinkStatus.STREAMING) {
                TextButton(onClick = onReconnect) { Text("Reconnect", color = accent) }
            }
        }
    }
}

/** Compact one-line variant for during a workout, where space is tight. */
@Composable
fun WatchStatusStrip(
    state: WatchLinkState,
    onReconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = accentFor(state.status)
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusDot(state.status, accent)
        Text(
            titleFor(state),
            style = MaterialTheme.typography.labelMedium,
            color = accent,
            modifier = Modifier.weight(1f),
        )
        if (state.refreshing) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = accent)
        } else if (state.status != WatchLinkStatus.STREAMING) {
            TextButton(onClick = onReconnect) { Text("Reconnect", color = accent) }
        }
    }
}

/** Pulses while data is actually flowing, so "live" is visible at a glance. */
@Composable
private fun StatusDot(status: WatchLinkStatus, accent: Color) {
    val alpha = if (status == WatchLinkStatus.STREAMING) {
        val transition = rememberInfiniteTransition(label = "pulse")
        val animated by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "pulseAlpha",
        )
        animated
    } else {
        1f
    }
    Box(
        Modifier
            .size(10.dp)
            .alpha(alpha)
            .clip(CircleShape)
            .background(accent),
    )
}
