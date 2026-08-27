package com.calistapp.app.ui.exercises

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.Capsule
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.Coral
import com.calistapp.app.ui.theme.Flame
import com.calistapp.app.ui.theme.FlameSoft
import com.calistapp.app.ui.theme.NumericMedium
import com.calistapp.app.ui.theme.Onyx
import com.calistapp.app.ui.theme.OnyxBorder
import com.calistapp.app.ui.theme.OnyxFillStrong
import com.calistapp.app.ui.theme.OnyxRaised
import com.calistapp.app.ui.theme.TitleSans
import com.calistapp.core.model.Difficulty
import com.calistapp.core.model.Exercise
import com.calistapp.core.model.SavedWorkout
import com.calistapp.core.progress.ExerciseProgress
import com.calistapp.core.model.formatKg
import java.text.SimpleDateFormat
import java.util.Locale

private enum class DetailTab(val label: String) {
    GUIDE("Guide"), MUSCLES("Muscles"), SKILLS("Skills"), PROGRESS("Progress"), DETAILS("Details")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExerciseDetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onStartWorkout: (String) -> Unit,
    onOpenSession: (String) -> Unit,
    viewModel: ExerciseDetailViewModel = hiltViewModel(),
) {
    val exercise by viewModel.exercise.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val trend by viewModel.trend.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val appearsIn by viewModel.appearsIn.collectAsStateWithLifecycle()
    val favourite by viewModel.favourite.collectAsStateWithLifecycle()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()

    val e = exercise
    var tab by rememberSaveable { mutableStateOf(DetailTab.GUIDE) }
    var showShare by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Onyx)) {
        TopBar(
            favourite = favourite,
            isUserAdded = viewModel.isUserAdded,
            onBack = onBack,
            onShare = { if (e != null) showShare = true },
            onToggleFavourite = viewModel::toggleFavourite,
            onEdit = { e?.let { onEdit(it.id) } },
            onDelete = { confirmDelete = true },
        )

        if (e == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Flame)
            }
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize()) {
            item {
                ExerciseMediaCarousel(
                    exercise = e,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1.15f),
                )
            }
            item { Header(e, onStartWorkout) }
            stickyHeader { TabStrip(tab, onSelect = { tab = it }) }
            item {
                when (tab) {
                    DetailTab.GUIDE -> GuideTab(e, aiState, viewModel::enrich)
                    DetailTab.MUSCLES -> MusclesTab(e)
                    DetailTab.SKILLS -> SkillsTab(e)
                    DetailTab.PROGRESS -> ProgressTab(e, progress, trend, history, onOpenSession)
                    DetailTab.DETAILS -> DetailsTab(e, appearsIn, onStartWorkout)
                }
            }
            item { Spacer(Modifier.height(24.dp).navigationBarsPadding()) }
        }
    }

    if (showShare && e != null) {
        ShareSheet(
            exercise = e,
            bestReps = progress?.mostReps?.reps,
            bestWeightKg = progress?.heaviest?.addedWeightKg,
            onDismiss = { showShare = false },
        )
    }

    if (confirmDelete) {
        val userAdded = viewModel.isUserAdded
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(if (userAdded) "Delete exercise?" else "Hide exercise?") },
            text = {
                Text(
                    if (userAdded) {
                        "This removes your custom exercise for good."
                    } else {
                        "This hides it from the library. Restore it any time from Profile › Hidden exercises."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.deleteOrHide(onBack) }) {
                    Text(if (userAdded) "Delete" else "Hide", color = Coral)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun TopBar(
    favourite: Boolean,
    isUserAdded: Boolean,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onToggleFavourite: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        com.calistapp.app.ui.common.BackButton(onBack)
        Spacer(Modifier.weight(1f))
        RoundIcon(Icons.Filled.Edit, "Edit", onEdit)
        Spacer(Modifier.size(6.dp))
        RoundIcon(Icons.Filled.Share, "Share", onShare)
        Spacer(Modifier.size(6.dp))
        RoundIcon(
            if (favourite) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
            "Bookmark",
            onToggleFavourite,
            tint = if (favourite) Flame else Chalk,
        )
        Spacer(Modifier.size(6.dp))
        Box {
            RoundIcon(Icons.Filled.MoreVert, "More", { menuOpen = true })
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                containerColor = OnyxRaised,
            ) {
                DropdownMenuItem(
                    text = { Text(if (isUserAdded) "Delete exercise" else "Hide exercise", color = Coral) },
                    leadingIcon = { Icon(Icons.Filled.DeleteOutline, null, tint = Coral) },
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        }
    }
}

@Composable
private fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, cd: String, onClick: () -> Unit, tint: Color = Chalk) {
    Box(
        Modifier.size(38.dp).clip(Capsule).background(OnyxFillStrong).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, cd, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun Header(e: Exercise, onStartWorkout: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(e.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Chalk)
        Text(e.bodyPart.displayName, style = MaterialTheme.typography.titleMedium, color = Ash)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(e.difficulty.easyLabel(), style = MaterialTheme.typography.labelLarge, color = Flame, fontWeight = FontWeight.Bold)
            repeat(3) { i ->
                Box(
                    Modifier.size(width = 14.dp, height = 5.dp).clip(Capsule)
                        .background(if (i <= e.difficulty.ordinal) Flame else Chalk.copy(alpha = 0.16f)),
                )
            }
        }
        Box(
            Modifier.fillMaxWidth().clip(Capsule).background(Flame).clickable { onStartWorkout(e.id) }.padding(vertical = 13.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.PlayArrow, null, tint = Onyx, modifier = Modifier.size(20.dp))
                Text("Start workout", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Onyx)
            }
        }
    }
}

