package com.calistapp.app.ui.medals

import android.graphics.Paint as NativePaint
import android.graphics.Path as NativePath
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.calistapp.app.ui.exercises.SvgPathNormalizer
import com.calistapp.app.ui.theme.Display
import com.calistapp.core.medals.MedalCatalog
import com.calistapp.core.medals.MedalDefinition
import com.calistapp.core.medals.MedalKind
import com.calistapp.core.medals.MedalMetal

/**
 * How a medal is drawn. The case shows all four; the unlock moment and the newest-medal card only STRUCK.
 *
 * - [STRUCK]: earned. Ribbon in its kind's colours, the metal, the emblem and (optionally) the rim.
 * - [LOCKED]: not earned. A dark disc with a grey emblem on a grey ribbon; may carry a progress arc.
 * - [GHOST]: the "preview the reward" state: the real metal, faded and desaturated, on a grey ribbon.
 *   It fills in as progress grows, but never past 70 % opacity, so it never reads as struck.
 * - [HIDDEN]: a hidden medal not found yet: a dashed empty disc with a question mark.
 */
enum class MedalLook { STRUCK, LOCKED, GHOST, HIDDEN }

/**
 * One medal, drawn natively from the design's geometry (a 100 × 118 box: ribbon on top, a disc of
 * radius 36 at (50, 78)). Width comes from [modifier]; the height follows the aspect.
 *
 * @param tierIndex which rung of the line this medal is (its metal and its number).
 * @param ribbon false draws only the disc, cropped square (for list rows and shelves).
 * @param rim the engraving along the top of the rim ("11 REPS · NOV 6 2026"); STRUCK, or a GHOST's placeholder.
 * @param progress 0..1 toward this tier: drawn as an orange arc around a LOCKED disc.
 * @param ghost 0..1 progress for a GHOST: opacity 0.25 + 0.45 × it, colour returning with it.
 */
@Composable
fun Medal(
    definition: MedalDefinition,
    tierIndex: Int,
    look: MedalLook,
    modifier: Modifier = Modifier,
    ribbon: Boolean = true,
    rim: String? = null,
    progress: Float? = null,
    ghost: Float = 0f,
    shadow: Boolean = true,
    description: String? = null,
    rimReveal: Float = 1f,
) {
    val measurer = rememberTextMeasurer(cacheSize = 4)
    Spacer(
        modifier
            .aspectRatio(if (ribbon) BOX_W / BOX_H else 1f)
            .semantics { contentDescription = description ?: definition.name }
            .drawBehind { drawMedal(definition, tierIndex, look, measurer, ribbon, rim, progress, ghost, shadow, rimReveal) },
    )
}

/** The back of a struck medal: its metal, plain, with the name, the engraving and the date. */
@Composable
fun MedalBack(
    definition: MedalDefinition,
    tierIndex: Int,
    engraving: String,
    date: String,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer(cacheSize = 6)
    Spacer(
        modifier
            .aspectRatio(BOX_W / BOX_H)
            .semantics { contentDescription = "${definition.name}, $engraving, $date" }
            .drawBehind { drawMedalBack(definition, tierIndex, engraving, date, measurer) },
    )
}

/**
 * Draws a medal filling this scope's width (height is width × 1.18 with the ribbon, × 1 without). The
 * composable and the share image both use it, so a shared medal is exactly the one on screen.
 */
