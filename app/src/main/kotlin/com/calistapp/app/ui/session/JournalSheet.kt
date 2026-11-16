package com.calistapp.app.ui.session

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calistapp.app.session.LiveSession
import com.calistapp.app.ui.common.DecimalPadSheet
import com.calistapp.app.ui.common.GlowBox
import com.calistapp.app.ui.common.HelpDot
import com.calistapp.app.ui.common.NumberPadSheet
import com.calistapp.app.ui.common.formatClock
import com.calistapp.app.ui.detail.difficultyColor
import com.calistapp.app.ui.exercises.ExerciseImage
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.AshFaint
import com.calistapp.app.ui.theme.Capsule
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.Coral
import com.calistapp.app.ui.theme.Display
import com.calistapp.app.ui.theme.Flame
import com.calistapp.app.ui.theme.FlameGlow
import com.calistapp.app.ui.theme.FlameHot
import com.calistapp.app.ui.theme.Onyx
import com.calistapp.app.ui.theme.PageDim
import com.calistapp.app.ui.theme.PageInk
import com.calistapp.app.ui.theme.PageWarm
import com.calistapp.app.ui.theme.Sky
import com.calistapp.core.model.EffortScale
import com.calistapp.core.model.ExerciseMeasure
import com.calistapp.core.model.PlannedExercise
import com.calistapp.core.model.SetLog
import com.calistapp.core.model.SkipReason
import com.calistapp.core.model.SlotPhase
import com.calistapp.core.model.WorkoutPlan
import com.calistapp.core.model.formatKg
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Which value of the journal is being edited. */
private sealed interface JournalEdit {
    data class Reps(val slotId: String, val setIndex: Int, val current: Int, val seconds: Boolean) : JournalEdit
    data class Weight(val slotId: String, val setIndex: Int, val current: Double) : JournalEdit
    data class Effort(val slotId: String, val setIndex: Int, val scale: EffortScale?, val value: Int?) : JournalEdit
    data class Note(val slotId: String, val setIndex: Int, val current: String) : JournalEdit

    /** The set being worked right now — its live counter, load and effort. */
    data object LiveReps : JournalEdit
    data object LiveWeight : JournalEdit
    data object LiveEffort : JournalEdit

    /** Edit mode: what a slot's remaining sets ask for. */
    data class Target(val slotId: String, val value: Int, val weightKg: Double, val seconds: Boolean) : JournalEdit
    data class TargetWeight(val slotId: String, val value: Int, val weightKg: Double) : JournalEdit
}

private val CardSurface = Color(0xFF141316)
private val WellSurface = Color(0xFF1E1C21)
private val NodeGap = 30.dp
private val RailX = 11.dp

