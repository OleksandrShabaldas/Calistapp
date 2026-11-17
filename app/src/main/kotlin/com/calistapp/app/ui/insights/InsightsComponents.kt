package com.calistapp.app.ui.insights

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calistapp.app.ui.common.EaseOutStrong
import com.calistapp.app.ui.common.GlowBox
import com.calistapp.app.ui.common.HelpDot
import com.calistapp.app.ui.common.pressScale
import com.calistapp.app.ui.common.rememberInteraction
import com.calistapp.app.ui.common.rememberReduceMotion
import com.calistapp.app.ui.common.sharedSurface
import com.calistapp.app.ui.theme.Amber
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.CardFlat
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.Display
import com.calistapp.app.ui.theme.Eyebrow
import com.calistapp.app.ui.theme.FlameGlow
import com.calistapp.app.ui.theme.FlameHot
import com.calistapp.app.ui.theme.Sky
import com.calistapp.core.insights.DayMark
import com.calistapp.core.insights.InsightChart
import com.calistapp.core.insights.InsightsPeriod
import com.calistapp.core.insights.InsightsReport
import com.calistapp.core.insights.Move
import com.calistapp.core.insights.MoveKind
import com.calistapp.core.insights.MovementGroup
import com.calistapp.core.insights.Question
import com.calistapp.core.insights.QuestionVerdict
import com.calistapp.core.insights.Verdict
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

// ---------------------------------------------------------------------------------------------- style
internal val CoverInk = Color(0xFF17110D)
internal val Hairline = Color.White.copy(alpha = 0.07f)
private val Track = Color(0xFF2A2A2E)
private val BarIdle = Color(0xFF4A4A4F)
private val Ink = Color(0xFF1A0D05)

/** How a verdict is drawn everywhere it appears: bar segment, card dash, pill, chart. */
internal data class VerdictLook(val color: Color, val text: Color, val brush: Brush, val glow: Boolean)

internal fun lookOf(q: QuestionVerdict): VerdictLook = when (q.verdict) {
    Verdict.GOOD -> VerdictLook(FlameHot, FlameGlow, Brush.horizontalGradient(listOf(FlameGlow, FlameHot)), true)
    Verdict.MIXED ->
        if (q.pill == "MOSTLY") VerdictLook(FlameHot.copy(alpha = 0.65f), FlameGlow, Brush.horizontalGradient(listOf(FlameGlow.copy(alpha = 0.65f), FlameHot.copy(alpha = 0.65f))), false)
        else VerdictLook(Amber, Amber, SolidColor(Amber), true)
    Verdict.WATCH -> VerdictLook(Sky, Sky, SolidColor(Sky), true)
    Verdict.NOT_ENOUGH_DATA -> VerdictLook(Track, Ash, SolidColor(Track), false)
}

internal val HowMeasured: Map<Question, String> = mapOf(
    Question.STRONGER to "Your best set of each movement this period against your best before it. Reps for bodyweight moves, added kilos for weighted ones, seconds for holds. The same number three sessions running counts as stuck.",
    Question.FITTER to "Heart-rate recovery: how far your heart rate drops in the minute after a hard set, averaged per session, against the period before. Faster recovery is one of the clearest signs of fitness.",
    Question.BALANCED to "Working sets split into push, pull, core and legs by each exercise's main muscles. Push, pull and legs should each get about a fifth of your sets or more.",
    Question.CONSISTENT to "Planned days come from your schedule, counted from the day you first set one up. Without a schedule, it counts how many weeks had a session.",
    Question.RECOVERING to "How hard you rated each session against how hard your heart rate says it was, compared with your own usual gap. Several sessions in a row that feel harder than they measure are an early sign of fatigue.",
)

private val MonoSmall = Eyebrow.copy(fontSize = 10.sp, letterSpacing = 1.sp)