fun DrawScope.drawMedal(
    definition: MedalDefinition,
    tierIndex: Int,
    look: MedalLook,
    measurer: TextMeasurer,
    ribbon: Boolean = true,
    rim: String? = null,
    progress: Float? = null,
    ghost: Float = 0f,
    shadow: Boolean = true,
    rimReveal: Float = 1f,
) {
    val tier = definition.tiers[tierIndex.coerceIn(0, definition.tiers.lastIndex)]
    val emblem = MedalEmblems.get(if (look == MedalLook.HIDDEN) MedalCatalog.HIDDEN_EMBLEM else definition.emblem)
    val label = if (definition.tiers.size > 1 && look != MedalLook.HIDDEN) tier.label else null
    val box = if (ribbon) Rect(0f, 0f, BOX_W, BOX_H) else Rect(7f, 35f, 93f, 121f)
    val scale = size.width / box.width
    val metal = MedalPalette.metal(tier.metal)
    val ghostPaint = if (look == MedalLook.GHOST) {
        val p = ghost.coerceIn(0f, 1f)
        Paint().apply {
            alpha = (0.25f + 0.45f * p).coerceAtMost(0.70f)
            colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.35f + 0.3f * p) })
        }
    } else {
        null
    }
    ghostPaint?.let { p -> drawIntoCanvas { it.saveLayer(Rect(Offset.Zero, size).inflate(size.width * 0.2f), p) } }
    withTransform({
        translate(-box.left * scale, -box.top * scale)
        scale(scale, scale, Offset.Zero)
    }) {
        drawMedalBody(definition.kind, look, metal, emblem, small = label != null, ribbon = ribbon, shadow = shadow)
        if (look == MedalLook.LOCKED && progress != null && progress > 0f) drawProgressArc(progress)
    }
    // Text is laid out in pixels so it stays crisp at every size.
    val unit = { x: Float, y: Float -> Offset((x - box.left) * scale, (y - box.top) * scale) }
    label?.let {
        val layout = measurer.measure(
            it,
            TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = ((if (it.length >= 3) 19f else 21f) * scale).toSp()),
        )
        val (ink, hi) = inkFor(look, metal)
        val centre = unit(50f, 98f)
        val topLeft = Offset(centre.x - layout.size.width / 2f, centre.y - layout.firstBaseline)
        hi?.let { c -> drawText(layout, color = c, topLeft = topLeft + Offset(0.5f * scale, 0.5f * scale)) }
        drawText(layout, color = ink, topLeft = topLeft)
    }
    rim?.takeIf { (look == MedalLook.STRUCK || look == MedalLook.GHOST) && it.isNotBlank() }?.let { drawRim(it, unit(50f, 78f), RIM_RADIUS * scale, scale, metal.ink, rimReveal) }
    ghostPaint?.let { drawIntoCanvas { it.restore() } }
}

