package com.calistapp.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.calistapp.app.data.recommend.RecommendationsUi
import com.calistapp.app.ui.common.GlowIcon
import com.calistapp.app.ui.common.ProgressRing
import com.calistapp.app.ui.common.glow
import com.calistapp.app.ui.exercises.ExerciseImage
import com.calistapp.app.ui.exercises.NextUpVideoPlayer
import com.calistapp.app.ui.theme.Amber
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.AshFaint
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.Coral
import com.calistapp.app.ui.theme.FlameGlow
import com.calistapp.app.ui.theme.FlameHot
import kotlin.math.roundToInt

/** Which recommendation gauge is open in the detail overlay. */
enum class GaugeKind { READINESS, CONDITIONS }

internal val OnOrange = Color(0xFF140A03)
internal val CardSurface = Color(0xFF141317)
internal val CardBorder = Color(0x12FFFFFF)
internal val emberBrush @Composable get() = Brush.verticalGradient(listOf(FlameGlow, FlameHot))

/** The flat, faintly-bordered card the dashboard's boxed widgets are built from. */
@Composable
fun DashCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(CardSurface)
            .border(1.dp, CardBorder, RoundedCornerShape(22.dp))
            .padding(contentPadding),
        content = content,
    )
}

/** The streak pill: a flame glyph (with a soft glow) + the day count. Tap for the streak heatmap. */
@Composable
fun StreakPill(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            // No .clip() — that would slice the flame's glow off; the shaped background/border and the
            // clickable don't need it.
            .background(FlameHot.copy(alpha = 0.12f), CircleShape)
            .border(1.dp, FlameHot.copy(alpha = 0.30f), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        GlowIcon(
            Icons.Filled.LocalFireDepartment,
            contentDescription = "Streak",
            tint = FlameHot,
            size = 16.dp,
            glowRadius = 5.dp,
            glowAlpha = 0.5f,
        )
        Text("$count", style = MaterialTheme.typography.titleSmall, color = Chalk, fontWeight = FontWeight.Bold)
    }
}

/**
 * The Next Up card. Tapping the card opens the workout's info screen; the Start button loads it and
 * heads straight to the pre-flight setup (so Start no longer just re-opens the info screen).
 */
@Composable
fun NextUpCard(state: NextUpState, onOpenInfo: () -> Unit, onStart: () -> Unit, modifier: Modifier = Modifier) {
    DashCard(modifier.clickable(onClick = onOpenInfo), contentPadding = 0.dp) {
        Box(Modifier.fillMaxWidth().height(168.dp)) {
            when {
                // The video bakes its fade + corner shadow into the view (they don't composite as
                // Compose overlays over the player's texture layer).
                state.videoUrls.isNotEmpty() -> NextUpVideoPlayer(
                    urls = state.videoUrls,
                    modifier = Modifier.fillMaxWidth().height(168.dp),
                )
                // Compose content (image / placeholder) takes the scrims via drawWithContent, which
                // does composite reliably.
                state.imageUrls.isNotEmpty() -> ExerciseImage(
                    urls = state.imageUrls,
                    contentDescription = null,
                    animate = true,
                    phaseKey = state.savedWorkoutId,
                    modifier = Modifier.fillMaxWidth().height(168.dp).mediaScrims(),
                )
                else -> Box(
                    Modifier.fillMaxWidth().height(168.dp)
                        .background(Brush.linearGradient(listOf(Color(0xFF2A2A2E), Color(0xFF0C0C0E))))
                        .mediaScrims(),
                )
            }
            Badge(if (state.scheduled) state.whenLabel.uppercase() else "NEXT UP", Modifier.align(Alignment.TopEnd).padding(12.dp))
        }
        Column(Modifier.padding(16.dp)) {
            Text(state.name, style = MaterialTheme.typography.headlineSmall, color = Chalk, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(state.meta, style = MaterialTheme.typography.bodyMedium, color = Ash)
            Spacer(Modifier.height(16.dp))
            StartButton(onStart)
        }
    }
}

/** Bottom fade + top-right corner shadow, for the Next Up image/placeholder (Compose content). */
private fun Modifier.mediaScrims(): Modifier = drawWithContent {
    drawContent()
    drawRect(
        Brush.verticalGradient(
            listOf(Color.Transparent, CardSurface),
            startY = size.height - 104.dp.toPx(),
            endY = size.height,
        ),
    )
    drawRect(
        Brush.radialGradient(
            listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent),
            center = Offset(size.width, 0f),
            radius = size.maxDimension * 0.62f,
        ),
    )
}