// ---------------------------------------------------------------------------------------------- period tabs
/** Week · 4 weeks · Quarter · Year, with an underline that slides to the picked tab. */
@Composable
internal fun PeriodTabs(selected: InsightsPeriod, onSelect: (InsightsPeriod) -> Unit, onPastRecaps: () -> Unit) {
    val density = LocalDensity.current
    val bounds = remember { mutableStateMapOf<InsightsPeriod, Pair<Dp, Dp>>() }
    val target = bounds[selected]
    val x by animateDpAsState(target?.first ?: 0.dp, spring(dampingRatio = 1f, stiffness = 500f), label = "tabX")
    val w by animateDpAsState(target?.second ?: 0.dp, spring(dampingRatio = 1f, stiffness = 500f), label = "tabW")
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                InsightsPeriod.entries.forEach { p ->
                    val on = p == selected
                    val color by animateColorAsState(if (on) Chalk else Ash, tween(180), label = "tabColor")
                    Text(
                        p.label,
                        color = color,
                        fontSize = 14.sp,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier
                            .onGloballyPositioned { c ->
                                with(density) { bounds[p] = c.positionInParent().x.toDp() to c.size.width.toDp() }
                            }
                            .clickable(interactionSource = rememberInteraction(), indication = null) { onSelect(p) }
                            .padding(vertical = 10.dp),
                    )
                }
            }
            if (target != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = x)
                        .width(w)
                        .height(2.dp)
                        .background(FlameHot, RoundedCornerShape(1.dp)),
                )
            }
        }
        Box(
            Modifier
                .size(44.dp)
                .clickable(interactionSource = rememberInteraction(), indication = null, onClick = onPastRecaps),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.History, contentDescription = "Past recaps", tint = Chalk, modifier = Modifier.size(20.dp)) }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
}

// ---------------------------------------------------------------------------------------------- cover
@Composable
internal fun CoverCard(report: InsightsReport, onPlay: () -> Unit, onShare: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    val fmt = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
    val cards = 2 + report.questions.count { it.verdict != Verdict.NOT_ENOUGH_DATA }
    Column(
        Modifier
            .fillMaxWidth()
            .background(CoverInk, shape)
            .drawBehind {
                drawRoundRect(
                    Brush.radialGradient(listOf(FlameHot.copy(alpha = 0.30f), Color.Transparent), center = Offset(size.width, size.height), radius = size.width * 0.9f),
                    cornerRadius = CornerRadius(26.dp.toPx()),
                )
            }
            .border(1.dp, Color.White.copy(alpha = 0.09f), shape)
            .padding(18.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                "ISSUE ${report.issue} · ${fmt.format(report.window.start).uppercase()} – ${fmt.format(report.window.endInclusive).uppercase()}",
                style = MonoSmall.copy(letterSpacing = 1.4.sp), color = Ash, modifier = Modifier.weight(1f),
            )
            Text("$cards CARDS", style = MonoSmall.copy(letterSpacing = 1.4.sp), color = Ash)
        }
        AnimatedContent(
            targetState = report.headline,
            transitionSpec = {
                (fadeIn(tween(240, easing = EaseOutStrong)) + slideInVertically(tween(320, easing = EaseOutStrong)) { it / 8 }) togetherWith fadeOut(tween(120))
            },
            label = "headline",
        ) { lines -> Headline(lines) }
        VerdictBar(report.questions, Modifier.padding(top = 22.dp))
        Row(Modifier.padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PrimaryPill("Play your recap", Icons.Rounded.PlayArrow, onPlay, Modifier.weight(1f))
            val i = rememberInteraction()
            Box(
                Modifier
                    .pressScale(i)
                    .size(48.dp)
                    .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                    .clickable(interactionSource = i, indication = null, onClick = onShare),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.IosShare, contentDescription = "Share recap", tint = Chalk, modifier = Modifier.size(18.dp)) }
        }
    }
}

/** First sentence in white with its last word lit; the second in white as the counterpoint. */
@Composable
private fun Headline(lines: List<String>) {
    val first = lines.firstOrNull().orEmpty()
    val cut = first.trimEnd().lastIndexOf(' ')
    val text = buildAnnotatedString {
        if (cut > 0) {
            append(first.substring(0, cut + 1))
            withStyle(SpanStyle(color = FlameHot, shadow = Shadow(FlameHot.copy(alpha = 0.5f), Offset.Zero, 22f))) { append(first.substring(cut + 1)) }
        } else {
            withStyle(SpanStyle(color = FlameHot, shadow = Shadow(FlameHot.copy(alpha = 0.5f), Offset.Zero, 22f))) { append(first) }
        }
        lines.drop(1).forEach { append(" "); append(it) }
    }
    Text(
        text,
        modifier = Modifier.padding(top = 12.dp),
        style = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 35.sp, letterSpacing = (-1).sp, color = Chalk),
    )
}

