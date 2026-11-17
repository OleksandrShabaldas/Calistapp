package com.calistapp.app.ui.insights

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calistapp.app.ui.common.EaseOutStrong
import com.calistapp.app.ui.common.Entrance
import com.calistapp.app.ui.common.GestureModalSheet
import com.calistapp.app.ui.common.GlowIcon
import com.calistapp.app.ui.common.pressScale
import com.calistapp.app.ui.common.rememberInteraction
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.CardFlat
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.Coral
import com.calistapp.app.ui.theme.Display
import com.calistapp.app.ui.theme.Eyebrow
import com.calistapp.app.ui.theme.FlameHot
import com.calistapp.app.ui.theme.Mint
import com.calistapp.app.ui.medals.AchievementsSection
import com.calistapp.core.insights.InsightsPeriod
import com.calistapp.core.medals.MedalReport
import com.calistapp.core.insights.InsightsReport
import com.calistapp.core.insights.Move
import com.calistapp.core.insights.NumberStat
import com.calistapp.core.insights.Question
import com.calistapp.core.insights.RecordBoard
import com.calistapp.core.insights.RecordEntry
import com.calistapp.core.insights.Verdict
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Everything the Insights tab can be asked to do; HistoryScreen wires these to navigation. */
data class InsightsActions(
    val onPeriod: (InsightsPeriod) -> Unit,
    val onPlayRecap: (InsightsReport) -> Unit,
    val onShare: (InsightsReport) -> Unit,
    val onPastRecaps: () -> Unit,
    val onOpenQuestion: (Question) -> Unit,
    val onMove: (Move) -> Unit,
    val onAllRecords: () -> Unit,
    val onStartWorkout: () -> Unit,
    val onAllMedals: () -> Unit,
    val onOpenMedal: (awardKey: String) -> Unit,
)

private val Mono = Eyebrow.copy(fontSize = 10.sp, letterSpacing = 1.6.sp)

/** The Insights tab as LazyColumn items, so it scrolls under History's own header and toggle. */
fun LazyListScope.insightsItems(
    report: InsightsReport?,
    period: InsightsPeriod,
    actions: InsightsActions,
    medals: MedalReport? = null,
) {
    item(key = "ins-tabs") { PeriodTabs(period, actions.onPeriod, actions.onPastRecaps) }
    if (report == null) return
    val issue = "${report.period}@${report.window.start}"
    if (report.isEmpty) {
        item(key = "ins-empty") { Entrance(0, issue) { EmptyIssue(report, actions.onStartWorkout) } }
        item(key = "ins-medals") { Entrance(1, issue) { AchievementsSection(medals, actions.onAllMedals) { actions.onOpenMedal(it.award.key) } } }
        return
    }
    item(key = "ins-cover") {
        Entrance(0, issue) { Column(Modifier.padding(top = 10.dp)) { CoverCard(report, { actions.onPlayRecap(report) }, { actions.onShare(report) }) } }
    }
    item(key = "ins-summary") { Entrance(1, issue) { Summary(report.summary) } }
    report.questions.forEachIndexed { i, q ->
        item(key = "ins-q-${q.question}") {
            Entrance(2 + i, issue) { QuestionCard(q, onOpen = { actions.onOpenQuestion(q.question) }, onMove = actions.onMove) }
        }
    }
    report.records?.let { board ->
        item(key = "ins-records") { Entrance(7, issue) { RecordsSection(board, report.period, actions.onAllRecords) } }
    }
    item(key = "ins-numbers") { Entrance(8, issue) { NumbersSection(report.numbers, report.period) } }
    item(key = "ins-medals") { Entrance(9, issue) { AchievementsSection(medals, actions.onAllMedals) { actions.onOpenMedal(it.award.key) } } }
    item(key = "ins-past") { PastRecapsRow(actions.onPastRecaps) }
}

@Composable
private fun Summary(text: String) {
    val cut = text.indexOf(". ").takeIf { it > 0 }
    Text(
        buildAnnotatedString {
            if (cut != null) {
                withStyle(SpanStyle(color = Chalk)) { append(text.substring(0, cut + 1)) }
                append(text.substring(cut + 1))
            } else append(text)
        },
        color = Ash, fontSize = 14.sp, lineHeight = 21.sp,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
    )
}

@Composable
private fun EmptyIssue(report: InsightsReport, onStart: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        Modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .background(CoverInk, shape)
            .border(1.dp, Color.White.copy(alpha = 0.09f), shape)
            .padding(18.dp),
    ) {
        Text("ISSUE 1", style = Mono, color = Ash)
        Text(
            report.headline.firstOrNull().orEmpty(),
            modifier = Modifier.padding(top = 12.dp),
            style = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.8).sp, color = Chalk),
        )
        VerdictBar(report.questions, Modifier.padding(top = 20.dp))
        Spacer(Modifier.height(20.dp))
        PrimaryPill("Build a workout", null, onStart)
    }
}

