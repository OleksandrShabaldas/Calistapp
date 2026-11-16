package com.calistapp.app.ui.session

import androidx.compose.animation.AnimatedContent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import com.calistapp.app.ui.common.GestureMotion
import com.calistapp.app.ui.common.rememberReduceMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.calistapp.app.session.WatchRejoin
import com.calistapp.app.ui.common.formatClock
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.Capsule
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.Coral
import com.calistapp.app.ui.theme.Flame
import com.calistapp.app.ui.theme.Mint
import com.calistapp.app.ui.theme.NumericMedium
import com.calistapp.app.ui.theme.OnyxBorder
import com.calistapp.app.ui.theme.OnyxFillStrong
import com.calistapp.app.ui.theme.OnyxRaised
import com.calistapp.core.model.HrZone
import com.calistapp.core.model.HrLinkHealth

/** The chip's own fill — the morph starts from exactly this. */
private val ChipFill = Color.Black.copy(alpha = 0.5f)
private val PanelWidth = 268.dp
private val PanelCorner = 20.dp

/** The always-on heart-rate + calorie chip in the corner of the hero. Tapping it opens [LiveHudPanel]. */
@Composable
fun LiveHudChip(
    bpm: Int,
    kcal: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Heart rate is arriving now. When it isn't, the last number is not shown as if it were live. */
    live: Boolean = true,
    /** The phone is bringing the watch back into the workout by itself. */
    reconnecting: Boolean = false,
) {
    Box(
        modifier
            .clip(Capsule)
            .background(ChipFill)
            .border(1.dp, OnyxBorder, Capsule)
            .clickable(onClick = onClick),
    ) {
        LiveHudChipContent(bpm, kcal, live, reconnecting)
    }
}

/** What the chip shows, without its capsule — so the morph can draw the capsule as it grows. */
@Composable
fun LiveHudChipContent(bpm: Int, kcal: Int, live: Boolean, reconnecting: Boolean) {
    Row(
        Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A stale reading used to sit here looking live for minutes after the watch went quiet.
        AnimatedContent(
            targetState = when {
                reconnecting -> HrChipState.RECONNECTING
                live && bpm > 0 -> HrChipState.LIVE
                else -> HrChipState.NO_SIGNAL
            },
            transitionSpec = { (fadeIn(tween(220)) + scaleIn(initialScale = 0.8f)) togetherWith fadeOut(tween(120)) },
            label = "hrChip",
        ) { state ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                when (state) {
                    HrChipState.RECONNECTING -> CircularProgressIndicator(color = Coral, strokeWidth = 1.6.dp, modifier = Modifier.size(13.dp))
                    else -> Icon(Icons.Filled.Favorite, null, tint = if (state == HrChipState.LIVE) Coral else Ash, modifier = Modifier.size(14.dp))
                }
                Text(
                    if (state == HrChipState.LIVE) "$bpm" else "—",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (state == HrChipState.LIVE) Chalk else Ash,
                )
            }
        }
        Icon(Icons.Filled.LocalFireDepartment, null, tint = Flame, modifier = Modifier.size(14.dp))
        Text("$kcal", style = MaterialTheme.typography.labelLarge, color = Chalk)
        Icon(Icons.Filled.KeyboardArrowDown, "Details", tint = Ash, modifier = Modifier.size(14.dp))
    }
}

/**
 * The expanded HUD: everything the chip stands in for — current HR and its zone, a live trace,
 * session average and peak, calories with a burn rate, watch link, and elapsed time. Rendered as a
 * card that animates open from the chip's corner (the caller handles the reveal + dismiss scrim).
 */
