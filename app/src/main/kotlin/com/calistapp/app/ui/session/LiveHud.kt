package com.calistapp.app.ui.session

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.calistapp.app.ui.common.formatClock
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.Capsule
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.Coral
import com.calistapp.app.ui.theme.Flame
import com.calistapp.app.ui.theme.NumericMedium
import com.calistapp.app.ui.theme.OnyxBorder
import com.calistapp.app.ui.theme.OnyxFillStrong
import com.calistapp.app.ui.theme.OnyxRaised
import com.calistapp.core.model.HrZone

/** The always-on heart-rate + calorie chip in the corner of the hero. Tapping it opens [LiveHudPanel]. */
@Composable
fun LiveHudChip(bpm: Int, kcal: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(Capsule)
            .background(Color.Black.copy(alpha = 0.5f))
            .border(1.dp, OnyxBorder, Capsule)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Favorite, null, tint = Coral, modifier = Modifier.size(14.dp))
        Text(if (bpm > 0) "$bpm" else "—", style = MaterialTheme.typography.labelLarge, color = Chalk)
        Icon(Icons.Filled.LocalFireDepartment, null, tint = Flame, modifier = Modifier.size(14.dp))
        Text("$kcal", style = MaterialTheme.typography.labelLarge, color = Chalk)
        Icon(Icons.Filled.KeyboardArrowDown, "Details", tint = Ash, modifier = Modifier.size(14.dp))
    }
}

/**
 * The expanded HUD: everything the chip stands in for — current HR and its zone, a live trace,
 * session average and peak, calories with a burn rate, watch link, and elapsed time. Rendered as a
 * card that animates open from the chip's corner (the caller handles the reveal + dismiss scrim).
 */
@Composable
fun LiveHudPanel(
    bpm: Int,
    avgHr: Int,
    peakHr: Int,
    maxHr: Int,
    recentBpm: List<Int>,
    kcal: Int,
    kcalPerMin: Double,
    elapsedMs: Long,
    watchLabel: String,
    showReconnect: Boolean,
    onReconnect: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = if (bpm > 0 && maxHr > 0) HrZone.forHr(bpm, maxHr) else null
    Column(
        modifier
            .width(268.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(OnyxRaised)
            .border(1.dp, OnyxBorder, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Favorite, null, tint = Coral, modifier = Modifier.size(16.dp))
            Text("  Heart rate", style = MaterialTheme.typography.labelMedium, color = Ash, modifier = Modifier.weight(1f))
            Icon(
                Icons.Filled.KeyboardArrowDown,
                "Close",
                tint = Ash,
                modifier = Modifier.size(20.dp).clip(Capsule).clickable(onClick = onClose),
            )
        }

        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (bpm > 0) "$bpm" else "—", style = NumericMedium, color = Chalk)
            Text("bpm", style = MaterialTheme.typography.labelMedium, color = Ash, modifier = Modifier.padding(bottom = 5.dp))
            if (zone != null) {
                Box(Modifier.weight(1f))
                Text(
                    "Z${zone.ordinal + 1} · ${zone.label}",
                    style = MaterialTheme.typography.labelLarge,
                    color = zoneColor(zone),
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }

        if (recentBpm.size >= 2) {
            Sparkline(
                values = recentBpm,
                color = Coral,
                modifier = Modifier.fillMaxWidth().height(40.dp),
            )
        }

        Row(Modifier.fillMaxWidth()) {
            HudStat("avg", if (avgHr > 0) "$avgHr" else "—", Modifier.weight(1f))
            HudStat("peak", if (peakHr > 0) "$peakHr" else "—", Modifier.weight(1f))
            HudStat("max", if (maxHr > 0) "$maxHr" else "—", Modifier.weight(1f))
        }

        Divider()

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.LocalFireDepartment, null, tint = Flame, modifier = Modifier.size(16.dp))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f).padding(start = 6.dp)) {
                Text("$kcal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Chalk)
                Text("kcal", style = MaterialTheme.typography.labelMedium, color = Ash, modifier = Modifier.padding(bottom = 2.dp))
            }
            Text(
                "${"%.1f".format(kcalPerMin)}/min",
                style = MaterialTheme.typography.labelLarge,
                color = Flame,
            )
        }

        Divider()

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Bolt, null, tint = Ash, modifier = Modifier.size(16.dp))
            Text("  $watchLabel", style = MaterialTheme.typography.labelLarge, color = Chalk, modifier = Modifier.weight(1f))
            if (showReconnect) {
                Text(
                    "Reconnect",
                    style = MaterialTheme.typography.labelLarge,
                    color = Flame,
                    modifier = Modifier.clip(Capsule).clickable(onClick = onReconnect).padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Elapsed", style = MaterialTheme.typography.labelLarge, color = Ash, modifier = Modifier.weight(1f))
            Text(formatClock(elapsedMs), style = MaterialTheme.typography.labelLarge, color = Chalk)
        }
    }
}

@Composable
private fun HudStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Chalk)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Ash)
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(OnyxFillStrong))
}

@Composable
private fun Sparkline(values: List<Int>, color: Color, modifier: Modifier = Modifier) {
    val lo = (values.min() - 2).coerceAtLeast(0)
    val hi = (values.max() + 2)
    val range = (hi - lo).coerceAtLeast(1).toFloat()
    Canvas(modifier) {
        val stepX = if (values.size <= 1) 0f else size.width / (values.size - 1)
        var prev: Offset? = null
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = size.height - (v - lo) / range * size.height
            val p = Offset(x, y)
            prev?.let {
                drawLine(color = color, start = it, end = p, strokeWidth = 3f, cap = StrokeCap.Round)
            }
            prev = p
        }
    }
}

private fun zoneColor(zone: HrZone): Color = when (zone) {
    HrZone.ZONE1, HrZone.ZONE2 -> Ash
    HrZone.ZONE3 -> Chalk
    HrZone.ZONE4 -> Flame
    HrZone.ZONE5 -> Coral
}
