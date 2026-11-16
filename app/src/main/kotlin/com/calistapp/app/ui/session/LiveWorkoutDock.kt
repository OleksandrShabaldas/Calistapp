package com.calistapp.app.ui.session

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calistapp.app.ui.common.RepeatingIconButton
import com.calistapp.app.ui.common.SheetDragState
import com.calistapp.app.ui.common.blockTouchesBelow
import com.calistapp.app.ui.common.formatClock
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.Capsule
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.Flame
import com.calistapp.app.ui.theme.FlameSoft
import com.calistapp.app.ui.theme.NumericLarge
import com.calistapp.app.ui.theme.OnyxRaised
import com.calistapp.app.ui.theme.Sky
import com.calistapp.core.model.SlotPhase
import com.calistapp.core.model.formatKg

/** State of one segment in the top progress bar. */
enum class SegState { DONE, CURRENT, UPCOMING, SKIPPED }

/** One dash of the progress bar: its state and the phase it belongs to. */
data class SegItem(val state: SegState, val phase: SlotPhase = SlotPhase.MAIN)

/** The story-style progress dashes: one per exercise in the current block/round. */
@Composable
fun SegmentBar(items: List<SegItem>, modifier: Modifier = Modifier) {
    if (items.isEmpty()) return
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        items.forEachIndexed { i, item ->
            // Phases sit apart, so the warm-up, the workout and the cool-down read as three blocks.
            if (i > 0) Spacer(Modifier.width(if (items[i - 1].phase != item.phase) 14.dp else 5.dp))
            val accent = if (item.phase == SlotPhase.COOL_DOWN) Sky else Flame
            val color by animateColorAsState(
                when (item.state) {
                    SegState.CURRENT -> accent
                    SegState.DONE -> accent.copy(alpha = 0.5f)
                    SegState.UPCOMING, SegState.SKIPPED -> Chalk.copy(alpha = 0.16f)
                },
                tween(450),
                label = "segment",
            )
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .then(
                        if (item.state == SegState.SKIPPED) {
                            // A skipped set is a dotted dash — there, but not done.
                            Modifier.drawBehind {
                                val r = size.height / 2
                                var x = r
                                while (x < size.width) {
                                    drawCircle(color.copy(alpha = 0.55f), r * 0.8f, Offset(x, r))
                                    x += r * 3.2f
                                }
                            }
                        } else {
                            Modifier.clip(Capsule).background(color)
                        },
                    ),
            )
        }
    }
}

/**
 * One vertical drag on the live screen, routed by its first movement: **up** lifts the journal, **down**
 * puts the workout away (minimise). Once the journal is out, every drag belongs to it. Shared by the
 * card, the video and the top bar, so a swipe means the same thing wherever it starts.
 */
class LiveDragRouter(
    private val journal: SheetDragState,
    private val minimise: com.calistapp.app.ui.common.DragDismissState,
) {
    private var route = 0

    fun start() {
        route = if (journal.isShown) 1 else 0
        if (route == 1) journal.dragStart()
    }

    /** [dy] = finger movement, down positive. */
    fun drag(dy: Float) {
        if (route == 0) {
            route = if (dy < 0) 1 else 2
            if (route == 1) journal.dragStart() else minimise.dragStart()
        }
        if (route == 1) journal.dragBy(-dy) else minimise.dragBy(dy)
    }

    fun end(velocity: Float) {
        when (route) {
            1 -> journal.dragEnd(-velocity)
            2 -> minimise.dragEnd(velocity)
        }
        route = 0
    }
}

/** Rounded shape shared by the live sheet — a big top radius, so it reads as a card lifting off the video. */
private val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

