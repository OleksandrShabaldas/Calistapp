package com.calistapp.app.ui.dashboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.FilterDrama
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calistapp.app.data.recommend.Conditions
import com.calistapp.app.ui.common.FitText
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.AshFaint
import com.calistapp.app.ui.theme.Display
import com.calistapp.app.ui.theme.FlameHot
import com.calistapp.app.ui.theme.Sky
import kotlinx.coroutines.delay

/*
 * The dashboard's glass gauges. Both recommendation rings — "should I train today" and "indoors or
 * out" — are the same object: a progress ring with a frosted lens sitting in it, holding the headline
 * (score or temperature) over a small caps verdict. Designed first for readiness (G3 + R3 on the board)
 * and generalised so the conditions gauge, and readiness's Easy/Rest/loading states, match it instead
 * of sitting next to it as plain rings.
 *
 * Everything behind the lens is the ring's own light, so the frost is drawn rather than sampled: the
 * lens shows that light softened (warm at the edge facing the ring, a cool haze where the key light
 * hits), with a lit rim and a specular arc. Soft shadows are layered strokes, not blur, so they render
 * the same on every Android version the app supports. Every tint derives from the gauge's accent.
 */

private val RingDiameter = 118.dp
private val RingStroke = 9.dp

/**
 * One glass gauge. [progress] fills the ring (animated), [accent] tints ring, glow and glass; [done]
 * swaps the lens content for a raised glass check that draws itself on. [lens] is the lens content.
 */
@Composable
fun GlassGauge(
    progress: Float,
    accent: Color,
    onClick: (() -> Unit)?,
    done: Boolean = false,
    /** Changes whenever the lens content should re-animate in (and the sheen sweep again). */
    contentKey: Any? = null,
    lens: @Composable () -> Unit,
) {
    val tint by animateColorAsState(accent, tween(500), label = "glassAccent")
    // One animated value drives the ring AND its glow, so the light never runs ahead of the arc.
    val fill by animateFloatAsState(progress.coerceIn(0f, 1f), tween(700, easing = FastOutSlowInEasing), label = "glassFill")
    val drawn = remember(done) { Animatable(if (done) 0f else 1f) }
    val sheen = remember(done, contentKey) { Animatable(0f) }
    LaunchedEffect(done, contentKey) {
        if (done) {
            delay(260)
            drawn.animateTo(1f, tween(620, easing = FastOutSlowInEasing))
        }
        sheen.snapTo(0f)
        sheen.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
    }
    Box(
        Modifier
            .size(RingDiameter)
            // The ring is the focal element here, so it glows — a soft ring of light shaped like the ring
            // itself (never a disc behind it), drawn before the clip so it isn't cut off.
            .drawBehind { drawRingGlow(if (done) 1f else fill, tint) }
            .clip(CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(RingDiameter)) {
            drawRing(if (done) 1f else fill, tint)
            drawInnerLight(tint)
            drawLens(sheen.value, tint)
            if (done) drawGlassCheck(drawn.value, tint)
        }
        Box(Modifier.widthIn(max = 80.dp), contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = done to contentKey,
                transitionSpec = { (fadeIn(tween(240)) + scaleIn(initialScale = 0.9f)) togetherWith (fadeOut(tween(200)) + scaleOut(targetScale = 1.12f)) },
                label = "lens-content",
            ) { (isDone, _) -> if (!isDone) lens() else Box(Modifier.size(1.dp)) }
        }
    }
}

/** The headline + caps-verdict stack used in every lens. */
@Composable
private fun LensStack(headline: String, verdict: String, accent: Color, icon: ImageVector? = null, headlineSize: Int = 32) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.height(1.dp))
        }
        Text(
            headline,
            style = TextStyle(
                fontFamily = Display,
                fontWeight = FontWeight.Bold,
                fontSize = headlineSize.sp,
                lineHeight = (headlineSize + 2).sp,
                letterSpacing = (-1).sp,
                shadow = Shadow(Color.Black.copy(alpha = 0.4f), Offset(0f, 3f), 10f),
            ),
            color = Color.White,
        )
        Spacer(Modifier.height(1.dp))
        FitText(
            verdict,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold),
            color = lerp(accent, Color.White, 0.72f),
            minFontSize = 7.sp,
        )
    }
}

