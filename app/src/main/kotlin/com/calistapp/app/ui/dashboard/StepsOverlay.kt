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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.calistapp.app.ui.common.GlowIcon
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.FlameHot
import kotlin.math.roundToInt

/**
 * The steps popup: a 30-day bar chart against your goal line, plus trend/context tiles (today vs
 * goal, 7-day average, best day, step-goal streak, distance and active calories). Animated in.
 */
@Composable
fun StepsInsightsOverlay(visible: Boolean, data: StepsInsights, onDismiss: () -> Unit) {
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
                StepsCard(data)
            }
        }
    }
}

@Composable
private fun StepsCard(data: StepsInsights) {
    val maxHeight = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.88f).dp
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlowIcon(Icons.AutoMirrored.Filled.DirectionsWalk, null, FlameHot, size = 28.dp, glowRadius = 7.dp, glowAlpha = 0.55f)
            Column {
                Text("%,d steps today".format(data.today), style = MaterialTheme.typography.headlineSmall, color = Chalk)
                Text("Goal %,d".format(data.goal), style = MaterialTheme.typography.bodySmall, color = Ash)
            }
        }

        StepChart(data)

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile("7-day average", "%,d".format(data.avg7d), Modifier.weight(1f))
                Tile("Step-goal streak", "${data.stepGoalStreak} d", Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile("Best day", "%,d".format(data.bestDaySteps), Modifier.weight(1f))
                Tile("Distance today", "%.1f km".format(data.distanceKm), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile("Active calories", "${data.activeKcal} kcal", Modifier.weight(1f))
                Tile("of goal", "${((data.today.toFloat() / data.goal.coerceAtLeast(1)) * 100).roundToInt()}%", Modifier.weight(1f))
            }
        }

        val tip = stepTip(data)
        if (tip != null) {
            Text(tip, style = MaterialTheme.typography.bodyMedium, color = Ash)
        }
    }
}

@Composable
private fun StepChart(data: StepsInsights) {
    val max = (data.last30.maxOfOrNull { it.steps } ?: 0).coerceAtLeast(data.goal).coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("LAST 30 DAYS", style = MaterialTheme.typography.labelSmall, color = FlameHot, fontWeight = FontWeight.Bold)
        Box(Modifier.fillMaxWidth().height(120.dp)) {
            // The goal line.
            val goalFrac = (data.goal.toFloat() / max).coerceIn(0f, 1f)
            Box(
                Modifier.fillMaxWidth().padding(top = (120 * (1f - goalFrac)).dp).height(1.dp)
                    .background(Chalk.copy(alpha = 0.25f)),
            )
            Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
                data.last30.forEach { day ->
                    val frac = (day.steps.toFloat() / max).coerceIn(0f, 1f)
                    val hit = day.steps >= data.goal
                    Box(
                        Modifier.weight(1f).height((4 + 116 * frac).dp).clip(RoundedCornerShape(2.dp))
                            .background(if (hit) SolidColor(FlameHot) else SolidColor(FlameHot.copy(alpha = 0.28f))),
                    )
                }
            }
        }
    }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.04f)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = Chalk, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = Ash)
    }
}

private fun stepTip(data: StepsInsights): String? = when {
    data.avg7d == 0 -> null
    data.today >= data.goal -> "Goal smashed — nice. You're averaging %,d a day this week.".format(data.avg7d)
    data.today >= data.avg7d -> "Ahead of your usual pace — %,d to go for the goal.".format((data.goal - data.today).coerceAtLeast(0))
    else -> "%,d steps short of your daily goal, and a little behind your usual. A short walk closes the gap.".format((data.goal - data.today).coerceAtLeast(0))
}