/**
 * The journal: the session as a calm timeline you've travelled down — warm-up, workout and cool-down
 * as phases on one glowing rail, one quiet line per finished exercise, and only the exercise in
 * progress (or one you tap) opened up with its editable values. "Edit" turns the same sheet into the
 * editor for today's session.
 *
 * The earlier version was a stack of grey cards with every figure in an orange-outlined box; the first
 * timeline put a thumbnail, a target line and three large numbers on every row. This keeps the rail
 * and the phases and drops the rest until you ask for it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalSheet(
    live: LiveSession,
    vm: ActiveSessionViewModel,
    imageUrlsFor: (String) -> List<String>,
    onAddExercise: () -> Unit,
    onOpenExercise: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var editing by remember { mutableStateOf<JournalEdit?>(null) }
    var editMode by rememberSaveable { mutableStateOf(false) }
    val pendingReorder by vm.pendingReorder.collectAsStateWithLifecycle()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PageWarm,
    ) {
        Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(PageWarm, PageDim, PageInk)))) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 680.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 28.dp),
            ) {
                JournalHeader(
                    editMode = editMode,
                    onToggleEdit = { editMode = !editMode },
                    onDone = { if (editMode) editMode = false else onDismiss() },
                )
                // The editor slides in from the side, the timeline back — a mode change, not a new screen.
                AnimatedContent(
                    targetState = editMode,
                    transitionSpec = {
                        (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { if (targetState) it / 5 else -it / 5 }) togetherWith
                            fadeOut(tween(160))
                    },
                    label = "journal-mode",
                ) { editing2 ->
                    if (editing2) {
                        SessionEditor(
                            live = live,
                            // While a reorder question is up, show the order it would give.
                            plan = pendingReorder?.plan ?: live.plan,
                            vm = vm,
                            onAddExercise = onAddExercise,
                            onEdit = { editing = it },
                        )
                    } else {
                        JournalTimeline(live, imageUrlsFor, onEdit = { editing = it }, onOpenExercise = onOpenExercise)
                    }
                }
            }
        }
    }

    pendingReorder?.let { pending ->
        ReorderChoicesSheet(
            behindNames = pending.behindNames,
            currentName = live.currentExercise?.name,
            lastRound = live.currentRound >= live.plan.rounds,
            onChoice = vm::resolveReorder,
        )
    }

    when (val e = editing) {
        is JournalEdit.Reps -> NumberPadSheet(
            title = if (e.seconds) "Seconds" else "Reps",
            initial = e.current,
            onConfirm = { vm.setSetReps(e.slotId, e.setIndex, it); editing = null },
            onDismiss = { editing = null },
        )
        is JournalEdit.Weight -> DecimalPadSheet(
            title = "Added weight",
            initial = e.current,
            onConfirm = { vm.setSetWeight(e.slotId, e.setIndex, it); editing = null },
            onDismiss = { editing = null },
        )
        is JournalEdit.Effort -> EffortInputSheet(
            initialScale = e.scale,
            initialValue = e.value,
            onConfirm = { s, v -> vm.setSetEffort(e.slotId, e.setIndex, s, v.toDouble()); editing = null },
            onDismiss = { editing = null },
        )
        is JournalEdit.Note -> NoteDialog(
            initial = e.current,
            onConfirm = { vm.setSetNote(e.slotId, e.setIndex, it); editing = null },
            onDismiss = { editing = null },
        )
        JournalEdit.LiveReps -> NumberPadSheet(
            title = if (live.currentExercise?.measure == ExerciseMeasure.SECONDS) "Seconds" else "Reps",
            initial = live.currentReps,
            onConfirm = { vm.setReps(it); editing = null },
            onDismiss = { editing = null },
        )
        JournalEdit.LiveWeight -> DecimalPadSheet(
            title = "Added weight",
            initial = live.currentSetWeightKg,
            onConfirm = { vm.setAddedWeight(it); editing = null },
            onDismiss = { editing = null },
        )
        JournalEdit.LiveEffort -> EffortInputSheet(
            initialScale = live.pendingEffortScale ?: live.currentEffortTarget?.scale,
            initialValue = live.pendingEffortValue?.toInt() ?: live.currentEffortTarget?.value?.toInt(),
            onConfirm = { s, v -> vm.setCurrentEffort(s, v.toDouble()); editing = null },
            onDismiss = { editing = null },
        )
        is JournalEdit.Target -> NumberPadSheet(
            title = if (e.seconds) "Seconds per set" else "Reps per set",
            initial = e.value,
            onConfirm = { vm.retarget(e.slotId, it, e.weightKg); editing = null },
            onDismiss = { editing = null },
        )
        is JournalEdit.TargetWeight -> DecimalPadSheet(
            title = "Added weight",
            initial = e.weightKg,
            onConfirm = { vm.retarget(e.slotId, e.value, it); editing = null },
            onDismiss = { editing = null },
        )
        null -> Unit
    }
}

@Composable
private fun JournalHeader(editMode: Boolean, onToggleEdit: () -> Unit, onDone: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(targetState = editMode, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) }, label = "title", modifier = Modifier.weight(1f)) { e ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (e) "Edit session" else "Journal",
                    style = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = (-0.6).sp),
                    color = Chalk,
                )
                if (e) {
                    HelpDot(
                        "Changes apply to today's session only. Your saved workout stays as it is unless you " +
                            "choose to keep them at the end.",
                    )
                }
            }
        }
        if (!editMode) {
            Box(
                Modifier
                    .clip(Capsule)
                    .border(1.dp, Chalk.copy(alpha = 0.12f), Capsule)
                    .clickable(onClick = onToggleEdit)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) { Text("Edit", style = MaterialTheme.typography.labelLarge, color = Chalk) }
            Spacer(Modifier.width(8.dp))
        }
        GlowBox(FlameHot, Capsule, glowRadius = 10.dp, glowAlpha = 0.4f) {
            Row(
                Modifier
                    .background(Brush.verticalGradient(listOf(FlameGlow, FlameHot)), Capsule)
                    .clickable(onClick = onDone)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(Icons.Filled.Check, null, tint = Onyx, modifier = Modifier.size(15.dp))
                Text("Done", style = MaterialTheme.typography.labelLarge, color = Onyx, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ---- The timeline --------------------------------------------------------------------------------

private enum class NodeKind { DONE, NOW, SKIPPED, UPCOMING, COOL }

@Composable
private fun JournalTimeline(
    live: LiveSession,
    imageUrlsFor: (String) -> List<String>,
    onEdit: (JournalEdit) -> Unit,
    onOpenExercise: (String) -> Unit,
) {
    var openSlot by rememberSaveable { mutableStateOf<String?>(null) }
    var openPhase by rememberSaveable { mutableStateOf<String?>(null) }
    val hasCool = live.plan.slotsIn(SlotPhase.COOL_DOWN).isNotEmpty()

    Column(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                // The rail: orange where the session has been, fading out, and settled blue toward a cool-down.
                val x = RailX.toPx()
                val stops = buildList {
                    add(0f to FlameHot.copy(alpha = 0.75f))
                    add(0.6f to FlameHot.copy(alpha = 0.45f))
                    add(0.75f to Chalk.copy(alpha = 0.1f))
                    add(1f to if (hasCool) Sky.copy(alpha = 0.3f) else Chalk.copy(alpha = 0.06f))
                }.toTypedArray()
                drawLine(
                    Brush.verticalGradient(*stops),
                    start = Offset(x, 10.dp.toPx()),
                    end = Offset(x, size.height - 10.dp.toPx()),
                    strokeWidth = 2.dp.toPx(),
                )
            }
            .animateContentSize(tween(240)),
    ) {
        SlotPhase.entries.forEach { phase ->
            val slots = live.plan.slotsIn(phase)
            if (slots.isEmpty()) return@forEach
            if (phase == SlotPhase.MAIN) {
                PhaseRow(phase, mainMeta(live), node = NodeKind.DONE.takeIf { slots.any { s -> live.setLogs.any { it.slotId == s.slotId } } } ?: NodeKind.UPCOMING, small = true)
                slots.forEach { slot ->
                    ExerciseEntry(
                        live = live,
                        slot = slot,
                        open = openSlot == slot.slotId,
                        imageUrls = imageUrlsFor(slot.exerciseId),
                        onToggle = { openSlot = if (openSlot == slot.slotId) null else slot.slotId },
                        onEdit = onEdit,
                        onOpenExercise = { onOpenExercise(slot.exerciseId) },
                    )
                }
            } else {
                val done = slots.count { live.setLogs.any { l -> l.slotId == it.slotId } }
                val resolved = slots.count { (live.completedSets[it.slotId] ?: 0) >= 1 }
                val node = when {
                    resolved == slots.size && done > 0 -> NodeKind.DONE
                    resolved == slots.size -> NodeKind.SKIPPED
                    phase == SlotPhase.COOL_DOWN -> NodeKind.COOL
                    else -> NodeKind.UPCOMING
                }
                val key = phase.name
                PhaseRow(
                    phase,
                    routineMeta(live, slots, done, resolved),
                    node = node,
                    onClick = { openPhase = if (openPhase == key) null else key },
                )
                AnimatedVisibility(openPhase == key, enter = expandVertically(tween(220)) + fadeIn(), exit = shrinkVertically(tween(180)) + fadeOut()) {
                    Column { slots.forEach { RoutineMoveRow(live, it) } }
                }
            }
        }
    }
}

private fun mainMeta(live: LiveSession): String {
    val main = live.plan.slotsIn(SlotPhase.MAIN)
    val done = main.count { s -> live.setLogs.any { it.slotId == s.slotId } }
    return if (live.plan.isCircuit && live.plan.rounds > 1) "Round ${live.currentRound} of ${live.plan.rounds} · $done/${main.size}"
    else "$done of ${main.size}"
}

private fun routineMeta(live: LiveSession, slots: List<PlannedExercise>, done: Int, resolved: Int): String {
    val word = if (slots.firstOrNull()?.let(live.plan::phaseOf) == SlotPhase.COOL_DOWN) "stretches" else "moves"
    val heldMs = live.setLogs.filter { l -> slots.any { it.slotId == l.slotId } }.sumOf { it.durationMs }
    return when {
        resolved == slots.size && done == slots.size -> "✓ ${slots.size} $word · ${formatClock(heldMs)}"
        resolved == slots.size && done > 0 -> "✓ $done of ${slots.size} $word · ${formatClock(heldMs)}"
        resolved == slots.size -> "skipped"
        done > 0 -> "$done of ${slots.size} ›"
        else -> "${slots.size} $word · ${(slots.sumOf { it.targetSeconds } + 59) / 60} min ›"
    }
}

@Composable
private fun PhaseRow(phase: SlotPhase, meta: String, node: NodeKind, small: Boolean = false, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick) else Modifier)
            .padding(top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(NodeGap), contentAlignment = Alignment.CenterStart) {
            Box(Modifier.padding(start = RailX - (if (small) 4.dp else 6.dp))) { TimelineNode(node, if (small) 8.dp else 12.dp) }
        }
        Text(
            phase.label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.6.sp),
            fontWeight = FontWeight.SemiBold,
            color = if (phase == SlotPhase.COOL_DOWN) Sky else FlameGlow,
            modifier = Modifier.weight(1f),
        )
        Text(meta, style = MaterialTheme.typography.labelMedium, color = Ash)
    }
}

/** A finished, skipped, upcoming or in-progress exercise — one quiet line, or opened up for editing. */
@Composable
private fun ExerciseEntry(
    live: LiveSession,
    slot: PlannedExercise,
    open: Boolean,
    imageUrls: List<String>,
    onToggle: () -> Unit,
    onEdit: (JournalEdit) -> Unit,
    onOpenExercise: () -> Unit,
) {
    val logs = live.setLogs.filter { it.slotId == slot.slotId }.sortedBy { it.setIndex }
    val skips = live.skipsFor(slot.slotId)
    val inProgress = live.isWorking && live.currentSlotId == slot.slotId
    val upNext = !live.isWorking && live.upNext?.slotId == slot.slotId
    val hold = slot.measure == ExerciseMeasure.SECONDS
    val removed = skips.any { it.reason == SkipReason.REMOVED }

    when {
        inProgress -> OpenCard(slot, imageUrls, now = true, onOpenExercise = onOpenExercise) {
            logs.forEach { SetWells(it, slot, hold, onEdit) }
            LiveWells(live, hold, onEdit)
        }
        open && logs.isNotEmpty() -> OpenCard(slot, imageUrls, now = false, onOpenExercise = onOpenExercise, onCollapse = onToggle) {
            logs.forEach { SetWells(it, slot, hold, onEdit) }
        }
        logs.isEmpty() && skips.isNotEmpty() -> QuietRow(
            node = NodeKind.SKIPPED,
            name = slot.name,
            dim = true,
            trailing = { Text(if (removed) "removed" else "skipped", style = MaterialTheme.typography.labelMedium, color = Ash) },
        )
        logs.isEmpty() -> QuietRow(
            node = NodeKind.UPCOMING,
            name = slot.name,
            dim = !upNext,
            meta = if (upNext) "up next · ${live.plan.targetLabelFor(slot)}" else live.plan.targetLabelFor(slot),
            metaColor = if (upNext) FlameGlow else Ash,
        )
        else -> QuietRow(
            node = NodeKind.DONE,
            name = slot.name,
            metaContent = { DoneMeta(logs, skips.size) },
            trailing = {
                val total = logs.sumOf { it.reps }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$total", style = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = (-0.4).sp), color = Chalk)
                    Text(if (hold) "s" else " reps", style = MaterialTheme.typography.labelSmall, color = Ash, modifier = Modifier.padding(bottom = 3.dp, start = 1.dp))
                }
            },
            onClick = onToggle,
        )
    }
}

