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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Lightbulb
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calistapp.app.R
import com.calistapp.app.ui.common.AiActionCard
import com.calistapp.app.ui.common.GlowBox
import com.calistapp.app.ui.common.GlowIcon
import com.calistapp.app.ui.common.glow
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.AshFaint
import com.calistapp.app.ui.theme.Capsule
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.CardFlat
import com.calistapp.app.ui.theme.Coral
import com.calistapp.app.ui.theme.Display
import com.calistapp.app.ui.theme.Eyebrow
import com.calistapp.app.ui.theme.Flame
import com.calistapp.app.ui.theme.FlameGlow
import com.calistapp.app.ui.theme.FlameHot
import com.calistapp.app.ui.theme.Mint
import com.calistapp.app.ui.theme.NumericMedium
import com.calistapp.app.ui.theme.Onyx
import com.calistapp.app.ui.theme.OnyxBorder
import com.calistapp.app.ui.theme.OnyxFillStrong
import com.calistapp.app.ui.theme.OnyxRaised
import com.calistapp.app.ui.theme.PageDim
import com.calistapp.app.ui.theme.PageInk
import com.calistapp.app.ui.theme.PageWarm
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

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(PageWarm, PageDim, PageInk)))) {
        if (e == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Flame)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    // Chrome and title ride on the hero and scroll away with it, so the sticky tab row
                    // owns the top edge once you scroll. The eyebrow + title sit over the demo: real
                    // Compose text composites fine over the ExoPlayer surface (a gradient scrim would
                    // not — see CLAUDE.md), so the title carries its own shadow for legibility.
                    Box(Modifier.fillMaxWidth().aspectRatio(1.12f)) {
                        ExerciseMediaCarousel(exercise = e, modifier = Modifier.matchParentSize())
                        // Bottom fade so the media edge dissolves into the page and the title reads. A
                        // plain Android View with a gradient drawable — a Compose scrim wouldn't
                        // composite over the ExoPlayer surface (CLAUDE.md video-scrim lesson).
                        AndroidView(
                            factory = { ctx -> android.view.View(ctx).apply { setBackgroundResource(R.drawable.hero_bottom_scrim) } },
                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(150.dp),
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
                        HeroTitle(e, modifier = Modifier.align(Alignment.BottomStart))
                    }
                }
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
        modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Every top button is a bare, soft-shadowed icon — the back arrow included, so they read as one
        // family (no glass chip on any of them).
        ShadowIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
        Spacer(Modifier.weight(1f))
        // Edit and Share moved into the overflow so the hero stays clean.
        ShadowIcon(
            if (favourite) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
            "Bookmark",
            onToggleFavourite,
            tint = if (favourite) Flame else Chalk,
        )
        Spacer(Modifier.size(4.dp))
        Box {
            ShadowIcon(Icons.Filled.MoreVert, "More", { menuOpen = true })
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = OnyxRaised) {
                DropdownMenuItem(
                    text = { Text("Edit") },
                    leadingIcon = { Icon(Icons.Filled.Edit, null, tint = Chalk) },
                    onClick = { menuOpen = false; onEdit() },
                )
                DropdownMenuItem(
                    text = { Text("Share") },
                    leadingIcon = { Icon(Icons.Filled.Share, null, tint = Chalk) },
                    onClick = { menuOpen = false; onShare() },
                )
                DropdownMenuItem(
                    text = { Text(if (isUserAdded) "Delete exercise" else "Hide exercise", color = Coral) },
                    leadingIcon = { Icon(Icons.Filled.DeleteOutline, null, tint = Coral) },
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        }
    }
}