/**
 * The live-workout card, resting low over the video.
 *
 * **Swipe it up for the journal**: the whole card is the handle (its buttons still work — a drag only
 * takes over once the finger has clearly moved). The journal grows out of the card's top edge and
 * follows the finger 1:1, then a velocity-seeded spring finishes the motion; it can be caught on the way.
 * Tap the grabber to open it too. This replaced an in-place "this exercise" panel the grabber used to
 * reveal — the exercise's details now open by tapping the video itself.
 *
 * The card takes every touch that lands on it, so a tap on its empty parts can't reach the video
 * underneath (it used to open the exercise from anywhere on the card).
 */
@Composable
fun LiveSheet(
    journal: SheetDragState,
    router: LiveDragRouter,
    peek: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    val drag = rememberDraggableState { delta -> router.drag(delta) }
    Column(
        modifier
            .fillMaxWidth()
            // The journal starts from this card's top edge, so the card "becomes" the journal.
            .onSizeChanged { journal.peekPx = it.height.toFloat() }
            .clip(SheetShape)
            .background(OnyxRaised)
            .draggable(
                drag,
                Orientation.Vertical,
                startDragImmediately = journal.progress.isRunning,
                onDragStarted = { router.start() },
                onDragStopped = { v -> router.end(v) },
            )
            .blockTouchesBelow(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Grabber — the visible promise that the card lifts. Tap opens the journal.
        Box(
            Modifier
                .fillMaxWidth()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { journal.open() }
                .semantics { contentDescription = "Open journal" }
                .padding(top = 10.dp, bottom = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(width = 38.dp, height = 5.dp)
                    .clip(Capsule)
                    .background(Chalk.copy(alpha = 0.28f)),
            )
        }

        Column(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = peek,
        )
    }
}

/**
 * The rep-counter row for a work block: −/＋ steppers around a big tappable number (tap to key it in),
 * a target ghost until touched, and a weight pill you can change mid-set.
 */
@Composable
fun RepCounterContent(
    reps: Int,
    target: Int,
    isHold: Boolean,
    touched: Boolean,
    onDelta: (Int) -> Unit,
    onOpenNumpad: () -> Unit,
    weightKg: Double,
    onOpenWeight: () -> Unit,
    /** True when [target] is what you did last time (not the plan) — labels the ghost accordingly. */
    fromHistory: Boolean = false,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RepeatingIconButton(onClick = { onDelta(-1) }, enabled = reps > 0, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.Remove, "One fewer", tint = if (reps > 0) Chalk else Ash, modifier = Modifier.size(26.dp))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onOpenNumpad)) {
            // The number pops on each change — a small, fast scale so a tap lands instead of silently
            // swapping a digit. Kept short (90ms) so rapid taps and a per-second hold don't lag.
            AnimatedContent(
                targetState = reps,
                transitionSpec = {
                    (fadeIn(tween(90)) + scaleIn(initialScale = 0.82f, animationSpec = tween(90))) togetherWith
                        (fadeOut(tween(90)) + scaleOut(targetScale = 1.12f, animationSpec = tween(90)))
                },
                label = "reps",
            ) { r ->
                Text(
                    "$r",
                    style = NumericLarge,
                    color = Flame.copy(alpha = if (touched) 1f else 0.35f),
                )
            }
            Text(
                if (isHold) "seconds" else "reps",
                style = MaterialTheme.typography.labelMedium,
                color = Ash,
            )
            if (!touched) {
                Text(
                    if (fromHistory) "last time $target · tap to log" else "target $target · tap to log",
                    style = MaterialTheme.typography.labelSmall,
                    color = Ash.copy(alpha = 0.8f),
                )
            }
        }
        RepeatingIconButton(onClick = { onDelta(1) }, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.Add, "One more", tint = Chalk, modifier = Modifier.size(26.dp))
        }
    }
    WeightPill(weightKg = weightKg, onClick = onOpenWeight)
}

