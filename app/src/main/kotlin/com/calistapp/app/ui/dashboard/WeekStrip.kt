package com.calistapp.app.ui.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.calistapp.app.ui.common.GlowBox
import com.calistapp.app.ui.common.glow
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.AshFaint
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.Coral
import com.calistapp.app.ui.theme.FlameHot
import java.time.LocalDate

/**
 * The weekly calorie strip — no card, short, wide bars. Swipe left/right to step through weeks; tap a
 * day to inspect it; tap the title for the monthly view; tap the number for the steps-vs-exercise
 * split; the "Plan your week" link sits under the total.
 */
@Composable
fun WeekStrip(
    week: WeekState,
    selectedDate: LocalDate?,
    onOpenMonth: () -> Unit,
    onOpenSchedule: () -> Unit,
    onSelectDay: (LocalDate) -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onResetWeek: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showBreakdown by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (total > SWIPE_THRESHOLD) onPreviousWeek()
                        else if (total < -SWIPE_THRESHOLD) onNextWeek()
                    },
                ) { _, dragAmount -> total += dragAmount }
            },
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onOpenMonth).padding(vertical = 2.dp, horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(week.title, style = MaterialTheme.typography.titleMedium, color = Chalk)
                Icon(Icons.Filled.CalendarMonth, "Open month", tint = Ash, modifier = Modifier.size(16.dp))
            }
            Column(horizontalAlignment = Alignment.End) {
                Box {
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = Chalk, fontWeight = FontWeight.Bold)) { append("%,d".format(week.totalKcal)) }
                            withStyle(SpanStyle(color = Ash, fontSize = 11.sp)) { append("  kcal") }
                        },
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.clickable { showBreakdown = !showBreakdown },
                    )
                    if (showBreakdown) {
                        Popup(
                            alignment = Alignment.TopEnd,
                            offset = IntOffset(0, 78),
                            properties = PopupProperties(focusable = false, dismissOnClickOutside = false),
                            onDismissRequest = { showBreakdown = false },
                        ) { CalorieBreakdown(week.stepKcal, week.workoutKcal, onDismiss = { showBreakdown = false }) }
                    }
                }
                Text(
                    "Plan your week ›",
                    style = MaterialTheme.typography.labelSmall,
                    color = FlameHot,
                    modifier = Modifier.padding(top = 3.dp).clickable(onClick = onOpenSchedule),
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            week.days.forEach { day ->
                DayColumn(day, week.maxKcal, isSelected = day.date == selectedDate, onClick = { onSelectDay(day.date) })
            }
        }

        if (!week.isCurrentWeek) {
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Row(
                    Modifier
                        .glow(FlameHot, spread = 12.dp, alpha = 0.38f)
                        .clip(CircleShape)
                        .background(emberBrush)
                        .clickable(onClick = onResetWeek)
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Filled.Today, contentDescription = null, tint = OnOrange, modifier = Modifier.size(15.dp))
                    Text("This week", style = MaterialTheme.typography.labelMedium, color = OnOrange, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun DayColumn(day: DayCell, maxKcal: Int, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (isSelected) Modifier
                    .background(Color.White.copy(alpha = 0.07f))
                    .border(1.dp, Chalk.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                else Modifier,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val frac = (day.kcal.toFloat() / maxKcal).coerceIn(0f, 1f)
        val animated by animateFloatAsState(frac, tween(600), label = "bar")
        val hasBurn = day.kcal > 0
        val barShape = RoundedCornerShape(8.dp)
        Box(Modifier.height(56.dp), contentAlignment = Alignment.BottomCenter) {
            val barHeight = if (hasBurn) 8.dp + 46.dp * animated else 6.dp
            val barBrush = if (hasBurn) emberBrush else SolidColor(Color.White.copy(alpha = 0.09f))
            if (day.isToday && hasBurn) {
                GlowBox(FlameHot, barShape, glowRadius = 9.dp, glowAlpha = 0.5f, modifier = Modifier.width(24.dp).height(barHeight)) {
                    Box(Modifier.matchParentSize().clip(barShape).background(barBrush))
                }
            } else {
                Box(Modifier.width(24.dp).height(barHeight).clip(barShape).background(barBrush))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            day.letter,
            style = MaterialTheme.typography.labelMedium,
            color = when {
                day.isSunday -> Coral
                day.isToday -> FlameHot
                else -> Ash
            },
            fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Medium,
        )
        Spacer(Modifier.height(6.dp))
        val dotColor = when {
            day.trained -> FlameHot
            day.planned -> Ash
            else -> AshFaint.copy(alpha = 0.4f)
        }
        Box(
            Modifier
                .then(if (day.trained) Modifier.glow(FlameHot, spread = 6.dp, alpha = 0.45f) else Modifier)
                .size(5.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
    }
}

/** The tap-the-number popup: how much of the week's burn came from steps vs. exercise. */
@Composable
private fun CalorieBreakdown(stepKcal: Int, workoutKcal: Int, onDismiss: () -> Unit) {
    val scale = remember { Animatable(0.86f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, tween(180)) }
    Column(
        Modifier
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value; alpha = scale.value }
            .width(190.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CardSurface)
            .glow(Color.Black, spread = 24.dp, alpha = 0.5f)
            .clickable(onClick = onDismiss)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val total = (stepKcal + workoutKcal).coerceAtLeast(1)
        BreakdownRow("Steps", stepKcal, stepKcal.toFloat() / total, FlameHot)
        BreakdownRow("Exercise", workoutKcal, workoutKcal.toFloat() / total, Coral)
    }
}

@Composable
private fun BreakdownRow(label: String, kcal: Int, fraction: Float, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = Ash)
            Text("$kcal kcal", style = MaterialTheme.typography.labelLarge, color = Chalk, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f))) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp).clip(CircleShape).background(color))
        }
    }
}

private const val SWIPE_THRESHOLD = 60f