/** The reverse of a struck medal, filling this scope's width (height = width × 1.18). */
fun DrawScope.drawMedalBack(
    definition: MedalDefinition,
    tierIndex: Int,
    engraving: String,
    date: String,
    measurer: TextMeasurer,
) {
    val tier = definition.tiers[tierIndex.coerceIn(0, definition.tiers.lastIndex)]
    val scale = size.width / BOX_W
    val metal = MedalPalette.metal(tier.metal)
    val maxW = (52f * scale).toInt().coerceAtLeast(1)
    fun line(text: String, fontSize: Float, weight: FontWeight, mono: Boolean) = measurer.measure(
        text,
        TextStyle(
            fontFamily = if (mono) FontFamily.Monospace else Display,
            fontWeight = weight,
            fontSize = (fontSize * scale).toSp(),
            textAlign = TextAlign.Center,
            hyphens = Hyphens.Auto,
        ),
        constraints = Constraints(maxWidth = maxW),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    val lines = listOf(
        line(definition.name.uppercase(), 6.2f, FontWeight.Bold, mono = false),
        line(engraving, 4.2f, FontWeight.Medium, mono = true),
        line(date.uppercase(), 4.2f, FontWeight.Medium, mono = true),
    )
    withTransform({ scale(scale, scale, Offset.Zero) }) {
        drawMedalBody(definition.kind, MedalLook.STRUCK, metal, emblem = null, small = false, ribbon = true, shadow = true, back = true)
    }
    val cx = 50f * scale
    var y = 78f * scale - (lines.sumOf { it.size.height } + 6f * scale) / 2f
    lines.forEachIndexed { i, l ->
        drawText(l, color = metal.ink.copy(alpha = if (i == 0) 1f else 0.85f), topLeft = Offset(cx - l.size.width / 2f, y))
        y += l.size.height + 3f * scale
    }
}

// ------------------------------------------------------------------------------------------- drawing

private const val BOX_W = 100f
private const val BOX_H = 118f
private const val RIM_RADIUS = 31.2f
private val DiscCentre = Offset(50f, 78f)
private const val DISC_R = 36f

private fun inkFor(look: MedalLook, metal: MetalStyle): Pair<Color, Color?> = when (look) {
    MedalLook.STRUCK, MedalLook.GHOST -> metal.ink to metal.highlight
    MedalLook.LOCKED -> Color(0xFF5A5A60) to null
    MedalLook.HIDDEN -> Color(0xFF5C5C60) to null
}

private fun DrawScope.drawMedalBody(
    kind: MedalKind,
    look: MedalLook,
    metal: MetalStyle,
    emblem: Emblem?,
    small: Boolean,
    ribbon: Boolean,
    shadow: Boolean,
    back: Boolean = false,
) {
    val struck = look == MedalLook.STRUCK
    if (shadow && struck) {
        // drop-shadow(0 5px 8px rgba(0,0,0,.55)), as a soft disc: works on every API level, unlike blur.
        drawCircle(
            Brush.radialGradient(
                0f to Color.Black.copy(alpha = 0.55f),
                0.78f to Color.Black.copy(alpha = 0.45f),
                1f to Color.Transparent,
                center = DiscCentre + Offset(0f, 5f),
                radius = DISC_R + 9f,
            ),
            radius = DISC_R + 9f,
            center = DiscCentre + Offset(0f, 5f),
        )
        if (metal.glow != null) {
            drawCircle(
                Brush.radialGradient(
                    0.7f to metal.glow.copy(alpha = 0.45f),
                    1f to Color.Transparent,
                    center = DiscCentre,
                    radius = DISC_R + 12f,
                ),
                radius = DISC_R + 12f,
                center = DiscCentre,
            )
        }
    }
    if (ribbon) {
        val (dark, light) = if (struck) MedalPalette.ribbon(kind) else MedalPalette.RibbonHidden
        drawPath(RibbonLeft, dark)
        drawPath(RibbonRight, light)
    }
    when (look) {
        MedalLook.STRUCK, MedalLook.GHOST -> {
            drawCircle(metal.brush, DISC_R, DiscCentre)
            drawCircle(metal.edge, DISC_R, DiscCentre, style = Stroke(2f))
            drawCircle(metal.ring, 28.5f, DiscCentre, style = Stroke(1.4f))
            if (back) {
                // The reverse is plain struck metal: a second, finer ring is the only ornament.
                drawCircle(metal.ring.copy(alpha = metal.ring.alpha * 0.6f), 25f, DiscCentre, style = Stroke(0.8f))
            }
        }
        MedalLook.LOCKED -> {
            drawCircle(Color(0xFF161619), DISC_R, DiscCentre)
            drawCircle(Color.White.copy(alpha = 0.15f), DISC_R, DiscCentre, style = Stroke(1.8f))
        }
        MedalLook.HIDDEN -> {
            drawCircle(Color(0xFF1C1C1F), DISC_R, DiscCentre)
            drawCircle(
                Color.White.copy(alpha = 0.2f), DISC_R, DiscCentre,
                style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))),
            )
        }
    }
    if (emblem == null) return
    val (ink, hi) = inkFor(look, metal)
    // Ladders: a small emblem over the tier's number. One-offs: one large emblem.
    val (x, y, s, hiOffset) = if (small && look != MedalLook.HIDDEN) {
        EmblemPlacement(40.4f, 55f, 19.2f, Offset(0.2f, 0.4f))
    } else {
        EmblemPlacement(31.4f, 59.4f, 37.2f, Offset(0.4f, 0.6f))
    }
    hi?.let { drawEmblem(emblem, x + hiOffset.x, y + hiOffset.y, s, it) }
    drawEmblem(emblem, x, y, s, ink)
}

private data class EmblemPlacement(val x: Float, val y: Float, val size: Float, val hiOffset: Offset)

