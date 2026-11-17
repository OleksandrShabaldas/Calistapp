package com.calistapp.app.ui.insights

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.calistapp.app.ui.common.GestureMotion
import com.calistapp.app.ui.common.pressScale
import com.calistapp.app.ui.common.rememberInteraction
import com.calistapp.app.ui.common.rememberReduceMotion
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.Display
import com.calistapp.app.ui.theme.Eyebrow
import com.calistapp.app.ui.theme.FlameGlow
import com.calistapp.app.ui.theme.FlameHot
import com.calistapp.app.ui.theme.PageInk
import com.calistapp.core.insights.InsightsReport
import com.calistapp.core.insights.Move
import com.calistapp.core.insights.Question
import com.calistapp.core.insights.QuestionVerdict
import com.calistapp.core.insights.Verdict
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val Mono = Eyebrow.copy(fontSize = 10.sp, letterSpacing = 1.6.sp)
private const val CARD_MS = 6_000
private const val HOLD_MS = 260L

private sealed interface RecapCard {
    data object Cover : RecapCard
    data class Answer(val q: QuestionVerdict, val win: Boolean) : RecapCard
    data object Record : RecapCard
    data object Moves : RecapCard
}

private fun cardsFor(r: InsightsReport): List<RecapCard> = buildList {
    add(RecapCard.Cover)
    r.questions.filter { it.verdict == Verdict.GOOD }.forEach { add(RecapCard.Answer(it, true)) }
    if (r.records != null) add(RecapCard.Record)
    r.questions.filter { it.verdict == Verdict.WATCH || it.verdict == Verdict.MIXED }.forEach { add(RecapCard.Answer(it, false)) }
    add(RecapCard.Moves)
}

/**
 * The issue as a story: a full-screen deck that plays itself (six seconds a card) and follows the
 * finger. Drag sideways to move between cards (1:1, commits past 40 % or on a flick), tap the left
 * third to go back and the rest to go on, hold anywhere to pause, drag down to close.
 */
