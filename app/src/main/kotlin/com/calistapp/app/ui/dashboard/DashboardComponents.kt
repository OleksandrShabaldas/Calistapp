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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calistapp.app.data.recommend.RecommendationState
import com.calistapp.app.ui.common.ProgressRing
import com.calistapp.app.ui.exercises.ExerciseImage
import com.calistapp.app.ui.theme.Amber
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.AshFaint
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.Coral
import com.calistapp.app.ui.theme.FlameGlow
import com.calistapp.app.ui.theme.FlameHot
import kotlin.math.roundToInt

/** Text dark enough to sit on the hot orange — matches the prototype's near-black on the Start button. */
private val OnOrange = Color(0xFF140A03)
private val CardSurface = Color(0xFF141317)
private val CardBorder = Color(0x12FFFFFF)
private val emberBrush @Composable get() = Brush.verticalGradient(listOf(FlameGlow, FlameHot))

/** The flat, faintly-bordered card the whole dashboard is built from (glass is gone). */
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

/** The streak pill: a proper flame glyph (an improvement on the prototype's emoji) + the day count. */
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
        Icon(Icons.Filled.LocalFireDepartment, contentDescription = "Streak", tint = FlameHot, modifier = Modifier.size(16.dp))
        Text("$count", style = MaterialTheme.typography.titleSmall, color = Chalk, fontWeight = FontWeight.Bold)
    }
}

/** The weekly calorie bars + the trained/planned dots under each day. */
@Composable
fun WeekBarsCard(week: WeekState, modifier: Modifier = Modifier) {
    DashCard(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("This week", style = MaterialTheme.typography.titleMedium, color = Chalk)
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Chalk, fontWeight = FontWeight.Bold)) { append("%,d".format(week.totalKcal)) }
                    withStyle(SpanStyle(color = Ash, fontSize = 11.sp)) { append("  kcal") }
                },
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            week.days.forEach { day -> DayColumn(day, week.maxKcal) }
        }
    }
}

@Composable
private fun DayColumn(day: DayCell, maxKcal: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val frac = (day.kcal.toFloat() / maxKcal).coerceIn(0f, 1f)
        val animated by animateFloatAsState(frac, tween(600), label = "bar")
        val hasBurn = day.kcal > 0
        Box(Modifier.height(76.dp), contentAlignment = Alignment.BottomCenter) {
            Box(
                Modifier
                    .width(20.dp)
                    .height(if (hasBurn) 8.dp + 64.dp * animated else 6.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (hasBurn) emberBrush else SolidColor(Color.White.copy(alpha = 0.09f))),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            day.letter,
            style = MaterialTheme.typography.labelMedium,
            color = if (day.isToday) FlameHot else Ash,
            fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Medium,
        )
        Spacer(Modifier.height(6.dp))
        val dotColor = when {
            day.trained -> FlameHot
            day.planned -> Ash
            else -> AshFaint.copy(alpha = 0.4f)
        }
        Box(Modifier.size(5.dp).clip(CircleShape).background(dotColor))
    }
}

