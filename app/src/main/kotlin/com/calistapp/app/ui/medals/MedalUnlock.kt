package com.calistapp.app.ui.medals

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.calistapp.app.ui.common.GestureMotion
import com.calistapp.app.ui.common.rememberHaptics
import com.calistapp.app.ui.common.rememberReduceMotion
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.Body
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.Display
import com.calistapp.app.ui.theme.Flame
import com.calistapp.app.ui.theme.FlameGlow
import com.calistapp.app.ui.theme.Onyx
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class Phase { INTRO, REST, FLY, DONE }

/** A flight between two places; scale and scrim are read off how far along y it has got. */
private class Flight(val y0: Float, val yT: Float, val s0: Float, val sT: Float, val scr0: Float, val scrT: Float, val final: Boolean, val back: Boolean = false)

/**
 * The unlock moment on the workout summary: "A · Drop". Each new medal falls in on its ribbon (a spring
 * of response 0.62 s / damping 0.74 for the fall, a pendulum from −16° of 1.1 s / 0.3 around the
 * ribbon's top), knocks once when it lands, and at 640 ms the rim is engraved with the number and the
 * date at a steady speed. Then it goes into the case: "Put it in the case", or a drag down, flies it
 * into its slot in [slotOf] (the summary's "Earned today"), shrinking as the dim lifts, both read off
 * the one flight. It can be caught on the way. Several medals queue: "Next medal" parks each in a tray
 * and "Put all in the case" takes the rest at once. A tap during the fall skips to the end (no knock).
 * Reduced motion fades instead of falling and flying.
 *
 * @param slotOf where an award's slot is on the summary, in root coordinates, when it is laid out.
 * @param onSeated an award has arrived in its slot: show it there.
 * @param onFinished every award here has been shown (or put away unseen with "all"): mark them seen.
 */
