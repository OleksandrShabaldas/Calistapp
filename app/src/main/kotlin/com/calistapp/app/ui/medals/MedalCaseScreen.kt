package com.calistapp.app.ui.medals

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calistapp.app.ui.common.Entrance
import com.calistapp.app.ui.common.GestureModalSheet
import com.calistapp.app.ui.common.GestureMotion
import com.calistapp.app.ui.common.rememberHaptics
import com.calistapp.app.ui.common.rememberReduceMotion
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.AshFaint
import com.calistapp.app.ui.theme.Body
import com.calistapp.app.ui.theme.CardFlat
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.Display
import com.calistapp.app.ui.theme.Eyebrow
import com.calistapp.app.ui.theme.Flame
import com.calistapp.app.ui.theme.FlameGlow
import com.calistapp.app.ui.theme.FlameHot
import com.calistapp.app.ui.theme.PageDim
import com.calistapp.app.ui.theme.PageInk
import com.calistapp.app.ui.theme.PageWarm
import com.calistapp.core.medals.MedalCatalog
import com.calistapp.core.medals.MedalKind
import com.calistapp.core.medals.MedalLine
import com.calistapp.core.medals.MedalReport
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

internal val MedalMono = Eyebrow.copy(fontSize = 10.sp, letterSpacing = 1.4.sp)
internal val CardBorder = Color(0xFF1D1D21)
internal val RowRule = Color(0xFF1C1C20)

/**
 * The medal case ("D · Spotlight"): the medals you have struck in a carousel you swipe through, then
 * the three closest next, then the whole case as one shelf per kind. Before anything is struck it
 * previews the reward instead ("C · Preview"): a faded sample on day 0, the closest medal filling in
 * as a ghost once training is under way.
 */
@Composable
fun MedalCaseScreen(onBack: () -> Unit, focus: String? = null, viewModel: MedalsViewModel = hiltViewModel()) {
    val report by viewModel.report.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf<OpenMedal?>(null) }
    var shelf by remember { mutableStateOf<MedalKind?>(null) }
    // Opened from a medal on Insights: that medal comes up lifted, once, over its place in the case.
    var focused by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(report, focus) {
        val r = report ?: return@LaunchedEffect
        if (focus == null || focused) return@LaunchedEffect
        val all = r.struckNewestFirst()
        val i = all.indexOfFirst { it.award.key == focus }
        if (i >= 0) open = OpenMedal(all[i], null, "${i + 1} of ${all.size}")
        focused = true
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(0f to PageWarm, 0.26f to PageDim, 1f to PageInk)),
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()) {
            val r = report
            CaseBar(
                onBack = onBack,
                subtitle = when {
                    r == null -> " "
                    r.struckCount == 0 -> "Nothing struck yet"
                    else -> "${r.struckCount} of ${r.totalTiers} struck"
                },
            )
            if (r == null) {
                Box(Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = FlameHot) }
                return@Column
            }
            val struck = r.struckNewestFirst()
            val closest = r.closestNext(4)
            if (struck.isEmpty()) {
                Entrance(0) { PreviewHero(closest.firstOrNull()) }
                Entrance(1) {
                    if (closest.isEmpty()) StarterCard() else NextCard("Also under way", closest.drop(1).take(3))
                }
            } else {
                Entrance(0) {
                    Spotlight(struck, startAt = struck.indexOfFirst { it.award.key == focus }.coerceAtLeast(0)) { medal, rect, pos ->
                        open = OpenMedal(medal, rect, pos)
                    }
                }
                if (closest.isNotEmpty()) Entrance(1) { NextCard("Closest next", closest.take(3)) }
            }
            Entrance(2) { Shelves(r) { shelf = it } }
            Spacer(Modifier.height(28.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }

        shelf?.let { kind ->
            report?.let { r ->
                KindSheet(r, kind, onOpen = { medal -> open = OpenMedal(medal, null, null) }, onDismiss = { shelf = null })
            }
        }
        open?.let { o -> MedalFullView(o.medal, o.origin, onDismiss = { open = null }, position = o.position) }
    }
}

