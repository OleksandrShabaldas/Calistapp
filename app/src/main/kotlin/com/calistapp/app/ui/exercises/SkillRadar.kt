package com.calistapp.app.ui.exercises

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.Flame
import com.calistapp.app.ui.theme.FlameGlow
import com.calistapp.app.ui.theme.OnyxBorder
import com.calistapp.app.ui.theme.OnyxFillStrong
import com.calistapp.core.model.Skills
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The Skills tab's centrepiece: a five-axis radar of the movement's training qualities that flips to
 * reveal the same numbers as labelled meters. The radar is the shape-at-a-glance; the meters are the
 * exact read — a tap turns the card over between them.
 *
 * Both faces are drawn from the one authored [Skills] profile (Strength / Endurance / Skill / Mobility
 * / Cardio, 0..100), never from invented figures — an unrated movement shows an empty state upstream
 * rather than a zeroed card.
 */
@Composable
fun SkillProfileCard(skills: Skills, modifier: Modifier = Modifier) {
    var flipped by rememberSaveable { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = tween(520),
        label = "skill-flip",
    )

    Box(
        modifier
            .fillMaxWidth()
            .height(288.dp)
            .graphicsLayer { rotationY = rotation; cameraDistance = 14f * density }
            .clip(RoundedCornerShape(20.dp))
            .background(OnyxFillStrong)
            .border(1.dp, OnyxBorder, RoundedCornerShape(20.dp))
            .clickable { flipped = !flipped }
            .padding(14.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (rotation <= 90f) {
            RadarFace(skills, Modifier.fillMaxSize())
        } else {
            // The whole card is mirrored once past 90°, so counter-rotate the back so it reads right.
            Box(Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }) {
                MetersFace(skills)
            }
        }
    }
}

@Composable
private fun RadarFace(skills: Skills, modifier: Modifier = Modifier) {
    val axes = skills.axes
    val measurer = rememberTextMeasurer()
    val labelStyle = remember { TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Chalk) }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            // Leave a margin for the labels drawn just past each axis tip.
            val radius = min(cx, cy) * 0.72f
            val n = axes.size
            fun point(i: Int, frac: Float): Offset {
                val angle = (-Math.PI / 2 + i * 2 * Math.PI / n).toFloat()
                return Offset(cx + cos(angle) * radius * frac, cy + sin(angle) * radius * frac)
            }

            // Grid rings + spokes.
            listOf(0.33f, 0.66f, 1f).forEach { ring ->
                val path = Path()
                for (i in 0 until n) {
                    val p = point(i, ring)
                    if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
                path.close()
                drawPath(path, color = Chalk.copy(alpha = 0.08f), style = Stroke(width = 1f))
            }
            for (i in 0 until n) {
                drawLine(Chalk.copy(alpha = 0.06f), Offset(cx, cy), point(i, 1f), strokeWidth = 1f)
            }

            // The value polygon.
            val value = Path()
            for (i in 0 until n) {
                val frac = (axes[i].second.coerceIn(0, 100)) / 100f
                val p = point(i, frac.coerceAtLeast(0.02f))
                if (i == 0) value.moveTo(p.x, p.y) else value.lineTo(p.x, p.y)
            }
            value.close()
            drawPath(value, color = Flame.copy(alpha = 0.26f))
            drawPath(value, color = Flame, style = Stroke(width = 2.4f))
            for (i in 0 until n) {
                val frac = (axes[i].second.coerceIn(0, 100)) / 100f
                drawCircle(FlameGlow, radius = 3f, center = point(i, frac.coerceAtLeast(0.02f)))
            }

            // Axis labels, anchored just past each tip.
            for (i in 0 until n) {
                val tip = point(i, 1.16f)
                val layout = measurer.measure(axes[i].first, labelStyle)
                drawText(layout, topLeft = Offset(tip.x - layout.size.width / 2f, tip.y - layout.size.height / 2f))
            }
        }
        Text(
            "Tap to flip · exact levels",
            style = MaterialTheme.typography.labelSmall,
            color = Ash,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun MetersFace(skills: Skills) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Text(
            "Attribute levels",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Ash,
        )
        skills.axes.forEach { (label, value) ->
            Column(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(Modifier.fillMaxWidth()) {
                    Text(label, style = MaterialTheme.typography.labelLarge, color = Chalk, modifier = Modifier.weight(1f))
                    Text(levelWord(value), style = MaterialTheme.typography.labelMedium, color = if (value >= 66) Flame else Ash)
                }
                Box(
                    Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(50))
                        .background(Chalk.copy(alpha = 0.06f)),
                ) {
                    Box(
                        Modifier.fillMaxWidth((value.coerceIn(0, 100)) / 100f).fillMaxHeight()
                            .clip(RoundedCornerShape(50)).background(Flame),
                    )
                }
            }
        }
        Text(
            "Tap to flip back",
            style = MaterialTheme.typography.labelSmall,
            color = Ash,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

private fun levelWord(v: Int): String = when {
    v >= 80 -> "Advanced"
    v >= 55 -> "Moderate"
    v >= 30 -> "Low"
    else -> "Minimal"
}