@Composable
fun MedalUnlockOverlay(
    medals: List<StruckMedal>,
    slotOf: (String) -> Rect?,
    onSeated: (String) -> Unit,
    onFinished: (List<String>) -> Unit,
) {
    if (medals.isEmpty()) return
    val reduce = rememberReduceMotion()
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val context = LocalContext.current
    val resolver = LocalFontFamilyResolver.current
    val seat by rememberUpdatedState(onSeated)
    val finish by rememberUpdatedState(onFinished)

    var idx by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf(Phase.INTRO) }
    var flight by remember { mutableStateOf<Flight?>(null) }
    var rest by remember { mutableStateOf<Rect?>(null) }
    var sharing by remember { mutableStateOf(false) }
    val trayBounds = remember { mutableStateMapOf<Int, Rect>() }
    val inTray = remember { mutableStateListOf<Int>() }

    val x = remember { Animatable(0f) }
    val y = remember { Animatable(0f) }
    val rot = remember { Animatable(0f) }
    val turn = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }
    val scrim = remember { Animatable(0f) }
    val eng = remember { Animatable(0f) }
    val cap = remember { Animatable(0f) }
    val act = remember { Animatable(0f) }
    var intro by remember { mutableStateOf<Job?>(null) }

    val dropPx = with(density) { 440.dp.toPx() }
    val dismissPx = with(density) { 120.dp.toPx() }
    val flingPx = GestureMotion.flingPx(density)
    val d520 = with(density) { 520.dp.toPx() }
    val d340 = with(density) { 340.dp.toPx() }
    val medal = medals[idx.coerceIn(0, medals.lastIndex)]
    val engraveMs = (medal.award.rimText().length * 26).coerceIn(620, 1300)

    /** The medal's drawn scale and the dim behind it, both derived from where the medal is. */
    fun displayed(): Pair<Float, Float> {
        val f = flight
        if (f != null) {
            val span = f.yT - f.y0
            val p = if (abs(span) < 1f) 1f else ((y.value - f.y0) / span).coerceIn(0f, 1f)
            return lerp(f.s0, f.sT, p) to lerp(f.scr0, f.scrT, p)
        }
        val d = y.value.coerceAtLeast(0f)
        return (1f - (d / d520).coerceIn(0f, 1f) * 0.22f) to scrim.value * (1f - (d / d340).coerceIn(0f, 1f) * 0.55f)
    }

    fun showMedal(i: Int) {
        idx = i
        phase = Phase.INTRO
        flight = null
        intro?.cancel()
        intro = scope.launch {
            x.snapTo(0f); turn.snapTo(0f); eng.snapTo(0f); cap.snapTo(0f); act.snapTo(0f)
            if (reduce) {
                y.snapTo(0f); rot.snapTo(0f)
                launch { alpha.animateTo(1f, tween(220, easing = LinearEasing)) }
                eng.snapTo(1f)
                launch { cap.animateTo(1f, tween(220, easing = LinearEasing)) }
                launch { act.animateTo(1f, tween(220, easing = LinearEasing)) }
                haptics.tick()
                phase = Phase.REST
                return@launch
            }
            alpha.snapTo(1f)
            y.snapTo(-dropPx)
            rot.snapTo(-16f)
            var knocked = false
            launch {
                y.animateTo(0f, GestureMotion.spring(0.62f, 0.26f)) {
                    if (!knocked && value >= 0f) { knocked = true; haptics.impact() }
                }
            }
            launch { rot.animateTo(0f, GestureMotion.spring(1.1f, 0.7f)) }
            delay(640)
            phase = Phase.REST
            launch { eng.animateTo(1f, tween(engraveMs, easing = LinearEasing)) }
            launch { cap.animateTo(1f, tween(380)) }
            delay(70)
            act.animateTo(1f, tween(320))
        }
    }

    /** A tap during the fall: straight to the end, without the knock. */
    fun skip() {
        if (phase != Phase.INTRO) return
        intro?.cancel()
        scope.launch {
            alpha.snapTo(1f); y.snapTo(0f); rot.snapTo(0f); eng.snapTo(1f); cap.snapTo(1f); act.snapTo(1f)
            phase = Phase.REST
        }
    }

    LaunchedEffect(Unit) {
        launch { scrim.animateTo(1f, tween(if (reduce) 200 else 280)) }
        showMedal(0)
    }

    fun fly(target: Rect?, vx: Float, vy: Float, dragged: Boolean, final: Boolean, onArrive: () -> Unit) {
        intro?.cancel()
        val (sc, scr) = displayed()
        phase = Phase.FLY
        scope.launch {
            launch { cap.animateTo(0f, tween(140)) }
            launch { act.animateTo(0f, tween(140)) }
            if (reduce) {
                if (final) launch { scrim.animateTo(0f, tween(200, easing = LinearEasing)) }
                alpha.animateTo(0f, tween(180, easing = LinearEasing))
                onArrive()
                return@launch
            }
            val from = rest
            val (dx, dy, k) = if (target != null && from != null && from.width > 0f) {
                val c = target.center - from.center
                Triple(c.x, c.y, target.width / from.width)
            } else {
                Triple(0f, dropPx * 2f, 0.3f)
            }
            flight = Flight(y.value, dy, sc, k, scr, if (final) 0f else scr, final)
            val bounce = if (dragged) 0.18f else 0f
            launch { x.animateTo(dx, GestureMotion.spring(0.5f, bounce), initialVelocity = vx) }
            launch { rot.animateTo(0f, GestureMotion.spring(0.4f, 0f)) }
            launch { turn.animateTo((turn.value / 360f).roundToInt() * 360f, GestureMotion.spring(0.4f, 0f)) }
            y.animateTo(dy, GestureMotion.spring(0.5f, bounce), initialVelocity = vy)
            if (flight?.back == true) return@launch
            onArrive()
        }
    }

    fun putInCase(vx: Float = 0f, vy: Float = 0f, dragged: Boolean = false, all: Boolean = false) {
        if (phase == Phase.FLY || phase == Phase.DONE) return
        val current = idx
        fly(slotOf(medals[current].award.key), vx, vy, dragged, final = true) {
            alpha.let { scope.launch { it.snapTo(0f) } }
            // Everything parked in the tray goes in with it.
            inTray.toList().forEachIndexed { n, i -> scope.launch { delay(40L * n); seat(medals[i].award.key) } }
            inTray.clear()
            seat(medals[current].award.key)
            haptics.tick()
            scope.launch {
                if (all) {
                    for (i in current + 1..medals.lastIndex) { delay(60L + (i - current) * 50L); seat(medals[i].award.key) }
                }
                scrim.snapTo(0f)
                phase = Phase.DONE
                finish(medals.map { it.award.key })
            }
        }
    }

    fun toTray(vx: Float = 0f, vy: Float = 0f, dragged: Boolean = false) {
        if (phase == Phase.FLY) return
        val current = idx
        fly(trayBounds[current], vx, vy, dragged, final = false) {
            inTray += current
            scope.launch {
                alpha.snapTo(0f)
                flight = null
                delay(120)
                showMedal(current + 1)
            }
        }
    }

    BackHandler(enabled = phase != Phase.DONE) { if (phase == Phase.INTRO) skip(); putInCase(all = true) }

    if (phase == Phase.DONE) return
    val last = idx == medals.lastIndex
    val (shownScale, shownScrim) = displayed()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // The dim; a tap anywhere during the fall skips to the end.
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.84f * shownScrim))
                .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false); skip() } }
                .clickable(remember { MutableInteractionSource() }, indication = null) {},
        )

        // Medals already shown wait in the tray, top right, until they all go in together.
        if (medals.size > 1) {
            val screenWidth = maxWidth
            Row(
                Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 12.dp, end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Many medals at once (an import, a first big session): the minis shrink so the tray fits.
                val mini = ((screenWidth * 0.6f) / medals.size - 6.dp).coerceIn(12.dp, 28.dp)
                medals.forEachIndexed { i, m ->
                    Box(
                        Modifier
                            .width(mini)
                            .onGloballyPositioned { trayBounds[i] = it.boundsInRoot() }
                            .graphicsLayer { this.alpha = if (i in inTray) 1f else 0f },
                    ) { Medal(m.definition, m.award.tierIndex, MedalLook.STRUCK, shadow = false) }
                }
            }
        }

        Column(
            Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.width(176.dp).onGloballyPositioned { rest = it.boundsInRoot() }) {
                Box(
                    Modifier
                        .graphicsLayer {
                            translationX = x.value
                            translationY = y.value
                            scaleX = shownScale
                            scaleY = shownScale
                            this.alpha = alpha.value
                        }
                        .pointerInput(idx) {
                            val tracker = VelocityTracker()
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val f = flight
                                val catching = phase == Phase.FLY && f != null && f.final && !f.back && !reduce
                                if (phase == Phase.INTRO) { skip(); return@awaitEachGesture }
                                if (phase != Phase.REST && !catching) return@awaitEachGesture
                                scope.launch { x.stop(); y.stop(); turn.stop() }
                                tracker.resetTracking()
                                tracker.addPosition(down.uptimeMillis, down.position)
                                var axis = if (catching) 2 else 0
                                var dx = 0f
                                var dy = 0f
                                val startX = x.value
                                val startY = y.value
                                val startTurn = turn.value
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    val d = change.positionChange()
                                    dx += d.x
                                    dy += d.y
                                    tracker.addPosition(change.uptimeMillis, change.position)
                                    if (axis == 0 && (abs(dx) > viewConfiguration.touchSlop || abs(dy) > viewConfiguration.touchSlop)) {
                                        axis = if (abs(dx) > abs(dy)) 1 else 2
                                    }
                                    if (axis == 1) {
                                        change.consume()
                                        scope.launch { turn.snapTo(startTurn + dx * 0.45f) }
                                    } else if (axis == 2) {
                                        change.consume()
                                        scope.launch {
                                            if (catching) x.snapTo(startX + dx)
                                            y.snapTo(if (catching) startY + dy else GestureMotion.elastic(startY + dy, 0f, Float.MAX_VALUE))
                                        }
                                    }
                                }
                                val v = tracker.calculateVelocity()
                                when {
                                    catching && f != null -> {
                                        // Let go of a medal caught on its way in: home, or on it goes.
                                        val span = f.yT - f.y0
                                        val p = if (abs(span) < 1f) 1f else ((y.value - f.y0) / span).coerceIn(0f, 1f)
                                        val goBack = v.y < -300f || (v.y < 300f && p < 0.5f)
                                        if (goBack) {
                                            val (sc, scr) = displayed()
                                            flight = Flight(y.value, 0f, sc, 1f, scr, 1f, final = false, back = true)
                                            scope.launch {
                                                launch { x.animateTo(0f, GestureMotion.spring(0.42f, 0.15f), initialVelocity = v.x) }
                                                y.animateTo(0f, GestureMotion.spring(0.42f, 0.15f), initialVelocity = v.y)
                                                flight = null
                                                phase = Phase.REST
                                                launch { cap.animateTo(1f, tween(240)) }
                                                act.animateTo(1f, tween(240))
                                            }
                                        } else {
                                            phase = Phase.REST
                                            flight = null
                                            putInCase(v.x, v.y, dragged = true)
                                        }
                                    }
                                    axis == 0 -> {
                                        val next = (turn.value / 180f).roundToInt() * 180f + 180f
                                        haptics.tick()
                                        scope.launch { turn.animateTo(next, GestureMotion.settle(0.45f)) }
                                    }
                                    axis == 1 -> {
                                        val vDeg = v.x * 0.45f
                                        val landing = (((turn.value + vDeg * 0.099f) / 180f).roundToInt() * 180f).coerceIn(startTurn - 180f, startTurn + 180f)
                                        if ((landing / 180f).roundToInt() != (startTurn / 180f).roundToInt()) haptics.tick()
                                        scope.launch { turn.animateTo(landing, GestureMotion.release(reduce), initialVelocity = vDeg) }
                                    }
                                    else -> {
                                        if (y.value > dismissPx || v.y > flingPx) {
                                            if (last) putInCase(v.x, v.y, dragged = true) else toTray(v.x, v.y, dragged = true)
                                        } else {
                                            scope.launch { y.animateTo(0f, GestureMotion.spring(0.42f, if (reduce) 0f else 0.15f), initialVelocity = v.y) }
                                        }
                                    }
                                }
                            }
                        },
                ) {
                    // The pendulum swings around the top of the ribbon.
                    Box(Modifier.graphicsLayer { transformOrigin = TransformOrigin(0.5f, 0f); rotationZ = rot.value }) {
                        val face = ((abs(turn.value) % 360f) + 360f) % 360f
                        Box(Modifier.graphicsLayer { rotationY = turn.value; cameraDistance = 14f * density.density }) {
                            if (face in 90f..270f) {
                                MedalBack(
                                    medal.definition, medal.award.tierIndex, medal.award.engraving, medal.award.longDate(),
                                    modifier = Modifier.graphicsLayer { rotationY = 180f },
                                )
                            } else {
                                Medal(medal.definition, medal.award.tierIndex, MedalLook.STRUCK, rim = medal.award.rimText(), rimReveal = eng.value)
                            }
                        }
                    }
                }
            }

            Column(
                Modifier.padding(top = 26.dp).graphicsLayer { this.alpha = cap.value; translationY = (1f - cap.value) * 10.dp.toPx() },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val n = medals.size
                Text(
                    ((if (n > 1) "${idx + 1} of $n · " else "") + "Medal earned · ${medal.definition.kind.label}").uppercase(),
                    style = MedalMono,
                    color = FlameGlow,
                    textAlign = TextAlign.Center,
                )
                Text(
                    medal.definition.titleFor(medal.award.tierIndex),
                    style = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 25.sp, lineHeight = 28.sp, letterSpacing = (-0.4).sp, hyphens = Hyphens.Auto),
                    color = Chalk,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    medal.award.valueLine(),
                    style = TextStyle(fontFamily = Body, fontSize = 14.sp),
                    color = Ash,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Column(
                Modifier.padding(top = 24.dp).fillMaxWidth().graphicsLayer { this.alpha = act.value; translationY = (1f - act.value) * 8.dp.toPx() },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val enabled = phase == Phase.REST || phase == Phase.INTRO
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Flame)
                        .clickable(enabled = enabled) {
                            skip()
                            if (!last) toTray() else putInCase()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (!last) "Next medal" else if (medals.size > 1) "Put them in the case" else "Put it in the case",
                        style = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 15.sp),
                        color = Onyx,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (!last) "Put all in the case" else if (sharing) "Preparing…" else "Share",
                    style = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
                    color = Chalk,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable(enabled = enabled && !sharing) {
                            if (!last) {
                                skip()
                                putInCase(all = true)
                            } else {
                                sharing = true
                                scope.launch {
                                    shareMedal(context, medal, TextMeasurer(resolver, density, LayoutDirection.Ltr), density)
                                    sharing = false
                                }
                            }
                        }
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                )
            }
        }
    }
}

