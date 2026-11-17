package com.calistapp.app.ui.insights

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calistapp.app.ui.common.EaseOutStrong
import com.calistapp.app.ui.common.GestureModalSheet
import com.calistapp.app.ui.common.HelpDot
import com.calistapp.app.ui.common.pressScale
import com.calistapp.app.ui.common.rememberInteraction
import com.calistapp.app.ui.common.sharedSurface
import com.calistapp.app.ui.theme.Amber
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.CardFlat
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.Display
import com.calistapp.app.ui.theme.Eyebrow
import com.calistapp.app.ui.theme.FlameHot
import com.calistapp.app.ui.theme.Mint
import com.calistapp.app.ui.theme.PageInk
import com.calistapp.core.insights.Evidence
import com.calistapp.core.insights.InsightsPeriod
import com.calistapp.core.insights.KeyMovement
import com.calistapp.core.insights.Move
import com.calistapp.core.insights.MoveKind
import com.calistapp.core.insights.MovementStatus
import com.calistapp.core.insights.Question
import com.calistapp.core.model.SessionOverview
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Mono = Eyebrow.copy(fontSize = 10.sp, letterSpacing = 1.4.sp)
private val RANGES = listOf(InsightsPeriod.FOUR_WEEKS to "4 weeks", InsightsPeriod.QUARTER to "3 months", InsightsPeriod.YEAR to "Year")

/** Where a move takes the user. Shared by the card footer and the question page so both act alike. */
internal fun performMove(move: Move, vm: InsightsViewModel, onOpenPlanner: () -> Unit, onOpenSchedule: () -> Unit, onOpenExercise: (String) -> Unit) {
    when (move.kind) {
        MoveKind.PLAN_GROUP_DAY -> vm.prepareWorkout(move, onOpenPlanner)
        MoveKind.ADD_LOAD, MoveKind.UNSTICK -> move.exerciseIds.firstOrNull()?.let(onOpenExercise)
        MoveKind.SHORTEN_DAY, MoveKind.PLAN_WEEK, MoveKind.EASY_DAY, MoveKind.EASY_CIRCUIT -> onOpenSchedule()
        MoveKind.KEEP_DOING -> Unit
    }
}

/**
 * One question on its own page, grown out of its card: the verdict, the same chart larger with a
 * range picker, the evidence behind it, the sessions it came from, and the move.
 */
@Composable
fun InsightPageScreen(
    question: Question,
    viewModel: InsightsViewModel,
    onBack: () -> Unit,
    onOpenSession: (String) -> Unit,
    onOpenPlanner: () -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenExercise: (String) -> Unit,
) {
    val report by viewModel.report.collectAsStateWithLifecycle()
    val byPeriod by viewModel.byPeriod.collectAsStateWithLifecycle()
    val overviews by viewModel.overviews.collectAsStateWithLifecycle()
    val r = report ?: return
    val q = r.questions.firstOrNull { it.question == question } ?: return
    val look = lookOf(q)
    var range by rememberSaveable { mutableStateOf(if (r.period == InsightsPeriod.WEEK) InsightsPeriod.FOUR_WEEKS else r.period) }
    LaunchedEffect(range) { if (range != r.period) viewModel.loadPeriod(range) }
    val chart = if (range == r.period) q.chart else byPeriod[range]?.questions?.firstOrNull { it.question == question }?.chart
    var showSessions by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .sharedSurface("insight-$question")
            .background(Brush.verticalGradient(listOf(CardFlat, PageInk))),
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp),
        ) {
            item {
                Row(Modifier.padding(start = 6.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(44.dp).clickable(interactionSource = rememberInteraction(), indication = null, onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Chalk, modifier = Modifier.size(22.dp)) }
                    Text("Insights", color = Ash, fontSize = 14.sp)
                }
            }
            item {
                Column(Modifier.padding(horizontal = 20.dp).padding(top = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = FlameHot)) { append(q.question.number) }
                                append(" · ${q.question.title.uppercase()}")
                            },
                            style = Mono, color = Ash, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Pill(q.pill, look)
                        Spacer(Modifier.width(6.dp))
                        HelpDot(HowMeasured.getValue(question), contentDescription = "How this is measured")
                    }
                    Text(
                        q.fact,
                        modifier = Modifier.padding(top = 14.dp),
                        style = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.8).sp, color = Chalk),
                    )
                }
            }
            if (q.chart !is com.calistapp.core.insights.InsightChart.None) {
                item {
                    RangeTabs(range) { range = it }
                }
                item {
                    AnimatedContent(
                        targetState = chart,
                        transitionSpec = { fadeIn(tween(220, easing = EaseOutStrong)) togetherWith fadeOut(tween(120)) },
                        label = "pageChart",
                    ) { c ->
                        val empty = c is com.calistapp.core.insights.InsightChart.None ||
                            (c is com.calistapp.core.insights.InsightChart.Trend && c.points.size < 2)
                        when {
                            c == null -> Spacer(Modifier.height(200.dp))
                            empty -> Text(
                                "Not enough history for this range yet.",
                                color = Ash, fontSize = 14.sp,
                                modifier = Modifier.fillMaxWidth().height(120.dp).padding(horizontal = 20.dp, vertical = 40.dp),
                            )
                            else -> Column { QuestionChart(q, look, c, trendHeight = 200.dp) }
                        }
                    }
                }
            }
            if (q.keyMovements.isNotEmpty()) {
                item { SectionLabel("YOUR KEY MOVEMENTS") }
                q.keyMovements.forEachIndexed { i, m -> item { MovementRow(m, last = i == q.keyMovements.lastIndex) } }
            }
            if (q.evidence.isNotEmpty()) {
                item { SectionLabel("WHAT'S BEHIND IT") }
                q.evidence.forEachIndexed { i, e -> item { EvidenceRow(e, last = i == q.evidence.lastIndex) } }
            }
            if (q.sessionIds.isNotEmpty()) {
                item {
                    val i = rememberInteraction()
                    Row(
                        Modifier
                            .padding(horizontal = 16.dp)
                            .padding(top = 20.dp)
                            .fillMaxWidth()
                            .pressScale(i, 0.985f)
                            .background(CardFlat, RoundedCornerShape(18.dp))
                            .border(1.dp, Hairline, RoundedCornerShape(18.dp))
                            .clickable(interactionSource = i, indication = null) { showSessions = true }
                            .padding(horizontal = 16.dp, vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val n = q.sessionIds.size
                        Text("See the $n session${if (n == 1) "" else "s"} behind this", color = Chalk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = Ash, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        q.move?.takeIf { it.action != null }?.let { move ->
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(PageInk.copy(alpha = 0f), PageInk)))
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                PrimaryPill(move.title, null, { performMove(move, viewModel, onOpenPlanner, onOpenSchedule, onOpenExercise) })
            }
        }
    }

    if (showSessions) {
        SessionsSheet(q.sessionIds.mapNotNull { overviews[it] }, onOpen = onOpenSession, onDismiss = { showSessions = false })
    }
}

