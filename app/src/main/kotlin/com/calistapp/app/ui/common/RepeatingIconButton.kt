package com.calistapp.app.ui.common

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

/**
 * An icon button that keeps firing while it's held down, accelerating as it goes.
 *
 * Logging a set of thirty push-ups one tap at a time is thirty taps, and the counter is the thing
 * you're using mid-set with a heart rate of 160. Holding is how every stepper on a phone has worked
 * for twenty years; the app just didn't do it.
 */
@Composable
fun RepeatingIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val currentOnClick by rememberUpdatedState(onClick)

    LaunchedEffect(pressed, enabled) {
        if (!pressed || !enabled) return@LaunchedEffect
        // The first fire comes from the click itself, so wait out the hold threshold before
        // deciding this is a hold rather than a tap.
        delay(HOLD_THRESHOLD_MS)
        var interval = INITIAL_INTERVAL_MS
        while (true) {
            currentOnClick()
            delay(interval)
            interval = (interval * DECAY).toLong().coerceAtLeast(MIN_INTERVAL_MS)
        }
    }

    IconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        interactionSource = interaction,
        content = content,
    )
}

private const val HOLD_THRESHOLD_MS = 400L
private const val INITIAL_INTERVAL_MS = 140L
private const val MIN_INTERVAL_MS = 40L
private const val DECAY = 0.82
