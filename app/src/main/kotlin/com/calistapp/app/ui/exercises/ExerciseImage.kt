package com.calistapp.app.ui.exercises

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlin.math.absoluteValue

/** How long each frame is held before crossfading to the next. */
private const val FRAME_MS = 750L
private const val CROSSFADE_MS = 260

/**
 * Renders an exercise as a **moving** demonstration rather than a still thumbnail.
 *
 * free-exercise-db ships two frames per exercise — the start and finish positions of the movement —
 * so cycling them at roughly rep tempo actually shows how the exercise is performed. Animated GIFs
 * work too and are handled by the same path (the app's Coil loader has the GIF decoder installed);
 * a single-frame entry simply renders static.
 *
 * All frames are composed at once and revealed by animating alpha, rather than swapping the model on
 * one `AsyncImage`. That keeps every frame decoded and in memory, so the loop is a true crossfade
 * instead of a reload flicker on each cycle.
 */
@Composable
fun ExerciseImage(
    urls: List<String>,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    /**
     * Offsets this instance's animation phase. Pass something stable per item (an id) so a scrolling
     * gallery doesn't flip every card in unison, which reads as a distracting strobe.
     */
    phaseKey: Any? = null,
) {
    val frames = remember(urls) { urls.filter { it.isNotBlank() } }

    if (frames.isEmpty()) {
        ExerciseImagePlaceholder(contentDescription, modifier)
        return
    }

    var index by remember(frames) { mutableIntStateOf(0) }
    val shouldAnimate = animate && frames.size > 1

    if (shouldAnimate) {
        LaunchedEffect(frames, phaseKey) {
            // Stagger the start so neighbouring cards fall out of step with each other.
            val offset = phaseKey?.let { (it.hashCode().absoluteValue % FRAME_MS).toLong() } ?: 0L
            delay(offset)
            while (true) {
                delay(FRAME_MS)
                index = (index + 1) % frames.size
            }
        }
    }

    Box(modifier) {
        frames.forEachIndexed { i, url ->
            val target = if (!shouldAnimate) { if (i == 0) 1f else 0f } else if (i == index) 1f else 0f
            val alpha by animateFloatAsState(
                targetValue = target,
                animationSpec = tween(CROSSFADE_MS),
                label = "frame$i",
            )
            // Skip fully transparent frames' draw work, but keep them composed so they stay decoded.
            AsyncImage(
                model = url,
                contentDescription = if (i == 0) contentDescription else null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(alpha),
            )
        }
    }
}

/** Single-image convenience for the places that only ever have one URL. */
@Composable
fun ExerciseImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) = ExerciseImage(
    urls = listOfNotNull(url?.takeIf { it.isNotBlank() }),
    contentDescription = contentDescription,
    modifier = modifier,
    animate = false,
)

/**
 * The full-size demonstration on the exercise detail screen, where learning the movement is the
 * whole point. Same animation as [ExerciseImage] plus the controls that matter when you're studying
 * a movement: tap to pause on a position, and a dot per frame so it's clear the loop is a
 * start→finish pair and not a glitch.
 */
@Composable
fun ExerciseDemo(
    urls: List<String>,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val frames = remember(urls) { urls.filter { it.isNotBlank() } }
    var playing by remember(frames) { mutableStateOf(true) }

    Box(modifier) {
        ExerciseImage(
            urls = frames,
            contentDescription = contentDescription,
            animate = playing,
            modifier = Modifier.fillMaxSize(),
        )

        if (frames.size > 1) {
            // Tap anywhere to freeze the movement mid-position.
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable { playing = !playing },
            )
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f),
                modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp),
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playing) "Pause demonstration" else "Play demonstration",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        if (playing) "${frames.size} positions" else "Paused",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

/**
 * Shown when an exercise has no artwork at all — a fair number of free-exercise-db entries and any
 * exercise you add yourself. Names the exercise rather than showing a bare generic icon, so the tile
 * still carries information.
 */
@Composable
private fun ExerciseImagePlaceholder(
    label: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(4.dp),
        ) {
            Icon(
                Icons.Filled.FitnessCenter,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            if (!label.isNullOrBlank()) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
