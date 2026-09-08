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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calistapp.app.ui.common.AiActionCard
import com.calistapp.app.ui.common.BackButton
import com.calistapp.app.ui.common.GlowBox
import com.calistapp.app.ui.common.glow
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.AshFaint
import com.calistapp.app.ui.theme.Capsule
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.Coral
import com.calistapp.app.ui.theme.Flame
import com.calistapp.app.ui.theme.FlameGlow
import com.calistapp.app.ui.theme.FlameHot
import com.calistapp.app.ui.theme.Mint
import com.calistapp.app.ui.theme.NumericMedium
import com.calistapp.app.ui.theme.Onyx
import com.calistapp.app.ui.theme.OnyxBorder
import com.calistapp.app.ui.theme.OnyxFillStrong
import com.calistapp.app.ui.theme.OnyxRaised
import com.calistapp.app.ui.theme.TitleSans
import com.calistapp.core.model.Difficulty
import com.calistapp.core.model.Exercise
import com.calistapp.core.model.Faq
import com.calistapp.core.model.SavedWorkout
import com.calistapp.core.model.formatKg
import com.calistapp.core.progress.ExerciseProgress
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
    onOpenWorkout: (String) -> Unit,
    viewModel: ExerciseDetailViewModel = hiltViewModel(),
) {
    val exercise by viewModel.exercise.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val trend by viewModel.trend.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val appearsIn by viewModel.appearsIn.collectAsStateWithLifecycle()
    val favourite by viewModel.favourite.collectAsStateWithLifecycle()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()
    val faqs by viewModel.faqs.collectAsStateWithLifecycle()
    val faqAsk by viewModel.faqAsk.collectAsStateWithLifecycle()

    val e = exercise
    var tab by rememberSaveable { mutableStateOf(DetailTab.GUIDE) }
    var showShare by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Onyx)) {
        if (e == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Flame)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    // Chrome rides on the hero and scrolls away with it, so the sticky tab row owns the
                    // top edge once you scroll — otherwise the two pin to the same band and overlap.
                    Box(Modifier.fillMaxWidth()) {
                        ExerciseMediaCarousel(
                            exercise = e,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1.12f),
                        )
                        TopActions(
                            favourite = favourite,
                            isUserAdded = viewModel.isUserAdded,
                            onBack = onBack,
                            onShare = { showShare = true },
                            onToggleFavourite = viewModel::toggleFavourite,
                            onEdit = { onEdit(e.id) },
                            onDelete = { confirmDelete = true },
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                    }
                }
                item { HeaderMeta(e) }
                stickyHeader { TabPills(tab, onSelect = { tab = it }) }
                item {
                    when (tab) {
                        DetailTab.GUIDE -> GuideTab(e, faqs, faqAsk, aiState, viewModel::enrich, viewModel::askFaq, viewModel::clearFaqError)
                        DetailTab.MUSCLES -> MusclesTab(e)
                        DetailTab.SKILLS -> SkillsTab(e)
                        DetailTab.PROGRESS -> ProgressTab(progress, trend, history, onOpenSession)
                        DetailTab.DETAILS -> DetailsTab(e, appearsIn, onOpenWorkout)
                    }
                }
                item { Spacer(Modifier.height(112.dp).navigationBarsPadding()) }
            }

            BottomCta(
                addToWorkout = viewModel.openedFromPicker,
                onStart = { onStartWorkout(e.id) },
                onAdd = { viewModel.addToDraft(); onBack() },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
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

    if (confirmDelete && e != null) {
        val userAdded = viewModel.isUserAdded
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = OnyxRaised,
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

// ---- Chrome -----------------------------------------------------------------------------------

@Composable
private fun TopActions(
    favourite: Boolean,
    isUserAdded: Boolean,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onToggleFavourite: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackButton(onBack)
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
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = OnyxRaised) {
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
private fun HeaderMeta(e: Exercise) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "${e.bodyPart.displayName} · ${e.difficulty.easyLabel()}".uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = FlameGlow,
        )
        Text(e.name, style = MaterialTheme.typography.headlineLarge, color = Chalk)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { i ->
                Box(
                    Modifier.size(width = 16.dp, height = 5.dp).clip(Capsule)
                        .background(if (i <= e.difficulty.ordinal) Flame else Chalk.copy(alpha = 0.16f)),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabPills(selected: DetailTab, onSelect: (DetailTab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Onyx).horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DetailTab.entries.forEach { t ->
            val on = t == selected
            if (on) {
                GlowBox(color = FlameHot, shape = Capsule, glowRadius = 10.dp, glowAlpha = 0.4f) {
                    Text(
                        t.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = Onyx,
                        modifier = Modifier.clip(Capsule)
                            .background(Brush.horizontalGradient(listOf(FlameHot, FlameGlow)))
                            .clickable { onSelect(t) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            } else {
                Text(
                    t.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = Ash,
                    modifier = Modifier.clip(Capsule)
                        .border(1.dp, OnyxBorder, Capsule)
                        .clickable { onSelect(t) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun BottomCta(addToWorkout: Boolean, onStart: () -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Onyx)))
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .navigationBarsPadding(),
    ) {
        GlowBox(color = FlameHot, shape = Capsule, glowRadius = 18.dp, glowAlpha = 0.5f, modifier = Modifier.fillMaxWidth()) {
            Box(
                Modifier.fillMaxWidth().height(56.dp).clip(Capsule)
                    .background(Brush.horizontalGradient(listOf(FlameHot, FlameGlow)))
                    .clickable { if (addToWorkout) onAdd() else onStart() },
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.PlayArrow, null, tint = Onyx, modifier = Modifier.size(20.dp))
                    Text(
                        if (addToWorkout) "Add to workout" else "Start workout",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Onyx,
                    )
                }
            }
        }
    }
}

// ---- Tabs -------------------------------------------------------------------------------------

@Composable
private fun GuideTab(
    e: Exercise,
    faqs: List<Faq>,
    faqAsk: FaqAskState,
    aiState: ExerciseAiState,
    onEnrich: () -> Unit,
    onAsk: (String) -> Unit,
    onClearError: () -> Unit,
) {
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
            NoteCard("Tips", e.tips, Mint)
        }
        if (e.commonMistakes.isNotEmpty()) {
            NoteCard("Common mistakes", e.commonMistakes, Coral)
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

        FaqBlock(faqs, faqAsk, onAsk, onClearError)
    }
}

@Composable
private fun MusclesTab(e: Exercise) {
    TabColumn {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(OnyxFillStrong)
                .border(1.dp, OnyxBorder, RoundedCornerShape(20.dp)).padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MuscleDiagram(e.primaryMuscles, e.secondaryMuscles)
        }
        if (e.primaryMuscles.isNotEmpty()) e.primaryMuscles.forEach { MuscleRow(it, primary = true) }
        if (e.secondaryMuscles.isNotEmpty()) e.secondaryMuscles.forEach { MuscleRow(it, primary = false) }
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
            SkillProfileCard(skills)
        }
        if (e.efficiency > 0) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
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

        BestSetBanner(progress, weighted)

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
            HistoryTimeline(history, onOpenSession)
        }
    }
}

@Composable
private fun DetailsTab(e: Exercise, appearsIn: List<SavedWorkout>, onOpenWorkout: (String) -> Unit) {
    TabColumn {
        SectionTitle("Details")
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(OnyxFillStrong)
                .border(1.dp, OnyxBorder, RoundedCornerShape(18.dp)),
        ) {
            SpecRow("Equipment", e.equipment.firstOrNull() ?: "Body only", divider = true)
            e.force?.let { SpecRow("Force", it.replaceFirstChar(Char::uppercase), divider = true) }
            e.mechanic?.let { SpecRow("Mechanic", it.replaceFirstChar(Char::uppercase), divider = true) }
            SpecRow("Difficulty", e.difficulty.easyLabel(), divider = e.tags.isNotEmpty())
            if (e.tags.isNotEmpty()) SpecRow("Tags", e.tags.filterNot { it == "authored" || it == "ai-enriched" || it == "user-edited" }.joinToString(", "), divider = false)
        }

        if (appearsIn.isNotEmpty()) {
            SectionTitle("Appears in")
            appearsIn.forEach { w -> AppearsInRow(w, onOpenWorkout) }
        }
    }
}

// ---- FAQ --------------------------------------------------------------------------------------

@Composable
private fun FaqBlock(faqs: List<Faq>, ask: FaqAskState, onAsk: (String) -> Unit, onClearError: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(-1) }
    SectionTitle("FAQ")
    if (faqs.isEmpty()) {
        Text(
            "No questions here yet — ask one below and it'll be answered and saved for this exercise.",
            style = MaterialTheme.typography.bodyMedium,
            color = Ash,
        )
    }
    faqs.forEachIndexed { i, f ->
        FaqRow(f, open == i) { open = if (open == i) -1 else i }
    }
    AskAiBox(ask, onAsk, onClearError)
}