/** A bare top-bar icon with a soft dark drop-shadow for legibility over the hero media (no glass chip). */
@Composable
private fun ShadowIcon(icon: ImageVector, cd: String, onClick: () -> Unit, tint: Color = Chalk) {
    Box(Modifier.size(38.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        GlowIcon(icon, cd, tint = tint, size = 24.dp, glowColor = Color.Black, glowRadius = 5.dp, glowAlpha = 0.6f)
    }
}

@Composable
private fun HeroTitle(e: Exercise, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(
            "${e.bodyPart.displayName} · ${e.difficulty.easyLabel()}".uppercase(),
            style = Eyebrow.copy(shadow = Shadow(Color.Black.copy(alpha = 0.5f), Offset(0f, 1f), 10f)),
            color = FlameGlow,
        )
        Text(
            e.name,
            style = TextStyle(
                fontFamily = Display,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                lineHeight = 32.sp,
                letterSpacing = (-0.6).sp,
                shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 2f), 24f),
            ),
            color = Chalk,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabPills(selected: DetailTab, onSelect: (DetailTab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(PageDim).horizontalScroll(rememberScrollState())
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
private fun BottomCta(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Onyx)))
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .navigationBarsPadding(),
    ) {
        // Always "Add to workout": this screen is a movement, opened to add it to a plan — never to
        // start a one-exercise session. It drops the movement into the draft and returns.
        GlowBox(color = FlameHot, shape = Capsule, glowRadius = 18.dp, glowAlpha = 0.5f, modifier = Modifier.fillMaxWidth()) {
            Box(
                Modifier.fillMaxWidth().height(56.dp).clip(Capsule)
                    .background(Brush.horizontalGradient(listOf(FlameHot, FlameGlow)))
                    .clickable { onAdd() },
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Add, null, tint = Onyx, modifier = Modifier.size(20.dp))
                    Text(
                        "Add to workout",
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
        // A clearer break between the numbered steps and the coloured note cards.
        if (e.tips.isNotEmpty() || e.commonMistakes.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
        }
        if (e.tips.isNotEmpty()) {
            NoteCard("Tips", e.tips, Mint, Icons.Outlined.Lightbulb)
        }
        if (e.commonMistakes.isNotEmpty()) {
            NoteCard("Common mistakes", e.commonMistakes, Coral, Icons.Filled.Close)
        }
        if (e.commonMistakes.isEmpty() && e.tips.isEmpty()) {
            AiCard(aiState, onEnrich)
        }

        // "Goes easy on" now lives inside the FAQ as a Q&A rather than a standalone section.
        FaqBlock(faqs, e.problematicAreas, faqAsk, onAsk, onClearError)
    }
}

@Composable
private fun MusclesTab(e: Exercise) {
    TabColumn {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardFlat)
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
            EfficiencyCard(e.efficiency)
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
            SectionTitle("Trend")
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
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CardFlat)
                .border(1.dp, OnyxBorder, RoundedCornerShape(18.dp)),
        ) {
            SpecRow("Equipment", e.equipment.firstOrNull() ?: "Body only", divider = true)
            e.force?.let { SpecRow("Force", it.replaceFirstChar(Char::uppercase), divider = true) }
            e.mechanic?.let { SpecRow("Mechanic", it.replaceFirstChar(Char::uppercase), divider = true) }
            SpecRow("Difficulty", e.difficulty.easyLabel(), divider = e.tags.isNotEmpty())
            if (e.tags.isNotEmpty()) SpecRow("Tags", e.tags.filterNot { it == "authored" || it == "ai-enriched" || it == "user-edited" }.joinToString(", "), divider = false)
        }

        if (appearsIn.isNotEmpty()) {
            SectionTitle("Appears in · ${appearsIn.size}")
            appearsIn.forEach { w -> AppearsInRow(w, onOpenWorkout) }
        }
    }
}

// ---- FAQ --------------------------------------------------------------------------------------

@Composable
private fun FaqBlock(
    faqs: List<Faq>,
    problematicAreas: List<String>,
    ask: FaqAskState,
    onAsk: (String) -> Unit,
    onClearError: () -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(-1) }
    // The old "Goes easy on" note, folded in as the first Q&A (authored, so no AI badge).
    val safety = if (problematicAreas.isNotEmpty()) {
        Faq(
            question = "What areas should I be careful with?",
            answer = "This movement can stress ${problematicAreas.joinToString(", ") { it.replaceFirstChar(Char::uppercase) }}. Ease off or stop if you feel joint pain there.",
            generated = false,
        )
    } else {
        null
    }
    val all = listOfNotNull(safety) + faqs
    SectionTitle("FAQ")
    if (all.isEmpty()) {
        Text(
            "No questions here yet — ask one below and it'll be answered and saved for this exercise.",
            style = MaterialTheme.typography.bodyMedium,
            color = Ash,
        )
    }
    all.forEachIndexed { i, f ->
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
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title.uppercase(), style = Eyebrow, color = Ash, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
}

@Composable
private fun NoteCard(title: String, lines: List<String>, accent: Color, icon: ImageVector) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(accent.copy(alpha = 0.08f))
            .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(16.dp))
            Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = accent)
        }
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
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CardFlat).padding(horizontal = 15.dp, vertical = 13.dp),
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
        modifier.clip(RoundedCornerShape(14.dp)).background(CardFlat).padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = NumericMedium, color = Flame, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Ash, textAlign = TextAlign.Center)
    }
}

/**
 * The Skills tab's efficiency widget — Calistapp's own strength-per-energy rating as an orange ring
 * (n/5), with a "?" that explains what efficiency means rather than spelling it out inline.
 */
