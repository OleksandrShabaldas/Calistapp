package com.calistapp.app.ui.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import com.calistapp.app.ui.common.GestureMotion
import com.calistapp.app.ui.common.GlowBox
import com.calistapp.app.ui.common.rememberReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.calistapp.app.ui.common.glow
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.AshFaint
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.Coral
import com.calistapp.app.ui.theme.FlameHot
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.min

/**
 * The weekly calorie strip — no card, short, wide bars. Swipe left/right to step through weeks; tap a
 * day to inspect it; tap the title for the monthly view; tap the number for the steps-vs-exercise
 * split; the "Plan your week" link sits under the total.
 *
 * The swipe follows the finger: the week slides 1:1 under it (rubber-banding where there's no week to
 * go to — nothing after this week), and on release a far-enough drag OR a quick flick carries it off in
 * the direction it was moving, at the finger's speed, while the next week slides in behind it. It used
 * to sit still until the finger lifted and then swap. Any week change — the "This week" button, picking
 * a day in another week — slides in from the side the new week lies on.
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

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val reduceMotion = rememberReduceMotion()
    val flingPx = GestureMotion.flingPx(density)
    val offset = remember { Animatable(0f) }
    var widthPx by remember { mutableFloatStateOf(1f) }
    var raw by remember { mutableFloatStateOf(0f) }
    // +1 = the older week is coming in from the left, -1 = the newer one from the right.
    var entering by remember { mutableIntStateOf(0) }
    val canGoNext = !week.isCurrentWeek
    val prev by rememberUpdatedState(onPreviousWeek)
    val next by rememberUpdatedState(onNextWeek)

    // A new week has arrived: slide it in from the side it lies on. A swipe already knows the side;
    // anything else (the "This week" button, a day picked in the month) compares the dates.
    val weekStart = week.days.firstOrNull()?.date
    var shownStart by remember { mutableStateOf(weekStart) }
    LaunchedEffect(weekStart) {
        val before = shownStart
        shownStart = weekStart
        if (before == null || weekStart == null || before == weekStart) return@LaunchedEffect
        val dir = if (entering != 0) entering else if (weekStart < before) 1 else -1
        entering = 0
        offset.snapTo(dir * -widthPx * 0.35f)
        offset.animateTo(0f, GestureMotion.release(reduceMotion, bounce = 0.12f, durationS = 0.45f))
    }

    val drag = rememberDraggableState { delta ->
        raw += delta
        // Free inside one width either way, elastic where there's no week to go to.
        val min = if (canGoNext) -widthPx else 0f
        scope.launch { offset.snapTo(GestureMotion.elastic(raw, min, widthPx)) }
    }

    Column(
        modifier
            .fillMaxWidth()
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .draggable(
                drag,
                Orientation.Horizontal,
                startDragImmediately = offset.isRunning,
                onDragStarted = { raw = offset.value; offset.stop() },
                onDragStopped = { v ->
                    val dir = when {
                        (v > flingPx || offset.value > widthPx * 0.3f) && v > -flingPx -> 1
                        (v < -flingPx || offset.value < -widthPx * 0.3f) && v < flingPx && canGoNext -> -1
                        else -> 0
                    }
                    if (dir == 0) {
                        offset.animateTo(0f, GestureMotion.snapBack(reduceMotion), initialVelocity = v)
                    } else {
                        // Off it goes at the finger's speed (no bounce on the way out), then the swap.
                        offset.animateTo(dir * widthPx, GestureMotion.spring(0.3f, 0f), initialVelocity = v)
                        entering = dir
                        if (dir > 0) prev() else next()
                        // Nothing came back (already at the oldest week): bring this one back in.
                        scope.launch {
                            delay(600)
                            if (entering != 0) {
                                entering = 0
                                offset.snapTo(-dir * widthPx * 0.35f)
                                offset.animateTo(0f, GestureMotion.snapBack(reduceMotion))
                            }
                        }
                    }
                },
            )
            // Tapping any empty part of the widget opens the month; the day columns, the total and the
            // plan link have their own taps and consume theirs first.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onOpenMonth)
            // After the drag detector, so moving the content doesn't move the finger's frame of reference.
            .graphicsLayer {
                translationX = offset.value
                alpha = 1f - 0.65f * (kotlin.math.abs(offset.value) / widthPx).coerceIn(0f, 1f)
            },
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Row(
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

        Box(Modifier.fillMaxWidth()) {
            // Each day's goal as a fraction of the strip's scale, animated so a swipe to another week
            // (or a goal that changes) morphs the line rather than snapping it.
            val goals = week.days.mapIndexed { i, day ->
                val target = if (week.maxKcal > 0) (day.targetKcal.toFloat() / week.maxKcal).coerceIn(0f, 1f) else 0f
                // Keyed per day: the list grows from empty to seven as the week loads, and unkeyed animation
                // state in a loop of changing length gets mis-paired by Compose (it crashed on first load).
                key(i) { animateFloatAsState(target, tween(600), label = "goal$i").value }
            }
            val reveal = remember { Animatable(0f) }
            LaunchedEffect(Unit) { reveal.animateTo(1f, tween(800, delayMillis = 200)) }
            Row(
                Modifier.fillMaxWidth().drawBehind { drawGoalLine(goals, week.days.map { it.targetKcal > 0 }, reveal.value) },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                week.days.forEach { day ->
                    DayColumn(
                        day,
                        week.maxKcal,
                        isSelected = day.date == selectedDate,
                        onClick = { onSelectDay(day.date) },
                    )
                }
            }
            goals.lastOrNull()?.takeIf { (week.days.lastOrNull()?.targetKcal ?: 0) > 0 }?.let { last ->
                Text(
                    "GOAL",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp, letterSpacing = 1.4.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = Chalk.copy(alpha = 0.55f * reveal.value),
                    modifier = Modifier.align(Alignment.TopEnd).offset(y = goalLineY(last) - 14.dp).padding(end = 2.dp),
                )
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
    val selShape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            // No .clip() — it would slice the today bar's glow off the sides (the bug). The selection
            // highlight uses shaped background/border, which clip only themselves.
            .then(
                if (isSelected) Modifier
                    .background(Color.White.copy(alpha = 0.07f), selShape)
                    .border(1.dp, Chalk.copy(alpha = 0.35f), selShape)
                else Modifier,
            )
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
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
                GlowBox(FlameHot, barShape, glowRadius = 14.dp, glowAlpha = 0.85f, modifier = Modifier.width(24.dp).height(barHeight)) {
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


// Bar geometry shared with [DayColumn]: a bar's box ends 61dp down its column (5dp padding + 56dp box),
// and a bar grows from an 8dp base by up to 46dp. Columns are 32dp wide (24dp bar + 4dp each side).
private val BarBottom = 61.dp
private val BarBase = 8.dp
private val BarScale = 46.dp
private val ColumnWidth = 32.dp

/** Where the goal line sits for a goal at [fraction] of the strip's scale: where a just-on-goal bar ends. */
private fun goalLineY(fraction: Float): Dp = BarBottom - (BarBase + BarScale * fraction)