@Composable
fun LiveHudPanel(
    bpm: Int,
    avgHr: Int,
    peakHr: Int,
    maxHr: Int,
    recentBpm: List<Int>,
    kcal: Int,
    kcalPerMin: Double,
    elapsedMs: Long,
    receivingHr: Boolean,
    watchUsable: Boolean,
    rejoin: WatchRejoin,
    onReconnect: () -> Unit,
    onClose: () -> Unit,
    /** A chosen heart-rate band; when there is one, its link is shown instead of the watch's. */
    band: com.calistapp.app.data.device.BandState = com.calistapp.app.data.device.BandState.None,
    modifier: Modifier = Modifier,
    /** How continuous the watch's stream is — readings a minute, longest gap, readings backfilled. */
    link: HrLinkHealth = HrLinkHealth(),
    /** Draw its own card. Off inside [LiveHudMorph], which draws the card as it grows from the chip. */
    chrome: Boolean = true,
) {
    val zone = if (bpm > 0 && maxHr > 0) HrZone.forHr(bpm, maxHr) else null
    Column(
        modifier
            .width(PanelWidth)
            .then(
                if (chrome) {
                    Modifier
                        .clip(RoundedCornerShape(PanelCorner))
                        .background(OnyxRaised)
                        .border(1.dp, OnyxBorder, RoundedCornerShape(PanelCorner))
                } else {
                    Modifier
                },
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Favorite, null, tint = Coral, modifier = Modifier.size(16.dp))
            Text("  Heart rate", style = MaterialTheme.typography.labelMedium, color = Ash, modifier = Modifier.weight(1f))
            // Up: it folds back up into the chip it came from. A 40dp target around a 20dp glyph, nudged
            // out so the glyph still lines up with the card's edge.
            Box(
                Modifier
                    .offset(x = 10.dp)
                    .size(40.dp)
                    .clip(Capsule)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.KeyboardArrowUp, "Close", tint = Ash, modifier = Modifier.size(20.dp))
            }
        }

        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (bpm > 0 && receivingHr) "$bpm" else "—", style = NumericMedium, color = if (receivingHr) Chalk else Ash)
            Text("bpm", style = MaterialTheme.typography.labelMedium, color = Ash, modifier = Modifier.padding(bottom = 5.dp))
            if (zone != null) {
                Box(Modifier.weight(1f))
                Text(
                    "Z${zone.ordinal + 1} · ${zone.label}",
                    style = MaterialTheme.typography.labelLarge,
                    color = zoneColor(zone),
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }

        if (recentBpm.size >= 2) {
            Sparkline(
                values = recentBpm,
                color = Coral,
                modifier = Modifier.fillMaxWidth().height(40.dp),
            )
        }

        Row(Modifier.fillMaxWidth()) {
            HudStat("avg", if (avgHr > 0) "$avgHr" else "—", Modifier.weight(1f))
            HudStat("peak", if (peakHr > 0) "$peakHr" else "—", Modifier.weight(1f))
            HudStat("max", if (maxHr > 0) "$maxHr" else "—", Modifier.weight(1f))
        }

        Divider()

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.LocalFireDepartment, null, tint = Flame, modifier = Modifier.size(16.dp))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f).padding(start = 6.dp)) {
                Text("$kcal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Chalk)
                Text("kcal", style = MaterialTheme.typography.labelMedium, color = Ash, modifier = Modifier.padding(bottom = 2.dp))
            }
            Text(
                "${"%.1f".format(kcalPerMin)}/min",
                style = MaterialTheme.typography.labelLarge,
                color = Flame,
            )
        }

        Divider()

        if (band != com.calistapp.app.data.device.BandState.None) {
            BandLinkRow(band)
        } else {
            WatchLinkRow(receivingHr = receivingHr, watchUsable = watchUsable, rejoin = rejoin, onReconnect = onReconnect)
        }

        // The stream's health, so a workout can show whether heart rate arrived continuously instead of
        // that only surfacing afterwards as straight lines in the chart. ~60 a minute is a healthy link.
        // Gone quiet, it says how long ago the last reading was — "gap 1s" (the longest gap between the
        // readings that DID arrive) read as healthy for eight silent minutes.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Signal", style = MaterialTheme.typography.labelLarge, color = Ash, modifier = Modifier.weight(1f))
            val age = link.newestAgeMs
            val quiet = age == null || link.readingsLastMinute == 0 || age > 15_000
            val healthy = !quiet && link.readingsLastMinute >= 45 && link.longestGapMs < 15_000
            Text(
                when {
                    age == null -> "no readings yet"
                    quiet -> "last reading ${agoLabel(age)}"
                    else -> "${link.readingsLastMinute}/min · gap ${link.longestGapMs / 1000}s" +
                        if (link.backfilled > 0) " · +${link.backfilled}" else ""
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (healthy) Chalk else Flame,
            )
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Elapsed", style = MaterialTheme.typography.labelLarge, color = Ash, modifier = Modifier.weight(1f))
            Text(formatClock(elapsedMs), style = MaterialTheme.typography.labelLarge, color = Chalk)
        }
    }
}

/**
 * The chip ⇄ panel morph's state. [progress] 0 = the chip, 1 = the panel; [chipBounds] is where the
 * chip sits (root coordinates), measured by the chip itself.
 */
