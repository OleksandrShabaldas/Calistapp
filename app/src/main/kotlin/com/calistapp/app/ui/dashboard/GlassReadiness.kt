package com.calistapp.app.ui.dashboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calistapp.app.ui.common.ProgressRing
import com.calistapp.app.ui.theme.Display
import com.calistapp.app.ui.theme.FlameGlow
import com.calistapp.app.ui.theme.FlameHot
import kotlinx.coroutines.delay

/*
 * The readiness gauge's two glass states, chosen from the design board (G3 + R3):
 *  - ready to train, not trained yet — a frosted lens filling the ring, holding the score and "READY";
 *  - today's workout done — the same lens with a raised glass check sitting on it.
 * The check now means "done" and nothing else: the old gauge put a check up for a readiness score of
 * 70+, which read as "workout done" on a day you hadn't trained.
 *
 * Everything behind the lens is the ring's own light, so the frost is drawn rather than sampled: the
 * lens shows that light softened (warm at the edge facing the ring, a cool haze where the key light
 * hits), with a lit rim and a specular arc. Soft shadows are layered strokes, not blur, so they render
 * the same on every Android version the app supports.
 */

private val RingDiameter = 118.dp
private val RingStroke = 9.dp

/**
 * The glass gauge. [done] shows the check (the score, if any, is only a tap away in the detail popup);
 * otherwise [score] sits in the lens. Tapping opens the readiness detail either way.
 */
@Composable
fun GlassReadinessGauge(score: Int?, done: Boolean, onClick: () -> Unit) {
    // The check draws itself on, and a sheen sweeps the lens as it lands — the verdict arrives rather
    // than snapping in. Re-runs when the state flips (back on the dashboard after a workout).
    val drawn = remember(done) { Animatable(if (done) 0f else 1f) }
    val sheen = remember(done) { Animatable(0f) }
    LaunchedEffect(done) {
        if (done) {
            delay(260)
            drawn.animateTo(1f, tween(620, easing = FastOutSlowInEasing))
        }
        sheen.snapTo(0f)
        sheen.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
    }
    val progress = if (done) 1f else (score ?: 0) / 100f
    // The glass sits over the ring rather than in its centre slot, which is bounded for a text label
    // and shrank the lens to a third of the ring.
    Box(
        Modifier
            .size(RingDiameter)
            // The ring is the focal element here, so it glows — a soft ring of light shaped like the
            // ring itself (never a disc behind it), drawn first and before the clip so it isn't cut off.
            .drawBehind { drawRingGlow(progress) }
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        ProgressRing(progress = progress, accent = FlameHot, diameter = RingDiameter, strokeWidth = RingStroke) {}
        Canvas(Modifier.size(RingDiameter)) {
            drawInnerLight()
            drawLens(sheen.value)
            if (done) drawGlassCheck(drawn.value)
        }
        Box(contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = done,
                transitionSpec = { (fadeIn(tween(240)) + scaleIn(initialScale = 0.9f)) togetherWith (fadeOut(tween(200)) + scaleOut(targetScale = 1.12f)) },
                label = "lens-content",
            ) { isDone ->
                if (!isDone && score != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "$score",
                            style = TextStyle(
                                fontFamily = Display,
                                fontWeight = FontWeight.Bold,
                                fontSize = 32.sp,
                                letterSpacing = (-1).sp,
                                shadow = Shadow(Color.Black.copy(alpha = 0.4f), Offset(0f, 3f), 10f),
                            ),
                            color = Color.White,
                        )
                        Spacer(Modifier.height(1.dp))
                        Text(
                            "READY",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.2.sp),
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFFFD7BF),
                        )
                    }
                } else {
                    Box(Modifier.size(1.dp))
                }
            }
        }
    }
}