// ---------------------------------------------------------------------------------------------- records
@Composable
private fun RecordsSection(board: RecordBoard, period: InsightsPeriod, onAll: () -> Unit) {
    val fmt = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
    Column(Modifier.padding(top = 22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("RECORD OF THE ${period.noun.uppercase()}", style = Mono, color = Ash, modifier = Modifier.weight(1f))
            if (board.others.isNotEmpty()) {
                Text("All records", color = Chalk, fontSize = 13.sp, modifier = Modifier.clickable(interactionSource = rememberInteraction(), indication = null, onClick = onAll).padding(vertical = 8.dp))
            }
        }
        LeadRecord(board.lead, fmt)
        if (board.others.isNotEmpty()) {
            Text("${board.others.size} MORE RECORD${if (board.others.size == 1) "" else "S"}", style = Mono, color = Ash, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
            board.others.forEachIndexed { i, r -> RecordRow(r, last = i == board.others.lastIndex) }
        }
    }
}

@Composable
private fun LeadRecord(r: RecordEntry, fmt: DateTimeFormatter) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .background(CoverInk, shape)
            .drawBehind {
                drawRoundRect(Brush.radialGradient(listOf(FlameHot.copy(alpha = 0.32f), Color.Transparent), center = Offset(size.width, size.height), radius = size.width * 0.9f), cornerRadius = CornerRadius(22.dp.toPx()))
            }
            .border(1.dp, FlameHot.copy(alpha = 0.30f), shape)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlowIcon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = com.calistapp.app.ui.theme.FlameGlow, size = 18.dp)
            Spacer(Modifier.width(8.dp))
            Text(r.name, color = Chalk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(fmt.format(Instant.ofEpochMilli(r.atMs).atZone(ZoneId.systemDefault())).uppercase(), style = Mono.copy(letterSpacing = 1.sp), color = Ash)
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.Bottom) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                Text(
                    r.value,
                    style = TextStyle(
                        fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = if (r.value.length > 5) 44.sp else 64.sp, lineHeight = 60.sp,
                        letterSpacing = (-3).sp, color = FlameHot, shadow = Shadow(FlameHot.copy(alpha = 0.55f), Offset.Zero, 28f),
                    ),
                    maxLines = 1,
                )
                if (r.unit.isNotBlank()) Text(r.unit, color = Chalk, fontFamily = Display, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
            }
            if (r.points.size >= 2) Sparkline(r)
        }
        if (r.note.isNotBlank()) Text(r.note, color = Ash, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun Sparkline(r: RecordEntry) {
    Canvas(Modifier.width(132.dp).height(58.dp)) {
        val pts = r.points
        val t0 = pts.first().atMs.toDouble(); val t1 = maxOf(pts.last().atMs.toDouble(), t0 + 1)
        val lo = pts.minOf { it.value }; val hi = pts.maxOf { it.value }; val span = (hi - lo).takeIf { it > 0 } ?: 1.0
        fun x(t: Long) = ((t - t0) / (t1 - t0) * (size.width - 8.dp.toPx())).toFloat() + 2.dp.toPx()
        fun y(v: Double) = (size.height - 6.dp.toPx() - (v - lo) / span * (size.height - 14.dp.toPx())).toFloat()
        val path = Path()
        pts.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(p.atMs), y(p.value)) else path.lineTo(x(p.atMs), y(p.value)) }
        drawPath(path, FlameHot, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        val end = Offset(x(pts.last().atMs), y(pts.last().value))
        drawCircle(Brush.radialGradient(listOf(FlameHot.copy(alpha = 0.5f), Color.Transparent), center = end, radius = 10.dp.toPx()), 10.dp.toPx(), end)
        drawCircle(FlameHot, 3.5.dp.toPx(), end)
    }
}

