package com.calistapp.app.ui.detail

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calistapp.app.ui.common.SegmentedRing
import com.calistapp.app.ui.common.RingSegment
import com.calistapp.app.ui.common.formatClock
import com.calistapp.app.ui.common.formatDate
import com.calistapp.app.ui.dashboard.CardSurface
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.Coral
import com.calistapp.app.ui.theme.Display
import com.calistapp.app.ui.theme.Flame
import com.calistapp.app.ui.theme.FlameGlow
import com.calistapp.app.ui.theme.FlameHot
import com.calistapp.app.ui.theme.Onyx
import com.calistapp.core.model.SessionSummary
import com.calistapp.core.progress.PersonalRecord
import com.calistapp.core.progress.TimelineExercise
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SessionDetailScreen(
    onBack: () -> Unit,
    onOpenExercise: (String) -> Unit,
    viewModel: SessionDetailViewModel = hiltViewModel(),
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()
    val audit by viewModel.audit.collectAsStateWithLifecycle()
    val records by viewModel.records.collectAsStateWithLifecycle()
    val progressions by viewModel.progressions.collectAsStateWithLifecycle()
    val deltas by viewModel.deltas.collectAsStateWithLifecycle()
    val recentRecovery by viewModel.recentRecoveryMeanDrop.collectAsStateWithLifecycle()
    val timeline by viewModel.timeline.collectAsStateWithLifecycle()
    val maxHr by viewModel.maxHr.collectAsStateWithLifecycle()

    val current = session
    if (current == null) {
        Box(Modifier.fillMaxSize().background(Onyx), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = FlameHot)
        }
        return
    }
    val s = current.summary ?: SessionSummary.EMPTY

    // Popup + dialog state.
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var showShare by rememberSaveable { mutableStateOf(false) }
    var showCalorie by rememberSaveable { mutableStateOf(false) }
    var showRecovery by rememberSaveable { mutableStateOf(false) }
    var showPb by remember { mutableStateOf<PersonalRecord?>(null) }
    var showExercise by remember { mutableStateOf<TimelineExercise?>(null) }
    var showRest by remember { mutableStateOf(true) }

    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val exercisesRequester = remember { BringIntoViewRequester() }

    val justFinished = remember(current.endMs) {
        current.endMs?.let { System.currentTimeMillis() - it < 30 * 60_000L } ?: false
    }
    val title = current.exerciseName
        ?: current.plan.name.ifBlank { null }
        ?: current.exerciseType.displayName
    val performedKeys = timeline.map { it.key }.toSet()
    val skipped = remember(current.plan, performedKeys) {
        current.plan.exercises.filter { it.slotId !in performedKeys }
    }

    Box(Modifier.fillMaxSize().background(Onyx)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .statusBarsPadding()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TopBar(onBack = onBack, onShare = { showShare = true })

            Header(
                eyebrow = if (justFinished) "Session complete" else "Workout summary",
                title = title,
                meta = "${formatDate(current.startMs)}  ·  ${formatClock(s.totalDurationMs)} total",
            )

            HeroEnergy(summary = s, enabled = audit != null, onClick = { showCalorie = true })

            StatRings(
                summary = s,
                timeline = timeline,
                skippedCount = skipped.size,
                onTap = { scope.launch { exercisesRequester.bringIntoView() } },
            )
            Text(
                "Rings split by active/rest & by exercise — colours match the list below",
                style = MaterialTheme.typography.labelMedium,
                color = Ash.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            )

            records.forEach { record ->
                PersonalBestCard(record) { showPb = record }
            }

            Section("How it felt") {
                RpeCard(rpe = current.rpe, onChange = viewModel::setRpe)
            }

            if (current.samples.size >= 2 || s.timeInZonesMs.isNotEmpty()) {
                Section("Heart rate") {
                    HeartRateSection(
                        summary = s,
                        samples = current.samples,
                        segments = current.segments,
                        maxHr = maxHr,
                        showRest = showRest,
                        onToggleRest = { showRest = !showRest },
                    )
                    if (s.timeInZonesMs.isNotEmpty()) ZonesCard(s)
                    s.hrRecovery?.let { RecoveryRow(it.meanDropBpm) { showRecovery = true } }
                }
            }

            Section("Exercises", modifier = Modifier.bringIntoViewRequester(exercisesRequester)) {
                if (timeline.isEmpty() && skipped.isEmpty()) {
                    Text(
                        "No exercises were logged for this session.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Ash,
                    )
                } else {
                    ExercisesSection(
                        timeline = timeline,
                        skipped = skipped,
                        deltas = deltas,
                        plan = current.plan,
                        onRowClick = { showExercise = it },
                    )
                }
            }

            Section("Notes") {
                NotesSection(saved = current.notes, onChange = viewModel::setNotes)
            }

            Section("AI coach") {
                AiSection(insight = current.aiInsight, aiState = aiState)
            }

            TextButton(onClick = { confirmDelete = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Delete session", color = Coral)
            }

            // Clearance for the sticky CTA + navigation bar.
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            Spacer(Modifier.height(96.dp))
        }

        StickyCta(
            hasInsight = current.aiInsight != null,
            loading = aiState is AiUiState.Loading,
            onClick = viewModel::generateInsight,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    // ---- Overlays ----
    if (showCalorie) audit?.let { CalorieBreakdownOverlay(it, current.summary?.totalKcal) { showCalorie = false } }
    showPb?.let { record ->
        PersonalBestOverlay(
            record = record,
            progression = progressions[record.exerciseKey].orEmpty(),
            onOpenExercise = record.exerciseKey.takeIf { it.isNotBlank() }?.let { key -> { onOpenExercise(key) } },
            onDismiss = { showPb = null },
        )
    }
    if (showRecovery) s.hrRecovery?.let { RecoveryOverlay(it, recentRecovery) { showRecovery = false } }
    showExercise?.let { ex ->
        ExerciseDetailOverlay(
            exercise = ex,
            segments = current.segments.filter { seg -> (seg.slotId ?: seg.exerciseName) == ex.key && seg.exerciseName != null && seg.type == com.calistapp.core.model.SegmentType.ACTIVE },
            samples = current.samples,
            delta = deltas[ex.name],
            onApplyEdits = viewModel::applySetEdits,
            onDismiss = { showExercise = null },
        )
    }
    if (showShare) {
        SessionShareSheet(session = current, summary = s, records = records, onDismiss = { showShare = false })
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this session?") },
            text = {
                Text(
                    "The heart-rate recording, every set you logged, and the calorie breakdown go " +
                        "with it. This can't be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.deleteSession(onBack) }) {
                    Text("Delete", color = Coral)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep it") } },
        )
    }
}

// ---------- Top bar & header ----------

@Composable
private fun TopBar(onBack: () -> Unit, onShare: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconSquare(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Chalk, modifier = Modifier.size(18.dp))
        }
        IconSquare(onClick = onShare) {
            Icon(Icons.Filled.IosShare, "Share workout", tint = Chalk, modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
private fun IconSquare(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

@Composable
private fun Header(eyebrow: String, title: String, meta: String) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Eyebrow(eyebrow, color = FlameHot)
        Text(title, style = MaterialTheme.typography.displaySmall, color = Chalk)
        Text(meta, style = MaterialTheme.typography.bodyMedium, color = Ash)
    }
}

// ---------- Hero energy ----------

@Composable
private fun HeroEnergy(summary: SessionSummary, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    val activeFrac = if (summary.totalKcal > 0) (summary.activeKcal / summary.totalKcal).toFloat() else 0f
    // The bloom breathes, the way the reference's does — a slow pulse that says "live", not a static disc.
    val bloomAlpha by rememberInfiniteTransition(label = "bloom").animateFloat(
        initialValue = 0.16f,
        targetValue = 0.34f,
        animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Reverse),
        label = "bloomAlpha",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF241811), Color(0xFF131215))))
            .border(1.dp, FlameHot.copy(alpha = 0.18f), shape)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        // A soft bloom in the top-right, clipped inside the card (an intentional inner glow, not a
        // halo meant to spill past the edge).
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 40.dp, y = (-50).dp)
                .size(200.dp)
                .background(Brush.radialGradient(listOf(FlameHot.copy(alpha = bloomAlpha), Color.Transparent))),
        )
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Eyebrow("Total energy", color = FlameGlow.copy(alpha = 0.85f))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${summary.totalKcal.toInt()}",
                    style = TextStyle(
                        fontFamily = Display,
                        fontWeight = FontWeight.Bold,
                        fontSize = 60.sp,
                        lineHeight = 56.sp,
                        letterSpacing = (-1.8).sp,
                        brush = Brush.linearGradient(listOf(FlameGlow, FlameHot)),
                    ),
                )
                Text("kcal", style = MaterialTheme.typography.titleMedium, color = Ash, modifier = Modifier.padding(bottom = 8.dp))
            }
            // Active / rest energy split.
            Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.06f))) {
                if (activeFrac > 0f) {
                    Box(Modifier.fillMaxHeight().weight(activeFrac).background(Brush.horizontalGradient(listOf(FlameHot, FlameGlow))))
                    Box(Modifier.width(2.dp))
                }
                Box(Modifier.fillMaxHeight().weight((1f - activeFrac).coerceAtLeast(0.0001f)).background(FlameGlow.copy(alpha = 0.28f)))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Legend(FlameHot, "${summary.activeKcal.toInt()}", "active")
                Legend(FlameGlow.copy(alpha = 0.4f), "${summary.restKcal.toInt()}", "rest")
                Spacer(Modifier.weight(1f))
                Text("${(summary.activeRatio * 100).toInt()}% work ratio", style = MaterialTheme.typography.labelMedium, color = Ash)
            }
            if (enabled) {
                Text("Tap to see how this was calculated", style = MaterialTheme.typography.labelSmall, color = Ash.copy(alpha = 0.75f))
            }
        }
    }
}

