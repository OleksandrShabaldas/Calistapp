package com.calistapp.app.ui.common

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Minimal drag-to-reorder for a `LazyColumn`, with no third-party dependency.
 *
 * Hold a row's drag handle and it follows your finger; when its midpoint passes a neighbour, [onMove]
 * swaps the two. Kept deliberately small — a workout plan is a handful of rows, not thousands, so it
 * doesn't need the edge auto-scroll and placement animations a general reorder library carries.
 *
 * [onMove] is given (from, to) list indices and should reorder the backing list; the state re-reads
 * the new layout on the next frame, so the dragged row stays under the finger across the swap.
 */
class ReorderState(
    val listState: LazyListState,
    private val onMove: (Int, Int) -> Unit,
) {
    var draggingIndex by mutableStateOf<Int?>(null)
        private set

    private var anchorOffset = 0
    private var accumulatedY by mutableStateOf(0f)

    private fun infoFor(index: Int?) =
        listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }

    /** Pixels to shift the dragged row from where the list laid it out. */
    val draggedTranslationY: Float
        get() {
            val info = infoFor(draggingIndex) ?: return 0f
            return (anchorOffset + accumulatedY) - info.offset
        }

    fun onDragStart(index: Int) {
        draggingIndex = index
        anchorOffset = infoFor(index)?.offset ?: 0
        accumulatedY = 0f
    }

    fun onDrag(deltaY: Float) {
        accumulatedY += deltaY
        val from = draggingIndex ?: return
        val dragging = infoFor(from) ?: return
        val top = anchorOffset + accumulatedY
        val middle = (top + dragging.size / 2f).toInt()
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            item.index != from && middle in item.offset..(item.offset + item.size)
        } ?: return

        onMove(from, target.index)
        draggingIndex = target.index
        // Re-anchor onto the target's slot, preserving the finger offset, so the row doesn't snap
        // when the list reorders under it on the next frame.
        anchorOffset = target.offset
        accumulatedY = top - target.offset
    }

    fun onDragEnd() {
        draggingIndex = null
        accumulatedY = 0f
    }
}

@Composable
fun rememberReorderState(listState: LazyListState, onMove: (Int, Int) -> Unit): ReorderState =
    remember(listState) { ReorderState(listState, onMove) }