/** The ring's light falling inward — what the lens picks up and frosts. */
private fun DrawScope.drawInnerLight() {
    val inner = size.minDimension / 2 - RingStroke.toPx()
    drawCircle(
        Brush.radialGradient(
            0f to Color(0xFF0D0A09),
            0.30f to Color(0xFF0D0A09),
            0.66f to FlameHot.copy(alpha = 0.07f),
            1f to FlameHot.copy(alpha = 0.32f),
            center = center,
            radius = inner,
        ),
        radius = inner,
    )
}

private fun DrawScope.lensRadius(): Float = (size.minDimension / 2 - RingStroke.toPx()) * 0.86f

/** The frosted lens: drop shadow, softened light, warm inner rim, lit rim edge and a specular arc. */
private fun DrawScope.drawLens(sheen: Float) {
    val r = lensRadius()
    val box = Rect(center - Offset(r, r), Size(r * 2, r * 2))

    softCircle(center + Offset(0f, 6.dp.toPx()), r, Color.Black.copy(alpha = 0.55f), spread = 10.dp.toPx())

    // Body: a cool haze where the key light (top-left) hits, the ring's orange pooling bottom-right.
    drawCircle(
        Brush.linearGradient(
            0f to Color.White.copy(alpha = 0.16f),
            0.55f to Color.White.copy(alpha = 0.04f),
            1f to Color(0xFFFFA06E).copy(alpha = 0.12f),
            start = box.topLeft,
            end = box.bottomRight,
        ),
        radius = r,
        center = center,
    )
    drawCircle(
        Brush.radialGradient(0f to Color.White.copy(alpha = 0.30f), 1f to Color.Transparent, center = Offset(box.left + r * 0.64f, box.top + r * 0.42f), radius = r * 0.62f),
        radius = r,
        center = center,
    )
    drawCircle(
        Brush.radialGradient(0f to FlameHot.copy(alpha = 0.34f), 1f to Color.Transparent, center = Offset(box.left + r * 1.40f, box.top + r * 1.84f), radius = r * 0.9f),
        radius = r,
        center = center,
    )
    // Warm inset glow at the edge — the ring refracting through the glass.
    drawCircle(
        Brush.radialGradient(0.78f to Color.Transparent, 1f to FlameHot.copy(alpha = 0.30f), center = center, radius = r),
        radius = r,
        center = center,
    )
    // A sheen sweeping across as the gauge settles.
    if (sheen in 0.01f..0.99f) {
        val x = box.left - r + sheen * r * 4f
        clipPath(Path().apply { addOval(box) }) {
            drawRect(
                Brush.linearGradient(
                    0f to Color.Transparent,
                    0.5f to Color.White.copy(alpha = 0.18f),
                    1f to Color.Transparent,
                    start = Offset(x - r * 0.5f, box.top),
                    end = Offset(x + r * 0.5f, box.bottom),
                ),
            )
        }
    }
    // Lit rim: bright where the key light hits, orange where it faces the ring.
    drawCircle(
        Brush.linearGradient(
            0f to Color.White.copy(alpha = 0.9f),
            0.45f to Color.White.copy(alpha = 0.10f),
            0.75f to Color(0xFFFFB38A).copy(alpha = 0.45f),
            1f to FlameGlow.copy(alpha = 0.95f),
            start = box.topLeft,
            end = box.bottomRight,
        ),
        radius = r - 0.8.dp.toPx(),
        center = center,
        style = Stroke(1.6.dp.toPx()),
    )
    drawArc(
        Color.White.copy(alpha = 0.55f),
        startAngle = 200f,
        sweepAngle = 52f,
        useCenter = false,
        topLeft = box.topLeft + Offset(r * 0.18f, r * 0.18f),
        size = Size(r * 1.64f, r * 1.64f),
        style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round),
    )
}