/** Readiness: the score in the lens, or the glass check once today's workout is done. */
@Composable
fun GlassReadinessGauge(score: Int?, done: Boolean, onClick: () -> Unit) {
    val s = score ?: 0
    val accent = if (done) FlameHot else readinessColor(s)
    val verdict = when (shortReadiness(s)) {
        "Train" -> "READY"
        "Easy" -> "EASY"
        else -> "REST"
    }
    GlassGauge(progress = s / 100f, accent = accent, onClick = onClick, done = done, contentKey = verdict) {
        if (score != null) LensStack("$score", verdict, accent)
    }
}

/** Indoors or out: the weather glyph and temperature over the verdict, the ring = how good outside is. */
@Composable
fun GlassConditionsGauge(conditions: Conditions, onClick: () -> Unit) {
    if (conditions.needsLocation) {
        GlassGauge(progress = 0f, accent = Ash, onClick = onClick, contentKey = "location") {
            LensStack("—", "ADD LOCATION", Ash, icon = Icons.Filled.LocationOn, headlineSize = 26)
        }
        return
    }
    val outdoor = conditions.outdoor ?: conditions.label.contains("Outdoor", ignoreCase = true)
    val accent = if (outdoor) FlameHot else Sky
    val progress = (conditions.score ?: if (outdoor) 82 else 30) / 100f
    val verdict = if (outdoor) "OUTDOOR" else "INDOOR"
    GlassGauge(progress = progress, accent = accent, onClick = onClick, contentKey = verdict) {
        LensStack(
            headline = conditions.tempC?.let { "$it°" } ?: conditions.detail.substringBefore("°", "—").let { if (it == "—") it else "$it°" },
            verdict = verdict,
            accent = accent,
            icon = weatherIcon(conditions.weatherCode, conditions.isDay),
            headlineSize = 28,
        )
    }
}

/** While a recommendation is generated: an empty glass gauge rather than a bare ring with text. */
@Composable
fun GlassPlaceholder() {
    GlassGauge(progress = 0f, accent = AshFaint, onClick = null, contentKey = "loading") {
        Text("Loading…", style = MaterialTheme.typography.labelMedium, color = Ash)
    }
}

/** WMO weather code → a glyph. */
internal fun weatherIcon(code: Int?, isDay: Boolean?): ImageVector = when (code) {
    null -> Icons.Filled.Cloud
    0 -> if (isDay == false) Icons.Filled.NightsStay else Icons.Filled.WbSunny
    1, 2 -> if (isDay == false) Icons.Filled.NightsStay else Icons.Filled.FilterDrama
    3 -> Icons.Filled.Cloud
    45, 48 -> Icons.Filled.Air
    51, 53, 55, 56, 57 -> Icons.Filled.Grain
    61, 63, 65, 66, 67, 80, 81, 82 -> Icons.Filled.Umbrella
    71, 73, 75, 77, 85, 86 -> Icons.Filled.AcUnit
    95, 96, 99 -> Icons.Filled.Thunderstorm
    else -> Icons.Filled.Cloud
}

private fun Color.lift(f: Float): Color = lerp(this, Color.White, f)