@Composable
private fun Legend(dot: Color, value: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(dot))
        Row {
            Text("$value ", style = MaterialTheme.typography.labelLarge, color = Chalk, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelLarge, color = Ash)
        }
    }
}

// ---------- Stat rings ----------

@Composable
private fun StatRings(
    summary: SessionSummary,
    timeline: List<TimelineExercise>,
    skippedCount: Int,
    onTap: () -> Unit,
) {
    val activeFrac = summary.activeRatio.toFloat()
    val durationSegs = listOf(
        RingSegment(activeFrac.coerceAtLeast(0.001f), FlameHot),
        RingSegment((1f - activeFrac).coerceAtLeast(0.001f), FlameHot.copy(alpha = 0.22f)),
    )
    val totalReps = timeline.sumOf { it.reps }
    val repsSegs = if (totalReps > 0) {
        timeline.mapIndexed { i, ex -> RingSegment((ex.reps.toFloat() / totalReps).coerceAtLeast(0.02f), exerciseColor(i)) }
    } else listOf(RingSegment(1f, FlameHot.copy(alpha = 0.22f)))
    val exerciseSegs = timeline.mapIndexed { i, _ -> RingSegment(1f, exerciseColor(i)) } +
        List(skippedCount) { RingSegment(1f, SkippedColor) }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        StatRing(Modifier.weight(1f), durationSegs, formatClock(summary.totalDurationMs), "Duration", onTap)
        StatRing(Modifier.weight(1f), repsSegs, "$totalReps", "Reps", onTap)
        StatRing(Modifier.weight(1f), exerciseSegs.ifEmpty { listOf(RingSegment(1f, FlameHot.copy(alpha = 0.22f))) }, "${timeline.size}", "Exercises", onTap)
    }
}