/** The raised glass check, drawn on to [progress]: shadow, translucent body, pooled orange, streak, rim. */
private fun DrawScope.drawGlassCheck(progress: Float) {
    if (progress <= 0f) return
    val r = lensRadius()
    val s = r * 1.2f
    val c = center + Offset(r * 0.02f, r * 0.04f)
    val full = Path().apply {
        moveTo(c.x - 0.40f * s, c.y + 0.02f * s)
        lineTo(c.x - 0.12f * s, c.y + 0.29f * s)
        lineTo(c.x + 0.42f * s, c.y - 0.30f * s)
    }
    val check = Path()
    PathMeasure().apply { setPath(full, false) }.let { it.getSegment(0f, it.length * progress.coerceIn(0f, 1f), check, true) }
    val w = r * 0.30f

    // The stroke's outline as a shape, so highlights can be clipped to the glass itself.
    val outline = android.graphics.Path().also { out ->
        android.graphics.Paint().apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = w
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
        }.getFillPath(check.asAndroidPath(), out)
    }.asComposePath()

    translate(top = 7.dp.toPx()) { softPath(check, w, Color.Black.copy(alpha = 0.45f), spread = 7.dp.toPx()) }
    drawPath(
        check,
        Brush.linearGradient(
            0f to Color.White.copy(alpha = 0.40f),
            0.5f to Color.White.copy(alpha = 0.12f),
            1f to Color(0xFFFF9A5C).copy(alpha = 0.36f),
            start = Offset(c.x - s * 0.4f, c.y - s * 0.3f),
            end = Offset(c.x + s * 0.4f, c.y + s * 0.3f),
        ),
        style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
    clipPath(outline) {
        translate(left = w * 0.20f, top = w * 0.26f) { softPath(check, w * 0.55f, FlameHot.copy(alpha = 0.55f), spread = w * 0.2f) }
        translate(left = -w * 0.13f, top = -w * 0.24f) {
            drawPath(check, Color.White.copy(alpha = 0.62f), style = Stroke(w * 0.16f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
    drawPath(
        outline,
        Brush.linearGradient(
            0f to Color.White.copy(alpha = 0.95f),
            0.45f to Color.White.copy(alpha = 0.18f),
            0.7f to Color(0xFFFFB38A).copy(alpha = 0.5f),
            1f to Color(0xFFFF7A2E).copy(alpha = 0.95f),
            start = Offset(c.x - s * 0.4f, c.y - s * 0.3f),
            end = Offset(c.x + s * 0.4f, c.y + s * 0.3f),
        ),
        style = Stroke(1.3.dp.toPx()),
    )
}

/** A soft-edged filled circle — a shadow or glow — from layered rings instead of a blur filter. */
private fun DrawScope.softCircle(at: Offset, radius: Float, color: Color, spread: Float, layers: Int = 8) {
    for (i in layers downTo 1) {
        val t = i / layers.toFloat()
        drawCircle(color.copy(alpha = color.alpha / layers * 1.6f * (1f - t * 0.6f)), radius = radius + spread * t - spread * 0.4f, center = at)
    }
}

/** A soft-edged stroke of [path] — layered widening strokes standing in for a Gaussian blur. */
private fun DrawScope.softPath(path: Path, width: Float, color: Color, spread: Float, layers: Int = 7) {
    for (i in layers downTo 0) {
        val t = i / layers.toFloat()
        drawPath(
            path,
            color.copy(alpha = color.alpha / (layers + 1) * 1.8f * (1f - t * 0.7f)),
            style = Stroke(width + spread * 2f * t, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/** A soft glow following the ring's stroke (and its filled arc), from layered strokes. */
private fun DrawScope.drawRingGlow(progress: Float) {
    if (progress <= 0f) return
    val stroke = RingStroke.toPx()
    val inset = stroke / 2
    val arc = Size(size.width - stroke, size.height - stroke)
    val layers = 6
    for (i in layers downTo 1) {
        val t = i / layers.toFloat()
        drawArc(
            FlameHot.copy(alpha = 0.16f * (1f - t * 0.75f)),
            startAngle = -90f,
            sweepAngle = 360f * progress,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arc,
            style = Stroke(stroke + 14.dp.toPx() * t, cap = StrokeCap.Round),
        )
    }
}