@Composable
private fun FaqRow(faq: Faq, open: Boolean, onToggle: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(if (open) 10.dp else 0.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                faq.question,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Chalk,
                modifier = Modifier.weight(1f),
            )
            if (faq.generated) {
                Text(
                    "AI",
                    style = MaterialTheme.typography.labelSmall,
                    color = FlameGlow,
                    modifier = Modifier.padding(horizontal = 8.dp).clip(Capsule)
                        .background(Flame.copy(alpha = 0.12f)).padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
            Text(if (open) "−" else "+", style = MaterialTheme.typography.titleMedium, color = if (open) FlameGlow else Ash)
        }
        if (open) {
            Text(faq.answer, style = MaterialTheme.typography.bodyMedium, color = Ash)
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(OnyxBorder))
}

@Composable
private fun AskAiBox(ask: FaqAskState, onAsk: (String) -> Unit, onClearError: () -> Unit) {
    var draft by rememberSaveable { mutableStateOf("") }
    val loading = ask is FaqAskState.Loading

    Column(Modifier.fillMaxWidth().padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it; if (ask is FaqAskState.Error) onClearError() },
            modifier = Modifier.fillMaxWidth().imePadding(),
            placeholder = { Text("Ask AI about this exercise…") },
            singleLine = true,
            enabled = !loading,
            trailingIcon = {
                if (loading) {
                    CircularProgressIndicator(color = Flame, strokeWidth = 2.dp, modifier = Modifier.size(20.dp).padding(end = 2.dp))
                } else {
                    Box(
                        Modifier.padding(end = 4.dp).size(36.dp).clip(Capsule)
                            .background(if (draft.isBlank()) OnyxFillStrong else Flame)
                            .clickable(enabled = draft.isNotBlank()) { onAsk(draft.trim()); draft = "" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, "Ask", tint = if (draft.isBlank()) AshFaint else Onyx, modifier = Modifier.size(18.dp))
                    }
                }
            },
        )
        (ask as? FaqAskState.Error)?.let {
            Text(it.message, style = MaterialTheme.typography.labelSmall, color = Coral)
        }
        Text(
            "Answers are AI-generated and saved to this exercise.",
            style = MaterialTheme.typography.labelSmall,
            color = Ash.copy(alpha = 0.7f),
        )
    }
}

