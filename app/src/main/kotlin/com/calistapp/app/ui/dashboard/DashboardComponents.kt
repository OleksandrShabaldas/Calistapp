package com.calistapp.app.ui.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.calistapp.app.data.recommend.RecommendationsUi
import com.calistapp.app.ui.common.GlowBox
import com.calistapp.app.ui.common.GlowIcon
import com.calistapp.app.ui.common.ProgressRing
import com.calistapp.app.ui.exercises.ExerciseImage
import com.calistapp.app.ui.exercises.NextUpVideoPlayer
import com.calistapp.app.ui.theme.Amber
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.AshFaint
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.Coral
import com.calistapp.app.ui.theme.FlameGlow
import com.calistapp.app.ui.theme.FlameHot

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

/**
 * Bottom fade + top-right corner shadow, for the Next Up image/placeholder (Compose content). Mirrors
 * `video_corner_scrim.xml`'s numbers exactly (260dp, 97%→92%→0%) so the fallback looks identical to the
 * video path — this had drifted out of sync with the XML once before (still on the old 65%/0.62× values
 * after the video drawable was fixed), so keep the two in lockstep if this shape changes again.
 */
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
            colorStops = arrayOf(
                0f to Color.Black.copy(alpha = 0.97f),
                0.5f to Color.Black.copy(alpha = 0.92f),
                1f to Color.Transparent,
            ),
            center = Offset(size.width, 0f),
            radius = 260.dp.toPx(),
        ),
    )
}

@Composable
private fun StartButton(onClick: () -> Unit) {
    // GlowBox (not the generic circular .glow()) so the halo hugs the pill's own rounded-rect shape
    // instead of a slapped-on circle poking out top/bottom; also the user's requested smaller pass.
    val shape = RoundedCornerShape(16.dp)
    GlowBox(
        color = FlameHot,
        shape = shape,
        glowRadius = 14.dp,
        glowAlpha = 0.48f,
        modifier = Modifier.fillMaxWidth().height(54.dp),
    ) {
        Row(
            Modifier
                .matchParentSize()
                .clip(shape)
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
}

/**
 * Shown in place of Next Up once you've trained today: a "today's workout" summary. Each of today's
 * finished sessions is a tappable row that opens its full summary; a green-lit check reads as done.
 */
@Composable
fun TodaySummaryCard(
    sessions: List<com.calistapp.core.model.SessionOverview>,
    onOpenSession: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (sessions.isEmpty()) return
    DashCard(modifier, contentPadding = 0.dp) {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, top = 16.dp, end = 18.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GlowIcon(Icons.Filled.CheckCircle, contentDescription = null, tint = FlameHot, size = 20.dp, glowRadius = 6.dp, glowAlpha = 0.5f)
            Text(
                if (sessions.size > 1) "TODAY'S WORKOUTS · ${sessions.size}" else "TODAY'S WORKOUT",
                style = MaterialTheme.typography.labelSmall,
                color = FlameHot,
                fontWeight = FontWeight.Bold,
            )
        }
        sessions.forEachIndexed { i, s ->
            Column(
                Modifier.fillMaxWidth().clickable { onOpenSession(s.id) }.padding(horizontal = 18.dp, vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.title, style = MaterialTheme.typography.titleMedium, color = Chalk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            buildString {
                                append("${s.totalKcal} kcal")
                                if (s.totalReps > 0) append(" · ${s.totalReps} reps")
                                if (s.avgHr > 0) append(" · ${s.avgHr} bpm")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Ash,
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Open summary", tint = FlameHot)
                }
            }
            if (i < sessions.lastIndex) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(1.dp).background(CardBorder))
            }
        }
        Spacer(Modifier.height(6.dp))
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

/**
 * Steps today + the daily energy-goal ring — one card, two centred halves. The ring is two-tone:
 * orange for the calories walking earned, red for the calories training earned, stacked toward the
 * goal. Once the goal is met the ring stays full while the inner percentage keeps climbing past 100%.
 * Under the step count sits the day's EEA — workout calories expressed as the steps they're worth.
 */
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
                if (state.eeaSteps > 0) {
                    Spacer(Modifier.height(7.dp))
                    EeaLine(state.eeaSteps)
                }
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                val target = state.targetKcal.coerceAtLeast(1).toFloat()
                StepsRing(
                    stepFrac = state.stepKcal / target,
                    workoutFrac = state.workoutKcal / target,
                    diameter = 96.dp,
                    strokeWidth = 9.dp,
                ) {
                    Text("${state.percentOfTarget}%", style = MaterialTheme.typography.titleLarge, color = Chalk)
                }
            }
        }
    }
}