/** "+15 kg · ● 8 RPE" — weight and effort folded into one quiet line under a finished exercise. */
@Composable
private fun DoneMeta(logs: List<SetLog>, skipped: Int) {
    val rated = logs.lastOrNull { it.effortLabel != null }
    val weights = logs.map { it.weightKg }.distinct()
    Row(verticalAlignment = Alignment.CenterVertically) {
        val parts = buildList {
            if (logs.size > 1) add("${logs.size} sets")
            if (skipped > 0) add("$skipped skipped")
            weights.singleOrNull()?.takeIf { it > 0 }?.let { add("+${formatKg(it)} kg") }
                ?: weights.maxOrNull()?.takeIf { it > 0 }?.let { add("up to +${formatKg(it)} kg") }
        }
        if (parts.isNotEmpty()) Text(parts.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = Ash)
        rated?.let { log ->
            val level = (log.effortValue ?: 0.0).roundToInt()
            val color = if (log.effortScale == EffortScale.RPE) difficultyColor(level) else Flame
            Box(Modifier.padding(start = if (parts.isEmpty()) 0.dp else 8.dp, end = 5.dp).size(7.dp).clip(CircleShape).background(color))
            Text(log.effortLabel.orEmpty(), style = MaterialTheme.typography.labelMedium, color = Ash)
        }
    }
}