@Stable
class HudMorphState internal constructor(
    private val scope: CoroutineScope,
    private val flingPx: Float,
    private val reduceMotion: Boolean,
) {
    val progress = Animatable(0f)
    var chipBounds by mutableStateOf<Rect?>(null)
    internal var overlayOrigin by mutableStateOf(Offset.Zero)
    internal var panelHeightPx by mutableFloatStateOf(0f)
    internal val overDrag = Animatable(0f)
    var isOpenTarget by mutableStateOf(false)
        private set
    val isVisible: Boolean get() = isOpenTarget || progress.value > 0f
    private var raw = 0f

    private fun travel(): Float = ((panelHeightPx.takeIf { it > 0f } ?: 900f) - (chipBounds?.height ?: 0f)).coerceAtLeast(1f)

    /** Tapped: no bounce — no force was applied. */
    fun open() { isOpenTarget = true; scope.launch { progress.animateTo(1f, GestureMotion.settle(0.4f)) } }
    fun close() { isOpenTarget = false; scope.launch { progress.animateTo(0f, GestureMotion.settle(0.34f)) } }

    internal fun dragStart() { raw = 0f; scope.launch { progress.stop() } }

    /** Up folds the panel back into the chip 1:1; down past open only stretches a little (elastic). */
    internal fun dragBy(dy: Float) {
        raw += dy
        scope.launch {
            if (raw < 0f) {
                progress.snapTo((1f + raw / travel()).coerceIn(0f, 1f))
                overDrag.snapTo(0f)
            } else {
                progress.snapTo(1f)
                overDrag.snapTo(raw * GestureMotion.Elastic)
            }
        }
    }

    internal fun dragEnd(velocityY: Float) {
        val close = velocityY < -flingPx || (velocityY < flingPx && progress.value < 0.6f)
        isOpenTarget = !close
        scope.launch { overDrag.animateTo(0f, GestureMotion.snapBack(reduceMotion)) }
        scope.launch {
            progress.animateTo(
                if (close) 0f else 1f,
                if (close) GestureMotion.release(reduceMotion) else GestureMotion.snapBack(reduceMotion),
                initialVelocity = velocityY / travel(),
            )
        }
    }
}

@Composable
fun rememberHudMorphState(): HudMorphState {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val reduce = rememberReduceMotion()
    return remember(scope, density, reduce) { HudMorphState(scope, GestureMotion.flingPx(density), reduce) }
}

/** Put on the chip: hides it while the morph is drawn in its place, and records where it is. */
fun Modifier.hudMorphSource(state: HudMorphState): Modifier = this
    .onGloballyPositioned { state.chipBounds = it.boundsInRoot() }
    .graphicsLayer { alpha = if (state.progress.value > 0f || state.isOpenTarget) 0f else 1f }

/**
 * The chip growing into the HUD panel — a container transform: one card whose bounds, corner radius and
 * fill go from the chip's to the panel's, anchored at the chip's top-right corner (so it opens down and
 * to the left, out of the chip), the chip's numbers fading out as the panel's content fades in. Laid
 * out at full size and clipped, so nothing reflows mid-morph. Swipe it up and it folds back into the
 * chip with the finger; tap above it, the chevron, or back to close.
 */