@Composable
private fun EfficiencyCard(efficiency: Int) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardFlat)
            .border(1.dp, OnyxBorder, RoundedCornerShape(16.dp)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(62.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val sw = 6.dp.toPx()
                val d = size.minDimension - sw
                val tl = Offset((size.width - d) / 2f, (size.height - d) / 2f)
                drawArc(Chalk.copy(alpha = 0.08f), 0f, 360f, false, tl, Size(d, d), style = Stroke(sw, cap = StrokeCap.Round))
                drawArc(Flame, -90f, 360f * (efficiency.coerceIn(0, 5) / 5f), false, tl, Size(d, d), style = Stroke(sw, cap = StrokeCap.Round))
            }
            Text("$efficiency/5", style = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 15.sp), color = Chalk)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("EFFICIENCY", style = Eyebrow, color = Ash)
                InfoDot("Calistapp's own rating of how much strength a movement builds for the energy it costs — higher means more result per rep. An estimate to help you choose, not a measurement.")
            }
            Text("Strength built vs. energy spent — Calistapp's own rating.", style = MaterialTheme.typography.bodySmall, color = Ash)
        }
    }
}

/** A tiny "?" that reveals a small tooltip popup right next to it — not a full-screen dialog. */
@Composable
private fun InfoDot(text: String) {
    var show by rememberSaveable { mutableStateOf(false) }
    Box {
        Box(
            Modifier.size(18.dp).clip(Capsule).border(1.dp, Ash.copy(alpha = 0.5f), Capsule).clickable { show = true },
            contentAlignment = Alignment.Center,
        ) {
            Text("?", style = MaterialTheme.typography.labelSmall, color = Ash)
        }
        if (show) {
            Popup(
                alignment = Alignment.BottomStart,
                offset = IntOffset(0, 8),
                onDismissRequest = { show = false },
                properties = PopupProperties(focusable = true),
            ) {
                Box(
                    Modifier.widthIn(max = 240.dp).clip(RoundedCornerShape(10.dp))
                        .background(OnyxRaised).border(1.dp, OnyxBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                ) {
                    Text(text, style = MaterialTheme.typography.bodySmall, color = Chalk)
                }
            }
        }
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
        Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(CardFlat)
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
    // Tap the card to cycle the metric — reps always, weight only when the movement is loaded, and
    // total load (weight × reps) as the third — mirroring the prototype's tap-to-switch trend in
    // place of a row of time-window pills.
    val metrics = remember(weighted) {
        buildList {
            add(TrendMetric("Reps over time", kg = false) { it.bestReps.toDouble() })
            if (weighted) add(TrendMetric("Weight over time", kg = true) { it.bestWeightKg })
            add(TrendMetric("Total load over time", kg = false) { it.bestVolume })
        }
    }
    var idx by rememberSaveable { mutableStateOf(0) }
    val metric = metrics[idx % metrics.size]
    val values = trend.map { metric.value(it) }
    val lo = values.min()
    val hi = values.max()
    val range = (hi - lo).coerceAtLeast(1.0)

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CardFlat)
            .border(1.dp, OnyxBorder, RoundedCornerShape(18.dp))
            .clickable { idx = (idx + 1) % metrics.size }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(metric.label, style = MaterialTheme.typography.titleSmall, color = Chalk)
            if (metrics.size > 1) Text("TAP TO SWITCH ↻", style = Eyebrow, color = FlameGlow)
        }
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val n = values.size
            val stepX = if (n <= 1) 0f else size.width / (n - 1)
            val topPad = size.height * 0.08f
            val usable = size.height * 0.84f
            val pts = values.mapIndexed { i, v ->
                Offset(i * stepX, topPad + (1f - ((v - lo) / range).toFloat()) * usable)
            }
            val area = Path().apply {
                moveTo(pts.first().x, size.height)
                pts.forEach { lineTo(it.x, it.y) }
                lineTo(pts.last().x, size.height)
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(Flame.copy(alpha = 0.30f), Flame.copy(alpha = 0f))))
            val line = Path().apply {
                moveTo(pts.first().x, pts.first().y)
                pts.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(line, Flame, style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            pts.forEach { drawCircle(FlameGlow, radius = 3.dp.toPx(), center = it) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (metric.kg) "${formatKg(lo)}kg" else "${lo.toInt()}", style = MaterialTheme.typography.labelSmall, color = Ash)
            Text(if (metric.kg) "${formatKg(hi)}kg" else "${hi.toInt()}", style = MaterialTheme.typography.labelSmall, color = Ash)
        }
    }
}

private class TrendMetric(val label: String, val kg: Boolean, val value: (ExerciseTrendPoint) -> Double)

private val dateFmt = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

private fun Difficulty.easyLabel(): String = when (this) {
    Difficulty.BEGINNER -> "Easy"
    Difficulty.INTERMEDIATE -> "Medium"
    Difficulty.ADVANCED -> "Hard"
}