@Composable
private fun QuietRow(
    node: NodeKind,
    name: String,
    dim: Boolean = false,
    meta: String? = null,
    metaColor: Color = Ash,
    metaContent: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(if (onClick != null) Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick) else Modifier)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(NodeGap), contentAlignment = Alignment.CenterStart) {
            Box(Modifier.padding(start = RailX - 6.dp)) { TimelineNode(node, 12.dp) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                name,
                style = TextStyle(fontFamily = Display, fontWeight = if (dim) FontWeight.Medium else FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = (-0.2).sp),
                color = if (dim) Chalk.copy(alpha = 0.55f) else Chalk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            when {
                metaContent != null -> metaContent()
                meta != null -> Text(meta, style = MaterialTheme.typography.labelMedium, color = metaColor)
            }
        }
        trailing?.invoke()
    }
}

/** An exercise opened up: its banked sets (and the set in progress) as editable values. */
@Composable
private fun OpenCard(
    slot: PlannedExercise,
    imageUrls: List<String>,
    now: Boolean,
    onOpenExercise: () -> Unit,
    onCollapse: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = NodeGap - 8.dp)
                .background(CardSurface, shape)
                .border(1.dp, if (now) FlameHot.copy(alpha = 0.45f) else Chalk.copy(alpha = 0.07f), shape)
                .then(if (onCollapse != null) Modifier.clickable(onClick = onCollapse) else Modifier)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ExerciseImage(
                    urls = imageUrls,
                    contentDescription = slot.name,
                    animate = false,
                    phaseKey = slot.slotId,
                    modifier = Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).clickable(onClick = onOpenExercise),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        slot.name,
                        style = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 17.sp, letterSpacing = (-0.2).sp),
                        color = Chalk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(onClick = onOpenExercise),
                    )
                    if (now) {
                        Text("IN PROGRESS", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp), fontWeight = FontWeight.Bold, color = FlameGlow)
                    }
                }
            }
            content()
        }
        Box(Modifier.padding(start = RailX - (if (now) 8.dp else 6.dp), top = 24.dp)) {
            TimelineNode(if (now) NodeKind.NOW else NodeKind.DONE, if (now) 16.dp else 12.dp)
        }
    }
}

