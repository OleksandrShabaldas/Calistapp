package com.calistapp.app.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.calistapp.app.ui.common.GlowIcon
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.Coral
import com.calistapp.app.ui.theme.FlameHot
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val DATE_FMT = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

/**
 * The streak popup: a scrollable GitHub-style heatmap of daily burn (orange = hit goal, redder = the
 * further over, fainter = the further under) plus the streak stats. Scales + fades in over a scrim.
 */
@Composable
fun StreakHeatmapOverlay(
    visible: Boolean,
    data: StreakData,
    onJumpToDay: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var appear by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { appear = true }
        val scrim by animateColorAsState(if (appear) Color.Black.copy(alpha = 0.64f) else Color.Transparent, tween(200), label = "scrim")
        Box(
            Modifier.fillMaxSize().background(scrim)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visible = appear,
                enter = fadeIn(tween(220)) + scaleIn(tween(260), initialScale = 0.92f),
                exit = fadeOut(tween(130)) + scaleOut(tween(130), targetScale = 0.92f),
            ) {
                StreakCard(data, onJumpToDay, onDismiss)
            }
        }
    }
}

@Composable
private fun StreakCard(data: StreakData, onJumpToDay: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val maxHeight = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.88f).dp
    val stats = data.stats
    Column(
        Modifier
            .fillMaxWidth(0.94f)
            .heightIn(max = maxHeight)
            .padding(vertical = 24.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(CardSurface)
            .border(1.dp, CardBorder, RoundedCornerShape(28.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .verticalScroll(rememberScrollState())
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // Header.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlowIcon(Icons.Filled.LocalFireDepartment, null, FlameHot, size = 30.dp, glowRadius = 8.dp, glowAlpha = 0.55f)
            Column {
                Text("${stats.current}-day streak", style = MaterialTheme.typography.headlineSmall, color = Chalk)
                Text("Days you hit your calorie goal", style = MaterialTheme.typography.bodySmall, color = Ash)
            }
        }

        Heatmap(data.cells)

        // Stat tiles.
        val lastBroken = stats.lastBrokenDay
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StatRow(
                StatTile("Current", "${stats.current} d"),
                StatTile("Longest", "${stats.longest} d"),
            )
            StatRow(
                StatTile("Longest miss", "${stats.longestMiss} d"),
                StatTile("Hit this month", "${stats.daysHitThisMonth}"),
            )
            StatRow(
                StatTile("Goal-hit rate", "${stats.goalHitPercent}%"),
                StatTile("Avg daily burn", "%,d kcal".format(stats.avgDailyBurn)),
            )
            stats.bestDay?.let {
                StatRow(
                    StatTile("Best day", "%,d kcal".format(stats.bestDayKcal)),
                    StatTile("on", it.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))),
                )
            }
        }

        // Last-broken day — tappable to jump to it.
        if (lastBroken != null) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.05f))
                    .clickable { onDismiss(); onJumpToDay(lastBroken) }.padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Last streak broke", style = MaterialTheme.typography.labelMedium, color = Ash)
                    Text(lastBroken.format(DATE_FMT), style = MaterialTheme.typography.titleMedium, color = Chalk)
                }
                Text("View day ›", style = MaterialTheme.typography.labelLarge, color = FlameHot, fontWeight = FontWeight.SemiBold)
            }
        }

        // Which weekdays you hit most.
        WeekdayPattern(stats.weekdayHitRate)
    }
}

/** A GitHub-style column-per-week heatmap, scrolled to the most recent week; scroll left for older. */
@Composable
private fun Heatmap(cells: List<HeatCell>) {
    if (cells.isEmpty()) {
        Text("Log a workout or some steps to start your heatmap.", style = MaterialTheme.typography.bodyMedium, color = Ash)
        return
    }
    // Pad the front so the first column starts on Monday, then chunk into week columns.
    val leading = cells.first().date.dayOfWeek.value - 1
    val padded: List<HeatCell?> = List(leading) { null } + cells
    val weeks = padded.chunked(7)
    val scroll = rememberScrollState()
    LaunchedEffect(weeks.size) { scroll.scrollTo(scroll.maxValue) }
    Row(Modifier.fillMaxWidth().horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        weeks.forEach { week ->
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                for (i in 0 until 7) HeatSquare(week.getOrNull(i))
            }
        }
    }
}

@Composable
private fun HeatSquare(cell: HeatCell?) {
    val color = when {
        cell == null -> Color.Transparent
        cell.kcal <= 0 -> Color.White.copy(alpha = 0.05f)
        cell.fraction >= 1f -> lerp(FlameHot, Coral, (cell.fraction - 1f).coerceIn(0f, 1f))
        else -> FlameHot.copy(alpha = cell.fraction.coerceIn(0.14f, 1f))
    }
    Box(Modifier.size(13.dp).clip(RoundedCornerShape(3.dp)).background(color))
}

private data class StatTile(val label: String, val value: String)

@Composable
private fun StatRow(a: StatTile, b: StatTile) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTileView(a, Modifier.weight(1f))
        StatTileView(b, Modifier.weight(1f))
    }
}

@Composable
private fun StatTileView(tile: StatTile, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.04f)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(tile.value, style = MaterialTheme.typography.titleLarge, color = Chalk, fontWeight = FontWeight.Bold)
        Text(tile.label, style = MaterialTheme.typography.labelMedium, color = Ash)
    }
}

@Composable
private fun WeekdayPattern(rates: Map<DayOfWeek, Float>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("BY WEEKDAY", style = MaterialTheme.typography.labelSmall, color = FlameHot, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            DayOfWeek.entries.forEach { day ->
                val rate = rates[day] ?: 0f
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.height(44.dp), contentAlignment = Alignment.BottomCenter) {
                        Box(
                            Modifier.width(20.dp).height((6 + 38 * rate).dp).clip(RoundedCornerShape(5.dp))
                                .background(FlameHot.copy(alpha = 0.35f + 0.65f * rate)),
                        )
                    }
                    Text(
                        day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (day == DayOfWeek.SUNDAY) Coral else Ash,
                    )
                }
            }
        }
    }
}
