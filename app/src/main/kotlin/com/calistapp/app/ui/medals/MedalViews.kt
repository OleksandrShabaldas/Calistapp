package com.calistapp.app.ui.medals

import android.content.Context
import android.graphics.Picture
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.calistapp.app.ui.common.GestureMotion
import com.calistapp.app.ui.common.blockTouchesBelow
import com.calistapp.app.ui.common.rememberHaptics
import com.calistapp.app.ui.common.rememberReduceMotion
import com.calistapp.app.ui.exercises.sharePicture
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.Body
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.Display
import com.calistapp.app.ui.theme.Eyebrow
import com.calistapp.app.ui.theme.FlameGlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Degrees a medal turns per pixel of horizontal drag: a full half-turn over about 400 px. */
private const val DEG_PER_PX = 0.45f

/**
 * A struck medal, lifted out of the slot it was tapped in ([origin], in root coordinates) to fill the
 * screen. While a finger is on it the medal is the finger: drag it down to put it back (past 120 dp or
 * on a flick; it rubber-bands if pushed up), or drag sideways to turn it over, where it lands on the
 * face the flick was heading for. Tapping the medal turns it with no bounce; tapping around it, or
 * back, drops it back into its slot.
 */
@Composable
fun MedalFullView(
    medal: StruckMedal,
    origin: Rect?,
    onDismiss: () -> Unit,
    position: String? = null,
) {
    val reduce = rememberReduceMotion()
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    val context = LocalContext.current
    val resolver = LocalFontFamilyResolver.current

    val lift = remember { Animatable(0f) }
    val dragY = remember { Animatable(0f) }
    val turn = remember { Animatable(0f) }
    var target by remember { mutableStateOf<Rect?>(null) }
    var closing by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        lift.animateTo(1f, if (reduce) tween(160) else GestureMotion.spring(0.48f, 0.12f))
    }

    fun dropBack() {
        if (closing) return
        closing = true
        scope.launch {
            launch { turn.animateTo((turn.value / 360f).roundToInt() * 360f, GestureMotion.settle(0.3f)) }
            lift.animateTo(0f, if (reduce) tween(140) else GestureMotion.settle(0.36f))
            onDismiss()
        }
    }

    fun throwAway(velocity: Float, distance: Float) {
        if (closing) return
        closing = true
        scope.launch {
            dragY.animateTo(distance, GestureMotion.release(reduce, bounce = 0f, durationS = 0.35f), initialVelocity = velocity)
            onDismiss()
        }
    }

    BackHandler { dropBack() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heightPx = with(density) { maxHeight.toPx() }
        val dismissPx = with(density) { 120.dp.toPx() }
        val flingPx = GestureMotion.flingPx(density)
        val medalWidth = min(maxWidth * 0.64f, 280.dp)
        val pulled = (dragY.value / (heightPx * 0.5f)).coerceIn(0f, 1f)
        val presence = lift.value.coerceIn(0f, 1f) * (1f - pulled)

        // Scrim and dim are derived from the medal's own position, never animated separately.
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.88f * presence))
                .blockTouchesBelow()
                .clickable(remember { MutableInteractionSource() }, indication = null) { dropBack() },
        )

        Column(
            Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier
                    .width(medalWidth)
                    .onGloballyPositioned { target = it.boundsInRoot() },
            ) {
                val from = origin
                val to = target
                val t = lift.value
                val startScale = if (from != null && to != null && to.width > 0f) from.width / to.width else 0.85f
                val startShift = if (from != null && to != null) from.center - to.center else Offset(0f, 40f)
                val face = ((abs(turn.value) % 360f) + 360f) % 360f
                val showBack = face in 90f..270f
                Box(
                    Modifier
                        .graphicsLayer {
                            translationX = startShift.x * (1f - t)
                            translationY = startShift.y * (1f - t) + dragY.value
                            val s = lerp(startScale, 1f, t) * (1f - 0.18f * pulled)
                            scaleX = s
                            scaleY = s
                            rotationY = turn.value
                            cameraDistance = 14f * density.density
                        }
                        .pointerInput(heightPx) {
                            val tracker = VelocityTracker()
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                if (closing) return@awaitEachGesture
                                // Catch it mid-flight: whatever it was doing stops under the finger.
                                scope.launch { dragY.stop(); turn.stop() }
                                tracker.resetTracking()
                                tracker.addPosition(down.uptimeMillis, down.position)
                                var axis = 0
                                var dx = 0f
                                var dy = 0f
                                val startTurn = turn.value
                                val startY = dragY.value
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
                                    when (axis) {
                                        1 -> {
                                            change.consume()
                                            scope.launch { turn.snapTo(startTurn + dx * DEG_PER_PX) }
                                        }
                                        2 -> {
                                            change.consume()
                                            val raw = startY + dy
                                            scope.launch { dragY.snapTo(GestureMotion.elastic(raw, 0f, Float.MAX_VALUE)) }
                                        }
                                    }
                                }
                                val v = tracker.calculateVelocity()
                                when (axis) {
                                    0 -> {
                                        // A tap turns it over, settling with no bounce: no force was applied.
                                        val next = (turn.value / 180f).roundToInt() * 180f + 180f
                                        haptics.tick()
                                        scope.launch { turn.animateTo(next, GestureMotion.settle(0.45f)) }
                                    }
                                    1 -> {
                                        val vDeg = v.x * DEG_PER_PX
                                        // Where a 0.99-per-ms deceleration would carry it, then the face nearest that.
                                        val projected = turn.value + vDeg * 0.099f
                                        val landing = ((projected / 180f).roundToInt() * 180f)
                                            .coerceIn(startTurn - 180f, startTurn + 180f)
                                        if ((landing / 180f).roundToInt() != (startTurn / 180f).roundToInt()) haptics.tick()
                                        scope.launch { turn.animateTo(landing, GestureMotion.release(reduce), initialVelocity = vDeg) }
                                    }
                                    2 -> {
                                        if (dragY.value > dismissPx || v.y > flingPx) {
                                            throwAway(v.y, heightPx)
                                        } else {
                                            scope.launch { dragY.animateTo(0f, GestureMotion.snapBack(reduce), initialVelocity = v.y) }
                                        }
                                    }
                                }
                            }
                        },
                ) {
                    // A soft warm light behind the struck face, part of the medal's own layer.
                    Box(
                        Modifier
                            .matchParentSize()
                            .graphicsLayer { alpha = presence }
                            .background(
                                Brush.radialGradient(
                                    0f to FlameGlow.copy(alpha = 0.26f),
                                    1f to Color.Transparent,
                                ),
                            ),
                    )
                    if (showBack) {
                        MedalBack(
                            medal.definition,
                            medal.award.tierIndex,
                            engraving = medal.award.engraving,
                            date = medal.award.longDate(),
                            // Counter-rotate the back so its text reads the right way round.
                            modifier = Modifier.graphicsLayer { rotationY = 180f },
                        )
                    } else {
                        Medal(
                            medal.definition,
                            medal.award.tierIndex,
                            MedalLook.STRUCK,
                            rim = medal.award.rimText(),
                        )
                    }
                }
            }

            Spacer(Modifier.height(26.dp))
            Column(
                Modifier.graphicsLayer {
                    alpha = presence
                    translationY = (1f - lift.value) * 24.dp.toPx()
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    listOfNotNull(position, medal.kindAndMetal()).joinToString(" · ").uppercase(),
                    style = Eyebrow.copy(fontSize = 10.sp, letterSpacing = 1.4.sp),
                    color = FlameGlow,
                    textAlign = TextAlign.Center,
                )
                Text(
                    medal.definition.titleFor(medal.award.tierIndex),
                    style = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 30.sp, hyphens = Hyphens.Auto),
                    color = Chalk,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    "${medal.award.valueLine()} · ${medal.award.shortDate()}",
                    style = TextStyle(fontFamily = Body, fontSize = 14.sp),
                    color = Ash,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Row(
                    Modifier
                        .padding(top = 20.dp)
                        .height(42.dp)
                        .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(999.dp))
                        .clickable(enabled = !sharing) {
                            sharing = true
                            scope.launch {
                                shareMedal(context, medal, TextMeasurer(resolver, density, LayoutDirection.Ltr), density)
                                sharing = false
                            }
                        }
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Filled.IosShare, null, tint = Chalk, modifier = Modifier.size(16.dp))
                    Text(if (sharing) "Preparing…" else "Share medal", style = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp), color = Chalk)
                }
            }
        }
    }
}

