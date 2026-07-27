package com.calistapp.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calistapp.app.ui.common.GlassCard
import com.calistapp.app.ui.common.MetricBlock
import com.calistapp.app.ui.common.ProgressRing
import com.calistapp.app.ui.common.RingContent
import com.calistapp.app.ui.common.SectionHeading
import com.calistapp.app.ui.common.SessionRow
import com.calistapp.app.ui.common.WatchStatusCard
import com.calistapp.app.ui.common.formatClock
import com.calistapp.app.ui.theme.Amber
import com.calistapp.app.ui.theme.Coral
import com.calistapp.app.ui.theme.Cream
import com.calistapp.app.ui.theme.CreamMuted
import com.calistapp.app.ui.theme.Emerald
import com.calistapp.app.ui.theme.Sky

/** Weekly calorie target used purely to scale the hero ring — progress needs a denominator. */
private const val WEEKLY_KCAL_GOAL = 3500f

@Composable
fun DashboardScreen(
    onStartWorkout: () -> Unit,
    onOpenSession: (String) -> Unit,
    onOpenProfile: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val onboarded by viewModel.isOnboarded.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val week by viewModel.week.collectAsStateWithLifecycle()
    val live by viewModel.live.collectAsStateWithLifecycle()
    val watchLink by viewModel.watchLink.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Greeting(name = profile.name, hasTrained = week.sessions > 0)

        // Hero: the week at a glance, with the number you actually care about in the middle.
        GlassCard(contentPadding = 22) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ProgressRing(
                    progress = week.totalKcal / WEEKLY_KCAL_GOAL,
                    accent = Emerald,
                ) {
                    RingContent(
                        value = "${week.totalKcal}",
                        caption = "kcal this week",
                        sub = "${week.sessions} ${if (week.sessions == 1) "session" else "sessions"}",
                        accent = Cream,
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                MetricBlock("Sessions", "${week.sessions}", Sky)
                MetricBlock("Active min", "${week.activeMinutes}", Amber)
                MetricBlock("Avg / session", avgPerSession(week), Coral)
            }
        }

        if (!onboarded) {
            GlassCard(accent = Amber) {
                Text("Finish setting up your profile", style = MaterialTheme.typography.titleLarge, color = Cream)
                Text(
                    "Accurate calories need your weight, age, sex and heart data.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CreamMuted,
                )
                TextButton(onClick = onOpenProfile, contentPadding = ButtonDefaults.TextButtonContentPadding) {
                    Text("Set up profile", color = Amber)
                }
            }
        }

        val current = live
        if (current != null) {
            GlassCard(accent = Emerald) {
                SectionHeading("Workout in progress")
                Text(
                    formatClock(current.elapsedMs),
                    style = MaterialTheme.typography.displaySmall,
                    color = Cream,
                )
                Text(
                    "${current.lastBpm} bpm · ${current.summary.totalKcal.toInt()} kcal so far",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CreamMuted,
                )
                Button(
                    onClick = onStartWorkout,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald),
                ) {
                    Text("Resume", fontWeight = FontWeight.Bold)
                }
            }
        } else {
            Button(
                onClick = onStartWorkout,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Emerald),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                Text("  Start workout", fontWeight = FontWeight.Bold)
            }
        }

        WatchStatusCard(state = watchLink, onReconnect = viewModel::reconnectWatch)

        if (recent.isNotEmpty()) {
            SectionHeading("Recent", count = recent.size)
            recent.forEach { session ->
                SessionRow(session = session, onClick = { onOpenSession(session.id) })
            }
        } else {
            GlassCard {
                Text(
                    "No workouts yet — start one and every rep gets counted.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CreamMuted,
                )
            }
        }
    }
}

/**
 * Editorial title with the first word set in accented italic serif — the reference's signature
 * opening move, and what stops the screen starting with a flat greeting.
 */
@Composable
private fun Greeting(name: String, hasTrained: Boolean) {
    val lead = if (name.isBlank()) "Ready" else "Hi"
    val rest = when {
        name.isBlank() && hasTrained -> " to go again?"
        name.isBlank() -> " to train?"
        hasTrained -> " $name — strong week so far"
        else -> " $name — let's get the first one in"
    }

    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = Emerald, fontStyle = FontStyle.Italic)) { append(lead) }
            append(rest)
        },
        style = MaterialTheme.typography.headlineLarge,
        color = Cream,
        modifier = Modifier.padding(top = 12.dp),
    )
}

private fun avgPerSession(week: WeekStats): String =
    if (week.sessions == 0) "—" else "${week.totalKcal / week.sessions}"