@Composable
fun RecapPlayer(
    report: InsightsReport,
    onClose: () -> Unit,
    onSeeData: (Question) -> Unit,
    onMove: (Move) -> Unit,
    onShare: () -> Unit,
) {
    val cards = remember(report) { cardsFor(report) }
    val reduce = rememberReduceMotion()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val flingPx = GestureMotion.flingPx(density)
    var index by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val dragX = remember { Animatable(0f) }
    val dragY = remember { Animatable(0f) }
    val enter = remember { Animatable(if (reduce) 1f else 0f) }
    var closing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { if (!reduce) enter.animateTo(1f, GestureMotion.settle(0.36f)) }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val w = constraints.maxWidth.toFloat()
            val h = constraints.maxHeight.toFloat()

            /** Slide the deck away (carrying a drag's [velocity]), then run [then]: act only once it's gone. */
            fun close(velocity: Float = 0f, then: (() -> Unit)? = null) {
                if (closing) return
                closing = true
                scope.launch {
                    dragY.animateTo(h, if (reduce) tween(160) else GestureMotion.release(reduce, bounce = 0f, durationS = 0.42f), velocity)
                    onClose()
                    then?.invoke()
                }
            }
            val closeThen: (() -> Unit) -> Unit = { action -> close(0f, action) }
            /** Slide to card [to] from wherever the deck is, carrying [velocity]; a tap passes 0. */
            fun go(to: Int, velocity: Float = 0f) {
                if (to !in cards.indices) { if (to >= cards.size) close() else scope.launch { dragX.animateTo(0f, GestureMotion.snapBack(reduce), velocity) }; return }
                val dir = if (to > index) -1f else 1f
                scope.launch {
                    if (!reduce) dragX.animateTo(dir * w, if (velocity == 0f) GestureMotion.settle(0.34f) else GestureMotion.release(reduce, bounce = 0f, durationS = 0.36f), velocity)
                    index = to
                    dragX.snapTo(0f)
                    progress.snapTo(0f)
                }
            }

            // The clock: fill the current card's segment, then move on. Paused while a finger is down.
            LaunchedEffect(index, paused, closing) {
                if (paused || closing) return@LaunchedEffect
                val remaining = ((1f - progress.value) * CARD_MS).toInt().coerceAtLeast(0)
                progress.animateTo(1f, tween(remaining, easing = LinearEasing))
                if (index < cards.lastIndex) go(index + 1)
            }

            val pullFrac = (dragY.value / h).coerceIn(0f, 1f)
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = (1f - pullFrac * 1.2f).coerceIn(0f, 1f) * enter.value }
                    .background(Color.Black.copy(alpha = 0.6f)),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = dragY.value
                        val s = (1f - pullFrac * 0.12f) * (0.96f + 0.04f * enter.value)
                        scaleX = s; scaleY = s
                        alpha = enter.value
                        clip = true
                        shape = RoundedCornerShape((pullFrac * 120f).coerceAtMost(28f).dp)
                    }
                    .background(PageInk)
                    .pointerInput(cards.size) {
                        val slop = 8.dp.toPx()
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
                            paused = true
                            var axis = 0 // 0 undecided, 1 horizontal, 2 vertical
                            var last = down
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                last = ch
                                tracker.addPosition(ch.uptimeMillis, ch.position)
                                val dx = ch.position.x - down.position.x
                                val dy = ch.position.y - down.position.y
                                if (axis == 0 && (abs(dx) > slop || abs(dy) > slop)) axis = if (abs(dx) > abs(dy)) 1 else if (dy > 0) 2 else 1
                                when (axis) {
                                    1 -> {
                                        // Past the first or last card the deck resists instead of hitting a wall.
                                        val min = if (index == cards.lastIndex) 0f else -w
                                        val max = if (index == 0) 0f else w
                                        scope.launch { dragX.snapTo(GestureMotion.elastic(dx, min, max)) }; ch.consume()
                                    }
                                    2 -> { scope.launch { dragY.snapTo(dy.coerceAtLeast(0f)) }; ch.consume() }
                                }
                                if (!ch.pressed) break
                            }
                            paused = false
                            val v = tracker.calculateVelocity()
                            when (axis) {
                                1 -> {
                                    val moved = -dragX.value / w
                                    when {
                                        moved > 0.4f || v.x < -flingPx -> go(index + 1, v.x)
                                        moved < -0.4f || v.x > flingPx -> go(index - 1, v.x)
                                        else -> scope.launch { dragX.animateTo(0f, GestureMotion.snapBack(reduce), v.x) }
                                    }
                                }
                                2 -> if (dragY.value > h * 0.22f || v.y > flingPx) close(v.y)
                                     else scope.launch { dragY.animateTo(0f, GestureMotion.snapBack(reduce), v.y) }
                                else -> {
                                    val held = last.uptimeMillis - down.uptimeMillis > HOLD_MS
                                    if (!held && !last.isConsumed) { if (down.position.x < w * 0.3f) go(index - 1) else go(index + 1) }
                                }
                            }
                        }
                    },
            ) {
                // the current card, and its neighbour sliding in from the side the finger is pulling
                val neighbour = when {
                    dragX.value < 0f -> index + 1
                    dragX.value > 0f -> index - 1
                    else -> -1
                }
                if (neighbour in cards.indices) {
                    Box(Modifier.fillMaxSize().graphicsLayer { translationX = dragX.value + if (neighbour > index) w else -w }) {
                        CardContent(cards[neighbour], report, onSeeData, onMove, onShare, closeThen)
                    }
                }
                Box(Modifier.fillMaxSize().graphicsLayer { translationX = dragX.value }) {
                    CardContent(cards[index], report, onSeeData, onMove, onShare, closeThen)
                }
                // progress segments and close
                Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        cards.indices.forEach { i ->
                            val f = when { i < index -> 1f; i == index -> progress.value; else -> 0f }
                            Box(Modifier.weight(1f).height(3.dp).background(Color.White.copy(alpha = 0.22f), RoundedCornerShape(2.dp))) {
                                Box(Modifier.fillMaxWidth(f).height(3.dp).background(Chalk, RoundedCornerShape(2.dp)))
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        val fmt = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
                        Text("ISSUE ${report.issue} · ${fmt.format(report.window.start).uppercase()} – ${fmt.format(report.window.endInclusive).uppercase()}", style = Mono, color = Ash, modifier = Modifier.weight(1f))
                        Box(
                            Modifier.size(40.dp).clickable(interactionSource = rememberInteraction(), indication = null) { close() },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.Close, contentDescription = "Close recap", tint = Chalk, modifier = Modifier.size(22.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CardContent(
    card: RecapCard,
    report: InsightsReport,
    onSeeData: (Question) -> Unit,
    onMove: (Move) -> Unit,
    onShare: () -> Unit,
    closeThen: (() -> Unit) -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(Brush.radialGradient(listOf(FlameHot.copy(alpha = 0.22f), Color.Transparent), center = Offset(size.width * 0.8f, size.height), radius = size.width * 1.1f))
            },
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(top = 70.dp, bottom = 24.dp)) {
            when (card) {
                RecapCard.Cover -> Cover(report)
                is RecapCard.Answer -> Answer(card, onSeeData, onMove, closeThen)
                RecapCard.Record -> RecordCard(report)
                RecapCard.Moves -> MovesCard(report, onMove, onShare, closeThen)
            }
        }
    }
}

@Composable
private fun Cover(r: InsightsReport) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.Center) {
        Text("YOUR ${r.period.noun.uppercase()} IN ${r.questions.count { it.verdict != Verdict.NOT_ENOUGH_DATA }} ANSWERS", style = Mono, color = FlameGlow)
        Text(
            r.headline.joinToString(" "),
            modifier = Modifier.padding(top = 14.dp),
            style = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 46.sp, letterSpacing = (-1.5).sp, color = Chalk),
        )
        VerdictBar(r.questions, Modifier.padding(top = 30.dp))
        Text("Tap to start", color = Ash, fontSize = 13.sp, modifier = Modifier.padding(top = 40.dp))
    }
}

