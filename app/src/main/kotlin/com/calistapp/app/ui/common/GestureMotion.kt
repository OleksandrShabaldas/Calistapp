package com.calistapp.app.ui.common

import android.provider.Settings
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.PI

/**
 * The motion rules every gesture in the app shares — see `.claude/skills/gestures/SKILL.md` (and
 * `compose.md` beside it) for the why. In short:
 *
 * - **Track** phase: while the finger is down the element *is* the finger — `snapTo`, never a spring.
 * - **Release** phase: a spring seeded with the finger's velocity ([release]). A small bounce, because
 *   a drag applied force.
 * - The same element moved by a **tap** gets zero bounce ([settle]) — no force was applied.
 * - A gesture that doesn't pass its threshold returns **fast** ([snapBack]).
 * - Past a boundary the element moves a fraction of the finger ([elastic], ≈0.2), never stops dead.
 * - Dismiss on distance **or** velocity ([FlingVelocityDp]).
 *
 * Springs are expressed like Apple's/Motion's `spring(duration, bounce)`: damping ratio = 1 − bounce,
 * stiffness from the perceptual duration (mass 1). Bounce scales inversely with element size — the
 * defaults suit sheets and cards; small elements can take more.
 */
object GestureMotion {

    /** A flick faster than this (dp/s) decides the gesture on its own, however short the travel. */
    const val FlingVelocityDp = 600f

    /** How much of the finger's travel an element follows once it's past its boundary. */
    const val Elastic = 0.2f

    fun <T> spring(durationS: Float, bounce: Float): SpringSpec<T> {
        val omega = 2f * PI.toFloat() / durationS
        return spring(dampingRatio = (1f - bounce).coerceIn(0.05f, 1f), stiffness = omega * omega)
    }

    /** The end of a drag: seeded with the release velocity by the caller, a little bounce. */
    fun <T> release(reduceMotion: Boolean = false, bounce: Float = 0.15f, durationS: Float = 0.5f): SpringSpec<T> =
        spring(durationS, if (reduceMotion) 0f else bounce)

    /** Opened or closed by a tap / button / back press: no bounce. */
    fun <T> settle(durationS: Float = 0.42f): SpringSpec<T> = spring(durationS, 0f)

    /** Didn't pass the threshold — go back quickly, as if the UI agrees rather than argues. */
    fun <T> snapBack(reduceMotion: Boolean = false): SpringSpec<T> = spring(0.32f, if (reduceMotion) 0f else 0.1f)

    /**
     * Rubber-band [raw] (finger travel) into [min]..[max]: inside, 1:1; past either end, only [factor] of
     * the overshoot. Feed it the *raw* accumulated travel, not the last output, so reversing direction
     * past the edge comes back exactly the way it went.
     */
    fun elastic(raw: Float, min: Float, max: Float, factor: Float = Elastic): Float = when {
        raw < min -> min + (raw - min) * factor
        raw > max -> max + (raw - max) * factor
        else -> raw
    }

    fun flingPx(density: Density): Float = with(density) { FlingVelocityDp.dp.toPx() }
}

/**
 * True when the system's animator duration scale is 0 ("Remove animations" in accessibility settings).
 * Tracking stays — the user produces that motion — but bounce and decorative motion around a gesture go.
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
            .getOrDefault(false)
    }
}

/**
 * Takes the hit for a surface without consuming anything, so taps on its empty parts stop here instead
 * of falling through to whatever is drawn underneath. Compose hit-tests overlapping siblings top-down
 * but skips a sibling with no pointer input at all — which is how a tap on the live workout card's
 * background opened the exercise behind it.
 */
fun Modifier.blockTouchesBelow(): Modifier = pointerInput(Unit) {
    awaitEachGesture { awaitFirstDown(requireUnconsumed = false) }
}