@Composable
private fun TabStrip(selected: DetailTab, onSelect: (DetailTab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Onyx).horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp).padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        DetailTab.entries.forEach { t ->
            val on = t == selected
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onSelect(t) }.padding(vertical = 10.dp)) {
                Text(t.label, style = MaterialTheme.typography.titleSmall, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, color = if (on) Flame else Ash)
                Spacer(Modifier.height(6.dp))
                Box(Modifier.size(width = 22.dp, height = 2.dp).background(if (on) Flame else Color.Transparent))
            }
        }
    }
}

// ---- Tabs -------------------------------------------------------------------------------------

@Composable
private fun GuideTab(e: Exercise, aiState: ExerciseAiState, onEnrich: () -> Unit) {
    TabColumn {
        if (e.overview.isNotBlank()) {
            SectionTitle("Overview")
            Text(e.overview, style = MaterialTheme.typography.bodyMedium, color = Chalk.copy(alpha = 0.9f))
        }
        if (e.instructions.isNotEmpty()) {
            SectionTitle("Steps")
            e.instructions.forEachIndexed { i, step -> NumberedRow(i + 1, step) }
        }
        if (e.tips.isNotEmpty()) {
            SectionTitle("Tips")
            e.tips.forEach { BulletRow(it, Flame) }
        }
        if (e.commonMistakes.isNotEmpty()) {
            SectionTitle("Common mistakes")
            e.commonMistakes.forEach { BulletRow(it, Ash) }
        }
        if (e.problematicAreas.isNotEmpty()) {
            SectionTitle("Goes easy on")
            Text(
                "Can stress ${e.problematicAreas.joinToString(", ")}. Stop if you feel joint pain.",
                style = MaterialTheme.typography.bodyMedium, color = Ash,
            )
        }
        if (e.commonMistakes.isEmpty() && e.tips.isEmpty()) {
            AiCard(aiState, onEnrich)
        }
    }
}

@Composable
private fun MusclesTab(e: Exercise) {
    TabColumn {
        MuscleDiagram(e.primaryMuscles, e.secondaryMuscles)
        if (e.primaryMuscles.isNotEmpty()) {
            e.primaryMuscles.forEach { MuscleRow(it, primary = true) }
        }
        if (e.secondaryMuscles.isNotEmpty()) {
            e.secondaryMuscles.forEach { MuscleRow(it, primary = false) }
        }
        if (e.primaryMuscles.isEmpty() && e.secondaryMuscles.isEmpty()) {
            EmptyNote("No muscle data for this movement.")
        }
    }
}