/**
 * The energy-goal ring, split into an orange (walking) and a red (training) arc that stack toward the
 * goal. The combined fill is capped at a full ring — kept in proportion when today's burn is over the
 * goal — so a met goal reads as a complete ring while the percentage in the middle carries on past 100.
 */
@Composable
private fun StepsRing(
    stepFrac: Float,
    workoutFrac: Float,
    modifier: Modifier = Modifier,
    diameter: Dp = 96.dp,
    strokeWidth: Dp = 9.dp,
    content: @Composable () -> Unit,
) {
    val total = stepFrac + workoutFrac
    val scale = if (total > 1f) 1f / total else 1f
    val animStep by animateFloatAsState((stepFrac * scale).coerceIn(0f, 1f), tween(700), label = "stepArc")
    val animWork by animateFloatAsState((workoutFrac * scale).coerceIn(0f, 1f), tween(700), label = "workArc")
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(Color.White.copy(alpha = 0.07f), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            if (animStep > 0f) {
                drawArc(FlameHot, -90f, 360f * animStep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            if (animWork > 0f) {
                drawArc(Coral, -90f + 360f * animStep, 360f * animWork, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        content()
    }
}

/** The "+ N EEA" line under the step count, red-accented, with a "?" that explains what EEA is. */
@Composable
private fun EeaLine(eeaSteps: Int) {
    var showInfo by remember { mutableStateOf(false) }
    Box {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                "+ %,d EEA".format(eeaSteps),
                style = MaterialTheme.typography.labelMedium,
                color = Coral,
                fontWeight = FontWeight.Bold,
            )
            Box(
                Modifier
                    .size(17.dp)
                    .clip(CircleShape)
                    .background(Coral.copy(alpha = 0.16f))
                    .border(1.dp, Coral.copy(alpha = 0.55f), CircleShape)
                    .clickable { showInfo = !showInfo },
                contentAlignment = Alignment.Center,
            ) {
                Text("?", style = MaterialTheme.typography.labelSmall, color = Coral, fontWeight = FontWeight.Bold)
            }
        }
        if (showInfo) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, 60),
                properties = PopupProperties(focusable = true),
                onDismissRequest = { showInfo = false },
            ) {
                EeaInfoCard(eeaSteps, onDismiss = { showInfo = false })
            }
        }
    }
}

@Composable
private fun EeaInfoCard(eeaSteps: Int, onDismiss: () -> Unit) {
    Column(
        Modifier
            .width(232.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CardSurface)
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onDismiss)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("EEA — Estimated Exercise Activity", style = MaterialTheme.typography.labelLarge, color = Coral, fontWeight = FontWeight.Bold)
        Text(
            "Steps only count the walking a pedometer sees, so a workout barely moves the number. EEA " +
                "converts today's workout calories into the steps it would take to burn the same — about " +
                "%,d — so hard training still shows up next to your step count.".format(eeaSteps),
            style = MaterialTheme.typography.bodySmall,
            color = Ash,
        )
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
        } else if (shortReadiness(r.score) == "Train") {
            // A clear "yes, train today": a filled ring with just a glowing check, per the ask — the
            // score still lives in the detail popup a tap away.
            ReadyCheckGauge(onClick = { onTap(GaugeKind.READINESS) })
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
            Text(bottom, style = MaterialTheme.typography.labelMedium, color = Chalk, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun GaugePlaceholder() {
    ProgressRing(progress = 0f, accent = AshFaint, diameter = 118.dp, strokeWidth = 9.dp) {
        Text("Loading…", style = MaterialTheme.typography.labelMedium, color = Ash, textAlign = TextAlign.Center)
    }
}

/** The readiness gauge when the answer is simply "train": full ring, a glowing check, nothing else. */
@Composable
private fun ReadyCheckGauge(onClick: () -> Unit) {
    ProgressRing(
        progress = 1f,
        accent = FlameHot,
        modifier = Modifier.clip(CircleShape).clickable(onClick = onClick),
        diameter = 118.dp,
        strokeWidth = 9.dp,
    ) {
        GlowIcon(
            Icons.Filled.Check,
            contentDescription = "Ready to train",
            tint = FlameHot,
            size = 46.dp,
            glowRadius = 12.dp,
            glowAlpha = 0.6f,
        )
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
