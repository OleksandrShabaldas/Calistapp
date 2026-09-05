package com.calistapp.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One slice of a [SegmentedRing]: what share of the perimeter it takes, and in what colour. */
data class RingSegment(val fraction: Float, val color: Color)

/**
 * A card whose *border* is split into coloured segments running around its rounded rectangle — the
 * split telling a small story the number alone can't (active vs rest of a duration, one colour per
 * exercise of a rep or exercise count).
 *
 * The border takes the card's own rounded-rectangle shape rather than being a circle floated behind
 * it — a circular ring behind a rectangular chip would poke past the flat edges, the exact mismatch
 * the design conventions warn against. Segments are walked along the real perimeter with a
 * [PathMeasure], so corners get their fair share of each colour instead of the colours only landing
 * on the straight runs.
 */
@Composable
fun SegmentedRing(
    segments: List<RingSegment>,
    innerColor: Color,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 18.dp,
    strokeWidth: Dp = 2.5.dp,
    contentPadding: Dp = 15.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .drawBehind { drawSegmentedBorder(segments, strokeWidth.toPx(), cornerRadius.toPx()) }
            .padding(strokeWidth + 1.dp)
            .clip(RoundedCornerShape((cornerRadius - strokeWidth).coerceAtLeast(0.dp)))
            .background(innerColor)
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/**
 * Stroke the rounded-rect perimeter as a sequence of coloured arcs. A hair of gap is left at the end
 * of each segment so adjacent colours read as distinct segments rather than one blended band; a
 * single-segment ring (e.g. a solid track) draws with no gap.
 */
private fun DrawScope.drawSegmentedBorder(
    segments: List<RingSegment>,
    strokePx: Float,
    radiusPx: Float,
) {
    if (segments.isEmpty()) return
    val inset = strokePx / 2f
    val left = inset
    val top = inset
    val right = size.width - inset
    val bottom = size.height - inset
    val r = (radiusPx - inset).coerceAtLeast(0f)
    val cx = (left + right) / 2f
    // Built by hand, clockwise from top-centre, so the first segment starts at 12 o'clock — the same
    // origin the reference uses (a conic from -90°). Path.addRoundRect would start near a corner and
    // put the "active" arc in the wrong place.
    val path = Path().apply {
        moveTo(cx, top)
        lineTo(right - r, top)
        arcTo(Rect(right - 2 * r, top, right, top + 2 * r), -90f, 90f, false)
        lineTo(right, bottom - r)
        arcTo(Rect(right - 2 * r, bottom - 2 * r, right, bottom), 0f, 90f, false)
        lineTo(left + r, bottom)
        arcTo(Rect(left, bottom - 2 * r, left + 2 * r, bottom), 90f, 90f, false)
        lineTo(left, top + r)
        arcTo(Rect(left, top, left + 2 * r, top + 2 * r), 180f, 90f, false)
        lineTo(cx, top)
        close()
    }
    val measure = PathMeasure().apply { setPath(path, forceClosed = true) }
    val total = measure.length
    val totalFraction = segments.sumOf { it.fraction.toDouble() }.toFloat().coerceAtLeast(1e-4f)
    val gapPx = if (segments.size > 1) 2.dp.toPx() else 0f

    var startLen = 0f
    for (seg in segments) {
        val len = seg.fraction / totalFraction * total
        val end = (startLen + len - gapPx).coerceAtLeast(startLen)
        val slice = Path()
        if (measure.getSegment(startLen, end, slice, true)) {
            drawPath(slice, seg.color, style = Stroke(width = strokePx, cap = StrokeCap.Round))
        }
        startLen += len
    }
}