private data class OpenMedal(val medal: StruckMedal, val origin: Rect?, val position: String?)

@Composable
private fun CaseBar(onBack: () -> Unit, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp)) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF18181B)).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Chalk, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.padding(start = 4.dp, top = 14.dp)) {
            Text("ACHIEVEMENTS", style = MedalMono, color = FlameHot)
            Text(
                "Your case",
                style = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, letterSpacing = (-0.6).sp),
                color = Chalk,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(subtitle, style = TextStyle(fontFamily = Body, fontSize = 13.sp), color = Ash, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

// ------------------------------------------------------------------------------------------- spotlight

/**
 * The struck medals, newest first, in a carousel. While the finger is down the row is the finger
 * (1:1, rubber-banding past either end); on release it lands on the medal the flick was heading for,
 * projected with a 0.99 deceleration, on a spring seeded with the release speed. Tapping a side medal
 * brings it to the centre; tapping the centre one lifts it out.
 */
@Composable
private fun Spotlight(items: List<StruckMedal>, startAt: Int = 0, onOpen: (StruckMedal, Rect?, String) -> Unit) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val reduce = rememberReduceMotion()
    val spacing = with(density) { 200.dp.toPx() }
    val medalHalf = with(density) { 88.dp.toPx() }
    val pos = remember { Animatable(startAt.coerceIn(0, items.lastIndex) * spacing) }
    var centreBounds by remember { mutableStateOf<Rect?>(null) }
    val last = items.lastIndex
    // Read live inside the gesture: the handler outlives compositions, so a captured index goes stale.
    fun centred(): Int = (pos.value / spacing).roundToInt().coerceIn(0, last)
    val current = centred()

    // A tick each time a new medal reaches the centre — only while it is moving, never on first show.
    LaunchedEffect(items.size) {
        snapshotFlow { (pos.value / spacing).roundToInt() }.distinctUntilChanged().drop(1).collect { haptics.tick() }
    }

    fun goTo(index: Int, velocity: Float = 0f, tapped: Boolean = false) {
        scope.launch {
            pos.animateTo(
                index.coerceIn(0, last) * spacing,
                if (tapped) GestureMotion.settle(0.42f) else GestureMotion.release(reduce),
                initialVelocity = velocity,
            )
        }
    }

    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        val centreX = with(density) { maxWidth.toPx() } / 2f
        Box(
            Modifier
                .fillMaxWidth()
                .height(250.dp)
                .pointerInput(items.size, centreX) {
                    val tracker = VelocityTracker()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        scope.launch { pos.stop() }
                        tracker.resetTracking()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        val start = pos.value
                        var dx = 0f
                        var dy = 0f
                        var horizontal = false
                        var abandoned = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val d = change.positionChange()
                            dx += d.x
                            dy += d.y
                            tracker.addPosition(change.uptimeMillis, change.position)
                            if (!horizontal && !abandoned) {
                                if (abs(dx) > viewConfiguration.touchSlop && abs(dx) > abs(dy)) horizontal = true
                                else if (abs(dy) > viewConfiguration.touchSlop) abandoned = true
                            }
                            if (abandoned) break
                            if (horizontal) {
                                change.consume()
                                val raw = start - dx
                                scope.launch { pos.snapTo(GestureMotion.elastic(raw, 0f, last * spacing)) }
                            }
                        }
                        when {
                            horizontal -> {
                                val v = -tracker.calculateVelocity().x
                                val projected = pos.value + v * 0.099f
                                // Lands on the medal the flick was heading for, never more than one away per flick of a short row.
                                goTo((projected / spacing).roundToInt(), v)
                            }
                            abandoned -> goTo(centred())
                            else -> {
                                val x = down.position.x - centreX
                                val at = centred()
                                when {
                                    abs(x) <= medalHalf -> onOpen(items[at], centreBounds, "${at + 1} of ${items.size}")
                                    x < 0 -> goTo(at - 1, tapped = true)
                                    else -> goTo(at + 1, tapped = true)
                                }
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            // The warm light the struck medal sits in.
            Box(
                Modifier
                    .size(250.dp)
                    .background(Brush.radialGradient(0f to FlameHot.copy(alpha = 0.26f), 0.65f to Color.Transparent)),
            )
            val first = ((pos.value / spacing).toInt() - 2).coerceAtLeast(0)
            val end = ((pos.value / spacing).toInt() + 2).coerceAtMost(last)
            for (i in first..end) {
                val dxPx = i * spacing - pos.value
                val t = (abs(dxPx) / spacing).coerceIn(0f, 1f)
                Box(
                    Modifier
                        .width(176.dp)
                        .then(if (i == current) Modifier.onGloballyPositioned { centreBounds = it.boundsInRoot() } else Modifier)
                        .graphicsLayer {
                            translationX = dxPx
                            translationY = lerp(0f, 22.dp.toPx(), t)
                            val s = lerp(1f, 0.62f, t)
                            scaleX = s
                            scaleY = s
                            alpha = lerp(1f, 0.38f, t)
                        },
                ) {
                    val m = items[i]
                    Medal(m.definition, m.award.tierIndex, MedalLook.STRUCK, rim = if (t < 0.5f) m.award.rimText() else null)
                }
            }
        }

        // Kept out of the scrolled row so it never moves with the finger, and keyed to the centred medal.
        Column(
            Modifier.fillMaxWidth().padding(top = 262.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val m = items[current]
            val eyebrow = if (items.size == 1) "Your first medal · ${m.kindAndMetal()}" else "${current + 1} of ${items.size} · ${m.kindAndMetal()}"
            Text(eyebrow.uppercase(), style = MedalMono, color = FlameGlow, textAlign = TextAlign.Center)
            Text(
                m.definition.titleFor(m.award.tierIndex),
                style = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 28.sp, hyphens = Hyphens.Auto),
                color = Chalk,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                "${m.award.valueLine()} · ${m.award.shortDate()}",
                style = TextStyle(fontFamily = Body, fontSize = 13.sp),
                color = Ash,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
            if (items.size > 1) {
                Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    // Past a dozen, dots stop being countable: show a window of nine around the current one.
                    val window = if (items.size <= 12) items.indices else (current - 4).coerceAtLeast(0).let { s -> s..(s + 8).coerceAtMost(last) }
                    for (i in window) {
                        val w by animateFloatAsState(if (i == current) 18f else 6f, tween(220, easing = FastOutSlowInEasing), label = "dot")
                        Box(
                            Modifier
                                .size(width = w.dp, height = 6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (i == current) FlameGlow else Color(0xFF3A3A3F)),
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------- preview (no medals)

/** "C · Preview the reward": the closest medal as a ghost, or a faded sample before anything is under way. */
@Composable
private fun PreviewHero(closest: NextMedal?) {
    Column(Modifier.fillMaxWidth().padding(top = 18.dp, start = 24.dp, end = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val definition = closest?.definition ?: PreviewMedal
        val tier = closest?.tierIndex ?: 0
        val ghost = closest?.fraction ?: 0.3f
        Box(contentAlignment = Alignment.Center) {
            if (closest != null) {
                Box(Modifier.size(230.dp).background(Brush.radialGradient(0f to FlameHot.copy(alpha = 0.10f + 0.16f * ghost), 0.65f to Color.Transparent)))
            }
            Medal(
                definition, tier, MedalLook.GHOST,
                modifier = Modifier.width(176.dp),
                ghost = ghost,
                rim = if (closest == null) "YOUR NUMBER · YOUR DATE" else "YOUR ${unitWord(closest)} · YOUR DATE",
            )
        }
        Text(
            if (closest == null) "PREVIEW" else "CLOSEST · ${(closest.fraction * 100).roundToInt()} % THERE",
            style = MedalMono,
            color = if (closest == null) Ash else FlameGlow,
            modifier = Modifier.padding(top = 18.dp),
        )
        Text(
            if (closest == null) "Every medal looks like this" else definition.titleFor(tier),
            style = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 28.sp, hyphens = Hyphens.Auto),
            color = Chalk,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            if (closest == null) "Your number and date go on the rim" else "${closest.label} · ${closest.toGo}",
            style = TextStyle(fontFamily = Body, fontSize = 13.sp),
            color = Ash,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

/** "YOUR REPS", "YOUR WEEKS": what the rim of a ghost will hold. */
private fun unitWord(next: NextMedal): String = when (next.definition.unit) {
    com.calistapp.core.medals.MedalUnit.REPS, com.calistapp.core.medals.MedalUnit.LEGS -> "REPS"
    com.calistapp.core.medals.MedalUnit.SECONDS -> "TIME"
    com.calistapp.core.medals.MedalUnit.KG -> "WEIGHT"
    com.calistapp.core.medals.MedalUnit.STREAK_WEEKS, com.calistapp.core.medals.MedalUnit.WEEKS -> "WEEKS"
    else -> "NUMBER"
}

@Composable
private fun StarterCard() {
    CaseCard("Where most people start") {
        StarterMedals.forEachIndexed { i, (id, sub) ->
            val d = MedalCatalog.byId(id) ?: return@forEachIndexed
            Row(
                Modifier.fillMaxWidth().then(if (i > 0) Modifier.topRule() else Modifier).padding(vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Medal(d, 0, MedalLook.LOCKED, modifier = Modifier.width(40.dp), ribbon = false)
                Column(Modifier.weight(1f)) {
                    Text(d.titleFor(0), style = RowTitle, color = Chalk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(sub, style = RowSub, color = Ash, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------- closest next

@Composable
internal fun NextCard(title: String, rows: List<NextMedal>) {
    if (rows.isEmpty()) return
    CaseCard(title) {
        rows.forEachIndexed { i, n -> NextRow(n, top = i > 0) }
    }
}

@Composable
internal fun NextRow(n: NextMedal, top: Boolean) {
    // The bar fills from empty when it first appears, rather than snapping to its value.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val fill by animateFloatAsState(if (shown) n.fraction else 0f, tween(700, easing = FastOutSlowInEasing), label = "next")
    Row(
        Modifier.fillMaxWidth().then(if (top) Modifier.topRule() else Modifier).padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Medal(n.definition, n.tierIndex, MedalLook.LOCKED, modifier = Modifier.width(40.dp), ribbon = false, progress = if (n.fraction >= 0.5f) n.fraction else null)
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(n.definition.titleFor(n.tierIndex), style = RowTitle, color = Chalk, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(n.toGo.uppercase(), style = MedalMono, color = FlameGlow, maxLines = 1)
            }
            Box(Modifier.padding(top = 7.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF26262A))) {
                Box(
                    Modifier
                        .fillMaxWidth(fill.coerceIn(0f, 1f))
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Brush.horizontalGradient(listOf(Flame, FlameGlow))),
                )
            }
        }
    }
}

// ------------------------------------------------------------------------------------------- shelves

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Shelves(report: MedalReport, onOpen: (MedalKind) -> Unit) {
    CaseCard("The whole case · ${report.struckCount} of ${report.totalTiers}") {
        MedalKind.entries.forEachIndexed { i, kind ->
            val (earned, total) = report.countsFor(kind)
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(if (i > 0) Modifier.topRule() else Modifier)
                    .clickable { onOpen(kind) }
                    .padding(vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.width(92.dp)) {
                    Text(kind.label, style = RowTitle.copy(fontSize = 13.5.sp), color = Chalk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("$earned / $total", style = MedalMono.copy(fontSize = 9.5.sp), color = Ash)
                }
                FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    report.lines.filter { it.definition.kind == kind }.forEach { line ->
                        line.definition.tiers.indices.forEach { ti ->
                            Medal(line.definition, ti, lookOf(line, ti), modifier = Modifier.width(22.dp), ribbon = false, shadow = false)
                        }
                    }
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = AshFaint, modifier = Modifier.size(18.dp))
            }
        }
    }
}

internal fun lookOf(line: MedalLine, tierIndex: Int): MedalLook = when {
    line.awards.any { it.tierIndex == tierIndex } -> MedalLook.STRUCK
    line.definition.hidden -> MedalLook.HIDDEN
    else -> MedalLook.LOCKED
}

/** Every medal of one kind, as a grid: struck ones open big, the rest say what earns them. */
@Composable
private fun KindSheet(report: MedalReport, kind: MedalKind, onOpen: (StruckMedal) -> Unit, onDismiss: () -> Unit) {
    val (earned, total) = report.countsFor(kind)
    GestureModalSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF101012)) { sheet ->
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val (dark, light) = MedalPalette.ribbon(kind)
                Box(Modifier.size(width = 18.dp, height = 8.dp).clip(RoundedCornerShape(2.dp)).background(Brush.horizontalGradient(0.5f to dark, 0.5f to light)))
                Text(kind.label, style = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 22.sp), color = Chalk, modifier = Modifier.weight(1f))
                Text("$earned / $total", style = MedalMono, color = Ash)
            }
            val cells = report.lines.filter { it.definition.kind == kind }.flatMap { line -> line.definition.tiers.indices.map { line to it } }
            cells.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (line, ti) -> KindCell(line, ti, Modifier.weight(1f)) { award -> sheet.dismiss { onOpen(StruckMedal(line, award)) } } }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun KindCell(line: MedalLine, tierIndex: Int, modifier: Modifier, onOpen: (com.calistapp.core.medals.MedalAward) -> Unit) {
    val award = line.awards.firstOrNull { it.tierIndex == tierIndex }
    val look = lookOf(line, tierIndex)
    val p = line.progress
    val isNext = award == null && p != null && p.nextTierIndex == tierIndex && p.measurable
    val sub = when {
        award != null -> award.shortDate().uppercase()
        look == MedalLook.HIDDEN -> MedalCatalog.HIDDEN_HOW.uppercase()
        isNext && p != null && p.fraction > 0 -> p.toGo.uppercase()
        else -> line.definition.how.uppercase()
    }
    Column(
        modifier.then(if (award != null) Modifier.clickable { onOpen(award) } else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Medal(
            line.definition, tierIndex, look,
            modifier = Modifier.width(84.dp),
            progress = if (isNext && p != null && p.fraction >= 0.5) p.fraction.toFloat() else null,
            description = line.definition.titleFor(tierIndex, award != null),
        )
        Text(
            line.definition.titleFor(tierIndex, award != null),
            style = RowTitle.copy(fontSize = 12.sp, lineHeight = 15.sp, hyphens = Hyphens.Auto),
            color = if (award != null) Chalk else Color(0xFFC9C9CD),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            sub,
            style = MedalMono.copy(fontSize = 9.sp, letterSpacing = 0.6.sp),
            color = if (isNext && (p?.fraction ?: 0.0) >= 0.75) FlameGlow else Ash,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

// ------------------------------------------------------------------------------------------- shared bits

internal val RowTitle = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp)
internal val RowSub = TextStyle(fontFamily = Body, fontSize = 12.sp, lineHeight = 16.sp)

@Composable
internal fun CaseCard(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = 14.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(CardFlat)
            .border(1.dp, CardBorder, RoundedCornerShape(22.dp))
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
    ) {
        Text(title.uppercase(), style = MedalMono, color = Ash, modifier = Modifier.padding(bottom = 6.dp))
        content()
    }
}

internal fun Modifier.topRule(): Modifier = drawBehind {
    drawLine(RowRule, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
}
