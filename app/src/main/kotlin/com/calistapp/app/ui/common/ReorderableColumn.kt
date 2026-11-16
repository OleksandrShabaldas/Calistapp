package com.calistapp.app.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Minimal drag-to-reorder for a `LazyColumn`, with no third-party dependency.
 *
 * Hold a row's drag handle and it follows your finger 1:1; when its midpoint passes a neighbour, [onMove]
 * swaps the two (give the rows `Modifier.animateItem()` so the neighbour slides out of the way). Past
 * the first or last row it only follows a fraction of the finger (rubber band) instead of sailing off
 * the list. Let go and it springs into its slot from wherever it was — it used to jump there. A tick on
 * pick-up and on each swap says it took. Kept small — a workout plan is a handful of rows, not
 * thousands, so it skips the edge auto-scroll a general reorder library carries.
 *
 * [onMove] is given (from, to) list indices and should reorder the backing list; the state re-reads
 * the new layout on the next frame, so the dragged row stays under the finger across the swap.
 */
class ReorderState(
    val listState: LazyListState,
    private val scope: CoroutineScope,
    private val onMove: (Int, Int) -> Unit,
    private val onPickUp: () -> Unit,
    private val reduceMotion: Boolean,
) {
    var draggingIndex by mutableStateOf<Int?>(null)
        private set

    /** The row gliding home after a drop, and how far it still is from its slot. */
    var settlingIndex by mutableStateOf<Int?>(null)
        private set
    private val settle = Animatable(0f)

    private var anchorOffset = 0
    private var accumulatedY by mutableStateOf(0f)

    private fun infoFor(index: Int?) =
        listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }

    /** Where the dragged row's top is drawn: the finger's position, rubber-banded at the list's ends. */
    private fun shownTop(): Float {
        val raw = anchorOffset + accumulatedY
        val info = listState.layoutInfo
        val dragging = infoFor(draggingIndex) ?: return raw
        val first = info.visibleItemsInfo.firstOrNull { it.index == 0 }?.offset?.toFloat()
        val last = info.visibleItemsInfo.firstOrNull { it.index == info.totalItemsCount - 1 }
            ?.let { (it.offset + it.size - dragging.size).toFloat() }
        return GestureMotion.elastic(raw, first ?: Float.NEGATIVE_INFINITY, last ?: Float.POSITIVE_INFINITY)
    }

    /** Pixels to shift the dragged row from where the list laid it out. */
    val draggedTranslationY: Float
        get() {
            val info = infoFor(draggingIndex) ?: return 0f
            return shownTop() - info.offset
        }

    /** The translation for row [index]: following the finger, gliding home, or none. */
    fun translationFor(index: Int): Float = when (index) {
        draggingIndex -> draggedTranslationY
        settlingIndex -> settle.value
        else -> 0f
    }

    /** Lifted above its neighbours: the dragged row and the one landing. */
    fun isLifted(index: Int): Boolean = index == draggingIndex || index == settlingIndex

    fun onDragStart(index: Int) {
        scope.launch { settle.snapTo(0f) }
        settlingIndex = null
        draggingIndex = index
        anchorOffset = infoFor(index)?.offset ?: 0
        accumulatedY = 0f
        onPickUp()
    }

    fun onDrag(deltaY: Float) {
        accumulatedY += deltaY
        val from = draggingIndex ?: return
        val dragging = infoFor(from) ?: return
        val top = shownTop()
        val middle = (top + dragging.size / 2f).toInt()
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            item.index != from && middle in item.offset..(item.offset + item.size)
        } ?: return

        onMove(from, target.index)
        draggingIndex = target.index
        // Re-anchor onto the target's slot, preserving the finger offset, so the row doesn't snap
        // when the list reorders under it on the next frame.
        val raw = anchorOffset + accumulatedY
        anchorOffset = target.offset
        accumulatedY = raw - target.offset
    }

    fun onDragEnd() {
        val index = draggingIndex
        val from = draggedTranslationY
        draggingIndex = null
        accumulatedY = 0f
        if (index == null || from == 0f) return
        // Spring into the slot from where the finger left it — the release phase is physics, not a jump.
        settlingIndex = index
        scope.launch {
            settle.snapTo(from)
            settle.animateTo(0f, GestureMotion.release(reduceMotion, bounce = 0.12f, durationS = 0.4f))
            if (settlingIndex == index) settlingIndex = null
        }
    }
}

@Composable
fun rememberReorderState(listState: LazyListState, onMove: (Int, Int) -> Unit): ReorderState {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val reduce = rememberReduceMotion()
    val move by rememberUpdatedState(onMove)
    return remember(listState, scope) {
        ReorderState(
            listState = listState,
            scope = scope,
            onMove = { from, to ->
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                move(from, to)
            },
            onPickUp = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
            reduceMotion = reduce,
        )
    }
}
