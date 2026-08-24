package com.calistapp.app.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.calistapp.app.ui.common.RepeatingIconButton
import com.calistapp.app.ui.common.formatClock
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.Capsule
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.Flame
import com.calistapp.app.ui.theme.FlameSoft
import com.calistapp.app.ui.theme.NumericLarge
import com.calistapp.app.ui.theme.OnyxBorder
import com.calistapp.app.ui.theme.OnyxFillStrong
import com.calistapp.core.model.formatKg

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

/**
 * The docked rep counter for a work block: a grabber that opens the "this exercise" panel, the
 * −/＋ steppers around a big tappable number (tap to key it in), a target ghost until touched, and a
 * weight pill you can change mid-set.
 */
@Composable
fun RepCounterDock(
    reps: Int,
    target: Int,
    isHold: Boolean,
    touched: Boolean,
    onDelta: (Int) -> Unit,
    onOpenNumpad: () -> Unit,
    weightKg: Double,
    onOpenWeight: () -> Unit,
    onSwipeUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
            .background(OnyxFillStrong)
            .padding(bottom = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Grabber — tap or swipe up to open the exercise's detail panel.
        Box(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onSwipeUp)
                .pointerInput(Unit) {
                    var acc = 0f
                    detectVerticalDragGestures(
                        onDragStart = { acc = 0f },
                        onVerticalDrag = { change, delta -> acc += delta; change.consume() },
                        onDragEnd = { if (acc < -40f) onSwipeUp() },
                    )
                }
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(width = 34.dp, height = 4.dp).clip(Capsule).background(Chalk.copy(alpha = 0.3f)))
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RepeatingIconButton(onClick = { onDelta(-1) }, enabled = reps > 0, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Remove, "One fewer", tint = if (reps > 0) Chalk else Ash, modifier = Modifier.size(26.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onOpenNumpad)) {
                Text(
                    "$reps",
                    style = NumericLarge,
                    color = Flame.copy(alpha = if (touched) 1f else 0.35f),
                )
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

        WeightPill(weightKg = weightKg, onClick = onOpenWeight, modifier = Modifier.padding(top = 12.dp))
    }
}

/** The mid-set load control — "Bodyweight" until you add plates, then "+20 kg". */
@Composable
fun WeightPill(weightKg: Double, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val weighted = weightKg > 0.0
    Row(
        modifier
            .clip(Capsule)
            .background(if (weighted) FlameSoft else OnyxFillStrong)
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

/** The docked rest state: a count-up recovery clock, a status line, and what's next. */
@Composable
fun RestDock(
    elapsedSeconds: Int,
    statusText: String,
    upNextText: String?,
    reached: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(OnyxFillStrong)
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            formatClock(elapsedSeconds * 1000L),
            style = NumericLarge,
            color = if (reached) Flame else Chalk,
        )
        Text(statusText, style = MaterialTheme.typography.labelMedium, color = Ash, textAlign = TextAlign.Center)
        if (upNextText != null) {
            Text(upNextText, style = MaterialTheme.typography.labelSmall, color = Ash.copy(alpha = 0.8f), textAlign = TextAlign.Center)
        }
    }
}

/** The docked "everything's done" state. */
@Composable
fun AllDoneDock(modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(OnyxFillStrong)
            .padding(vertical = 20.dp),
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