@Composable
private fun SkillsTab(e: Exercise) {
    TabColumn {
        val skills = e.skills
        if (skills == null) {
            EmptyNote("Skill profile not rated yet.")
        } else {
            skills.axes.forEach { (label, value) -> SkillBar(label, value) }
        }
        if (e.efficiency > 0) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Efficiency", style = MaterialTheme.typography.labelLarge, color = Ash)
                Text("★ ${e.efficiency}/5", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Flame)
            }
            Text("Strength built vs. energy spent — Calistapp's own rating.", style = MaterialTheme.typography.labelSmall, color = Ash.copy(alpha = 0.8f))
        }
        if (skills != null) {
            Text("Skill profile is an estimate, not a measurement.", style = MaterialTheme.typography.labelSmall, color = Ash.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun ProgressTab(
    e: Exercise,
    progress: ExerciseProgress?,
    trend: List<ExerciseTrendPoint>,
    history: List<ExerciseHistoryEntry>,
    onOpenSession: (String) -> Unit,
) {
    TabColumn {
        if (progress == null) {
            EmptyNote("No sessions with this movement yet. Do it once and your records show up here.")
            return@TabColumn
        }
        val weighted = progress.heaviest != null

        SectionTitle("Records")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (weighted) {
                RecordTile("Best weight", "+${formatKg(progress.heaviest!!.addedWeightKg)}kg", Modifier.weight(1f))
                RecordTile("Best reps", "${progress.mostReps?.reps ?: 0}", Modifier.weight(1f))
                val vol = progress.maxVolume?.let { it.addedWeightKg * it.reps } ?: 0.0
                RecordTile("Best volume", "${formatKg(vol)}kg", Modifier.weight(1f))
            } else {
                RecordTile("Best set", "${progress.mostReps?.reps ?: 0}", Modifier.weight(1f))
                RecordTile("Total reps", "${progress.totalReps}", Modifier.weight(1f))
                RecordTile("Sessions", "${progress.sessionCount}", Modifier.weight(1f))
            }
        }

        if (trend.size >= 2) {
            SectionTitle(if (weighted) "Weight progress" else "Reps progress")
            TrendChart(trend = trend, weighted = weighted)
        }

        if (history.isNotEmpty()) {
            SectionTitle("History")
            history.take(20).forEach { entry -> HistoryRow(entry, onOpenSession) }
        }
    }
}

@Composable
private fun DetailsTab(e: Exercise, appearsIn: List<SavedWorkout>, onStartWorkout: (String) -> Unit) {
    TabColumn {
        SectionTitle("Details")
        InfoRow("Equipment", e.equipment.firstOrNull() ?: "Body only")
        e.force?.let { InfoRow("Force", it.replaceFirstChar(Char::uppercase)) }
        e.mechanic?.let { InfoRow("Mechanic", it.replaceFirstChar(Char::uppercase)) }
        InfoRow("Difficulty", e.difficulty.easyLabel())
        if (e.tags.isNotEmpty()) InfoRow("Tags", e.tags.joinToString(", "))

        SectionTitle("Appears in")
        if (appearsIn.isEmpty()) {
            EmptyNote("Not in any of your saved workouts yet.")
        } else {
            appearsIn.forEach { w ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(OnyxFillStrong).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(w.name, style = MaterialTheme.typography.bodyLarge, color = Chalk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(w.summaryLabel, style = MaterialTheme.typography.labelSmall, color = Ash)
                    }
                }
            }
        }
    }
}

// ---- Small pieces -----------------------------------------------------------------------------

@Composable
private fun TabColumn(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = TitleSans.copy(fontSize = 18.sp), color = Chalk, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun NumberedRow(n: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("$n", color = Onyx, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, modifier = Modifier.size(20.dp).clip(Capsule).background(Flame).padding(top = 2.dp), textAlign = TextAlign.Center)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Chalk.copy(alpha = 0.9f))
    }
}

@Composable
private fun BulletRow(text: String, dot: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("•", color = dot, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Chalk.copy(alpha = 0.9f))
    }
}

@Composable
private fun MuscleRow(name: String, primary: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(8.dp).clip(Capsule).background(if (primary) Flame else Flame.copy(alpha = 0.45f)))
        Text(name, style = MaterialTheme.typography.bodyMedium, color = Chalk, modifier = Modifier.weight(1f))
        Text(if (primary) "primary" else "secondary", style = MaterialTheme.typography.labelSmall, color = Ash)
    }
}

