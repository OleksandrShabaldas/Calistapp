package com.calistapp.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.Coral
import com.calistapp.app.ui.theme.Flame
import com.calistapp.app.ui.theme.Sky
import com.calistapp.core.model.HeartRateSample
import com.calistapp.core.model.HrZone
import com.calistapp.core.model.Segment
import com.calistapp.core.model.SegmentType
import kotlin.math.max
import kotlin.math.min

/**
 * Heart rate against time, with the trace **coloured by the zone each reading falls in** — the same
 * cool-to-hot palette as the "Time in zones" bars ([hrZoneColor]). So the graph doesn't just show the
 * shape of the effort, it shows the intensity of every moment of it, and a spell in the red reads as
 * red here and red in the zone breakdown both.
 *
 * Rest blocks are shaded (toggleable) so you can see how effort and recovery mapped onto the curve,
 * and the peak is marked. A bare Canvas is invisible to a screen reader, so the whole thing carries a
 * spoken summary.
 */
@Composable
fun HeartRateChart(
    samples: List<HeartRateSample>,
    segments: List<Segment>,
    avgHr: Int,
    maxHr: Int,
    modifier: Modifier = Modifier,
    showRest: Boolean = true,
    restColor: Color = Sky,
) {
    if (samples.size < 2) {
        Text(
            "Not enough heart-rate data to chart.",
            style = MaterialTheme.typography.bodySmall,
            color = Ash,
        )
        return
    }

    val minBpm = samples.minOf { it.bpm } - 5
    val maxBpm = samples.maxOf { it.bpm } + 5
    val gridColor = Color.White.copy(alpha = 0.14f)
    val peak = samples.maxByOrNull { it.bpm }!!

    val spokenMinutes = ((samples.last().timestampMs - samples.first().timestampMs) / 60_000).toInt()
    val restCount = segments.count { it.type == SegmentType.REST }
    val chartDescription = buildString {
        append("Heart rate over $spokenMinutes minutes. ")
        append("Ranged from ${samples.minOf { it.bpm }} to ${samples.maxOf { it.bpm }} beats per ")
        append("minute, averaging $avgHr, peaking at ${peak.bpm}. ")
        append("Coloured by intensity zone. ")
        if (restCount > 0 && showRest) append("$restCount rest periods are shaded.")
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .semantics { contentDescription = chartDescription },
        ) {
            val startT = samples.first().timestampMs
            val endT = samples.last().timestampMs
            val spanT = (endT - startT).coerceAtLeast(1L).toFloat()
            val range = (maxBpm - minBpm).coerceAtLeast(1).toFloat()

            fun px(t: Long): Float = (t - startT) / spanT * size.width
            fun py(bpm: Int): Float = size.height - (bpm - minBpm) / range * size.height

            if (showRest) {
                segments.filter { it.type == SegmentType.REST }.forEach { seg ->
                    val x0 = px(seg.startMs.coerceIn(startT, endT))
                    val x1 = px((seg.endMs ?: endT).coerceIn(startT, endT))
                    drawRect(
                        color = restColor.copy(alpha = 0.12f),
                        topLeft = Offset(min(x0, x1), 0f),
                        size = Size(max(x1 - x0, 0f), size.height),
                    )
                }
            }

            // A faint area under the curve, so the line has a body rather than floating.
            val fill = Path().apply {
                moveTo(px(samples.first().timestampMs), size.height)
                samples.forEach { lineTo(px(it.timestampMs), py(it.bpm)) }
                lineTo(px(samples.last().timestampMs), size.height)
                close()
            }
            drawPath(
                fill,
                brush = Brush.verticalGradient(
                    listOf(Flame.copy(alpha = 0.20f), Flame.copy(alpha = 0f)),
                ),
            )

            // Baseline at average HR.
            val avgY = py(avgHr.coerceIn(minBpm, maxBpm))
            drawLine(
                color = gridColor,
                start = Offset(0f, avgY),
                end = Offset(size.width, avgY),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 9f)),
            )

            // The trace, one short segment per reading, coloured by the zone that reading sits in.
            for (i in 0 until samples.size - 1) {
                val a = samples[i]
                val b = samples[i + 1]
                val zone = HrZone.forHr((a.bpm + b.bpm) / 2, maxHr)
                drawLine(
                    color = hrZoneColor(zone),
                    start = Offset(px(a.timestampMs), py(a.bpm)),
                    end = Offset(px(b.timestampMs), py(b.bpm)),
                    strokeWidth = 2.4.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }

            // Peak marker.
            drawPeak(px(peak.timestampMs), py(peak.bpm))
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AxisLabel("0:00")
            AxisLabel(formatClock(samples.last().timestampMs - samples.first().timestampMs), TextAlign.End)
        }
    }
}

private fun DrawScope.drawPeak(x: Float, y: Float) {
    drawCircle(Coral.copy(alpha = 0.45f), radius = 6.dp.toPx(), center = Offset(x, y))
    drawCircle(Color.White, radius = 3.dp.toPx(), center = Offset(x, y))
}

@Composable
private fun AxisLabel(text: String, align: TextAlign = TextAlign.Start) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = Ash,
        textAlign = align,
    )
}