/** One banked set as three soft wells — result, load, effort — each tapping into its own editor. */
@Composable
private fun SetWells(log: SetLog, slot: PlannedExercise, hold: Boolean, onEdit: (JournalEdit) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("SET ${log.setIndex}", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp), color = AshFaint, modifier = Modifier.width(40.dp))
            Well(
                value = "${log.reps}",
                label = if (hold) "SEC" else if (com.calistapp.core.model.isUnilateral(slot.name) && log.reps > 1) "REPS · ${(log.reps + 1) / 2}/SIDE" else "REPS",
                modifier = Modifier.weight(1f),
            ) { onEdit(JournalEdit.Reps(log.slotId, log.setIndex, log.reps, hold)) }
            Well(
                value = if (log.weightKg > 0) "+${formatKg(log.weightKg)}" else "—",
                label = "KG",
                dim = log.weightKg <= 0,
                modifier = Modifier.weight(1f),
            ) { onEdit(JournalEdit.Weight(log.slotId, log.setIndex, log.weightKg)) }
            val planned = slot.sets().getOrNull(log.setIndex - 1)?.effort
            val level = log.effortValue?.roundToInt()
            Well(
                value = level?.toString() ?: planned?.value?.roundToInt()?.let { "→$it" } ?: "—",
                label = (log.effortScale ?: planned?.scale)?.label ?: "EFFORT",
                valueColor = if (level != null && log.effortScale == EffortScale.RPE) difficultyColor(level) else if (level != null) Chalk else AshFaint,
                modifier = Modifier.weight(1f),
            ) {
                onEdit(JournalEdit.Effort(log.slotId, log.setIndex, log.effortScale ?: planned?.scale, level ?: planned?.value?.roundToInt()))
            }
        }
        Text(
            if (log.note.isBlank()) "＋ Note" else "“${log.note}”",
            style = MaterialTheme.typography.labelMedium,
            color = Ash,
            modifier = Modifier
                .padding(start = 46.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable { onEdit(JournalEdit.Note(log.slotId, log.setIndex, log.note)) }
                .padding(vertical = 2.dp, horizontal = 2.dp),
        )
    }
}

/** The set being worked right now, editable in place: the live counter, the load and the effort. */
@Composable
private fun LiveWells(live: LiveSession, hold: Boolean, onEdit: (JournalEdit) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("SET ${live.setIndex}", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp), color = FlameGlow, modifier = Modifier.width(40.dp))
        Well("${live.currentReps}", if (hold) "SEC" else "REPS", modifier = Modifier.weight(1f)) { onEdit(JournalEdit.LiveReps) }
        Well(
            if (live.currentSetWeightKg > 0) "+${formatKg(live.currentSetWeightKg)}" else "—",
            "KG",
            dim = live.currentSetWeightKg <= 0,
            modifier = Modifier.weight(1f),
        ) { onEdit(JournalEdit.LiveWeight) }
        val level = live.pendingEffortValue?.roundToInt()
        Well(
            level?.toString() ?: "—",
            live.pendingEffortScale?.label ?: "EFFORT",
            valueColor = if (level != null && live.pendingEffortScale == EffortScale.RPE) difficultyColor(level) else if (level != null) Chalk else AshFaint,
            modifier = Modifier.weight(1f),
        ) { onEdit(JournalEdit.LiveEffort) }
    }
}

