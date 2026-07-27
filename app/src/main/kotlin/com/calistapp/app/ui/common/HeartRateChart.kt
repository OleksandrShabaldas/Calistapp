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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.calistapp.app.ui.theme.Coral
import com.calistapp.app.ui.theme.Sky
import com.calistapp.core.model.HeartRateSample
import com.calistapp.core.model.Segment
import com.calistapp.core.model.SegmentType
import kotlin.math.max
import kotlin.math.min

/**
 * Draws heart rate against time for a completed session. Rest segments are shaded so you can see,
 * at a glance, how effort and recovery mapped onto your HR — the same active/rest split that made
 * the calorie count accurate.
 */
@Composable
fun HeartRateChart(
    samples: List<HeartRateSample>,
    segments: List<Segment>,
    avgHr: Int,
    modifier: Modifier = Modifier,
    lineColor: Color = Coral,
    restColor: Color = Sky,
) {
    if (samples.size < 2) {
        Text(
            "Not enough heart-rate data to chart.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    val minBpm = (samples.minOf { it.bpm } - 5)
    val maxBpm = (samples.maxOf { it.bpm } + 5)
    val gridColor = MaterialTheme.colorScheme.outline

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(160.dp),
        ) {
            val startT = samples.first().timestampMs
            val endT = samples.last().timestampMs
            val spanT = (endT - startT).coerceAtLeast(1L).toFloat()
            val range = (maxBpm - minBpm).coerceAtLeast(1).toFloat()

            fun px(t: Long): Float = (t - startT) / spanT * size.width
            fun py(bpm: Int): Float = size.height - (bpm - minBpm) / range * size.height

            // Shade rest periods.
            segments.filter { it.type == SegmentType.REST }.forEach { seg ->
                val x0 = px(seg.startMs.coerceIn(startT, endT))
                val x1 = px((seg.endMs ?: endT).coerceIn(startT, endT))
                drawRect(
                    color = restColor.copy(alpha = 0.15f),
                    topLeft = Offset(min(x0, x1), 0f),
                    size = Size(max(x1 - x0, 0f), size.height),
                )
            }

            // Baseline grid line at the average HR.
            val avgY = py(avgHr.coerceIn(minBpm, maxBpm))
            drawLine(
                color = gridColor,
                start = Offset(0f, avgY),
                end = Offset(size.width, avgY),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)),
            )

            // The HR trace.
            val path = Path()
            samples.forEachIndexed { i, s ->
                val x = px(s.timestampMs)
                val y = py(s.bpm)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("$minBpm bpm", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("avg $avgHr · rest shaded", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("$maxBpm bpm", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
