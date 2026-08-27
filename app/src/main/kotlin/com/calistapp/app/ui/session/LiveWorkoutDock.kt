package com.calistapp.app.ui.session

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calistapp.app.ui.common.RepeatingIconButton
import com.calistapp.app.ui.common.formatClock
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.Capsule
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.Flame
import com.calistapp.app.ui.theme.FlameSoft
import com.calistapp.app.ui.theme.NumericLarge
import com.calistapp.app.ui.theme.OnyxRaised
import com.calistapp.core.model.formatKg
import kotlinx.coroutines.launch

/** State of one segment in the top progress bar. */
enum class SegState { DONE, CURRENT, UPCOMING }

/** The story-style progress dashes: one per exercise in the current block/round. */
@Composable
fun SegmentBar(states: List<SegState>, modifier: Modifier = Modifier) {
    if (states.isEmpty()) return
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        states.forEach { s ->
            val color = when (s) {
                SegState.CURRENT -> Flame
                SegState.DONE -> Flame.copy(alpha = 0.5f)
                SegState.UPCOMING -> Chalk.copy(alpha = 0.16f)
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(Capsule)
                    .background(color),
            )
        }
    }
}

/** Rounded shape shared by the live sheet — a big top radius, so it reads as a card lifting off the video. */
private val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

/** How far the sheet can be dragged up to reveal the "this exercise" detail. */
private val RevealMax = 340.dp

/**
 * The draggable live-workout sheet.
 *
 * One card, not a stack of modals: it rests low (so most of the video shows), and its grabber drags
 * up **in place** to reveal [reveal] — the same "this exercise" detail that used to open as a separate
 * sheet — sliding the [peek] controls down with it. [expand] (0 = resting, 1 = open) is owned by the
 * caller so the video behind can fade as the card rises. Drag settles to the nearest end with a spring.
 */
@Composable
fun LiveSheet(
    expand: Animatable<Float, AnimationVector1D>,
    canReveal: Boolean,
    reveal: @Composable ColumnScope.() -> Unit,
    peek: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val revealMaxPx = with(density) { RevealMax.toPx() }
    val settle = spring<Float>(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)

    fun animateTo(target: Float) = scope.launch { expand.animateTo(target, settle) }

    Column(
        modifier
            .fillMaxWidth()
            .clip(SheetShape)
            .background(OnyxRaised),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Grabber — drag it up/down, or tap to toggle. The one control that opens the detail.
        Box(
            Modifier
                .fillMaxWidth()
                .then(
                    if (canReveal) {
                        Modifier
                            .clickable { animateTo(if (expand.value > 0.5f) 0f else 1f) }
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onVerticalDrag = { change, dy ->
                                        change.consume()
                                        val next = (expand.value - dy / revealMaxPx).coerceIn(0f, 1f)
                                        scope.launch { expand.snapTo(next) }
                                    },
                                    onDragEnd = { animateTo(if (expand.value > 0.32f) 1f else 0f) },
                                    onDragCancel = { animateTo(if (expand.value > 0.32f) 1f else 0f) },
                                )
                            }
                    } else {
                        Modifier
                    },
                )
                .padding(top = 10.dp, bottom = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(width = 38.dp, height = 5.dp)
                    .clip(Capsule)
                    .background(Chalk.copy(alpha = 0.28f + 0.22f * expand.value)),
            )
        }

        // The reveal: its height grows with the drag, pushing the peek controls down. Content is laid
        // out at full height and clipped, so nothing reflows as it opens — only the window onto it.
        if (canReveal) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(RevealMax * expand.value)
                    .clipToBounds(),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .height(RevealMax)
                        .verticalScroll(rememberScrollState())
                        .graphicsLayer { alpha = expand.value }
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    content = reveal,
                )
            }
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
                    "target $target · tap to log",
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