@Composable
private fun StatRing(
    modifier: Modifier,
    segments: List<RingSegment>,
    value: String,
    label: String,
    onClick: () -> Unit,
) {
    SegmentedRing(
        segments = segments,
        innerColor = CardSurface,
        modifier = modifier.height(84.dp).clickable(onClick = onClick),
        cornerRadius = 18.dp,
        strokeWidth = 2.5.dp,
        contentPadding = 8.dp,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = Chalk, fontWeight = FontWeight.Bold)
            Eyebrow(label)
        }
    }
}

// ---------- Personal best ----------

@Composable
private fun PersonalBestCard(record: PersonalRecord, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(FlameHot.copy(alpha = 0.16f), FlameHot.copy(alpha = 0.04f))))
            .border(1.dp, FlameHot.copy(alpha = 0.3f), shape)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(Brush.linearGradient(listOf(FlameGlow, FlameHot))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.EmojiEvents, null, tint = Color(0xFF140A03), modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text("New personal best", style = MaterialTheme.typography.titleMedium, color = Chalk, fontWeight = FontWeight.Bold)
            Text(record.exerciseName, style = MaterialTheme.typography.bodySmall, color = Ash)
        }
        Text(record.label, style = MaterialTheme.typography.titleLarge, color = FlameGlow, fontWeight = FontWeight.Bold)
    }
}