/** Track + filled arc, swept from 12 o'clock. */
private fun DrawScope.drawRing(progress: Float, accent: Color) {
    val stroke = RingStroke.toPx()
    val inset = stroke / 2f
    val arc = Size(size.width - stroke, size.height - stroke)
    drawArc(Color.White.copy(alpha = 0.07f), 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
    if (progress > 0f) {
        drawArc(
            Brush.sweepGradient(listOf(accent.copy(alpha = 0.55f), accent, accent.copy(alpha = 0.55f))),
            -90f, 360f * progress, false, Offset(inset, inset), arc,
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
}

/** The ring's light falling inward — what the lens picks up and frosts. */
private fun DrawScope.drawInnerLight(accent: Color) {
    val inner = size.minDimension / 2 - RingStroke.toPx()
    drawCircle(
        Brush.radialGradient(
            0f to Color(0xFF0D0A09),
            0.30f to Color(0xFF0D0A09),
            0.66f to accent.copy(alpha = 0.07f),
            1f to accent.copy(alpha = 0.32f),
            center = center,
            radius = inner,
        ),
        radius = inner,
    )
}

private fun DrawScope.lensRadius(): Float = (size.minDimension / 2 - RingStroke.toPx()) * 0.86f

/** The frosted lens: drop shadow, softened light, warm inner rim, lit rim edge and a specular arc. */
private fun DrawScope.drawLens(sheen: Float, accent: Color) {
    val r = lensRadius()
    val box = Rect(center - Offset(r, r), Size(r * 2, r * 2))

    softCircle(center + Offset(0f, 6.dp.toPx()), r, Color.Black.copy(alpha = 0.55f), spread = 10.dp.toPx())

    // Body: a cool haze where the key light (top-left) hits, the ring's colour pooling bottom-right.
    drawCircle(
        Brush.linearGradient(
            0f to Color.White.copy(alpha = 0.16f),
            0.55f to Color.White.copy(alpha = 0.04f),
            1f to accent.lift(0.35f).copy(alpha = 0.12f),
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
        Brush.radialGradient(0f to accent.copy(alpha = 0.34f), 1f to Color.Transparent, center = Offset(box.left + r * 1.40f, box.top + r * 1.84f), radius = r * 0.9f),
        radius = r,
        center = center,
    )
    // Inset glow at the edge — the ring refracting through the glass.
    drawCircle(
        Brush.radialGradient(0.78f to Color.Transparent, 1f to accent.copy(alpha = 0.30f), center = center, radius = r),
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
    // Lit rim: bright where the key light hits, tinted where it faces the ring.
    drawCircle(
        Brush.linearGradient(
            0f to Color.White.copy(alpha = 0.9f),
            0.45f to Color.White.copy(alpha = 0.10f),
            0.75f to accent.lift(0.45f).copy(alpha = 0.45f),
            1f to accent.lift(0.15f).copy(alpha = 0.95f),
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

/** The raised glass check, drawn on to [progress]: shadow, translucent body, pooled colour, streak, rim. */
private fun DrawScope.drawGlassCheck(progress: Float, accent: Color) {
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
            1f to accent.lift(0.3f).copy(alpha = 0.36f),
            start = Offset(c.x - s * 0.4f, c.y - s * 0.3f),
            end = Offset(c.x + s * 0.4f, c.y + s * 0.3f),
        ),
        style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
    clipPath(outline) {
        translate(left = w * 0.20f, top = w * 0.26f) { softPath(check, w * 0.55f, accent.copy(alpha = 0.55f), spread = w * 0.2f) }
        translate(left = -w * 0.13f, top = -w * 0.24f) {
            drawPath(check, Color.White.copy(alpha = 0.62f), style = Stroke(w * 0.16f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
    drawPath(
        outline,
        Brush.linearGradient(
            0f to Color.White.copy(alpha = 0.95f),
            0.45f to Color.White.copy(alpha = 0.18f),
            0.7f to accent.lift(0.45f).copy(alpha = 0.5f),
            1f to accent.lift(0.08f).copy(alpha = 0.95f),
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
private fun DrawScope.drawRingGlow(progress: Float, accent: Color) {
    if (progress <= 0f) return
    val stroke = RingStroke.toPx()
    val inset = stroke / 2
    val arc = Size(size.width - stroke, size.height - stroke)
    val layers = 6
    for (i in layers downTo 1) {
        val t = i / layers.toFloat()
        drawArc(
            accent.copy(alpha = 0.16f * (1f - t * 0.75f)),
            startAngle = -90f,
            sweepAngle = 360f * progress,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arc,
            style = Stroke(stroke + 14.dp.toPx() * t, cap = StrokeCap.Round),
        )
    }
}