// ---- Small pieces -----------------------------------------------------------------------------

@Composable
private fun TabColumn(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = TitleSans.copy(fontSize = 18.sp), color = Chalk, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun NoteCard(title: String, lines: List<String>, accent: Color) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(accent.copy(alpha = 0.08f))
            .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = accent)
        lines.forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("•", color = accent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text(line, style = MaterialTheme.typography.bodyMedium, color = Chalk.copy(alpha = 0.85f))
            }
        }
    }
}

@Composable
private fun NumberedRow(n: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(26.dp).clip(RoundedCornerShape(9.dp))
                .background(Flame.copy(alpha = 0.12f))
                .border(1.dp, Flame.copy(alpha = 0.28f), RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("$n", color = FlameGlow, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Chalk.copy(alpha = 0.9f), modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun MuscleRow(name: String, primary: Boolean) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(OnyxFillStrong).padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(11.dp).clip(RoundedCornerShape(3.dp)).background(if (primary) Flame else Flame.copy(alpha = 0.5f)))
        Text(name.replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.titleSmall, color = Chalk, modifier = Modifier.weight(1f))
        Text(
            if (primary) "PRIMARY" else "SECONDARY",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (primary) FlameGlow else Ash,
        )
    }
}

@Composable
private fun BestSetBanner(progress: ExerciseProgress, weighted: Boolean) {
    val label = if (weighted) {
        "Best set · +${formatKg(progress.heaviest!!.addedWeightKg)}kg × ${progress.heaviest!!.reps}"
    } else {
        "Best set · ${progress.mostReps?.reps ?: 0} reps"
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(Brush.horizontalGradient(listOf(Flame.copy(alpha = 0.16f), Flame.copy(alpha = 0.03f))))
            .border(1.dp, Flame.copy(alpha = 0.28f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(Icons.Filled.EmojiEvents, null, tint = FlameGlow, modifier = Modifier.size(22.dp).glow(Flame, spread = 8.dp, alpha = 0.4f))
        Text(label, style = MaterialTheme.typography.titleSmall, color = Chalk)
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
private fun SpecRow(label: String, value: String, divider: Boolean) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Ash, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall, color = Chalk, textAlign = TextAlign.End)
    }
    if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(OnyxBorder))
}