/** The Next Up card — a training photo, the workout, and a glowing Start button. */
@Composable
fun NextUpCard(state: NextUpState, onStart: () -> Unit, modifier: Modifier = Modifier) {
    DashCard(modifier, contentPadding = 0.dp) {
        Box(Modifier.fillMaxWidth().height(168.dp)) {
            if (state.imageUrls.isNotEmpty()) {
                ExerciseImage(
                    urls = state.imageUrls,
                    contentDescription = null,
                    animate = true,
                    phaseKey = state.savedWorkoutId,
                    modifier = Modifier.fillMaxWidth().height(168.dp),
                )
            } else {
                Box(Modifier.fillMaxWidth().height(168.dp).background(Brush.linearGradient(listOf(Color(0xFF2A2A2E), Color(0xFF0C0C0E)))))
            }
            // Scrim so the badge and the corners read against any photo.
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
            .shadow(18.dp, RoundedCornerShape(16.dp), spotColor = FlameHot, ambientColor = FlameHot)
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
private fun Badge(text: String, modifier: Modifier = Modifier) {
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

/** Steps today + the daily energy-goal ring. */
@Composable
fun StepsGoalCard(state: StepsState, modifier: Modifier = Modifier) {
    DashCard(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Icon(Icons.AutoMirrored.Filled.DirectionsWalk, contentDescription = null, tint = FlameHot, modifier = Modifier.size(24.dp))
                Spacer(Modifier.height(8.dp))
                Text("Steps", style = MaterialTheme.typography.labelMedium, color = Ash)
                Text("%,d".format(state.steps), style = MaterialTheme.typography.headlineMedium, color = Chalk)
            }
            ProgressRing(progress = state.progress, accent = FlameHot, diameter = 96.dp, strokeWidth = 9.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${(state.progress * 100).roundToInt()}%", style = MaterialTheme.typography.titleLarge, color = Chalk)
                    Text("OF ${compactGoal(state.stepGoal)}", style = MaterialTheme.typography.labelSmall, color = Ash)
                }
            }
        }
    }
}

/** The AI "should I train / indoors or out" widget. */
@Composable
fun RecommendationsCard(
    state: RecommendationState,
    onRefresh: () -> Unit,
    onEnableLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    DashCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(FlameHot))
            Text("Training recommendations", style = MaterialTheme.typography.titleMedium, color = Chalk)
        }
        Spacer(Modifier.height(18.dp))
        when (state) {
            is RecommendationState.Loading -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                GaugePlaceholder("Readiness", "SLEEP + LOAD")
                GaugePlaceholder("Conditions", "WEATHER + AIR")
            }
            is RecommendationState.Failed -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(state.message, style = MaterialTheme.typography.bodySmall, color = Ash, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text("Tap to retry", style = MaterialTheme.typography.labelLarge, color = FlameHot, modifier = Modifier.clickable(onClick = onRefresh))
            }
            is RecommendationState.Ready -> {
                val rec = state.rec
                Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, horizontalArrangement = Arrangement.SpaceEvenly) {
                    RecGauge(
                        progress = rec.readiness.score / 100f,
                        accent = readinessColor(rec.readiness.score),
                        top = shortReadiness(rec.readiness.score),
                        bottom = "${rec.readiness.score}%",
                        captionTitle = "Readiness",
                        captionSub = "SLEEP + LOAD",
                    )
                    RecGauge(
                        progress = conditionsFill(rec.conditions.label),
                        accent = FlameHot,
                        top = rec.conditions.label,
                        bottom = rec.conditions.detail.ifBlank { "—" },
                        captionTitle = "Conditions",
                        captionSub = "WEATHER + AIR",
                        onClick = if (rec.conditions.needsLocation) onEnableLocation else null,
                    )
                }
                AnimatedVisibility(expanded) {
                    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReasonLine("Readiness", rec.readiness.reason)
                        ReasonLine("Conditions", rec.conditions.reason)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReasonLine(label: String, reason: String) {
    if (reason.isBlank()) return
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = FlameHot, fontWeight = FontWeight.SemiBold)) { append("$label  ") }
            withStyle(SpanStyle(color = Ash)) { append(reason) }
        },
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun RecGauge(
    progress: Float,
    accent: Color,
    top: String,
    bottom: String,
    captionTitle: String,
    captionSub: String,
    onClick: (() -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    ) {
        ProgressRing(progress = progress, accent = accent, diameter = 90.dp, strokeWidth = 8.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(top, style = MaterialTheme.typography.titleSmall, color = accent, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(bottom, style = MaterialTheme.typography.labelSmall, color = Chalk)
            }
        }
        Text(captionTitle, style = MaterialTheme.typography.titleSmall, color = Chalk)
        Text(captionSub, style = MaterialTheme.typography.labelSmall, color = Ash)
    }
}

@Composable
private fun GaugePlaceholder(captionTitle: String, captionSub: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ProgressRing(progress = 0f, accent = AshFaint, diameter = 90.dp, strokeWidth = 8.dp) {
            Text("…", style = MaterialTheme.typography.titleLarge, color = Ash)
        }
        Text(captionTitle, style = MaterialTheme.typography.titleSmall, color = Chalk)
        Text(captionSub, style = MaterialTheme.typography.labelSmall, color = Ash)
    }
}

private fun readinessColor(score: Int): Color = when {
    score >= 70 -> FlameHot
    score >= 40 -> Amber
    else -> Coral
}

private fun shortReadiness(score: Int): String = when {
    score >= 70 -> "Train"
    score >= 40 -> "Easy"
    else -> "Rest"
}

private fun conditionsFill(label: String): Float = when {
    label.contains("Outdoor", ignoreCase = true) -> 0.82f
    label.contains("Indoor", ignoreCase = true) -> 0.42f
    else -> 0f
}

private fun compactGoal(goal: Int): String =
    if (goal >= 1000) {
        val k = goal / 1000.0
        (if (k % 1.0 == 0.0) k.toInt().toString() else "%.1f".format(k)) + "K"
    } else goal.toString()
