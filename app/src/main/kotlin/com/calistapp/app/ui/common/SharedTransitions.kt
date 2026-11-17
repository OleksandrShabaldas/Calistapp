package com.calistapp.app.ui.common

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The app-wide shared-element scope (set once around the NavHost) and the current destination's
 * animated-visibility scope (set per route that takes part). Kept in locals so a card deep inside a
 * list can grow into its page without every layer passing scopes through.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedScope = staticCompositionLocalOf<SharedTransitionScope?> { null }
val LocalNavScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * A surface that grows into (and back out of) the surface on another screen with the same [key]:
 * the bounds travel on a critically damped spring while the old content fades out and the new one
 * fades in, so it reads as one object opening rather than a screen change. A no-op outside a shared
 * scope (previews, other hosts).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedSurface(key: String, corner: Dp = 22.dp): Modifier {
    val shared = LocalSharedScope.current ?: return this
    val nav = LocalNavScope.current ?: return this
    val reduce = rememberReduceMotion()
    return with(shared) {
        this@sharedSurface.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = nav,
            enter = fadeIn(tween(if (reduce) 150 else 220, delayMillis = if (reduce) 0 else 60)),
            exit = fadeOut(tween(140)),
            boundsTransform = if (reduce) BoundsTransform { _, _ -> tween(0) } else BoundsTransform { _, _ -> spring(dampingRatio = 1f, stiffness = 420f) },
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(corner)),
        )
    }
}