private fun DrawScope.drawEmblem(emblem: Emblem, x: Float, y: Float, size: Float, color: Color) {
    withTransform({
        translate(x, y)
        scale(size / 24f, size / 24f, Offset.Zero)
    }) {
        val stroke = Stroke(width = emblem.stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        emblem.paths.forEach { drawPath(it, color, style = stroke) }
    }
}

private fun DrawScope.drawProgressArc(progress: Float) {
    val r = 41f
    val topLeft = DiscCentre - Offset(r, r)
    val arcSize = Size(2 * r, 2 * r)
    drawCircle(Color.White.copy(alpha = 0.08f), r, DiscCentre, style = Stroke(2.6f))
    drawArc(
        Color(0xFFFF8A3D), -90f, 360f * progress.coerceIn(0f, 1f), false,
        topLeft = topLeft, size = arcSize, style = Stroke(2.6f, cap = StrokeCap.Round),
    )
}

/**
 * The engraving, centred on the top of the rim and reading clockwise, in pixels. [reveal] below 1 is
 * the engraving in progress: only that share of the text is cut, and a hot point sits where the tool is.
 */
private fun DrawScope.drawRim(text: String, centre: Offset, radius: Float, scale: Float, ink: Color, reveal: Float = 1f) {
    val paint = NativePaint(NativePaint.ANTI_ALIAS_FLAG or NativePaint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textSize = 4.1f * scale
        letterSpacing = 0.5f / 4.1f
        color = ink.copy(alpha = 0.85f).toArgb()
    }
    val circumference = (2 * Math.PI * radius).toFloat()
    // Never wrap past the bottom: a long engraving shrinks to fit three quarters of the rim.
    val width = paint.measureText(text)
    if (width > circumference * 0.75f) paint.textSize *= circumference * 0.75f / width
    val textWidth = paint.measureText(text)
    val path = NativePath().apply {
        // Starts at the bottom and runs clockwise (left, top, right), so half way round is the top.
        addArc(RectF(centre.x - radius, centre.y - radius, centre.x + radius, centre.y + radius), 90f, 359.9f)
    }
    val vOffset = paint.textSize * 0.35f
    val start = circumference / 2f - textWidth / 2f
    if (reveal <= 0f) return
    val cut = if (reveal >= 1f) text else text.substring(0, (text.length * reveal).toInt().coerceIn(0, text.length))
    drawIntoCanvas { it.nativeCanvas.drawTextOnPath(cut, path, start, vOffset, paint) }
    if (reveal < 1f) {
        // The tool: a hot point travelling clockwise, just ahead of the last letter cut.
        val along = start + paint.measureText(cut)
        val angle = Math.toRadians(90.0 + along / circumference * 360.0)
        val r = radius - paint.textSize * 0.15f
        val p = Offset(centre.x + (r * Math.cos(angle)).toFloat(), centre.y + (r * Math.sin(angle)).toFloat())
        drawCircle(Brush.radialGradient(0f to Color(0xFFFFD2A8), 0.35f to Color(0xFFFF8A3D).copy(alpha = 0.8f), 1f to Color.Transparent, center = p, radius = 5f * scale), 5f * scale, p)
        drawCircle(Color(0xFFFFF1E2), 1.1f * scale, p)
    }
}

private val RibbonLeft = Path().apply { moveTo(30f, 0f); lineTo(46f, 0f); lineTo(50f, 32f); lineTo(37f, 37f); close() }
private val RibbonRight = Path().apply { moveTo(54f, 0f); lineTo(70f, 0f); lineTo(63f, 37f); lineTo(50f, 32f); close() }

// ------------------------------------------------------------------------------------------- palette

/** One metal: the disc gradient, its edge and inner ring, and the colours struck into it. */
class MetalStyle(
    stops: List<Pair<Float, Color>>,
    val edge: Color,
    val ring: Color,
    val ink: Color,
    val highlight: Color?,
    val glow: Color? = null,
    /** A flat colour standing for the metal in tier pips and labels. */
    val pip: Color,
) {
    // objectBoundingBox (35 %, 30 %), r = 80 % of the disc's 72-unit box, as in the design's SVG.
    val brush: Brush = Brush.radialGradient(
        *stops.toTypedArray(),
        center = Offset(14f + 0.35f * 72f, 42f + 0.30f * 72f),
        radius = 0.8f * 72f,
    )
}

object MedalPalette {
    val Copper = MetalStyle(
        listOf(0f to Color(0xFFFFE3C4), 0.35f to Color(0xFFF6A066), 0.65f to Color(0xFFD2652A), 0.9f to Color(0xFF7A3210), 1f to Color(0xFF4A1E08)),
        edge = Color(0xFF3A1606), ring = Color(0x80FFECD6), ink = Color(0xFF4A1E08), highlight = Color(0x8CFFECD6), pip = Color(0xFFD2652A),
    )
    val Silver = MetalStyle(
        listOf(0f to Color.White, 0.35f to Color(0xFFE2E3E8), 0.65f to Color(0xFFA9ACB3), 0.9f to Color(0xFF5E6066), 1f to Color(0xFF34353A)),
        edge = Color(0xFF3A3B40), ring = Color(0x99FFFFFF), ink = Color(0xFF2A2B30), highlight = Color(0xBFFFFFFF), pip = Color(0xFFC9CBD1),
    )
    val Gold = MetalStyle(
        listOf(0f to Color(0xFFFFF7D6), 0.35f to Color(0xFFF7D97E), 0.65f to Color(0xFFDDA73E), 0.9f to Color(0xFF8C5F16), 1f to Color(0xFF4E3309)),
        edge = Color(0xFF5A3A0A), ring = Color(0x99FFF8DC), ink = Color(0xFF4E3309), highlight = Color(0xA6FFFAE1), pip = Color(0xFFE8B84A),
    )
    val Onyx = MetalStyle(
        listOf(0f to Color(0xFF55555C), 0.5f to Color(0xFF2C2C31), 1f to Color(0xFF141416)),
        edge = Color(0xFFFF6A1A), ring = Color(0x73FF8A3D), ink = Color(0xFFFF8A3D), highlight = null,
        glow = Color(0xFFFF6A1A), pip = Color(0xFF2C2C31),
    )

    fun metal(m: MedalMetal): MetalStyle = when (m) {
        MedalMetal.COPPER -> Copper
        MedalMetal.SILVER -> Silver
        MedalMetal.GOLD -> Gold
        MedalMetal.ONYX -> Onyx
    }

    /** Ribbon halves, dark then light: the ribbon is the kind. */
    fun ribbon(kind: MedalKind): Pair<Color, Color> = when (kind) {
        MedalKind.SKILLS -> Color(0xFFB24A18) to Color(0xFFFF6A1A)
        MedalKind.STRENGTH -> Color(0xFFB8901F) to Color(0xFFF5C242)
        MedalKind.SHOWING_UP -> Color(0xFF8E8E94) to Color(0xFFE4E4E7)
        MedalKind.HEART -> Color(0xFF2E7DA8) to Color(0xFF57B7E6)
        MedalKind.BALANCE -> Color(0xFF5E52B8) to Color(0xFF9B8CFF)
        MedalKind.LIFE -> Color(0xFF23865A) to Color(0xFF3FBF7F)
    }

    val RibbonHidden = Color(0xFF2E2E32) to Color(0xFF3A3A3F)

    fun metalName(m: MedalMetal): String = when (m) {
        MedalMetal.COPPER -> "Copper"
        MedalMetal.SILVER -> "Silver"
        MedalMetal.GOLD -> "Gold"
        MedalMetal.ONYX -> "Onyx"
    }
}

// ------------------------------------------------------------------------------------------- emblems

/** A line drawing in a 24 × 24 box, stroked round. */
class Emblem(val paths: List<Path>, val stroke: Float)

/**
 * The emblems drawn on the medal faces, from the design's symbol set. Path data goes through
 * [SvgPathNormalizer]: Compose's parser silently drops arcs whose flags run together, and several of
 * these (the kettlebell's handle, the flame, the moon) are arcs.
 */
object MedalEmblems {
    private class Spec(
        val d: List<String>,
        val circles: List<Triple<Float, Float, Float>> = emptyList(),
        val rects: List<FloatArray> = emptyList(),
        val stroke: Float = 2f,
    )

    private val specs: Map<String, Spec> = mapOf(
        "e-pullup" to Spec(listOf("M3 4h18M8 4l2.5 4.5M16 4l-2.5 4.5M12 12v5M9 21l3-4 3 4"), listOf(Triple(12f, 10f, 2f))),
        "e-muscleup" to Spec(listOf("M3 15h18M12 21V5M7 10l5-5 5 5")),
        "e-pistol" to Spec(listOf("M9 8v6l-3 6M9 14h11M9 10l5 1"), listOf(Triple(9f, 5f, 2f))),
        "e-lsit" to Spec(listOf("M7 8v6h12M4 11v7M10 11v7"), listOf(Triple(7f, 5f, 2f))),
        "e-lever" to Spec(listOf("M3 4h18M8 4v5M7 9.5h9M16 9.5l-3 3.5h-3"), listOf(Triple(5f, 9.5f, 1.8f))),
        "e-pushup" to Spec(listOf("M7 11l13 3M8 11v6M3 19h18"), listOf(Triple(5f, 10f, 1.8f))),
        "e-dip" to Spec(
            listOf("M5 21V11M19 21V11M3 11h4M17 11h4M12 7v7M12 9l-6 2M12 9l6 2M12 14l-2 5M12 14l2 5"),
            listOf(Triple(12f, 4.5f, 1.8f)),
        ),
        "e-kettle" to Spec(listOf("M9 8.5a3 3 0 0 1 6 0"), listOf(Triple(12f, 14f, 6f))),
        "e-flame" to Spec(listOf("M12 3c1 4 5 6 5 10a5 5 0 0 1-10 0c0-3 2-4 2-7 1 2 2 3 3 3 0-2 0-4 0-6z")),
        "e-check" to Spec(listOf("M5 12.5l4.5 4.5L19 7.5"), stroke = 2.2f),
        "e-calcheck" to Spec(listOf("M4 10h16M9 3v4M15 3v4M9 15l2 2 4-4"), rects = listOf(floatArrayOf(4f, 5f, 16f, 15f, 3f))),
        "e-comeback" to Spec(listOf("M4 12a8 8 0 1 0 2.3-5.7M4 4v4h4")),
        "e-heartpulse" to Spec(listOf("M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z", "M8 11h2l1-2 2 4 1-2h2")),
        "e-trenddown" to Spec(listOf("M3 7l6 6 4-4 8 8M21 11v6h-6")),
        "e-ecg" to Spec(listOf("M2 12h5l2-5 3 10 2-5h8")),
        "e-gauge" to Spec(listOf("M4 17a8 8 0 0 1 16 0M12 17l-3-5"), listOf(Triple(12f, 17f, 1f))),
        "e-body" to Spec(listOf("M12 7v7M5 9h14M12 14l-4 7M12 14l4 7"), listOf(Triple(12f, 4f, 2f))),
        "e-scales" to Spec(listOf("M12 4v16M7 20h10M5 8h14M5 8l-2.5 6a3 3 0 0 0 5 0L5 8zM19 8l-2.5 6a3 3 0 0 0 5 0L19 8z"), stroke = 1.9f),
        "e-legs" to Spec(listOf("M9 3v7l-2 9h3M15 3v7l2 9h-3")),
        "e-compass" to Spec(listOf("M15.5 8.5l-2 5-5 2 2-5z"), listOf(Triple(12f, 12f, 9f))),
        "e-snow" to Spec(listOf("M12 3v18M4.2 7.5l15.6 9M4.2 16.5l15.6-9")),
        "e-sunrise" to Spec(listOf("M3 18h18M7 18a5 5 0 0 1 10 0M12 6v3M5 10l2 2M19 10l-2 2")),
        "e-moon" to Spec(listOf("M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z")),
        "e-question" to Spec(listOf("M9 9a3 3 0 1 1 4.5 2.6c-.9.5-1.5 1.2-1.5 2.4M12 18h.01")),
        // Not in the drawn set (both are hidden until earned): two linked rings, and a struck "100".
        "e-twice" to Spec(emptyList(), listOf(Triple(8.5f, 12f, 5f), Triple(15.5f, 12f, 5f))),
        "e-hundred" to Spec(
            listOf("M3.5 8.5l2-1.5v10"),
            rects = listOf(floatArrayOf(9f, 7f, 5f, 10f, 2.5f), floatArrayOf(16f, 7f, 5f, 10f, 2.5f)),
        ),
    )

    private val cache = HashMap<String, Emblem>()

    fun get(id: String): Emblem = synchronized(cache) {
        cache.getOrPut(id) { build(specs[id] ?: specs.getValue(MedalCatalog.HIDDEN_EMBLEM)) }
    }

    private fun build(spec: Spec): Emblem {
        val paths = ArrayList<Path>()
        spec.d.forEach { paths += PathParser().parsePathString(SvgPathNormalizer.normalize(it)).toPath() }
        spec.circles.forEach { (cx, cy, r) -> paths += Path().apply { addOval(Rect(cx - r, cy - r, cx + r, cy + r)) } }
        spec.rects.forEach { (x, y, w, h, rx) ->
            paths += Path().apply { addRoundRect(RoundRect(Rect(x, y, x + w, y + h), CornerRadius(rx, rx))) }
        }
        return Emblem(paths, spec.stroke)
    }
}

private operator fun FloatArray.component5(): Float = this[4]