/** Five segments, one per question, no verdict words: the colour is the verdict. */
@Composable
internal fun VerdictBar(questions: List<QuestionVerdict>, modifier: Modifier = Modifier) {
    val label = questions.joinToString(", ") { "${it.question.short} ${it.pill.lowercase()}" }
    Column(modifier.semantics { contentDescription = "Verdicts: $label" }) {
        Row(Modifier.fillMaxWidth().height(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            questions.forEach { q ->
                val look = lookOf(q)
                val color by animateColorAsState(look.color, tween(320), label = "segment")
                val seg = Modifier.weight(1f).height(6.dp)
                if (look.glow) {
                    GlowBox(color, RoundedCornerShape(3.dp), glowRadius = 6.dp, glowAlpha = 0.55f, modifier = seg) {
                        Box(Modifier.fillMaxWidth().height(6.dp).background(look.brush, RoundedCornerShape(3.dp)))
                    }
                } else {
                    Box(seg.background(look.brush, RoundedCornerShape(3.dp)))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            questions.forEach { q ->
                Text(q.question.short, Modifier.weight(1f), color = Chalk, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** The glowing orange capsule used for the one primary action on a surface. */
@Composable
internal fun PrimaryPill(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp = 48.dp, fontSize: Int = 15) {
    val i = rememberInteraction()
    GlowBox(FlameHot, RoundedCornerShape(50), glowRadius = 14.dp, glowAlpha = 0.42f, modifier = modifier.pressScale(i)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(height)
                .background(Brush.verticalGradient(listOf(FlameGlow, FlameHot)), RoundedCornerShape(50))
                .clickable(interactionSource = i, indication = null, onClick = onClick)
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let { Icon(it, contentDescription = null, tint = Ink, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)) }
            Text(text, color = Ink, fontSize = fontSize.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------------------------------------- question card
@Composable
internal fun QuestionCard(q: QuestionVerdict, onOpen: () -> Unit, onMove: (Move) -> Unit) {
    val look = lookOf(q)
    val shape = RoundedCornerShape(22.dp)
    val press = rememberInteraction()
    Column(
        Modifier
            .fillMaxWidth()
            .pressScale(press, 0.985f)
            .sharedSurface("insight-${q.question}")
            .background(CardFlat, shape)
            .border(1.dp, Hairline, shape)
            .clickable(interactionSource = press, indication = null, onClick = onOpen),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dash(look)
                Spacer(Modifier.width(10.dp))
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = FlameHot)) { append(q.question.number) }
                        append(" · ${q.question.title.uppercase()}")
                    },
                    style = MonoSmall, color = Ash, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Pill(q.pill, look)
                Spacer(Modifier.width(6.dp))
                HelpDot(HowMeasured.getValue(q.question), contentDescription = "How this is measured")
            }
            AnimatedContent(
                targetState = q.fact,
                transitionSpec = { fadeIn(tween(220, easing = EaseOutStrong)) togetherWith fadeOut(tween(120)) },
                label = "fact",
            ) { fact ->
                Text(
                    fact,
                    modifier = Modifier.padding(top = 12.dp),
                    style = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = (-0.5).sp, color = if (q.verdict == Verdict.NOT_ENOUGH_DATA) Ash else Chalk),
                )
            }
        }
        QuestionChart(q, look)
        q.move?.let { MoveStrip(it, look, onMove) }
    }
}

@Composable
private fun Dash(look: VerdictLook) {
    val m = Modifier.width(22.dp).height(5.dp)
    if (look.glow) {
        GlowBox(look.color, RoundedCornerShape(3.dp), glowRadius = 5.dp, glowAlpha = 0.55f, modifier = m) {
            Box(Modifier.width(22.dp).height(5.dp).background(look.brush, RoundedCornerShape(3.dp)))
        }
    } else Box(m.background(look.brush, RoundedCornerShape(3.dp)))
}

@Composable
internal fun Pill(text: String, look: VerdictLook) {
    val c = if (look.color == Track) Ash else look.text
    Text(
        text,
        style = MonoSmall.copy(fontSize = 9.5.sp),
        color = c,
        maxLines = 1,
        modifier = Modifier
            .background(c.copy(alpha = 0.12f), RoundedCornerShape(50))
            .border(1.dp, c.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

/** The action at the foot of a card. A one-tap action gets a button; the rest are a row with a chevron. */
@Composable
internal fun MoveStrip(move: Move, look: VerdictLook, onMove: (Move) -> Unit) {
    val press = rememberInteraction()
    val actionable = move.action != null
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind { drawLine(Hairline, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
            .clickable(interactionSource = press, indication = null, enabled = actionable) { onMove(move) }
            .padding(start = 16.dp, end = if (move.kind == MoveKind.PLAN_GROUP_DAY) 12.dp else 16.dp, top = 13.dp, bottom = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(iconFor(move.kind), contentDescription = null, tint = if (move.kind == MoveKind.EASY_DAY) Sky else FlameGlow, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(move.title, color = Chalk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (move.detail.isNotBlank()) Text(move.detail, color = Ash, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
        if (move.kind == MoveKind.PLAN_GROUP_DAY && move.action != null) {
            Spacer(Modifier.width(10.dp))
            PrimaryPill(move.action!!, null, { onMove(move) }, Modifier.width(96.dp), height = 36.dp, fontSize = 13)
        } else if (actionable) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = Ash, modifier = Modifier.size(18.dp))
        }
    }
}

private fun iconFor(kind: MoveKind) = when (kind) {
    MoveKind.ADD_LOAD -> Icons.Rounded.FitnessCenter
    MoveKind.UNSTICK -> Icons.Rounded.Autorenew
    MoveKind.PLAN_GROUP_DAY -> Icons.Rounded.Add
    MoveKind.KEEP_DOING -> Icons.Rounded.Check
    MoveKind.EASY_CIRCUIT -> Icons.Rounded.MonitorHeart
    MoveKind.SHORTEN_DAY, MoveKind.PLAN_WEEK -> Icons.Rounded.CalendarMonth
    MoveKind.EASY_DAY -> Icons.Rounded.Bedtime
}

// ---------------------------------------------------------------------------------------------- charts
@Composable
internal fun QuestionChart(q: QuestionVerdict, look: VerdictLook, chart: InsightChart = q.chart, trendHeight: Dp = 140.dp) {
    val ink = if (look.color == Track) Ash else look.color.copy(alpha = 1f)
    when (val c = chart) {
        is InsightChart.Trend -> if (c.points.size >= 2) TrendChart(c, ink, trendHeight) else Spacer(Modifier.height(16.dp))
        is InsightChart.Split -> SplitChart(c, ink, q.move?.group)
        is InsightChart.Calendar -> WeekdayChart(c.days, ink)
        is InsightChart.EffortGap -> EffortChart(c, ink)
        InsightChart.None -> Spacer(Modifier.height(16.dp))
    }
}

/** A line to the card's edges with a soft fill, the latest point lit, the lowest and highest labelled. */
@Composable
internal fun TrendChart(chart: InsightChart.Trend, color: Color, height: Dp = 140.dp) {
    val reduce = rememberReduceMotion()
    val progress = remember(chart.points) { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(chart.points) { if (!reduce) progress.animateTo(1f, tween(700, easing = EaseOutStrong)) }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .height(height)
            .semantics { contentDescription = "Trend from ${chart.lowLabel} to ${chart.highLabel}" },
    ) {
        val pts = chart.points
        val t0 = pts.first().atMs.toDouble(); val t1 = max(pts.last().atMs.toDouble(), t0 + 1)
        val lo = pts.minOf { it.value }; val hi = pts.maxOf { it.value }
        val span = (hi - lo).takeIf { it > 0 } ?: 1.0
        Canvas(Modifier.matchParentSize()) {
            val right = size.width * 0.92f
            fun x(t: Long) = ((t - t0) / (t1 - t0) * right).toFloat()
            fun y(v: Double) = (size.height * (0.88f - 0.76f * ((v - lo) / span).toFloat()))
            val line = monotonePath(pts.map { x(it.atMs) }, pts.map { y(it.value) })
            val fill = Path().apply { addPath(line); lineTo(x(pts.last().atMs), size.height); lineTo(0f, size.height); close() }
            chart.markers.forEach { m ->
                val mx = x(m.atMs)
                drawLine(Color.White.copy(alpha = 0.22f), Offset(mx, 0f), Offset(mx, size.height), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            }
            clipRect(right = size.width * progress.value) {
                drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.34f), Color.Transparent)))
                drawPath(line, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
            }
            if (progress.value > 0.97f) {
                val end = Offset(x(pts.last().atMs), y(pts.last().value))
                drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.55f), Color.Transparent), center = end, radius = 18.dp.toPx()), 18.dp.toPx(), end)
                drawCircle(CardFlat, 9.dp.toPx(), end)
                drawCircle(color, 6.dp.toPx(), end)
            }
        }
        Text(chart.lowLabel, color = Ash, fontFamily = Display, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp, top = 18.dp))
        Text(chart.highLabel, color = Chalk, fontFamily = Display, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.TopEnd).padding(end = 38.dp))
        chart.markers.firstOrNull()?.let { m ->
            Text(m.label.uppercase(), style = MonoSmall.copy(fontSize = 9.sp), color = Ash, modifier = Modifier.align(Alignment.TopStart).padding(start = 16.dp))
        }
    }
}

/**
 * A smooth line through the points that never overshoots them (Fritsch–Carlson monotone cubic):
 * a plateau stays flat and a rise never dips below where it started, unlike a plain cubic spline.
 */
internal fun monotonePath(xs: List<Float>, ys: List<Float>): Path {
    val path = Path()
    val n = xs.size
    if (n == 0) return path
    path.moveTo(xs[0], ys[0])
    if (n == 1) return path
    val d = FloatArray(n - 1) { (ys[it + 1] - ys[it]) / (xs[it + 1] - xs[it]).coerceAtLeast(0.001f) }
    val m = FloatArray(n)
    m[0] = d[0]; m[n - 1] = d[n - 2]
    for (i in 1 until n - 1) m[i] = if (d[i - 1] * d[i] <= 0f) 0f else (d[i - 1] + d[i]) / 2f
    for (i in 0 until n - 1) {
        if (d[i] == 0f) { m[i] = 0f; m[i + 1] = 0f; continue }
        val a = m[i] / d[i]; val b = m[i + 1] / d[i]
        val s = a * a + b * b
        if (s > 9f) { val t = 3f / kotlin.math.sqrt(s); m[i] = t * a * d[i]; m[i + 1] = t * b * d[i] }
    }
    for (i in 0 until n - 1) {
        val h = xs[i + 1] - xs[i]
        path.cubicTo(xs[i] + h / 3, ys[i] + m[i] * h / 3, xs[i + 1] - h / 3, ys[i + 1] - m[i + 1] * h / 3, xs[i + 1], ys[i + 1])
    }
    return path
}

/** Push / Pull / Core / Legs as bars, the weak group lit, and the muscles that went quiet as chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SplitChart(chart: InsightChart.Split, color: Color, weak: MovementGroup?) {
    val maxSets = chart.parts.maxOfOrNull { it.sets }?.coerceAtLeast(1) ?: 1
    val weakest = weak ?: chart.parts.filter { it.sets > 0 }.minByOrNull { it.share }?.group
    Column(Modifier.padding(horizontal = 16.dp).padding(top = 18.dp)) {
        chart.parts.forEach { part ->
            val lit = part.group == weakest
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(part.group.label, Modifier.width(44.dp), color = if (lit) color else Chalk, fontSize = 12.5.sp, fontWeight = if (lit) FontWeight.SemiBold else FontWeight.Normal)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f).height(10.dp)) {
                    val frac = (part.sets.toFloat() / maxSets).coerceIn(0.02f, 1f)
                    if (lit) {
                        GlowBox(color, RoundedCornerShape(3.dp), glowRadius = 6.dp, glowAlpha = 0.5f, modifier = Modifier.fillMaxWidth(frac).height(10.dp)) {
                            Box(Modifier.fillMaxWidth().height(10.dp).background(color, RoundedCornerShape(3.dp)))
                        }
                    } else Box(Modifier.fillMaxWidth(frac).height(10.dp).background(BarIdle, RoundedCornerShape(3.dp)))
                }
                Spacer(Modifier.width(10.dp))
                Text(part.sets.toString(), Modifier.width(30.dp), color = if (lit) color else Chalk, style = MonoSmall.copy(fontSize = 11.sp, letterSpacing = 0.sp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }
        }
        if (chart.neglected.isNotEmpty()) {
            androidx.compose.foundation.layout.FlowRow(
                Modifier.padding(top = 12.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                chart.neglected.forEachIndexed { i, n ->
                    val c = if (i == 0) color else Color.White.copy(alpha = 0.18f)
                    Row(
                        Modifier
                            .drawBehind {
                                drawRoundRect(c.copy(alpha = if (i == 0) 0.6f else 1f), cornerRadius = CornerRadius(size.height / 2), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))))
                            }
                            .padding(horizontal = 11.dp, vertical = 7.dp),
                    ) {
                        Text(n.muscle, color = Chalk, fontSize = 12.sp)
                        Text(" · ${n.days} d", color = if (i == 0) color else Ash, fontSize = 12.sp)
                    }
                }
            }
        } else Spacer(Modifier.height(16.dp))
    }
}

/** Planned days by weekday: a full bar where every planned day happened, a dashed one filled as far as you got. */
@Composable
private fun WeekdayChart(days: List<DayMark>, color: Color) {
    val byDow = DayOfWeek.entries.associateWith { dow -> days.filter { it.date.dayOfWeek == dow } }
    val anyPlanned = days.any { it.planned }
    val maxTrained = byDow.values.maxOf { list -> list.count { it.trained } }.coerceAtLeast(1)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 18.dp, bottom = 16.dp).height(100.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        DayOfWeek.entries.forEach { dow ->
            val list = byDow.getValue(dow)
            val planned = list.count { it.planned }
            val done = list.count { it.planned && it.trained }
            val trained = list.count { it.trained }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                val full = 76.dp
                when {
                    anyPlanned && planned > 0 && done == planned -> SolidBar(full, color)
                    anyPlanned && planned > 0 -> Box(
                        Modifier
                            .fillMaxWidth()
                            .height(full)
                            .drawBehind {
                                drawRoundRect(color.copy(alpha = 0.75f), cornerRadius = CornerRadius(8.dp.toPx()), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))))
                                val h = size.height * done / planned
                                drawRoundRect(Brush.verticalGradient(listOf(FlameGlow, FlameHot)), topLeft = Offset(0f, size.height - h), size = androidx.compose.ui.geometry.Size(size.width, h), cornerRadius = CornerRadius(8.dp.toPx()))
                            },
                    )
                    !anyPlanned && trained > 0 -> SolidBar(full * (trained.toFloat() / maxTrained), color)
                    else -> Box(Modifier.fillMaxWidth().height(6.dp).background(Color(0xFF2A2522), RoundedCornerShape(3.dp)))
                }
                Text(dow.name.take(1), style = MonoSmall.copy(letterSpacing = 0.sp), color = Ash, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun SolidBar(height: Dp, color: Color) {
    GlowBox(color, RoundedCornerShape(8.dp), glowRadius = 8.dp, glowAlpha = 0.4f, modifier = Modifier.fillMaxWidth().height(height)) {
        Box(Modifier.fillMaxWidth().height(height).background(Brush.verticalGradient(listOf(FlameGlow, FlameHot)), RoundedCornerShape(8.dp)))
    }
}

/** Each recent session as a column off a centre line: above = felt harder than its heart rate, below = easier. */
@Composable
private fun EffortChart(chart: InsightChart.EffortGap, color: Color) {
    val pts = chart.points
    val devs = pts.map { it.felt - it.expected }
    val maxAbs = max(1.5, devs.maxOfOrNull { abs(it) } ?: 1.5)
    Column(Modifier.padding(horizontal = 16.dp).padding(top = 18.dp)) {
        Canvas(Modifier.fillMaxWidth().height(96.dp)) {
            val n = devs.size.coerceAtLeast(1)
            val gap = 8.dp.toPx()
            val w = (size.width - gap * (n - 1)) / n
            val mid = size.height / 2
            drawLine(Color.White.copy(alpha = 0.16f), Offset(0f, mid), Offset(size.width, mid), 1.dp.toPx())
            devs.forEachIndexed { i, d ->
                val left = i * (w + gap)
                val h = (abs(d) / maxAbs * mid).toFloat().coerceAtLeast(2.dp.toPx())
                if (d > 0) {
                    val strong = d >= 1.0
                    if (strong) drawRoundRect(Brush.verticalGradient(listOf(color.copy(alpha = 0.0f), color.copy(alpha = 0.35f))), Offset(left - 4.dp.toPx(), mid - h - 8.dp.toPx()), androidx.compose.ui.geometry.Size(w + 8.dp.toPx(), h + 8.dp.toPx()), CornerRadius(8.dp.toPx()))
                    drawRoundRect(if (strong) color else color.copy(alpha = 0.35f), Offset(left, mid - h), androidx.compose.ui.geometry.Size(w, h), CornerRadius(4.dp.toPx()))
                } else {
                    drawRoundRect(Chalk.copy(alpha = 0.22f), Offset(left, mid), androidx.compose.ui.geometry.Size(w, h), CornerRadius(4.dp.toPx()))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp)) {
            Text("${pts.size} SESSIONS AGO", style = MonoSmall.copy(fontSize = 9.5.sp), color = Ash, modifier = Modifier.weight(1f))
            Text("FELT HARDER ↑", style = MonoSmall.copy(fontSize = 9.5.sp), color = Ash, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text("LAST", style = MonoSmall.copy(fontSize = 9.5.sp), color = Chalk, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
    }
}
