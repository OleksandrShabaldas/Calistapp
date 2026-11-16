package com.calistapp.app.ui.common

import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationEndReason
import androidx.compose.animation.core.SpringSpec
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.calistapp.app.ui.theme.Capsule
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.OnyxRaised
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/*
 * The app's one bottom sheet, built to the gesture rules (GestureMotion.kt; .claude/skills/gestures).
 *
 * Material3 1.3's ModalBottomSheet tracks the finger but settles every release with a fixed 300 ms
 * tween — every fling lands identically, a gentle flick doesn't dismiss, and the bounce rules can't be
 * applied. This one: tracks 1:1 (snapTo, no spring under the finger), rubber-bands past fully open,
 * decides on distance OR velocity, finishes with a spring seeded by the release velocity (a little
 * bounce after a drag, none after a tap), can be caught mid-flight, and fades its scrim from the same
 * progress value so the two never drift apart. It can also be driven by a drag that starts somewhere
 * else — the live workout card swipes the journal up this way.
 */

private val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

/** Position of one gesture-driven sheet. Create with [rememberSheetDragState]. */
@Stable
class SheetDragState internal constructor(
    private val scope: CoroutineScope,
    private val flingPx: Float,
    private val reduceMotion: Boolean,
    private val fallbackHeightPx: Float,
) {
    /** 0 = hidden below the screen, 1 = open; a little above 1 while over-dragged (rubber-banded). */
    val progress = Animatable(0f)

    /** [progress] clamped to 0..1 — what linked values (scrim, the screen behind) should follow. */
    val fraction: Float get() = progress.value.coerceIn(0f, 1f)

    /** True from the moment it starts opening until a close has finished. */
    var isShown by mutableStateOf(false)
        private set
    var isDragging by mutableStateOf(false)
        private set

    /** Where it's heading (or resting): used by the open/close toggles. */
    var isOpenTarget by mutableStateOf(false)
        private set

    internal var heightPx by mutableFloatStateOf(0f)

    /**
     * How much of the sheet's top sits where the sheet "lives" while closed — the live workout card's
     * height, so the journal grows out of the card's top edge instead of rising from below the screen.
     * The sheet fades in over the first few percent of travel so nothing shows at rest.
     */
    var peekPx by mutableFloatStateOf(0f)
    internal var onClosed: (() -> Unit)? = null
    private var raw = 0f
    private var startedOpen = false
    private val height get() = heightPx.takeIf { it > 0f } ?: fallbackHeightPx

    /** Finger travel from closed to open. */
    private val travel get() = (height - peekPx).coerceAtLeast(height * 0.25f)

    /** A finger went down on something that moves the sheet. Catches it mid-animation. */
    fun dragStart() {
        isShown = true
        isDragging = true
        startedOpen = isOpenTarget
        raw = progress.value.coerceAtMost(1f) * travel
        scope.launch { progress.stop() }
    }

    /** The finger moved [upPx] upwards (negative = down). 1:1 below fully open, elastic above it. */
    fun dragBy(upPx: Float) {
        if (!isDragging) dragStart()
        raw += upPx
        val p = raw / travel
        val shown = if (p > 1f) 1f + (p - 1f) * GestureMotion.Elastic else p.coerceAtLeast(0f)
        scope.launch { progress.snapTo(shown) }
    }

    /** The finger lifted, moving [upVelocity] px/s upwards. Distance OR velocity decides. */
    fun dragEnd(upVelocity: Float) {
        if (!isDragging) return
        isDragging = false
        val open = when {
            upVelocity > flingPx -> true
            upVelocity < -flingPx -> false
            // From closed it only has to come up about a third; from open, be pulled down 40% to go.
            startedOpen -> progress.value > 0.6f
            else -> progress.value > 0.35f
        }
        val spec: SpringSpec<Float> = if (open == startedOpen) GestureMotion.snapBack(reduceMotion) else GestureMotion.release(reduceMotion)
        animate(open, spec, upVelocity / travel)
    }

    /** Open by a tap — no bounce: no force was applied. */
    fun open() {
        isShown = true
        animate(true, GestureMotion.settle(), 0f)
    }

    /** Close by a tap / back / scrim; [then] runs once it's fully down (instead of the sheet's onClosed). */
    fun close(then: (() -> Unit)? = null) = animate(false, GestureMotion.settle(0.36f), 0f, then)

    private fun animate(open: Boolean, spec: SpringSpec<Float>, velocity: Float, then: (() -> Unit)? = null) {
        isOpenTarget = open
        scope.launch {
            val result = progress.animateTo(if (open) 1f else 0f, spec, initialVelocity = velocity)
            if (!open && result.endReason == AnimationEndReason.Finished) {
                isShown = false
                (then ?: onClosed)?.invoke()
            }
        }
    }

    /** Nested scrolling: a scrolled-to-top list hands its downward drag to the sheet, and takes it back. */
    internal val nestedScroll = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            // Finger going up while the sheet is pulled down: raise the sheet before scrolling content.
            if (source == NestedScrollSource.UserInput && available.y < 0 && isDragging && progress.value < 1f) {
                dragBy(-available.y)
                return Offset(0f, available.y)
            }
            return Offset.Zero
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            // Content is at its top and the finger keeps pulling down: the sheet follows the finger.
            if (source == NestedScrollSource.UserInput && available.y > 0) {
                dragBy(-available.y)
                return Offset(0f, available.y)
            }
            return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (isDragging) {
                dragEnd(-available.y)
                return available
            }
            return Velocity.Zero
        }
    }
}