// ---------- Sticky AI CTA ----------

@Composable
private fun StickyCta(hasInsight: Boolean, loading: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Onyx.copy(alpha = 0.85f), Onyx)))
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.horizontalGradient(listOf(FlameHot, FlameGlow)))
                .clickable(enabled = !loading, onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(color = Color(0xFF140A03), strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Analyzing…", style = MaterialTheme.typography.titleMedium, color = Color(0xFF140A03), fontWeight = FontWeight.Bold)
            } else {
                Icon(Icons.Filled.AutoAwesome, null, tint = Color(0xFF140A03), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(9.dp))
                Text(
                    if (hasInsight) "Regenerate with AI Coach" else "Analyze with AI Coach",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF140A03),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

// ---------- Section wrapper ----------

@Composable
private fun Section(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Eyebrow(title, modifier = Modifier.padding(start = 4.dp))
        content()
    }
}

// ---------- How it felt (RPE) ----------

/**
 * Rating of perceived exertion, 1–10, as a bar of ascending steps that fill green (easy) through to
 * red (maximal) — the one intensity signal no sensor produces. Tapping the current value clears it,
 * so a mis-tap isn't permanent.
 */
@Composable
private fun RpeCard(rpe: Int?, onChange: (Int?) -> Unit) {
    FlatCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(rpe?.toString() ?: "—", style = MaterialTheme.typography.headlineSmall, color = Chalk, fontWeight = FontWeight.Bold)
                Text(" / 10", style = MaterialTheme.typography.titleSmall, color = Ash, modifier = Modifier.padding(bottom = 2.dp))
            }
            Text(
                rpe?.let { rpeLabel(it) } ?: "Unrated",
                style = MaterialTheme.typography.labelLarge,
                color = rpe?.let { difficultyColor(it) } ?: Ash,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            (1..10).forEach { n ->
                val on = rpe != null && n <= rpe
                Box(
                    Modifier
                        .weight(1f)
                        .height((16 + n * 2).dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (on) difficultyColor(n) else Color.White.copy(alpha = 0.07f))
                        .clickable { onChange(if (rpe == n) null else n) },
                )
            }
        }
        Text(
            rpe?.let { "$it — ${rpeLabel(it)}" } ?: "Tap to rate how hard it felt. 1 is barely anything, 10 is all you had.",
            style = MaterialTheme.typography.labelSmall,
            color = Ash,
        )
    }
}

private fun rpeLabel(rpe: Int): String = when (rpe) {
    1, 2 -> "very easy"
    3, 4 -> "easy"
    5, 6 -> "moderate"
    7, 8 -> "hard"
    9 -> "very hard"
    else -> "maximal"
}

// ---------- AI coach insight ----------

@Composable
private fun AiSection(insight: String?, aiState: AiUiState) {
    FlatCard {
        when {
            insight != null -> Text(insight, style = MaterialTheme.typography.bodyMedium, color = Chalk)
            aiState is AiUiState.Loading -> Text("Analyzing your session…", style = MaterialTheme.typography.bodyMedium, color = Ash)
            else -> Text(
                "Get personalized feedback and recommendations based on this session's heart-rate and effort data.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ash,
            )
        }
        (aiState as? AiUiState.Error)?.let {
            Text(it.message, style = MaterialTheme.typography.labelMedium, color = Coral)
        }
    }
}