/** A Box child that fills its parent; `matchParentSize` without needing a BoxScope receiver here. */
@Composable
private fun Modifier.matchParentSize(): Modifier = this.then(Modifier.fillMaxSize())

// ------------------------------------------------------------------------------------------- sharing

/**
 * Share a struck medal as an image: the medal exactly as drawn on screen (same draw code, rim and all)
 * on the app's onyx, with its kind, name and number. Drawn straight into a picture, never captured from
 * the screen, so it's the same at any phone size.
 */
suspend fun shareMedal(context: Context, medal: StruckMedal, measurer: TextMeasurer, density: Density) {
    val w = 1080
    val h = 1350
    val picture = Picture()
    val canvas = Canvas(picture.beginRecording(w, h))
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, canvas, Size(w.toFloat(), h.toFloat())) {
        drawShareCard(medal, measurer)
    }
    picture.endRecording()
    sharePicture(context, picture, medal.definition.titleFor(medal.award.tierIndex))
}

private fun DrawScope.drawShareCard(medal: StruckMedal, measurer: TextMeasurer) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF161110), Color(0xFF0E0C0D), Color(0xFF0A0A0B))))
    drawCircle(
        Brush.radialGradient(0f to Color(0xFFFF6A1A).copy(alpha = 0.30f), 1f to Color.Transparent, center = Offset(size.width / 2f, 560f), radius = 520f),
        radius = 520f,
        center = Offset(size.width / 2f, 560f),
    )
    val medalW = 560f
    val left = (size.width - medalW) / 2f
    translateTo(left, 150f, medalW, medalW * 1.18f) {
        drawMedal(medal.definition, medal.award.tierIndex, MedalLook.STRUCK, measurer, rim = medal.award.rimText())
    }
    var y = 150f + medalW * 1.18f + 70f
    fun centred(text: String, style: TextStyle, color: Color, gap: Float) {
        val layout = measurer.measure(text, style.copy(textAlign = TextAlign.Center), constraints = Constraints(maxWidth = (size.width - 160f).toInt()))
        drawText(layout, color = color, topLeft = Offset((size.width - layout.size.width) / 2f, y))
        y += layout.size.height + gap
    }
    centred(medal.kindAndMetal().uppercase(), TextStyle(fontFamily = FontFamily.Monospace, fontSize = 30f.toSp(), letterSpacing = 4f.toSp()), FlameGlow, 18f)
    centred(medal.definition.titleFor(medal.award.tierIndex), TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 76f.toSp()), Chalk, 16f)
    centred("${medal.award.valueLine()} · ${medal.award.shortDate()}", TextStyle(fontFamily = Body, fontSize = 38f.toSp()), Ash, 0f)
    val mark = measurer.measure("CALISTAPP", TextStyle(fontFamily = FontFamily.Monospace, fontSize = 26f.toSp(), letterSpacing = 6f.toSp()))
    drawText(mark, color = Color(0xFF5C5C60), topLeft = Offset((size.width - mark.size.width) / 2f, size.height - 90f))
}

/** Draw [block] inside a [w] × [h] box at ([x], [y]), as if that box were the whole scope. */
private inline fun DrawScope.translateTo(x: Float, y: Float, w: Float, h: Float, crossinline block: DrawScope.() -> Unit) {
    drawContext.transform.translate(x, y)
    val previous = drawContext.size
    drawContext.size = Size(w, h)
    block()
    drawContext.size = previous
    drawContext.transform.translate(-x, -y)
}
