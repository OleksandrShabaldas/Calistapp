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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calistapp.app.ui.common.GlassCard
import com.calistapp.app.ui.common.SegmentedToggle
import com.calistapp.app.ui.common.SessionRow
import com.calistapp.app.ui.theme.Cream
import com.calistapp.app.ui.theme.CreamMuted

private const val TAB_SESSIONS = 0

@Composable
fun HistoryScreen(
    onOpenSession: (String) -> Unit,
    onStartWorkout: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val bodyMass by viewModel.bodyMass.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(TAB_SESSIONS) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                "History",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        // Two views of the same training: what you did, and where it's going.
        item {
            SegmentedToggle(
                options = listOf("Sessions", "Progress"),
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
                        Text(
                            "No sessions yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = Cream,
                        )
                        Text(
                            "Every workout you finish lands here, with its heart-rate trace, its " +
                                "sets, and the working behind its calorie figure.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CreamMuted,
                        )
                        Button(onClick = onStartWorkout, modifier = Modifier.fillMaxWidth()) {
                            Text("Build your first workout")
                        }
                    }
                }
            } else {
                items(sessions, key = { it.id }) { session ->
                    SessionRow(session = session, onClick = { onOpenSession(session.id) })
                }
            }
        } else {
            progressTab(progress, bodyMass)
        }
    }
}