@Composable
fun LiveHudMorph(
    state: HudMorphState,
    chip: @Composable () -> Unit,
    panel: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bounds = state.chipBounds
    if (!state.isVisible || bounds == null) return
    BackHandler(enabled = state.isOpenTarget) { state.close() }
    val density = LocalDensity.current
    val panelWidthPx = with(density) { PanelWidth.toPx() }
    val panelCornerPx = with(density) { PanelCorner.toPx() }
    val borderPx = with(density) { 1.dp.toPx() }
    val drag = rememberDraggableState { state.dragBy(it) }

    Box(modifier.fillMaxSize().onGloballyPositioned { state.overlayOrigin = it.positionInRoot() }) {
        // Scrim, as dark as the panel is open.
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind { drawRect(Color.Black.copy(alpha = 0.5f * state.progress.value.coerceIn(0f, 1f))) }
                .pointerInput(state) { detectTapGestures { state.close() } },
        )
        val chipRect = bounds.translate(-state.overlayOrigin)
        Box(
            Modifier
                .offset {
                    val p = state.progress.value
                    val w = lerpF(chipRect.width, panelWidthPx, p)
                    IntOffset((chipRect.right - w).roundToInt(), (chipRect.top + state.overDrag.value).roundToInt())
                }
                .layout { measurable, _ ->
                    val p = state.progress.value
                    val panelH = state.panelHeightPx.takeIf { it > 0f } ?: chipRect.height
                    val w = lerpF(chipRect.width, panelWidthPx, p).roundToInt().coerceAtLeast(1)
                    val h = lerpF(chipRect.height, panelH, p).roundToInt().coerceAtLeast(1)
                    val placeable = measurable.measure(Constraints.fixed(w, h))
                    layout(w, h) { placeable.place(0, 0) }
                }
                .drawBehind {
                    val p = state.progress.value.coerceIn(0f, 1f)
                    val corner = CornerRadius(lerpF(size.height / 2f, panelCornerPx, p).coerceAtMost(size.height / 2f))
                    drawRoundRect(lerp(ChipFill, OnyxRaised, p), cornerRadius = corner)
                    drawRoundRect(OnyxBorder, cornerRadius = corner, style = Stroke(borderPx))
                }
                .graphicsLayer {
                    val p = state.progress.value.coerceIn(0f, 1f)
                    shape = RoundedCornerShape(lerpF(size.height / 2f, panelCornerPx, p).coerceAtMost(size.height / 2f))
                    clip = true
                }
                .draggable(
                    drag,
                    Orientation.Vertical,
                    startDragImmediately = state.progress.isRunning,
                    onDragStarted = { state.dragStart() },
                    onDragStopped = { v -> state.dragEnd(v) },
                ),
        ) {
            // The panel, at its real size, pinned to the top-right and revealed by the growing card.
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .wrapContentSize(Alignment.TopEnd, unbounded = true)
                    .onSizeChanged { state.panelHeightPx = it.height.toFloat() }
                    .graphicsLayer { alpha = ((state.progress.value - 0.3f) / 0.55f).coerceIn(0f, 1f) },
            ) { panel() }
            // The chip's own numbers, where they were, fading out as the card grows.
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .wrapContentSize(Alignment.TopEnd, unbounded = true)
                    .graphicsLayer { alpha = (1f - state.progress.value / 0.25f).coerceIn(0f, 1f) },
            ) { chip() }
        }
    }
}

private fun lerpF(a: Float, b: Float, t: Float): Float = a + (b - a) * t

private enum class HrChipState { LIVE, NO_SIGNAL, RECONNECTING }

private fun agoLabel(ms: Long): String {
    val s = ms / 1000
    return when {
        s < 60 -> "${s}s ago"
        s < 3600 -> "${s / 60} min ago"
        else -> "${s / 3600} h ago"
    }
}

/** What the watch line shows — the rejoin state folded together with whether heart rate is flowing. */
private sealed interface LinkLine {
    data object Streaming : LinkLine
    data class Quiet(val watchUsable: Boolean) : LinkLine
    data class Working(val auto: Boolean) : LinkLine
    data object Back : LinkLine
    data class Failed(val reason: WatchRejoin.Reason) : LinkLine
}

/**
 * The watch line of the panel: what the link is doing and the Reconnect control, which now shows its
 * own progress and outcome — it used to give no feedback at all. Each state swaps in with a short
 * vertical slide, so a change reads as the result of the tap.
 */
