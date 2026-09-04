package com.calistapp.app.ui.dashboard

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Today
import com.calistapp.app.ui.common.SessionRow
import com.calistapp.app.ui.common.formatClock
import com.calistapp.app.ui.common.glow
import com.calistapp.app.ui.theme.Amber
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.FlameHot
import kotlinx.coroutines.delay

@Composable
fun DashboardScreen(
    onStartWorkout: () -> Unit,
    onOpenWorkout: (String) -> Unit,
    onOpenSession: (String) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSchedule: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val header by viewModel.header.collectAsStateWithLifecycle()
    val week by viewModel.week.collectAsStateWithLifecycle()
    val steps by viewModel.steps.collectAsStateWithLifecycle()
    val nextUp by viewModel.nextUp.collectAsStateWithLifecycle()
    val recommendations by viewModel.recommendations.collectAsStateWithLifecycle()
    val onboarded by viewModel.isOnboarded.collectAsStateWithLifecycle()
    val live by viewModel.live.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val dayView by viewModel.dayView.collectAsStateWithLifecycle()
    val streak by viewModel.streak.collectAsStateWithLifecycle()
    val stepsInsights by viewModel.stepsInsights.collectAsStateWithLifecycle()

    var expandedGauge by remember { mutableStateOf<GaugeKind?>(null) }
    var showMonth by remember { mutableStateOf(false) }
    var showStreak by remember { mutableStateOf(false) }
    var showSteps by remember { mutableStateOf(false) }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result -> if (result.values.any { it }) viewModel.regenerateConditions() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        var slot = 0
        Spacer(Modifier.size(4.dp))
        Entrance(slot++) { GreetingHeader(header, onStreakClick = { showStreak = true }) }

        if (!onboarded) {
            Entrance(slot++) {
                DashCard {
                    Text("Finish setting up your profile", style = MaterialTheme.typography.titleLarge, color = Chalk)
                    Text(
                        "Accurate calories need your weight, age, sex and heart data.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Ash,
                    )
                    TextButton(onClick = onOpenProfile, contentPadding = ButtonDefaults.TextButtonContentPadding) {
                        Text("Set up profile", color = Amber)
                    }
                }
            }
        }

        live?.let { current ->
            Entrance(slot++) {
                DashCard(Modifier.clickable(onClick = onStartWorkout)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(FlameHot))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Workout in progress", style = MaterialTheme.typography.titleMedium, color = Chalk)
                            Text(
                                "${formatClock(current.elapsedMs)} · ${current.summary.totalKcal.toInt()} kcal",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Ash,
                            )
                        }
                        Text("Resume", style = MaterialTheme.typography.labelLarge, color = FlameHot)
                    }
                }
            }
        }

        Entrance(slot++) {
            WeekStrip(
                week = week,
                selectedDate = selectedDate,
                onOpenMonth = { showMonth = true },
                onOpenSchedule = onOpenSchedule,
                onSelectDay = viewModel::selectDay,
                onPreviousWeek = viewModel::previousWeek,
                onNextWeek = viewModel::nextWeek,
                onResetWeek = viewModel::resetToCurrentWeek,
            )
        }

        val viewing = dayView
        if (viewing != null) {
            Entrance(slot++) {
                StepsWidget(
                    StepsState(
                        steps = viewing.steps,
                        stepGoal = viewing.stepGoal,
                        earnedKcal = viewing.earnedKcal,
                        targetKcal = viewing.targetKcal,
                        progress = viewing.progress,
                        goalMet = viewing.earnedKcal >= viewing.targetKcal,
                    ),
                )
            }
            viewing.sessions.forEach { session ->
                Entrance(slot++) { SessionRow(session = session, onClick = { onOpenSession(session.id) }) }
            }
            Entrance(slot++) { PastDayNotice(viewing.dateLabel, hadWorkout = viewing.sessions.isNotEmpty(), onBack = viewModel::clearSelectedDay) }
        } else {
            Entrance(slot++) {
                nextUp?.let { NextUpCard(it, onStart = { onOpenWorkout(it.savedWorkoutId) }) } ?: DashCard {
                    Text("No workout yet", style = MaterialTheme.typography.titleLarge, color = Chalk)
                    Text(
                        "Build one and it'll wait here, ready to start.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Ash,
                    )
                    TextButton(onClick = onStartWorkout, contentPadding = ButtonDefaults.TextButtonContentPadding) {
                        Text("Build a workout", color = FlameHot)
                    }
                }
            }

            Entrance(slot++) { StepsWidget(steps, onClick = { showSteps = true }) }
            Spacer(Modifier.size(8.dp))
            Entrance(slot++) {
                RecommendationsRow(
                    state = recommendations,
                    onTap = { expandedGauge = it },
                    onEnableLocation = {
                        locationLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
                        )
                    },
                )
            }
        }

        Spacer(Modifier.size(4.dp))
    }

    GaugeDetailOverlay(
        kind = expandedGauge,
        readiness = recommendations.readiness,
        conditions = recommendations.conditions,
        onRegenerateConditions = viewModel::regenerateConditions,
        onDismiss = { expandedGauge = null },
    )
    MonthOverlay(
        visible = showMonth,
        onDismiss = { showMonth = false },
        onSelectDay = { viewModel.selectDay(it); showMonth = false },
    )
    StreakHeatmapOverlay(
        visible = showStreak,
        data = streak,
        onJumpToDay = viewModel::selectDay,
        onDismiss = { showStreak = false },
    )
    StepsInsightsOverlay(visible = showSteps, data = stepsInsights, onDismiss = { showSteps = false })
}