@Composable
private fun StartButton(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .glow(FlameHot, spread = 20.dp, alpha = 0.38f)
            .clip(RoundedCornerShape(16.dp))
            .background(emberBrush)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = OnOrange, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text("Start workout", style = MaterialTheme.typography.titleMedium, color = OnOrange, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun Badge(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(CircleShape)
            .background(FlameHot.copy(alpha = 0.14f))
            .border(1.dp, FlameHot.copy(alpha = 0.5f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = FlameHot, fontWeight = FontWeight.Bold)
    }
}

/** Steps today + the daily energy-goal ring — one card, split into two centred halves. */
@Composable
fun StepsWidget(state: StepsState, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    DashCard(if (onClick != null) modifier.clickable(onClick = onClick) else modifier, contentPadding = 20.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                GlowIcon(
                    Icons.AutoMirrored.Filled.DirectionsWalk,
                    contentDescription = null,
                    tint = FlameHot,
                    size = 26.dp,
                    glowRadius = 7.dp,
                    glowAlpha = 0.5f,
                )
                Spacer(Modifier.height(8.dp))
                Text("Steps", style = MaterialTheme.typography.labelMedium, color = Ash)
                Text(
                    "%,d / %,d".format(state.steps, state.stepGoal),
                    style = MaterialTheme.typography.titleMedium,
                    color = Chalk,
                    fontWeight = FontWeight.Bold,
                )
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                ProgressRing(progress = state.progress, accent = FlameHot, diameter = 96.dp, strokeWidth = 9.dp) {
                    Text("${(state.progress * 100).roundToInt()}%", style = MaterialTheme.typography.titleLarge, color = Chalk)
                }
            }
        }
    }
}

/**
 * The two AI gauges, bare (no card, no titles) — tap either for the detail overlay. Each resolves
 * independently: while its half is being generated it shows "Loading…" rather than stale data.
 */
@Composable
fun RecommendationsRow(
    state: RecommendationsUi,
    onTap: (GaugeKind) -> Unit,
    onEnableLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        val r = state.readiness
        if (state.readinessLoading || r == null) {
            GaugePlaceholder()
        } else {
            RecGauge(
                progress = r.score / 100f,
                accent = readinessColor(r.score),
                top = shortReadiness(r.score),
                bottom = "${r.score}%",
                onClick = { onTap(GaugeKind.READINESS) },
            )
        }
        val c = state.conditions
        if (state.conditionsLoading || c == null) {
            GaugePlaceholder()
        } else {
            RecGauge(
                progress = conditionsFill(c.label),
                accent = FlameHot,
                top = c.label,
                bottom = c.detail.ifBlank { "—" },
                onClick = { if (c.needsLocation) onEnableLocation() else onTap(GaugeKind.CONDITIONS) },
            )
        }
    }
}

/** A single recommendation gauge — just the ring and what's inside it (bigger than before). */
@Composable
fun RecGauge(
    progress: Float,
    accent: Color,
    top: String,
    bottom: String,
    modifier: Modifier = Modifier,
    diameter: Dp = 118.dp,
    onClick: (() -> Unit)? = null,
) {
    val ringMod = if (onClick != null) modifier.clip(CircleShape).clickable(onClick = onClick) else modifier
    ProgressRing(progress = progress, accent = accent, modifier = ringMod, diameter = diameter, strokeWidth = 9.dp) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(top, style = MaterialTheme.typography.titleSmall, color = accent, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(bottom, style = MaterialTheme.typography.labelMedium, color = Chalk)
        }
    }
}

@Composable
private fun GaugePlaceholder() {
    ProgressRing(progress = 0f, accent = AshFaint, diameter = 118.dp, strokeWidth = 9.dp) {
        Text("Loading…", style = MaterialTheme.typography.labelMedium, color = Ash, textAlign = TextAlign.Center)
    }
}

internal fun readinessColor(score: Int): Color = when {
    score >= 70 -> FlameHot
    score >= 40 -> Amber
    else -> Coral
}

internal fun shortReadiness(score: Int): String = when {
    score >= 70 -> "Train"
    score >= 40 -> "Easy"
    else -> "Rest"
}

internal fun conditionsFill(label: String): Float = when {
    label.contains("Outdoor", ignoreCase = true) -> 0.82f
    label.contains("Indoor", ignoreCase = true) -> 0.42f
    else -> 0f
}

internal fun compactGoal(goal: Int): String =
    if (goal >= 1000) {
        val k = goal / 1000.0
        (if (k % 1.0 == 0.0) k.toInt().toString() else "%.1f".format(k)) + "K"
    } else goal.toString()