@Composable
private fun Well(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    dim: Boolean = false,
    valueColor: Color = if (dim) AshFaint else Chalk,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .height(50.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(WellSurface)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(value, style = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 19.sp, letterSpacing = (-0.4).sp), color = valueColor)
        Text(label, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp, fontSize = 9.sp), color = Ash, maxLines = 1)
    }
}

/** A warm-up or cool-down move inside its expanded phase. */
@Composable
private fun RoutineMoveRow(live: LiveSession, slot: PlannedExercise) {
    val log = live.setLogs.lastOrNull { it.slotId == slot.slotId }
    val skipped = log == null && live.skipsFor(slot.slotId).isNotEmpty()
    val node = when {
        log != null -> NodeKind.DONE
        skipped -> NodeKind.SKIPPED
        live.isWorking && live.currentSlotId == slot.slotId -> NodeKind.NOW
        else -> if (live.plan.phaseOf(slot) == SlotPhase.COOL_DOWN) NodeKind.COOL else NodeKind.UPCOMING
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(NodeGap), contentAlignment = Alignment.CenterStart) {
            Box(Modifier.padding(start = RailX - 4.dp)) { TimelineNode(node, 8.dp) }
        }
        Text(
            slot.name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (log != null) Chalk else Chalk.copy(alpha = 0.55f),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            when {
                log != null -> "${log.reps}s"
                skipped -> "skipped"
                else -> "${slot.targetSeconds}s"
            },
            style = MaterialTheme.typography.labelMedium,
            color = Ash,
        )
    }
}

@Composable
private fun TimelineNode(kind: NodeKind, size: androidx.compose.ui.unit.Dp) {
    when (kind) {
        NodeKind.DONE -> GlowBox(FlameHot, CircleShape, glowRadius = 6.dp, glowAlpha = 0.6f, modifier = Modifier.size(size)) {
            Box(Modifier.size(size).background(Brush.verticalGradient(listOf(FlameGlow, FlameHot)), CircleShape))
        }
        NodeKind.NOW -> {
            // The node you're on breathes, so the eye finds "here" on the rail without reading.
            val pulse by rememberInfiniteTransition(label = "now").animateFloat(
                0.10f, 0.26f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "halo",
            )
            Box(
                Modifier
                    .size(size)
                    .drawBehind { drawCircle(FlameHot.copy(alpha = pulse), radius = this.size.minDimension / 2 + 5.dp.toPx()) }
                    .background(Onyx, CircleShape)
                    .border(2.dp, FlameHot, CircleShape),
            )
        }
        NodeKind.SKIPPED -> Canvas(Modifier.size(size)) {
            drawCircle(Onyx)
            drawCircle(
                Chalk.copy(alpha = 0.4f),
                style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.5.dp.toPx(), 2.dp.toPx()))),
            )
        }
        NodeKind.UPCOMING -> Box(Modifier.size(size).background(Onyx, CircleShape).border(1.5.dp, Chalk.copy(alpha = 0.22f), CircleShape))
        NodeKind.COOL -> Box(Modifier.size(size).background(Onyx, CircleShape).border(1.5.dp, Sky.copy(alpha = 0.6f), CircleShape))
    }
}

// ---- Edit today's session -----------------------------------------------------------------------

/**
 * The journal as an editor for today's session: remove (or restore) exercises, drag to reorder, change
 * what the remaining sets ask for, change a circuit's rounds, add an exercise. Banked sets are locked.
 * Nothing here touches the saved workout — finishing asks once whether to keep the changes.
 */
@Composable
private fun SessionEditor(
    live: LiveSession,
    plan: WorkoutPlan,
    vm: ActiveSessionViewModel,
    onAddExercise: () -> Unit,
    onEdit: (JournalEdit) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("WORKOUT", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.6.sp), fontWeight = FontWeight.SemiBold, color = FlameGlow, modifier = Modifier.weight(1f))
            if (live.plan.isCircuit) {
                Text("Rounds", style = MaterialTheme.typography.labelMedium, color = Ash)
                Spacer(Modifier.width(8.dp))
                StepButton(Icons.Filled.Remove, enabled = live.plan.rounds > live.currentRound) { vm.setRounds(live.plan.rounds - 1) }
                AnimatedContent(targetState = live.plan.rounds, label = "rounds") { r ->
                    Text("$r", style = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 16.sp), color = Chalk, modifier = Modifier.padding(horizontal = 10.dp))
                }
                StepButton(Icons.Filled.Add, enabled = live.plan.rounds < 9) { vm.setRounds(live.plan.rounds + 1) }
            }
        }

        ReorderableSlots(live, plan, vm, onEdit)

        Row(
            Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(16.dp))
                .drawBehind {
                    drawRoundRect(
                        FlameHot.copy(alpha = 0.45f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()),
                        style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                    )
                }
                .clickable(onClick = onAddExercise),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Add, null, tint = Color(0xFFFFB48A), modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add exercise", style = MaterialTheme.typography.labelLarge, color = Color(0xFFFFB48A))
        }

        listOf(SlotPhase.WARM_UP, SlotPhase.COOL_DOWN).forEach { phase ->
            val slots = live.plan.slotsIn(phase).filter { (live.completedSets[it.slotId] ?: 0) == 0 }
            if (slots.isEmpty()) return@forEach
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    phase.label.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.6.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = if (phase == SlotPhase.COOL_DOWN) Sky else FlameGlow,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Remove all",
                    style = MaterialTheme.typography.labelMedium,
                    color = Ash,
                    modifier = Modifier.clip(Capsule).clickable { slots.forEach { vm.removeExercise(it.slotId) } }.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            slots.forEach { slot ->
                EditorRow(
                    slot = slot,
                    leading = { RemoveButton { vm.removeExercise(slot.slotId) } },
                    trailing = { TargetChip("${slot.targetSeconds}", "s") { onEdit(JournalEdit.Target(slot.slotId, slot.targetSeconds, 0.0, seconds = true)) } },
                )
            }
        }
    }
}

