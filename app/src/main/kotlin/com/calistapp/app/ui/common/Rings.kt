package com.calistapp.app.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.calistapp.app.ui.theme.CreamMuted
import com.calistapp.app.ui.theme.NumericLarge
import com.calistapp.app.ui.theme.NumericMedium

/**
 * The hero element: a big number sitting inside a progress arc.
 *
 * A ring communicates "how far through" pre-attentively — you read it before you read the digits —
 * which is exactly what you want from a screen glanced at between sets.
 */
@Composable
fun ProgressRing(
    progress: Float,
    accent: Color,
    modifier: Modifier = Modifier,
    diameter: Dp = 208.dp,
    strokeWidth: Dp = 12.dp,
    content: @Composable () -> Unit,
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(700),
        label = "ring",
    )

    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)

            // Track.
            drawArc(
                color = Color.White.copy(alpha = 0.07f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            // Filled portion, swept from 12 o'clock.
            if (animated > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(accent.copy(alpha = 0.55f), accent, accent.copy(alpha = 0.55f)),
                    ),
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

/** Big value + caption, for the middle of a [ProgressRing]. */
@Composable
fun RingContent(
    value: String,
    caption: String,
    sub: String? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    // Merged so a screen reader announces "1240 of 3500 kcal, 2 of 4 sessions" as one figure rather
    // than three disconnected fragments in whatever order it walks them.
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = listOfNotNull(value, caption, sub).joinToString(", ")
        },
    ) {
        Text(value, style = NumericLarge, color = accent)
        Text(caption, style = MaterialTheme.typography.bodyMedium, color = CreamMuted)
        if (sub != null) {
            Text(sub, style = MaterialTheme.typography.bodySmall, color = CreamMuted.copy(alpha = 0.7f))
        }
    }
}

/**
 * The small satellite rings under the hero — one per secondary metric, mirroring the macro row in
 * the reference design.
 */
@Composable
fun MiniRing(
    label: String,
    value: String,
    sub: String,
    progress: Float,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.semantics(mergeDescendants = true) {
            contentDescription = "$label: $value $sub"
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = CreamMuted)
        ProgressRing(
            progress = progress,
            accent = accent,
            diameter = 58.dp,
            strokeWidth = 5.dp,
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Text(sub, style = MaterialTheme.typography.labelSmall, color = CreamMuted.copy(alpha = 0.75f))
    }
}

/** A labelled metric without a ring — for dense rows where four circles would be noise. */
@Composable
fun MetricBlock(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.semantics(mergeDescendants = true) { contentDescription = "$label: $value" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = NumericMedium, color = accent)
        Text(label, style = MaterialTheme.typography.labelMedium, color = CreamMuted)
    }
}
