package com.calistapp.app.ui.planner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calistapp.app.ui.common.AmbientOverride
import com.calistapp.app.ui.common.BackButton
import com.calistapp.app.ui.common.GlassCard
import com.calistapp.app.ui.common.GlowBox
import com.calistapp.app.ui.exercises.AmbientVideoBackground
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.Capsule
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.Coral
import com.calistapp.app.ui.theme.Display
import com.calistapp.app.ui.theme.Eyebrow
import com.calistapp.app.ui.theme.Flame
import com.calistapp.app.ui.theme.FlameGlow
import com.calistapp.app.ui.theme.FlameHot
import com.calistapp.app.ui.theme.Onyx
import com.calistapp.app.ui.theme.OnyxBorder
import com.calistapp.app.ui.theme.OnyxFillStrong
import com.calistapp.app.ui.theme.PageDim
import com.calistapp.app.ui.theme.PageInk
import com.calistapp.app.ui.theme.PageWarm
import com.calistapp.core.model.PlannedExercise
import com.calistapp.core.model.SessionOverview
import com.calistapp.core.model.WorkoutPlan

// The prototype's type voices, kept local to this screen so the tuning lives next to what it dresses.
private val CoverTitle = TextStyle(
    fontFamily = Display, fontWeight = FontWeight.SemiBold,
    fontSize = 38.sp, lineHeight = 40.sp, letterSpacing = (-1.1).sp,
    shadow = Shadow(Color.Black.copy(alpha = 0.5f), Offset(0f, 2f), 24f),
)
private val RowName = TextStyle(
    fontFamily = Display, fontWeight = FontWeight.SemiBold,
    fontSize = 17.sp, lineHeight = 20.sp, letterSpacing = (-0.2).sp,
)
private val HistoryDate = TextStyle(
    fontFamily = Display, fontWeight = FontWeight.SemiBold,
    fontSize = 16.sp, lineHeight = 20.sp, letterSpacing = (-0.2).sp,
)
private val KcalValue = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 16.sp)

/**
 * A saved workout as its own screen: a warm cover with the name and shape of the session, one glowing
 * Start, an Edit, and the exercises and history it's produced. Sits on the app's ambient wash — pushed
 * a touch warmer here so opening a workout feels like stepping up to it.
 */
@Composable
fun SavedWorkoutDetailScreen(
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onOpenSession: (String) -> Unit,
    onOpenExercise: (String) -> Unit,
    onDeleted: () -> Unit,
    onBack: () -> Unit,
    viewModel: SavedWorkoutDetailViewModel = hiltViewModel(),
) {
    AmbientOverride(FlameHot)

    val workout by viewModel.workout.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val videoUrls by viewModel.videoUrls.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete workout?") },
            text = { Text("This removes the saved workout. Sessions you've already run from it stay in your history.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete(); onDeleted() }) {
                    Text("Delete", color = Coral)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }

    val w = workout
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(PageWarm, PageDim, PageInk)))) {
        // A heavily blurred, dimmed, grained loop of this workout's own demo footage behind the
        // content, so the screen feels alive while the type leads. Falls back to the warm ambient
        // wash when none of the movements has a video.
        if (videoUrls.isNotEmpty()) {
            AmbientVideoBackground(videoUrls, Modifier.matchParentSize())
        }
        LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 12.dp),
    ) {
        item { Cover(w?.name ?: "Workout", w?.plan, onBack, onDelete = { confirmDelete = true }) }

        if (w == null) {
            item {
                GlassCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text("This workout is no longer available.", color = Ash)
                }
            }
            return@LazyColumn
        }

        item {
            Actions(
                onStart = { viewModel.loadIntoDraft(); viewModel.markUsed(); onStart() },
                onEdit = { viewModel.loadIntoDraft(); onEdit() },
            )
        }

        item {
            Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp).height(1.dp).background(OnyxBorder))
        }

        itemsIndexed(w.plan.exercises, key = { _, it -> it.slotId }) { index, slot ->
            ExerciseLine(
                slot = slot,
                showDivider = index < w.plan.exercises.lastIndex,
                onClick = { onOpenExercise(slot.exerciseId) },
            )
        }

        item {
            Text(
                "History",
                style = MaterialTheme.typography.bodySmall,
                color = Ash,
                modifier = Modifier.padding(horizontal = 24.dp).padding(top = 34.dp, bottom = 4.dp),
            )
        }
        if (history.isEmpty()) {
            item {
                Text(
                    "Not run yet — start it, and every session you run lands here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ash,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                )
            }
        } else {
            itemsIndexed(history, key = { _, it -> it.id }) { index, session ->
                WorkoutHistoryRow(
                    session = session,
                    showDivider = index < history.lastIndex,
                    onClick = { onOpenSession(session.id) },
                )
            }
        }

        item { Spacer(Modifier.height(8.dp).navigationBarsPadding()) }
    }
    }
}