/**
 * The workout's exercises with drag handles. Hold a handle and the row lifts above the others (it used
 * to slide *behind* the rows below it — a translated row still paints in list order) while the rows it
 * passes slide out of its way, the same feel as the workout builder. The move is committed once, on
 * drop, so the session's reorder rules (and a circuit's "what about the ones now behind you?") run once.
 */
@Composable
private fun ReorderableSlots(live: LiveSession, plan: WorkoutPlan, vm: ActiveSessionViewModel, onEdit: (JournalEdit) -> Unit) {
    val main = plan.slotsIn(SlotPhase.MAIN)
    val rowPx = with(LocalDensity.current) { (54.dp + 8.dp).toPx() }
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragDy by remember { mutableFloatStateOf(0f) }
    // Leftover offset the dropped row glides home from once the list has its new order.
    val settle = remember { Animatable(0f) }
    var committed by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var snapShifts by remember { mutableStateOf(false) }

    val order = main.map { it.slotId }
    val fromIndex = order.indexOf(dragging)
    val target = if (fromIndex < 0) -1 else (fromIndex + (dragDy / rowPx).roundToInt()).coerceIn(0, main.lastIndex)

    // The reordered list has arrived: the dropped row now sits in its new slot. The rows that had made
    // room are already where they belong, so they snap; the dropped row eases off its last finger offset.
    LaunchedEffect(order) {
        val (from, to) = committed ?: return@LaunchedEffect
        if (order.indexOf(dragging) != to) return@LaunchedEffect
        committed = null
        snapShifts = true
        settle.snapTo(dragDy - (to - from) * rowPx)
        dragDy = 0f
        withFrameNanos { }
        snapShifts = false
        settle.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow))
        dragging = null
    }

    fun glideHome() = scope.launch {
        settle.snapTo(dragDy)
        dragDy = 0f
        settle.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow))
        dragging = null
    }

    // The drag handler outlives recompositions, so the drop must read the order and the finger offset
    // as they are *now* — not as they were captured when the handler was first set up (which dropped
    // every move as "no move").
    val currentOrder by rememberUpdatedState(order)
    fun drop(slotId: String) {
        val ord = currentOrder
        val from = ord.indexOf(slotId)
        val to = if (from < 0) -1 else (from + (dragDy / rowPx).roundToInt()).coerceIn(0, ord.lastIndex)
        if (from >= 0 && to >= 0 && to != from) {
            committed = from to to
            vm.moveExercise(slotId, to)
            // Nothing came back (the move changed nothing for the session): glide home, don't hang.
            scope.launch {
                delay(900)
                if (committed != null) {
                    committed = null
                    glideHome()
                }
            }
        } else {
            glideHome()
        }
    }

    main.forEachIndexed { index, slot ->
        val logs = live.setLogs.count { it.slotId == slot.slotId }
        val removed = live.removedSlots.contains(slot.slotId)
        val resolved = live.completedSets[slot.slotId] ?: 0
        val owed = plan.targetSetsFor(slot.slotId) - resolved
        val isDragging = dragging == slot.slotId
        val shift = when {
            fromIndex < 0 || isDragging || committed != null -> 0f
            fromIndex < target && index in (fromIndex + 1)..target -> -rowPx
            target < fromIndex && index in target until fromIndex -> rowPx
            else -> 0f
        }
        val animatedShift by animateFloatAsState(
            targetValue = shift,
            animationSpec = if (snapShifts) snap() else spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
            label = "rowShift",
        )
        // Lifted while held, settling back down as it lands.
        val lift by animateFloatAsState(if (isDragging && committed == null) 1f else 0f, tween(160), label = "lift")
        Box(
            Modifier
                .zIndex(if (isDragging) 1f else 0f)
                .graphicsLayer {
                    translationY = if (isDragging) dragDy + settle.value else animatedShift
                    shadowElevation = 18f * lift
                    shape = RoundedCornerShape(16.dp)
                    clip = false
                    scaleX = 1f + 0.02f * lift
                    scaleY = 1f + 0.02f * lift
                },
        ) {
            when {
                removed -> EditorRow(
                    slot = slot,
                    dim = true,
                    subtitle = "removed from today",
                    leading = { Icon(Icons.Filled.Remove, null, tint = Ash, modifier = Modifier.size(22.dp)) },
                    trailing = {
                        Text(
                            "Restore",
                            style = MaterialTheme.typography.labelLarge,
                            color = FlameGlow,
                            modifier = Modifier.clip(Capsule).clickable { vm.restoreExercise(slot.slotId) }.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    },
                )
                owed <= 0 -> EditorRow(
                    slot = slot,
                    dim = true,
                    subtitle = if (logs > 0) "done · $logs ${if (logs == 1) "set" else "sets"}" else "skipped",
                    leading = { Icon(Icons.Filled.Lock, null, tint = Ash, modifier = Modifier.size(16.dp).padding(1.dp)) },
                )
                else -> {
                    val next = slot.sets().getOrNull(resolved) ?: slot.sets().lastOrNull()
                    val hold = slot.measure == ExerciseMeasure.SECONDS
                    val value = next?.reps ?: if (hold) slot.targetSeconds else slot.targetReps
                    val kg = next?.weightKg ?: slot.addedWeightKg
                    EditorRow(
                        slot = slot,
                        subtitle = when {
                            live.isWorking && live.currentSlotId == slot.slotId -> "in progress"
                            logs > 0 -> "$logs done · $owed to go"
                            else -> null
                        },
                        leading = { RemoveButton { vm.removeExercise(slot.slotId) } },
                        trailing = {
                            TargetChip("$value", if (hold) "s" else "reps") { onEdit(JournalEdit.Target(slot.slotId, value, kg, hold)) }
                            Spacer(Modifier.width(6.dp))
                            TargetChip(if (kg > 0) "+${formatKg(kg)}" else "+", "kg") { onEdit(JournalEdit.TargetWeight(slot.slotId, value, kg)) }
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Filled.DragHandle,
                                "Drag to reorder",
                                tint = if (isDragging) FlameGlow else Ash,
                                modifier = Modifier
                                    .size(30.dp)
                                    .padding(4.dp)
                                    .pointerInput(slot.slotId, index) {
                                        detectDragGestures(
                                            onDragStart = {
                                                if (committed == null && dragging == null) {
                                                    dragging = slot.slotId
                                                    dragDy = 0f
                                                }
                                            },
                                            onDragEnd = { if (dragging == slot.slotId && committed == null) drop(slot.slotId) },
                                            onDragCancel = { if (dragging == slot.slotId && committed == null) drop(slot.slotId) },
                                        ) { change, amount ->
                                            change.consume()
                                            if (dragging == slot.slotId && committed == null) dragDy += amount.y
                                        }
                                    },
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorRow(
    slot: PlannedExercise,
    leading: @Composable () -> Unit,
    dim: Boolean = false,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CardSurface)
            .border(1.dp, Chalk.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(30.dp), contentAlignment = Alignment.CenterStart) { leading() }
        Column(Modifier.weight(1f)) {
            Text(
                slot.name,
                style = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
                color = if (dim) Chalk.copy(alpha = 0.5f) else Chalk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Ash) }
        }
        trailing?.let { Row(verticalAlignment = Alignment.CenterVertically) { it() } }
    }
}

@Composable
private fun RemoveButton(onClick: () -> Unit) {
    Box(
        Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(Coral.copy(alpha = 0.16f))
            .border(1.dp, Coral.copy(alpha = 0.5f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Box(Modifier.size(width = 10.dp, height = 2.dp).background(Coral, RoundedCornerShape(1.dp))) }
}

@Composable
private fun TargetChip(value: String, unit: String, onClick: () -> Unit) {
    Row(
        Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF1F1D22))
            .border(1.dp, Chalk.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(value, style = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 13.sp), color = Chalk)
        Text(unit, style = MaterialTheme.typography.labelSmall, color = Ash)
    }
}

@Composable
private fun StepButton(icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Chalk.copy(alpha = if (enabled) 0.07f else 0.03f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = if (enabled) Chalk else AshFaint, modifier = Modifier.size(16.dp)) }
}

@Composable
private fun NoteDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set note") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("How did it feel?") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim()) }) { Text("Save", color = Flame) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