@Composable
fun rememberSheetDragState(): SheetDragState {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val reduce = rememberReduceMotion()
    return remember(scope, density, reduce) {
        SheetDragState(scope, GestureMotion.flingPx(density), reduce, with(density) { 560.dp.toPx() })
    }
}

/**
 * The sheet and its scrim, filling the space it's given (the whole screen). Composes nothing while
 * [state] isn't shown. [handleBack] makes the system back close it — off inside a dialog window, which
 * routes back itself.
 */
@Composable
fun GestureSheetLayout(
    state: SheetDragState,
    onClosed: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = OnyxRaised,
    background: Brush? = null,
    scrimAlpha: Float = 0.6f,
    showHandle: Boolean = true,
    handleBack: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val closed by rememberUpdatedState(onClosed)
    SideEffect { state.onClosed = { closed() } }
    if (!state.isShown) return
    BackHandler(enabled = handleBack && state.isOpenTarget) { state.close() }

    val drag = rememberDraggableState { delta -> state.dragBy(-delta) }
    val dragModifier = Modifier.draggable(
        drag,
        Orientation.Vertical,
        startDragImmediately = state.progress.isRunning,
        onDragStarted = { state.dragStart() },
        onDragStopped = { v -> state.dragEnd(-v) },
    )

    Box(modifier.fillMaxSize()) {
        // Scrim: as faded as the sheet is open, from the same value. Tap to close; drag it to move the
        // sheet (so a sheet on its way down can be caught from anywhere).
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind { drawRect(Color.Black.copy(alpha = scrimAlpha * state.fraction)) }
                .pointerInput(state) { detectTapGestures { state.close() } }
                .then(dragModifier),
        )
        Box(Modifier.fillMaxSize().statusBarsPadding().padding(top = 24.dp), contentAlignment = Alignment.BottomCenter) {
            val bleed = containerColor
            Column(
                Modifier
                    .fillMaxWidth()
                    .onSizeChanged { state.heightPx = it.height.toFloat() }
                    .graphicsLayer {
                        val p = state.progress.value
                        val peek = state.peekPx.coerceAtMost(size.height * 0.75f)
                        val travel = size.height - peek
                        // Hidden → open slides by its travel; over-drag lifts it a little more.
                        translationY = (1f - min(p, 1f)) * travel - max(p - 1f, 0f) * travel
                        // Growing out of a card: invisible at rest, opaque within the first ~8%.
                        alpha = if (peek > 0f) (p / 0.08f).coerceIn(0f, 1f) else 1f
                    }
                    // What shows under the sheet while it's lifted past open by an over-drag or a bounce.
                    .drawBehind { drawRect(bleed, topLeft = Offset(0f, size.height - 1f), size = Size(size.width, size.height * 0.4f)) }
                    .then(if (background != null) Modifier.background(background, SheetShape) else Modifier.background(containerColor, SheetShape))
                    .clip(SheetShape)
                    .nestedScroll(state.nestedScroll)
                    .then(dragModifier)
                    .blockTouchesBelow()
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                if (showHandle) SheetHandle(state.fraction)
                content()
            }
        }
    }
}

@Composable
private fun SheetHandle(progress: Float) {
    // Full-width grab area (≥44dp tall), pill in the middle; the pill brightens as the sheet opens.
    Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(width = 38.dp, height = 5.dp)
                .clip(Capsule)
                .background(Chalk.copy(alpha = 0.18f + 0.14f * progress)),
        )
    }
}

/** Handed to a modal sheet's content: close the sheet, then act. */
class GestureSheetScope internal constructor(
    private val closeThen: ((() -> Unit)) -> Unit,
) {
    /**
     * Slide the sheet away, then run [then] — which must take the sheet out of composition (it's the
     * same handler that used to hide it instantly). Lets a choice register, then the sheet leave.
     */
    fun dismiss(then: () -> Unit) = closeThen(then)
}

/**
 * Drop-in for Material3's ModalBottomSheet, in its own window above everything. Opens itself when it
 * enters composition; [onDismissRequest] fires after a swipe/scrim/back close has finished animating.
 */
@Composable
fun GestureModalSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = OnyxRaised,
    background: Brush? = null,
    scrimAlpha: Float = 0.6f,
    showHandle: Boolean = true,
    content: @Composable ColumnScope.(sheet: GestureSheetScope) -> Unit,
) {
    val state = rememberSheetDragState()
    var finished by remember { mutableStateOf(false) }
    if (finished) return
    val dismiss by rememberUpdatedState(onDismissRequest)
    androidx.compose.runtime.LaunchedEffect(state) { state.open() }

    Dialog(
        onDismissRequest = { state.close() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.apply {
                // Our scrim and slide replace the platform dim and dialog animation.
                setDimAmount(0f)
                clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                setWindowAnimations(0)
                setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
                @Suppress("DEPRECATION")
                setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            }
        }
        GestureSheetLayout(
            state = state,
            onClosed = { finished = true; dismiss() },
            modifier = modifier,
            containerColor = containerColor,
            background = background,
            scrimAlpha = scrimAlpha,
            showHandle = showHandle,
            handleBack = false,
        ) {
            val sheet = remember(state) { GestureSheetScope { then -> state.close { finished = true; then() } } }
            content(sheet)
        }
    }
}