@Composable
private fun RecordRow(r: RecordEntry, last: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(Hairline, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
                if (last) drawLine(Hairline, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            }
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(r.name, color = Chalk, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(10.dp))
        Text(r.delta, style = Mono.copy(letterSpacing = 0.5.sp), color = Mint)
        Text(
            if (r.unit.isBlank()) r.value else "${r.value} ${r.unit}",
            modifier = Modifier.width(84.dp), textAlign = TextAlign.End,
            fontFamily = Display, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Chalk, maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------------------------------------- numbers
@Composable
private fun NumbersSection(numbers: List<NumberStat>, period: InsightsPeriod) {
    val heading = when (period) {
        InsightsPeriod.WEEK -> "THIS WEEK · VS THE WEEK BEFORE"
        InsightsPeriod.FOUR_WEEKS -> "THESE 4 WEEKS · VS THE 4 BEFORE"
        InsightsPeriod.QUARTER -> "THIS QUARTER · VS THE ONE BEFORE"
        InsightsPeriod.YEAR -> "THIS YEAR · VS THE YEAR BEFORE"
    }
    val lead = numbers.take(3)
    val follow = numbers.drop(3)
    Column(Modifier.padding(top = 30.dp)) {
        Text(heading, style = Mono, color = Ash)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .drawBehind { drawLine(Hairline, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val weights = listOf(0.8f, 1.25f, 1f)
            lead.forEachIndexed { i, n ->
                Column(Modifier.weight(weights.getOrElse(i) { 1f })) {
                    RollingValue(n.value, TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 32.sp, letterSpacing = (-0.8).sp, color = Chalk))
                    Text(n.label, color = Ash, fontSize = 11.5.sp, modifier = Modifier.padding(top = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    n.delta?.let { Text(it, style = Mono.copy(letterSpacing = 0.5.sp), color = deltaColor(n.good), modifier = Modifier.padding(top = 4.dp)) }
                }
            }
        }
        follow.forEach { n ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .drawBehind { drawLine(Hairline, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
                    .padding(vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(n.label, color = Ash, fontSize = 13.5.sp, modifier = Modifier.weight(1f))
                RollingValue(n.value, TextStyle(fontFamily = Display, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Chalk))
                Text(n.delta.orEmpty(), style = Mono.copy(letterSpacing = 0.5.sp), color = deltaColor(n.good), modifier = Modifier.width(72.dp), textAlign = TextAlign.End)
            }
        }
    }
}

private fun deltaColor(good: Boolean?) = when (good) { true -> Mint; false -> Coral; null -> Ash }

/** A figure that rolls up when it grows and down when it shrinks, instead of snapping. */
@Composable
private fun RollingValue(value: String, style: TextStyle) {
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            val up = (targetState.filter(Char::isDigit).toLongOrNull() ?: 0) >= (initialState.filter(Char::isDigit).toLongOrNull() ?: 0)
            (slideInVertically(tween(280, easing = EaseOutStrong)) { if (up) it / 2 else -it / 2 } + fadeIn(tween(200))) togetherWith
                (slideOutVertically(tween(200)) { if (up) -it / 2 else it / 2 } + fadeOut(tween(140)))
        },
        label = "value",
    ) { v -> Text(v, style = style, maxLines = 1) }
}

// ---------------------------------------------------------------------------------------------- past recaps
@Composable
private fun PastRecapsRow(onOpen: () -> Unit) {
    val i = rememberInteraction()
    Row(
        Modifier
            .padding(top = 18.dp)
            .fillMaxWidth()
            .pressScale(i, 0.985f)
            .background(CardFlat, RoundedCornerShape(18.dp))
            .border(1.dp, Hairline, RoundedCornerShape(18.dp))
            .clickable(interactionSource = i, indication = null, onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Past recaps", color = Chalk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = Ash, modifier = Modifier.size(18.dp))
    }
}

/** The previous issues of this period. Tapping one plays its recap. */
@Composable
internal fun PastRecapsSheet(issues: List<InsightsReport>, onPlay: (InsightsReport) -> Unit, onDismiss: () -> Unit) {
    val fmt = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
    GestureModalSheet(onDismissRequest = onDismiss) { sheet ->
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            Text("Past recaps", fontFamily = Display, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Chalk, modifier = Modifier.padding(bottom = 12.dp))
            if (issues.isEmpty()) {
                Text("Your earlier issues appear here once you've trained for more than one period.", color = Ash, fontSize = 14.sp)
            }
            issues.forEach { r ->
                val i = rememberInteraction()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pressScale(i, 0.985f)
                        .clickable(interactionSource = i, indication = null) { sheet.dismiss { onPlay(r) } }
                        .drawBehind { drawLine(Hairline, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("ISSUE ${r.issue} · ${fmt.format(r.window.start).uppercase()} – ${fmt.format(r.window.endInclusive).uppercase()}", style = Mono.copy(letterSpacing = 1.2.sp), color = Ash)
                        Text(r.headline.joinToString(" "), color = Chalk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                        VerdictBar(r.questions, Modifier.padding(top = 10.dp).fillMaxWidth(0.6f))
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = Ash, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** Every record this period, with what it beat. */
@Composable
internal fun AllRecordsSheet(board: RecordBoard, onDismiss: () -> Unit) {
    GestureModalSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            Text("Records", fontFamily = Display, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Chalk, modifier = Modifier.padding(bottom = 12.dp))
            (listOf(board.lead) + board.others).forEachIndexed { i, r, -> RecordRow(r, last = i == board.others.size) }
        }
    }
}

/** Plain-text share of an issue: the headline, the five verdicts and the summary. */
internal fun shareIssue(context: Context, r: InsightsReport) {
    val verdicts = r.questions.joinToString("\n") { "${it.question.short}: ${if (it.verdict == Verdict.NOT_ENOUGH_DATA) "not yet" else it.pill.lowercase()}" }
    val text = "${r.headline.joinToString(" ")}\n\n$verdicts\n\n${r.summary}"
    val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
    context.startActivity(Intent.createChooser(intent, "Share recap"))
}