/** Fades + lifts a home-screen element in, staggered by its slot, for a smooth entrance on open. */
@Composable
private fun Entrance(index: Int, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 45L)
        shown = true
    }
    val p by animateFloatAsState(if (shown) 1f else 0f, tween(360, easing = FastOutSlowInEasing), label = "entrance")
    Box(
        Modifier
            .alpha(p)
            .offset { IntOffset(0, ((1f - p) * 40.dp.toPx()).toInt()) },
    ) { content() }
}

/** Shown in past-day mode where the recommendations would be — a note plus a way back to today. */
@Composable
private fun PastDayNotice(dateLabel: String, hadWorkout: Boolean, onBack: () -> Unit) {
    DashCard {
        Text("Viewing $dateLabel", style = MaterialTheme.typography.titleLarge, color = Chalk)
        Text(
            if (hadWorkout) "This day's steps and workout are shown above. Recommendations are only for today."
            else "No workout was logged this day. Recommendations are only for today.",
            style = MaterialTheme.typography.bodyMedium,
            color = Ash,
        )
        Spacer(Modifier.size(14.dp))
        Row(
            Modifier
                .glow(FlameHot, spread = 14.dp, alpha = 0.36f)
                .clip(CircleShape)
                .background(emberBrush)
                .clickable(onClick = onBack)
                .padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(Icons.Filled.Today, contentDescription = null, tint = OnOrange, modifier = Modifier.size(16.dp))
            Text("Back to today", style = MaterialTheme.typography.labelLarge, color = OnOrange, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun GreetingHeader(header: HeaderState, onStreakClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(
                header.dateLabel.ifBlank { " " },
                style = MaterialTheme.typography.labelMedium,
                color = FlameHot,
            )
            Spacer(Modifier.size(6.dp))
            Text(
                buildAnnotatedString {
                    append(header.greeting)
                    if (header.name.isNotBlank()) {
                        append(",\n")
                        append(header.name)
                    }
                    withStyle(SpanStyle(color = FlameHot)) { append(".") }
                },
                style = MaterialTheme.typography.displaySmall,
                color = Chalk,
            )
        }
        Spacer(Modifier.width(12.dp))
        StreakPill(header.streak, onClick = onStreakClick)
    }
}