@Composable
private fun Cover(name: String, plan: WorkoutPlan?, onBack: () -> Unit, onDelete: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackButton(onBack)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(38.dp).clip(Capsule).background(OnyxFillStrong).clickable(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.DeleteOutline, "Delete workout", tint = Coral, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(34.dp))
        Text("ROUTINE", style = Eyebrow, color = FlameGlow)
        Spacer(Modifier.height(12.dp))
        Text(name, style = CoverTitle, color = Chalk)
        if (plan != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                metaLine(plan),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = Ash,
            )
        }
    }
}

/** "5 exercises · 2 rounds · ~38 min", with orange separators. */
@Composable
private fun metaLine(plan: WorkoutPlan) = buildAnnotatedString {
    fun dot() = withStyle(SpanStyle(color = FlameGlow)) { append("   ·   ") }
    append("${plan.exercises.size} ${if (plan.exercises.size == 1) "exercise" else "exercises"}")
    if (plan.isCircuit) {
        dot()
        append("${plan.rounds} ${if (plan.rounds == 1) "round" else "rounds"}")
    }
    dot()
    append("~${plan.estimatedMinutes} min")
}

@Composable
private fun Actions(onStart: () -> Unit, onEdit: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // The prototype's Start is a flat hot-orange pill (the gradient is reserved for the Save/Add
        // CTAs on Build and Exercise Detail); the glow stands in for its big soft drop-shadow.
        GlowBox(color = FlameHot, shape = Capsule, glowRadius = 22.dp, glowAlpha = 0.55f, modifier = Modifier.fillMaxWidth()) {
            Box(
                Modifier.fillMaxWidth().height(60.dp).clip(Capsule)
                    .background(FlameHot)
                    .clickable(onClick = onStart),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.PlayArrow, null, tint = Onyx, modifier = Modifier.size(22.dp))
                    Text("Start workout", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Onyx)
                }
            }
        }
        Text(
            "Edit routine",
            style = MaterialTheme.typography.titleSmall,
            color = Ash,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clip(Capsule).clickable(onClick = onEdit).padding(vertical = 6.dp),
        )
    }
}

@Composable
private fun ExerciseLine(
    slot: PlannedExercise,
    showDivider: Boolean,
    onClick: () -> Unit,
) {
    Column(Modifier.clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 22.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(slot.displayName, style = RowName, color = Chalk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(slot.bodyPart.displayName, style = MaterialTheme.typography.bodySmall, color = Ash, modifier = Modifier.padding(top = 6.dp))
            }
            Text(slot.targetLabel, style = MaterialTheme.typography.bodyMedium, color = Chalk.copy(alpha = 0.68f), modifier = Modifier.padding(top = 2.dp))
        }
        if (showDivider) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 28.dp).height(1.dp).background(OnyxBorder))
        }
    }
}

@Composable
private fun WorkoutHistoryRow(session: SessionOverview, showDivider: Boolean, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 18.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Text(historyDate.format(session.startMs), style = HistoryDate, color = Chalk)
                Text(
                    if (session.totalReps > 0) "${session.totalReps} reps · ${session.avgHr} bpm" else "avg ${session.avgHr} bpm",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ash,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Text("${session.totalKcal} kcal", style = KcalValue, color = FlameGlow)
        }
        if (showDivider) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 28.dp).height(1.dp).background(OnyxBorder))
        }
    }
}

private val historyDate = java.text.SimpleDateFormat("EEE d MMM", java.util.Locale.getDefault())