@Composable
private fun SkillBar(label: String, value: Int) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = Chalk, modifier = Modifier.weight(1f))
            Text("$value%", style = MaterialTheme.typography.labelLarge, color = Ash)
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(Capsule).background(OnyxFillStrong)) {
            Box(Modifier.fillMaxWidth(value.coerceIn(0, 100) / 100f).height(8.dp).clip(Capsule).background(Flame))
        }
    }
}

@Composable
private fun RecordTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(OnyxFillStrong).padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = NumericMedium, color = Flame, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Ash, textAlign = TextAlign.Center)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Ash, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Chalk, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun EmptyNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Ash, modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
private fun HistoryRow(entry: ExerciseHistoryEntry, onOpenSession: (String) -> Unit) {
    val reps = entry.sets.joinToString(", ") { "${it.reps}" }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(OnyxFillStrong)
            .clickable { onOpenSession(entry.sessionId) }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(dateFmt.format(entry.atMs), style = MaterialTheme.typography.bodyMedium, color = Chalk)
            Text("${entry.sets.size} ${if (entry.sets.size == 1) "set" else "sets"} · $reps", style = MaterialTheme.typography.labelSmall, color = Ash)
        }
    }
}

@Composable
private fun AiCard(aiState: ExerciseAiState, onEnrich: () -> Unit) {
    com.calistapp.app.ui.common.AiActionCard(
        title = "AI coaching notes",
        loading = aiState is ExerciseAiState.Loading,
        error = (aiState as? ExerciseAiState.Error)?.message,
        actionLabel = "Generate",
        onAction = onEnrich,
    ) {
        Text(
            "Generate an overview, mistakes and tips for this movement.",
            color = Ash,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun TrendChart(trend: List<ExerciseTrendPoint>, weighted: Boolean) {
    var window by rememberSaveable { mutableStateOf(TrendWindow.ALL) }
    val now = System.currentTimeMillis()
    val cutoff = when (window) {
        TrendWindow.D30 -> now - 30L * 86_400_000
        TrendWindow.D90 -> now - 90L * 86_400_000
        TrendWindow.ALL -> Long.MIN_VALUE
    }
    val points = trend.filter { it.atMs >= cutoff }
    val values = points.map { if (weighted) it.bestWeightKg else it.bestReps.toDouble() }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TrendWindow.entries.forEach { w ->
                val on = w == window
                Text(
                    w.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (on) Onyx else Ash,
                    modifier = Modifier.clip(Capsule).background(if (on) Flame else OnyxFillStrong).clickable { window = w }.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        if (values.size < 2) {
            EmptyNote("Not enough sessions in this window yet.")
        } else {
            val lo = (values.min()).coerceAtMost(values.max())
            val hi = values.max()
            val range = (hi - lo).coerceAtLeast(1.0)
            Canvas(Modifier.fillMaxWidth().height(120.dp)) {
                val stepX = if (values.size <= 1) 0f else size.width / (values.size - 1)
                var prev: Offset? = null
                values.forEachIndexed { i, v ->
                    val x = i * stepX
                    val y = size.height - ((v - lo) / range).toFloat() * size.height
                    val p = Offset(x, y)
                    prev?.let { drawLine(Flame, it, p, strokeWidth = 4f, cap = StrokeCap.Round) }
                    drawCircle(Flame, radius = 4f, center = p)
                    prev = p
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (weighted) "${formatKg(lo)}kg" else "${lo.toInt()}", style = MaterialTheme.typography.labelSmall, color = Ash)
                Text(if (weighted) "${formatKg(hi)}kg" else "${hi.toInt()}", style = MaterialTheme.typography.labelSmall, color = Ash)
            }
        }
    }
}

private enum class TrendWindow(val label: String) { D30("30d"), D90("90d"), ALL("All") }

private val dateFmt = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

private fun Difficulty.easyLabel(): String = when (this) {
    Difficulty.BEGINNER -> "Easy"
    Difficulty.INTERMEDIATE -> "Medium"
    Difficulty.ADVANCED -> "Hard"
}
