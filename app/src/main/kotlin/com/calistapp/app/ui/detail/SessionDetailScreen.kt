package com.calistapp.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calistapp.app.ui.common.HeartRateChart
import com.calistapp.app.ui.common.KeyValueRow
import com.calistapp.app.ui.common.SectionCard
import com.calistapp.app.ui.common.StatTile
import com.calistapp.app.ui.common.formatClock
import com.calistapp.app.ui.common.formatCompact
import com.calistapp.app.ui.common.formatDate
import com.calistapp.app.ui.theme.Amber
import com.calistapp.app.ui.theme.Coral
import com.calistapp.app.ui.theme.Emerald
import com.calistapp.app.ui.theme.Sky
import com.calistapp.core.model.HrZone
import com.calistapp.core.model.SessionSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    onBack: () -> Unit,
    viewModel: SessionDetailViewModel = hiltViewModel(),
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(session?.exerciseType?.displayName ?: "Session") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val current = session
        if (current == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        val s = current.summary ?: SessionSummary.EMPTY

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(formatDate(current.startMs), color = MaterialTheme.colorScheme.onSurfaceVariant)
            current.exerciseName?.let { name ->
                Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            SectionCard {
                Text(
                    "${s.totalKcal.toInt()} kcal",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatTile("Active burn", "${s.activeKcal.toInt()} kcal", accent = Emerald)
                    StatTile("Rest burn", "${s.restKcal.toInt()} kcal", accent = Sky)
                    StatTile("Work ratio", "${(s.activeRatio * 100).toInt()}%", accent = Amber)
                }
            }

            SectionCard(title = "Duration") {
                KeyValueRow("Total", formatClock(s.totalDurationMs))
                KeyValueRow("Active", formatCompact(s.activeDurationMs))
                KeyValueRow("Rest", formatCompact(s.restDurationMs))
            }

            // Where the energy actually went — the payoff of tracking exercises and reps.
            if (s.perExercise.isNotEmpty()) {
                SectionCard(title = "By exercise") {
                    s.perExercise.forEach { row ->
                        KeyValueRow(
                            row.exerciseName,
                            "${row.kcal.toInt()} kcal · ${row.sets} × · ${row.reps} reps",
                        )
                    }
                    if (s.totalReps > 0) {
                        KeyValueRow("Total reps", "${s.totalReps}")
                    }
                }
            }

            if (!current.plan.isEmpty) {
                SectionCard(title = "Planned vs performed") {
                    current.plan.exercises.forEach { slot ->
                        val done = current.setLogs.filter { it.slotId == slot.slotId }
                        KeyValueRow(
                            slot.name,
                            "${done.size}/${slot.targetSets} sets · ${done.sumOf { it.reps }} reps " +
                                "(target ${slot.targetLabel})",
                        )
                    }
                }
            }

            SectionCard(title = "Heart rate") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatTile("Average", "${s.avgHr}")
                    StatTile("Active avg", "${s.avgActiveHr}", accent = Emerald)
                    StatTile("Peak", "${s.peakHr}", accent = Coral)
                    StatTile("Min", "${s.minHr}", accent = Sky)
                }
            }

            if (current.samples.size >= 2) {
                SectionCard(title = "Heart rate over time") {
                    HeartRateChart(
                        samples = current.samples,
                        segments = current.segments,
                        avgHr = s.avgHr,
                    )
                }
            }

            if (s.timeInZonesMs.isNotEmpty()) {
                SectionCard(title = "Time in HR zones") {
                    HrZoneBars(s)
                }
            }

            AiCoachCard(
                insight = current.aiInsight,
                aiState = aiState,
                onGenerate = viewModel::generateInsight,
            )

            TextButton(onClick = { viewModel.deleteSession(onBack) }) {
                Text("Delete session", color = Coral)
            }
        }
    }
}

@Composable
private fun HrZoneBars(s: SessionSummary) {
    val zoneColors = mapOf(
        HrZone.ZONE1 to Sky, HrZone.ZONE2 to Emerald, HrZone.ZONE3 to Amber,
        HrZone.ZONE4 to Color(0xFFF97316), HrZone.ZONE5 to Coral,
    )
    val maxMs = s.timeInZonesMs.values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HrZone.entries.forEach { zone ->
            val ms = s.timeInZonesMs[zone] ?: 0L
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(zone.name.removePrefix("ZONE"), modifier = Modifier.width(20.dp), style = MaterialTheme.typography.labelMedium)
                Box(
                    Modifier
                        .weight(1f)
                        .height(16.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction = (ms.toFloat() / maxMs).coerceIn(0f, 1f))
                            .height(16.dp)
                            .background(zoneColors[zone] ?: Emerald, RoundedCornerShape(8.dp)),
                    )
                }
                Text(formatCompact(ms), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AiCoachCard(
    insight: String?,
    aiState: AiUiState,
    onGenerate: () -> Unit,
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("AI Coach", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }

        when (aiState) {
            is AiUiState.Loading -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp).width(20.dp))
                Text("Analyzing your session…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            is AiUiState.Error -> Text(
                aiState.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )

            AiUiState.Idle -> Unit
        }

        if (insight != null) {
            Text(insight, style = MaterialTheme.typography.bodyMedium)
        } else if (aiState !is AiUiState.Loading) {
            Text(
                "Get personalized feedback and recommendations based on this session's heart-rate and effort data.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (aiState !is AiUiState.Loading) {
            Button(onClick = onGenerate, modifier = Modifier.fillMaxWidth()) {
                Text(if (insight == null) "Analyze with AI" else "Regenerate analysis")
            }
        }
    }
}