@Composable
private fun Answer(card: RecapCard.Answer, onSeeData: (Question) -> Unit, onMove: (Move) -> Unit, closeThen: (() -> Unit) -> Unit) {
    val q = card.q
    val look = lookOf(q)
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text(if (card.win) "A WIN" else if (q.verdict == Verdict.WATCH) "WORTH WATCHING" else "A GAP", style = Mono, color = look.text)
            Text(q.question.title, color = Ash, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
            Text(
                q.fact,
                modifier = Modifier.padding(top = 10.dp),
                style = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 35.sp, letterSpacing = (-1).sp, color = Chalk),
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(Modifier.fillMaxWidth()) { QuestionChart(q, look, trendHeight = 260.dp) }
        }
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            GhostPill("See the data", Modifier.weight(1f)) { closeThen { onSeeData(q.question) } }
            q.move?.takeIf { !card.win && it.action != null }?.let { m ->
                PrimaryPill(m.title, null, { closeThen { onMove(m) } }, Modifier.weight(1.4f), fontSize = 14)
            }
        }
    }
}

@Composable
private fun RecordCard(r: InsightsReport) {
    val lead = r.records?.lead ?: return
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.Center) {
        Text("RECORD OF THE ${r.period.noun.uppercase()}", style = Mono, color = FlameGlow)
        Text(lead.name, color = Chalk, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                lead.value,
                style = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = if (lead.value.length > 5) 72.sp else 120.sp, letterSpacing = (-5).sp, color = FlameHot,
                    shadow = androidx.compose.ui.graphics.Shadow(FlameHot.copy(alpha = 0.55f), Offset.Zero, 40f)),
                maxLines = 1,
            )
            if (lead.unit.isNotBlank()) Text(lead.unit, color = Chalk, fontFamily = Display, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp, bottom = 20.dp))
        }
        if (lead.note.isNotBlank()) Text(lead.note, color = Ash, fontSize = 15.sp, lineHeight = 21.sp)
    }
}

@Composable
private fun MovesCard(r: InsightsReport, onMove: (Move) -> Unit, onShare: () -> Unit, closeThen: (() -> Unit) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text("YOUR MOVES", style = Mono, color = FlameGlow, modifier = Modifier.padding(start = 4.dp))
        val n = r.moves.size
        Text(
            if (n == 0) "Nothing to change. Keep going." else "${if (n == 1) "One move" else "$n moves"} for this week.",
            modifier = Modifier.padding(start = 4.dp, top = 10.dp),
            style = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 37.sp, letterSpacing = (-1).sp, color = Chalk),
        )
        Spacer(Modifier.height(22.dp))
        r.moves.forEach { m ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(18.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                    .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(m.title, color = Chalk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(m.detail, color = Ash, fontSize = 12.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                }
                if (m.action != null) {
                    Spacer(Modifier.width(10.dp))
                    PrimaryPill("Add", null, { closeThen { onMove(m) } }, Modifier.width(76.dp), height = 36.dp, fontSize = 13)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostPill("Share recap", Modifier.weight(1f)) { onShare() }
            PrimaryPill("Done", null, { closeThen {} }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun GhostPill(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val i = rememberInteraction()
    Box(
        modifier
            .pressScale(i)
            .height(48.dp)
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
            .clickable(interactionSource = i, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Chalk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
}

