package com.calistapp.app.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calistapp.app.ui.common.GlassCard
import com.calistapp.app.ui.common.SegmentedToggle
import com.calistapp.app.ui.common.SessionRow
import com.calistapp.app.ui.common.SwipeToDeleteRow
import com.calistapp.app.ui.common.UndoBar
import com.calistapp.app.ui.common.rememberPendingDeletes
import com.calistapp.app.ui.insights.AllRecordsSheet
import com.calistapp.app.ui.insights.InsightsActions
import com.calistapp.app.ui.insights.InsightsViewModel
import com.calistapp.app.ui.insights.PastRecapsSheet
import com.calistapp.app.ui.insights.RecapPlayer
import com.calistapp.app.ui.insights.insightsItems
import com.calistapp.app.ui.insights.performMove
import com.calistapp.app.ui.insights.shareIssue
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.Chalk

private const val TAB_SESSIONS = 0

@Composable
fun HistoryScreen(
    onOpenInsight: (com.calistapp.core.insights.Question) -> Unit,
    onOpenSession: (String) -> Unit,
    onStartWorkout: () -> Unit,
    onOpenPlanner: () -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenExercise: (String) -> Unit,
    onOpenMedals: (focus: String?) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
    insights: InsightsViewModel = hiltViewModel(),
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val report by insights.report.collectAsStateWithLifecycle()
    val period by insights.period.collectAsStateWithLifecycle()
    val past by insights.past.collectAsStateWithLifecycle()
    val medals by insights.medalReport.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(TAB_SESSIONS) }
    var showPast by remember { mutableStateOf(false) }
    var showRecords by remember { mutableStateOf(false) }
    var recap by remember { mutableStateOf<com.calistapp.core.insights.InsightsReport?>(null) }
    val context = LocalContext.current
    // Swipe a session left to delete it; it's only really deleted once the undo window has passed.
    val pendingDeletes = rememberPendingDeletes { viewModel.deleteSession(it) }

    val actions = InsightsActions(
        onPeriod = insights::setPeriod,
        onPlayRecap = { recap = it },
        onShare = { shareIssue(context, it) },
        onPastRecaps = { insights.loadPast(); showPast = true },
        onOpenQuestion = onOpenInsight,
        onMove = { move -> performMove(move, insights, onOpenPlanner, onOpenSchedule, onOpenExercise) },
        onAllRecords = { showRecords = true },
        onStartWorkout = onStartWorkout,
        onAllMedals = { onOpenMedals(null) },
        onOpenMedal = { key -> onOpenMedals(key) },
    )

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(if (tab == TAB_SESSIONS) 10.dp else 14.dp),
        ) {
            item {
                Text("History", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
            // Two views of the same training: what you did, and what it adds up to.
            item {
                SegmentedToggle(
                    options = listOf("Sessions", "Insights"),
                    selectedIndex = tab,
                    onSelect = { tab = it },
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            if (tab == TAB_SESSIONS) {
                if (sessions.isEmpty()) {
                    item {
                        // An empty state that only says "empty" is a dead end. This one says what to do
                        // and offers to do it.
                        GlassCard {
                            Text("No sessions yet", style = MaterialTheme.typography.titleMedium, color = Chalk)
                            Text(
                                "Every workout you finish lands here, with its heart-rate trace, its " +
                                    "sets, and the working behind its calorie figure.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Ash,
                            )
                            Button(onClick = onStartWorkout, modifier = Modifier.fillMaxWidth()) {
                                Text("Build your first workout")
                            }
                        }
                    }
                } else {
                    items(sessions.filter { it.id !in pendingDeletes.pending }, key = { it.id }) { session ->
                        SwipeToDeleteRow(
                            onDelete = { pendingDeletes.delete(session.id, session.title) },
                            modifier = Modifier.animateItem(),
                        ) {
                            SessionRow(session = session, onClick = { onOpenSession(session.id) })
                        }
                    }
                }
            } else {
                insightsItems(report, period, actions, medals)
            }
        }
        UndoBar(pendingDeletes, Modifier.align(Alignment.BottomCenter).padding(bottom = 112.dp))
    }

    if (showPast) PastRecapsSheet(past, onPlay = { actions.onPlayRecap(it) }, onDismiss = { showPast = false })
    if (showRecords) report?.records?.let { AllRecordsSheet(it, onDismiss = { showRecords = false }) }
    recap?.let { r ->
        RecapPlayer(
            report = r,
            onClose = { recap = null },
            onSeeData = onOpenInsight,
            onMove = actions.onMove,
            onShare = { shareIssue(context, r) },
        )
    }
}
