package com.calistapp.app.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.os.Build
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calistapp.app.ui.common.glow
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.AshFaint
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.Coral
import com.calistapp.app.ui.theme.FlameHot
import java.time.LocalDate

private val DAY_HEADERS = listOf("M", "T", "W", "T", "F", "S", "S")
private val CAN_BLUR = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** The orange (goal) + red (over-goal) progress arcs of one day cell. Shared by the sharp + glow passes. */
private fun DrawScope.drawRingArcs(cell: MonthDayCell, strokeWidth: Dp) {
    val sw = strokeWidth.toPx()
    val inset = sw / 2f
    val arcSize = Size(size.width - sw, size.height - sw)
    val tl = Offset(inset, inset)
    if (cell.orangeFraction > 0f) {
        drawArc(FlameHot, -90f, 360f * cell.orangeFraction, false, tl, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
    }
    if (cell.redFraction > 0f) {
        drawArc(Coral, -90f, 360f * cell.redFraction, false, tl, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
    }
}

/**
 * The near-fullscreen monthly calendar. Each date wears a ring — orange for the day's calorie goal,
 * a red ring on top once you go past it (full red = twice the goal) — plus dots for planned and
 * completed workouts, all glowing. Scales in over a dimmed backdrop; swipe the arrows for other
 * months. Stats for the month sit at the bottom.
 */
@Composable
fun MonthOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    onSelectDay: (LocalDate) -> Unit,
    viewModel: MonthViewModel = hiltViewModel(),
) {
    if (!visible) return
    val month by viewModel.monthView.collectAsStateWithLifecycle()

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        var appear by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
        androidx.compose.runtime.LaunchedEffect(Unit) { appear = true }
        val scrim by androidx.compose.animation.animateColorAsState(
            if (appear) Color.Black.copy(alpha = 0.66f) else Color.Transparent,
            tween(200),
            label = "scrim",
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(scrim)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visible = appear,
                enter = fadeIn(tween(220)) + scaleIn(tween(260), initialScale = 0.92f),
                exit = fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 0.92f),
            ) {
                MonthCard(month, viewModel::previousMonth, viewModel::nextMonth, viewModel::resetToCurrentMonth, onDismiss, onSelectDay)
            }
        }
    }
}

@Composable
private fun MonthCard(month: MonthView, onPrev: () -> Unit, onNext: () -> Unit, onReset: () -> Unit, onClose: () -> Unit, onSelectDay: (LocalDate) -> Unit) {
    val maxHeight = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.9f).dp
    Box(
        Modifier
            .fillMaxWidth(0.94f)
            .heightIn(max = maxHeight)
            .padding(vertical = 24.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(CardSurface)
            .border(1.dp, CardBorder, RoundedCornerShape(28.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Header: prev · title · next, and a close.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrev) { Icon(Icons.Filled.ChevronLeft, "Previous month", tint = Chalk) }
                Text(
                    month.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Chalk,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                IconButton(onClick = onNext, enabled = !month.isCurrentMonth) {
                    Icon(Icons.Filled.ChevronRight, "Next month", tint = if (month.isCurrentMonth) AshFaint else Chalk)
                }
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close", tint = Ash) }
            }

            // "This month" — jump back when browsing other months.
            if (!month.isCurrentMonth) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Row(
                        Modifier
                            .glow(FlameHot, spread = 12.dp, alpha = 0.36f)
                            .clip(CircleShape)
                            .background(emberBrush)
                            .clickable(onClick = onReset)
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Filled.Today, contentDescription = null, tint = OnOrange, modifier = Modifier.size(15.dp))
                        Text("This month", style = MaterialTheme.typography.labelMedium, color = OnOrange, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Weekday header.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DAY_HEADERS.forEachIndexed { i, d ->
                    Text(
                        d,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (i == 6) Coral else Ash,
                        modifier = Modifier.width(40.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }

            // The grid, in rows of seven.
            val cells: List<MonthDayCell?> = List(month.leadingBlanks) { null } + month.days
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    for (i in 0 until 7) DayCellView(week.getOrNull(i), onSelectDay)
                }
            }

            Spacer(Modifier.height(2.dp))
            MonthStats(month)
        }
    }
}

@Composable
private fun DayCellView(cell: MonthDayCell?, onSelectDay: (LocalDate) -> Unit) {
    Column(
        Modifier
            .width(40.dp)
            // No .clip() — it slices the ring's glow off the sides. Null-indication click needs none.
            .then(
                if (cell != null && !cell.isFuture) {
                    Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelectDay(cell.date) }
                } else {
                    Modifier
                },
            )
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (cell == null) {
            Spacer(Modifier.size(40.dp))
            Spacer(Modifier.size(5.dp))
            return@Column
        }
        val hasFill = cell.orangeFraction > 0f || cell.redFraction > 0f
        Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
            // The ring glows itself: a blurred copy of its own arcs sits behind the sharp ones (API 31+).
            if (CAN_BLUR && hasFill) {
                Canvas(Modifier.size(38.dp).blur(6.dp, BlurredEdgeTreatment.Unbounded)) { drawRingArcs(cell, 3.dp) }
            }
            // Today: a tinted disc behind the number so it clearly stands out from the other days.
            if (cell.isToday) {
                Box(Modifier.size(24.dp).clip(CircleShape).background(FlameHot.copy(alpha = 0.18f)))
            }
            Canvas(Modifier.size(38.dp)) {
                val sw = 3.dp.toPx()
                val inset = sw / 2f
                val arcSize = Size(size.width - sw, size.height - sw)
                val tl = Offset(inset, inset)
                drawArc(Color.White.copy(alpha = 0.08f), 0f, 360f, false, tl, arcSize, style = Stroke(sw))
                drawRingArcs(cell, 3.dp)
            }
            Text(
                "${cell.date.dayOfMonth}",
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    cell.isToday -> FlameHot
                    cell.isFuture -> AshFaint
                    else -> Chalk
                },
                fontWeight = if (cell.isToday) FontWeight.Bold else FontWeight.Normal,
            )
        }
        // Workout dot.
        when {
            cell.trained -> Box(Modifier.glow(FlameHot, spread = 5.dp, alpha = 0.45f).size(5.dp).clip(CircleShape).background(FlameHot))
            cell.planned -> Box(Modifier.size(5.dp).clip(CircleShape).background(Ash))
            else -> Spacer(Modifier.size(5.dp))
        }
    }
}

@Composable
private fun MonthStats(month: MonthView) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.04f)).padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Stat("%,d".format(month.totalBurned), "kcal burned")
        Stat("%,d".format(month.avgSteps), "avg steps")
        Stat("${month.exercisesLogged}", "workouts")
        Stat("${month.daysHitGoal}", "goals hit")
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = Chalk, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Ash)
    }
}