/** The mid-set load control — "Bodyweight" until you add plates, then "+20 kg". */
@Composable
fun WeightPill(weightKg: Double, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val weighted = weightKg > 0.0
    Row(
        modifier
            .clip(Capsule)
            .background(if (weighted) FlameSoft else Chalk.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(Icons.Filled.FitnessCenter, null, tint = if (weighted) Flame else Ash, modifier = Modifier.size(15.dp))
        Text(
            if (weighted) "+${formatKg(weightKg)} kg" else "Bodyweight",
            style = MaterialTheme.typography.labelLarge,
            color = if (weighted) Flame else Ash,
        )
    }
}

/**
 * The rest state: a "RESTING" badge over a count-up recovery clock (rest is a stopwatch now, never a
 * countdown), and — prominent — what's coming next, so it's never ambiguous whether you're recovering
 * or the next exercise has already begun.
 */
@Composable
fun RestContent(
    elapsedSeconds: Int,
    isWarmup: Boolean,
    upNextName: String?,
    upNextIsNew: Boolean,
) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // The state badge. Its own pill so "resting" reads at a glance, distinct from a work block.
        Box(
            Modifier
                .clip(Capsule)
                .background(Chalk.copy(alpha = 0.08f))
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Text(
                if (isWarmup) "WARM-UP" else "RESTING",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Ash,
            )
        }
        Text(
            formatClock(elapsedSeconds * 1000L),
            style = NumericLarge,
            color = Chalk,
        )
        Text(
            if (isWarmup) "warm up, then start when you're ready" else "recovering — start the next set when ready",
            style = MaterialTheme.typography.labelMedium,
            color = Ash,
            textAlign = TextAlign.Center,
        )
        if (upNextName != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (upNextIsNew) "UP NEXT" else "NEXT SET",
                    style = MaterialTheme.typography.labelSmall,
                    color = Flame,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    upNextName,
                    style = MaterialTheme.typography.titleSmall,
                    color = Chalk,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/**
 * The rest-state prompt to rate the set you just finished. Effort logging used to live only in the
 * Journal, which nobody found; surfacing it here — right after you bank a set, while you rest — is
 * where rating a set is actually natural. Optional and one tap: unrated it invites, rated it shows.
 */
@Composable
fun RateSetChip(effortLabel: String?, onClick: () -> Unit) {
    val rated = effortLabel != null
    Row(
        Modifier
            .clip(Capsule)
            .background(if (rated) FlameSoft else Chalk.copy(alpha = 0.05f))
            .border(1.dp, if (rated) Flame.copy(alpha = 0.35f) else Flame.copy(alpha = 0.55f), Capsule)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(Icons.Filled.Bolt, contentDescription = null, tint = Flame, modifier = Modifier.size(16.dp))
        Text(
            if (rated) "Effort · $effortLabel" else "Rate that set",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (rated) Flame else Chalk,
        )
    }
}

/** The "everything's done" state. */
@Composable
fun AllDoneContent() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("All sets done", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Flame)
        Text(
            "Finish to score and save the workout.",
            style = MaterialTheme.typography.labelMedium,
            color = Ash,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The full-screen "GET READY" lead-in: a big white number that pops in and swells out each second,
 * so the last three seconds before a set actually land instead of ticking by as small text.
 */
@Composable
fun CountdownOverlay(seconds: Int, modifier: Modifier = Modifier) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            "GET READY",
            fontSize = 20.sp,
            letterSpacing = 4.sp,
            fontWeight = FontWeight.Bold,
            color = Chalk.copy(alpha = 0.85f),
        )
        AnimatedContent(
            targetState = seconds,
            transitionSpec = {
                (scaleIn(initialScale = 0.4f, animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow)) +
                    fadeIn(tween(160))) togetherWith
                    (scaleOut(targetScale = 1.5f, animationSpec = tween(240)) + fadeOut(tween(240)))
            },
            label = "countdown",
        ) { n ->
            Text(
                "$n",
                fontSize = 136.sp,
                lineHeight = 140.sp,
                letterSpacing = (-4).sp,
                fontWeight = FontWeight.Black,
                color = Chalk,
            )
        }
    }
}
