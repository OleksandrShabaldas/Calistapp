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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.calistapp.app.data.recommend.RecommendationState
import com.calistapp.app.ui.common.ProgressRing
import com.calistapp.app.ui.common.glow
import com.calistapp.app.ui.exercises.ExerciseImage
import com.calistapp.app.ui.exercises.ExerciseVideoPlaylist
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

/** The streak pill: a flame glyph (with a soft glow) + the day count. */
@Composable
fun StreakPill(count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(CircleShape)
            .background(FlameHot.copy(alpha = 0.12f))
            .border(1.dp, FlameHot.copy(alpha = 0.30f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            Icons.Filled.LocalFireDepartment,
            contentDescription = "Streak",
            tint = FlameHot,
            modifier = Modifier.glow(FlameHot, spread = 8.dp, alpha = 0.28f).size(16.dp),
        )
        Text("$count", style = MaterialTheme.typography.titleSmall, color = Chalk, fontWeight = FontWeight.Bold)
    }
}

/** The Next Up card — the workout's exercises play as looping video, one after another. */
@Composable
fun NextUpCard(state: NextUpState, onStart: () -> Unit, modifier: Modifier = Modifier) {
    DashCard(modifier, contentPadding = 0.dp) {
        Box(Modifier.fillMaxWidth().height(168.dp)) {
            when {
                state.videoUrls.isNotEmpty() -> ExerciseVideoPlaylist(
                    urls = state.videoUrls,
                    active = true,
                    playing = true,
                    modifier = Modifier.fillMaxWidth().height(168.dp),
                )
                state.imageUrls.isNotEmpty() -> ExerciseImage(
                    urls = state.imageUrls,
                    contentDescription = null,
                    animate = true,
                    phaseKey = state.savedWorkoutId,
                    modifier = Modifier.fillMaxWidth().height(168.dp),
                )
                else -> Box(
                    Modifier.fillMaxWidth().height(168.dp)
                        .background(Brush.linearGradient(listOf(Color(0xFF2A2A2E), Color(0xFF0C0C0E)))),
                )
            }
            Box(Modifier.fillMaxWidth().height(168.dp).background(Brush.verticalGradient(listOf(Color.Transparent, CardSurface))))
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
fun StepsWidget(state: StepsState, modifier: Modifier = Modifier) {
    DashCard(modifier, contentPadding = 20.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.AutoMirrored.Filled.DirectionsWalk,
                    contentDescription = null,
                    tint = FlameHot,
                    modifier = Modifier.glow(FlameHot, spread = 11.dp, alpha = 0.28f).size(26.dp),
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

/** The two AI gauges, bare (no card, no titles) — tap either for the detail overlay. */
@Composable
fun RecommendationsRow(
    state: RecommendationState,
    onTap: (GaugeKind) -> Unit,
    onEnableLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        when (state) {
            is RecommendationState.Loading -> {
                GaugePlaceholder()
                GaugePlaceholder()
            }
            is RecommendationState.Failed -> Text(
                state.message,
                style = MaterialTheme.typography.bodySmall,
                color = Ash,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            )
            is RecommendationState.Ready -> {
                val rec = state.rec
                RecGauge(
                    progress = rec.readiness.score / 100f,
                    accent = readinessColor(rec.readiness.score),
                    top = shortReadiness(rec.readiness.score),
                    bottom = "${rec.readiness.score}%",
                    onClick = { onTap(GaugeKind.READINESS) },
                )
                RecGauge(
                    progress = conditionsFill(rec.conditions.label),
                    accent = FlameHot,
                    top = rec.conditions.label,
                    bottom = rec.conditions.detail.ifBlank { "—" },
                    onClick = {
                        if (rec.conditions.needsLocation) onEnableLocation() else onTap(GaugeKind.CONDITIONS)
                    },
                )
            }
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
        Text("…", style = MaterialTheme.typography.titleLarge, color = Ash)
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