@Composable
private fun AppearsInRow(w: SavedWorkout, onOpenWorkout: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(OnyxFillStrong)
            .clickable { onOpenWorkout(w.id) }.padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(Flame.copy(alpha = 0.28f), OnyxFillStrong))),
            contentAlignment = Alignment.Center,
        ) {
            Text(w.name.take(2).uppercase(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = FlameGlow)
        }
        Column(Modifier.weight(1f)) {
            Text(w.name, style = MaterialTheme.typography.titleSmall, color = Chalk, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(w.summaryLabel, style = MaterialTheme.typography.labelSmall, color = Ash)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = AshFaint)
    }
}

@Composable
private fun EmptyNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Ash, modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
private fun HistoryTimeline(history: List<ExerciseHistoryEntry>, onOpenSession: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        history.take(15).forEachIndexed { i, entry ->
            val reps = entry.sets.joinToString(", ") { "${it.reps}" }
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Box(Modifier.width(22.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                    Box(Modifier.width(2.dp).fillMaxHeight().background(Chalk.copy(alpha = 0.10f)))
                    Box(
                        Modifier.padding(top = 5.dp).size(10.dp).clip(Capsule)
                            .then(if (i == 0) Modifier.glow(Flame, spread = 6.dp, alpha = 0.6f) else Modifier)
                            .background(if (i == 0) Flame else Chalk.copy(alpha = 0.28f)),
                    )
                }
                Column(
                    Modifier.weight(1f).clickable { onOpenSession(entry.sessionId) }.padding(start = 10.dp, bottom = 18.dp),
                ) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(dateFmt.format(entry.atMs), style = MaterialTheme.typography.titleSmall, color = Chalk, modifier = Modifier.weight(1f))
                        val best = entry.sets.maxOfOrNull { it.reps } ?: 0
                        Text("$best reps", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = if (i == 0) FlameGlow else Ash)
                    }
                    Text(
                        "${entry.sets.size} ${if (entry.sets.size == 1) "set" else "sets"} · $reps",
                        style = MaterialTheme.typography.labelSmall,
                        color = Ash,
                        modifier = Modifier.padding(top = 5.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AiCard(aiState: ExerciseAiState, onEnrich: () -> Unit) {
    AiActionCard(
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
