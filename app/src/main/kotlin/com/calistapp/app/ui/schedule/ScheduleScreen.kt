package com.calistapp.app.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calistapp.app.data.session.ScheduledItem
import com.calistapp.app.ui.common.BackButton
import com.calistapp.app.ui.common.SectionHeading
import com.calistapp.app.ui.dashboard.DashCard
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.FlameHot
import com.calistapp.app.ui.theme.OnyxFill
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val OnOrange = androidx.compose.ui.graphics.Color(0xFF140A03)
private val DAY_LETTERS = listOf("M", "T", "W", "T", "F", "S", "S")

@Composable
fun ScheduleScreen(
    onBack: () -> Unit,
    onOpenPlanner: () -> Unit,
    viewModel: ScheduleViewModel = hiltViewModel(),
) {
    val saved by viewModel.savedWorkouts.collectAsStateWithLifecycle()
    val weekPlan by viewModel.weekPlan.collectAsStateWithLifecycle()
    val recurring by viewModel.recurringByWorkout.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BackButton(onBack)
            Spacer(Modifier.width(12.dp))
            Text("Plan your week", style = MaterialTheme.typography.headlineMedium, color = Chalk)
        }

        if (saved.isEmpty()) {
            DashCard {
                Text("No saved workouts yet", style = MaterialTheme.typography.titleLarge, color = Chalk)
                Text(
                    "Build a workout and save it, then come back to put it on your week.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ash,
                )
                TextButton(onClick = onOpenPlanner, contentPadding = ButtonDefaults.TextButtonContentPadding) {
                    Text("Build a workout", color = FlameHot)
                }
            }
        } else {
            SectionHeading("This week")
            DashCard {
                viewModel.weekDates.forEach { date ->
                    ThisWeekDayRow(
                        date = date,
                        items = weekPlan[date.dayOfWeek].orEmpty(),
                        onSkip = { item -> viewModel.skipThisWeek(date.dayOfWeek, item.workout.id) },
                        onMove = { item, to -> viewModel.moveThisWeek(date.dayOfWeek, to, item.workout.id) },
                    )
                }
            }

            SectionHeading("Repeat weekly")
            saved.forEach { workout ->
                DashCard {
                    Text(workout.name, style = MaterialTheme.typography.titleMedium, color = Chalk)
                    Text(workout.summaryLabel, style = MaterialTheme.typography.bodySmall, color = Ash)
                    Spacer(Modifier.size(12.dp))
                    WeekdayToggles(
                        selected = recurring[workout.id].orEmpty(),
                        onToggle = { day -> viewModel.toggleRecurring(workout.id, day) },
                    )
                }
            }

            Spacer(Modifier.size(4.dp))
        }
    }
}

@Composable
private fun ThisWeekDayRow(
    date: LocalDate,
    items: List<ScheduledItem>,
    onSkip: (ScheduledItem) -> Unit,
    onMove: (ScheduledItem, DayOfWeek) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${date.dayOfMonth}",
            style = MaterialTheme.typography.titleSmall,
            color = if (date == LocalDate.now()) FlameHot else Chalk,
            modifier = Modifier.width(64.dp),
        )
        if (items.isEmpty()) {
            Text("Rest", style = MaterialTheme.typography.bodyMedium, color = Ash)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items.forEach { item -> PlannedChip(item, date.dayOfWeek, onSkip, onMove) }
            }
        }
    }
}

@Composable
private fun PlannedChip(
    item: ScheduledItem,
    day: DayOfWeek,
    onSkip: (ScheduledItem) -> Unit,
    onMove: (ScheduledItem, DayOfWeek) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .clip(CircleShape)
                .background(FlameHot.copy(alpha = 0.14f))
                .border(1.dp, FlameHot.copy(alpha = 0.35f), CircleShape)
                .clickable { open = true }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(item.workout.name, style = MaterialTheme.typography.labelLarge, color = FlameHot, fontWeight = FontWeight.SemiBold)
            item.movedFrom?.let {
                Text("  moved", style = MaterialTheme.typography.labelSmall, color = Ash)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Skip this week") }, onClick = { open = false; onSkip(item) })
            DayOfWeek.entries.filter { it != day }.forEach { target ->
                DropdownMenuItem(
                    text = { Text("Move to ${target.getDisplayName(TextStyle.FULL, Locale.getDefault())}") },
                    onClick = { open = false; onMove(item, target) },
                )
            }
        }
    }
}

@Composable
private fun WeekdayToggles(selected: Set<DayOfWeek>, onToggle: (DayOfWeek) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (0..6).forEach { i ->
            val day = DayOfWeek.of(i + 1)
            val isOn = day in selected
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (isOn) FlameHot else OnyxFill)
                    .clickable { onToggle(day) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    DAY_LETTERS[i],
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isOn) OnOrange else Ash,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