@Composable
private fun RangeTabs(selected: InsightsPeriod, onSelect: (InsightsPeriod) -> Unit) {
    Row(Modifier.padding(horizontal = 20.dp).padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        RANGES.forEach { (p, label) ->
            val on = p == selected
            Text(
                label,
                color = if (on) Chalk else Ash,
                fontSize = 13.sp,
                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier
                    .background(if (on) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.08f) else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(50))
                    .clickable(interactionSource = rememberInteraction(), indication = null) { onSelect(p) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = Mono, color = Ash, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 26.dp, bottom = 6.dp))
}

@Composable
private fun MovementRow(m: KeyMovement, last: Boolean) {
    Row(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .drawBehind {
                drawLine(Hairline, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
                if (last) drawLine(Hairline, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(m.name, color = Chalk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when (m.status) {
                    MovementStatus.STUCK -> m.note
                    MovementStatus.NEW -> "New this period · ${m.to}"
                    else -> "${m.from} → ${m.to}"
                },
                color = Ash, fontSize = 12.5.sp, modifier = Modifier.padding(top = 2.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        when (m.status) {
            MovementStatus.IMPROVED -> Text(m.delta, style = Mono.copy(letterSpacing = 0.5.sp), color = Mint)
            MovementStatus.STUCK -> StatusTag("STUCK", Amber)
            MovementStatus.SLIPPED -> StatusTag("SLIPPED", com.calistapp.app.ui.theme.Coral)
            MovementStatus.NEW -> StatusTag("NEW", Ash)
            MovementStatus.STEADY -> StatusTag("STEADY", Ash)
        }
    }
}

@Composable
private fun StatusTag(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text, style = Mono.copy(fontSize = 9.5.sp, letterSpacing = 1.sp), color = color,
        modifier = Modifier.background(color.copy(alpha = 0.12f), RoundedCornerShape(50)).border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun EvidenceRow(e: Evidence, last: Boolean) {
    Column(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .drawBehind {
                drawLine(Hairline, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
                if (last) drawLine(Hairline, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            }
            .padding(vertical = 12.dp),
    ) {
        Text(e.title, color = Chalk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(e.detail, color = Ash, fontSize = 12.5.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

/** The sessions a verdict was built from; tapping one opens its summary. */
@Composable
private fun SessionsSheet(sessions: List<SessionOverview>, onOpen: (String) -> Unit, onDismiss: () -> Unit) {
    val fmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
    GestureModalSheet(onDismissRequest = onDismiss) { sheet ->
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            Text("Sessions behind this", fontFamily = Display, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Chalk, modifier = Modifier.padding(bottom = 10.dp))
            sessions.take(20).forEach { s ->
                val i = rememberInteraction()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pressScale(i, 0.985f)
                        .clickable(interactionSource = i, indication = null) { sheet.dismiss { onOpen(s.id) } }
                        .drawBehind { drawLine(Hairline, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
                        .padding(vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(s.title, color = Chalk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(fmt.format(Instant.ofEpochMilli(s.startMs).atZone(ZoneId.systemDefault())), color = Ash, fontSize = 12.5.sp)
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = Ash, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