@Composable
private fun WatchLinkRow(receivingHr: Boolean, watchUsable: Boolean, rejoin: WatchRejoin, onReconnect: () -> Unit) {
    val line = when (rejoin) {
        is WatchRejoin.Working -> LinkLine.Working(rejoin.auto)
        WatchRejoin.Back -> LinkLine.Back
        is WatchRejoin.Failed -> if (receivingHr) LinkLine.Streaming else LinkLine.Failed(rejoin.reason)
        WatchRejoin.Idle -> if (receivingHr) LinkLine.Streaming else LinkLine.Quiet(watchUsable)
    }
    AnimatedContent(
        targetState = line,
        transitionSpec = {
            (fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 3 }) togetherWith
                (fadeOut(tween(140)) + slideOutVertically(tween(180)) { -it / 3 })
        },
        label = "watchLink",
    ) { state ->
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                when (state) {
                    is LinkLine.Working -> CircularProgressIndicator(color = Flame, strokeWidth = 1.8.dp, modifier = Modifier.size(15.dp))
                    LinkLine.Back -> Icon(Icons.Filled.CheckCircle, null, tint = Mint, modifier = Modifier.size(16.dp))
                    is LinkLine.Failed -> Icon(Icons.Filled.ErrorOutline, null, tint = Flame, modifier = Modifier.size(16.dp))
                    LinkLine.Streaming -> Icon(Icons.Filled.Bolt, null, tint = Mint, modifier = Modifier.size(16.dp))
                    is LinkLine.Quiet -> Icon(Icons.Filled.Bolt, null, tint = Ash, modifier = Modifier.size(16.dp))
                }
                Text(
                    "  " + when (state) {
                        is LinkLine.Working -> if (state.auto) "Watch went quiet — reconnecting…" else "Reconnecting…"
                        LinkLine.Back -> "Heart rate back"
                        is LinkLine.Failed -> "Couldn't reconnect"
                        LinkLine.Streaming -> "Watch streaming"
                        is LinkLine.Quiet -> if (state.watchUsable) "No heart rate" else "Watch offline"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (state == LinkLine.Back) Mint else Chalk,
                    modifier = Modifier.weight(1f),
                )
                if (state is LinkLine.Failed || state is LinkLine.Quiet) {
                    Text(
                        if (state is LinkLine.Failed) "Retry" else "Reconnect",
                        style = MaterialTheme.typography.labelLarge,
                        color = Flame,
                        modifier = Modifier.clip(Capsule).clickable(onClick = onReconnect).padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            if (state is LinkLine.Failed) {
                Text(
                    when (state.reason) {
                        WatchRejoin.Reason.NO_WATCH -> "No watch is connected to this phone."
                        WatchRejoin.Reason.NO_ANSWER -> "The watch didn't answer. Open Calistapp on it."
                        WatchRejoin.Reason.NO_HEART_RATE -> "The watch is back, but no heart rate. Is it snug on your wrist?"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = Ash,
                )
            }
        }
    }
}

/**
 * The band's line in the panel. No Reconnect button: the band reconnects by itself as soon as it's back
 * in range, and the line says when it's doing that.
 */
@Composable
private fun BandLinkRow(band: com.calistapp.app.data.device.BandState) {
    AnimatedContent(
        targetState = band,
        transitionSpec = {
            (fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 3 }) togetherWith
                (fadeOut(tween(140)) + slideOutVertically(tween(180)) { -it / 3 })
        },
        contentKey = { it::class },
        label = "bandLink",
    ) { state ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            when (state) {
                is com.calistapp.app.data.device.BandState.Streaming ->
                    Icon(Icons.Filled.Bolt, null, tint = if (state.contact) Mint else Coral, modifier = Modifier.size(16.dp))
                is com.calistapp.app.data.device.BandState.Connecting,
                is com.calistapp.app.data.device.BandState.Reconnecting,
                -> CircularProgressIndicator(color = Flame, strokeWidth = 1.8.dp, modifier = Modifier.size(15.dp))
                else -> Icon(Icons.Filled.ErrorOutline, null, tint = Flame, modifier = Modifier.size(16.dp))
            }
            val name = state.bandName.orEmpty()
            Text(
                "  " + when (state) {
                    is com.calistapp.app.data.device.BandState.Streaming -> if (state.contact) "$name streaming" else "$name — no skin contact"
                    is com.calistapp.app.data.device.BandState.Connecting -> "Connecting to $name…"
                    is com.calistapp.app.data.device.BandState.Reconnecting -> "$name — reconnecting…"
                    is com.calistapp.app.data.device.BandState.NeedsPermission -> "$name needs Bluetooth permission"
                    is com.calistapp.app.data.device.BandState.BluetoothOff -> "Bluetooth is off"
                    com.calistapp.app.data.device.BandState.None -> ""
                },
                style = MaterialTheme.typography.labelLarge,
                color = Chalk,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun HudStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Chalk)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Ash)
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(OnyxFillStrong))
}

@Composable
private fun Sparkline(values: List<Int>, color: Color, modifier: Modifier = Modifier) {
    val lo = (values.min() - 2).coerceAtLeast(0)
    val hi = (values.max() + 2)
    val range = (hi - lo).coerceAtLeast(1).toFloat()
    Canvas(modifier) {
        val stepX = if (values.size <= 1) 0f else size.width / (values.size - 1)
        var prev: Offset? = null
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = size.height - (v - lo) / range * size.height
            val p = Offset(x, y)
            prev?.let {
                drawLine(color = color, start = it, end = p, strokeWidth = 3f, cap = StrokeCap.Round)
            }
            prev = p
        }
    }
}

private fun zoneColor(zone: HrZone): Color = when (zone) {
    HrZone.ZONE1, HrZone.ZONE2 -> Ash
    HrZone.ZONE3 -> Chalk
    HrZone.ZONE4 -> Flame
    HrZone.ZONE5 -> Coral
}