/**
 * The daily goal as one hairline across the whole strip — replacing a 2dp dash per day — drawn
 * *behind* the bars, so a day that clears its goal visibly breaks through it. On a day with a reduced
 * goal it steps to that day's level with soft corners; the ends fade out rather than stopping hard.
 * [reveal] draws it in from the left as the strip appears.
 */
private fun DrawScope.drawGoalLine(goals: List<Float>, hasGoal: List<Boolean>, reveal: Float) {
    if (goals.isEmpty() || hasGoal.none { it } || reveal <= 0f) return
    val col = ColumnWidth.toPx()
    val step = (size.width - col) / (goals.size - 1).coerceAtLeast(1)
    val xs = goals.indices.map { col / 2 + it * step }
    val ys = goals.map { goalLineY(it).toPx() }
    val corner = 4.dp.toPx()
    val path = Path().apply {
        moveTo(0f, ys[0])
        for (i in 0 until goals.lastIndex) {
            val y0 = ys[i]
            val y1 = ys[i + 1]
            if (abs(y1 - y0) < 0.5f) continue
            val mid = (xs[i] + xs[i + 1]) / 2
            val dir = if (y1 > y0) 1f else -1f
            val r = min(corner, abs(y1 - y0) / 2)
            lineTo(mid - r, y0)
            quadraticBezierTo(mid, y0, mid, y0 + dir * r)
            lineTo(mid, y1 - dir * r)
            quadraticBezierTo(mid, y1, mid + r, y1)
        }
        lineTo(size.width, ys.last())
    }
    val brush = Brush.horizontalGradient(
        0f to Chalk.copy(alpha = 0f),
        0.07f to Chalk.copy(alpha = 0.30f),
        0.93f to Chalk.copy(alpha = 0.30f),
        1f to Chalk.copy(alpha = 0f),
        startX = 0f,
        endX = size.width,
    )
    clipRect(right = size.width * reveal) { drawPath(path, brush, style = Stroke(1.dp.toPx())) }
}
