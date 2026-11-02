package com.music.bitchord.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Hands one touch over from a tile to the reorder drag. A tile "arms" it —
 * from its long press (the press that also selects it), or straight away
 * from its reorder grip — and [reorderDragHost], on the list or grid itself,
 * turns the rest of that touch into the drag once the finger moves.
 *
 * The drag is followed at the list's level, not the tile's: the tile moves
 * under the finger (and changes place), so its own coordinates are no good
 * for measuring the finger by.
 */
class ReorderArm {
    /** The armed tile's key, or null. */
    var key: String? = null
        private set
    /** Armed by the grip: no long press, the drag starts on the first move. */
    var immediate: Boolean = false
        private set
    /** Run if the touch ends without a drag (a long press that only selects). */
    var onNoDrag: (() -> Unit)? = null
    /** Run after a drag, e.g. to undo a selection the long press made. */
    var afterDrag: (() -> Unit)? = null

    fun arm(key: String, immediate: Boolean = false) {
        this.key = key
        this.immediate = immediate
    }

    fun clear() {
        key = null
        immediate = false
        onNoDrag = null
        afterDrag = null
    }
}

/**
 * On a list or grid: once a tile has [ReorderArm.arm]ed the touch, moving the
 * finger past the touch slop starts a drag ([onStart], which may decline),
 * [onDrag] follows it (in this element's coordinates, which hold still), and
 * lifting ends it ([onEnd]). The touch is taken on the Initial pass while
 * dragging, so the list does not scroll and the tile does not click.
 */
@Composable
fun Modifier.reorderDragHost(
    arm: ReorderArm,
    onStart: (key: String, position: Offset) -> Boolean,
    onDrag: (delta: Offset, position: Offset) -> Unit,
    onEnd: () -> Unit,
): Modifier {
    // The touch handler below outlives recompositions; it must always call
    // the latest callbacks, which see the page's current song list. (Holding
    // the first ones made every drag after the first saved order work on a
    // list the page no longer showed.)
    val start by rememberUpdatedState(onStart)
    val drag by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onEnd)
    return reorderDragHostImpl(arm, { k, p -> start(k, p) }, { d, p -> drag(d, p) }, { end() })
}

private fun Modifier.reorderDragHostImpl(
    arm: ReorderArm,
    onStart: (key: String, position: Offset) -> Boolean,
    onDrag: (delta: Offset, position: Offset) -> Unit,
    onEnd: () -> Unit,
): Modifier = this.pointerInput(arm) {
    val slop = viewConfiguration.touchSlop
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var dragging = false
        var anchor: Offset? = null
        var last = down.position
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            if (event.changes.count { it.pressed } > 1 && !dragging) break
            val key = arm.key
            if (key == null) {
                last = change.position
                continue
            }
            if (!dragging) {
                val from = anchor ?: (if (arm.immediate) down.position else last).also { anchor = it }
                if ((change.position - from).getDistance() > slop) {
                    dragging = onStart(key, change.position)
                    if (!dragging) {
                        arm.clear()
                        break
                    }
                    last = change.position
                }
            } else {
                onDrag(change.position - last, change.position)
                last = change.position
            }
            if (dragging) event.changes.forEach { it.consume() }
        }
        if (dragging) {
            // The lift ends here; let no click follow it.
            onEnd()
            arm.afterDrag?.invoke()
        } else {
            arm.onNoDrag?.invoke()
        }
        arm.clear()
    }
}

/**
 * Drag-to-reorder for a [LazyGridState], by item key. The dragged tile
 * follows the finger (see [offsetOf]); when its middle crosses another of
 * [isMovable]'s tiles, [onMove] swaps them in the data and the grid lays the
 * rest out around it. Near the top or bottom edge the grid scrolls by itself.
 * Letting go springs the tile from the finger into its place.
 */
class GridReorderState(
    val gridState: LazyGridState,
    private val scope: CoroutineScope,
) {
    var isMovable: (Any?) -> Boolean = { it is String }
    var onMove: (fromKey: String, toKey: String) -> Unit = { _, _ -> }
    var edgeZone: Float = 0f
    var edgeSpeed: Float = 0f

    var draggedKey by mutableStateOf<String?>(null)
        private set
    /** The tile settling into place after a drop. */
    var droppingKey by mutableStateOf<String?>(null)
        private set
    var autoScrollSpeed by mutableFloatStateOf(0f)
        private set

    private var initialOffset = Offset.Zero
    private var delta by mutableStateOf(Offset.Zero)
    private var finger = Offset.Zero
    private val drop = Animatable(Offset.Zero, Offset.VectorConverter)

    private fun info(key: Any?): LazyGridItemInfo? =
        gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }

    fun start(key: String, position: Offset): Boolean {
        val item = info(key) ?: return false
        scope.launch { drop.snapTo(Offset.Zero) }
        droppingKey = null
        draggedKey = key
        initialOffset = Offset(item.offset.x.toFloat(), item.offset.y.toFloat())
        delta = Offset.Zero
        finger = position
        return true
    }

    fun drag(by: Offset, position: Offset) {
        delta += by
        finger = position
        settle()
    }

    /** Called as the grid scrolls itself: the tile under the finger may be a new one. */
    fun onScrolled() = settle()

    fun end() {
        val key = draggedKey ?: return
        val residual = offsetOf(key)
        draggedKey = null
        autoScrollSpeed = 0f
        droppingKey = key
        scope.launch {
            drop.snapTo(residual)
            drop.animateTo(Offset.Zero, spring(0.6f, 800f))
            if (droppingKey == key) droppingKey = null
        }
    }

    /** How far [key]'s tile is drawn from its place. */
    fun offsetOf(key: Any?): Offset = when (key) {
        null -> Offset.Zero
        draggedKey -> info(key)?.let { initialOffset + delta - Offset(it.offset.x.toFloat(), it.offset.y.toFloat()) } ?: Offset.Zero
        droppingKey -> drop.value
        else -> Offset.Zero
    }

    private fun settle() {
        val key = draggedKey ?: return
        val dragged = info(key) ?: return
        val middle = initialOffset + delta + Offset(dragged.size.width / 2f, dragged.size.height / 2f)
        val target = gridState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            item.key != key && isMovable(item.key) &&
                middle.x >= item.offset.x && middle.x <= item.offset.x + item.size.width &&
                middle.y >= item.offset.y && middle.y <= item.offset.y + item.size.height
        }
        if (target != null) onMove(key, target.key as String)

        val info = gridState.layoutInfo
        // The finger is in the grid's own coordinates: the visible content
        // runs from under the top bar's padding to above the bottom's.
        val top = info.beforeContentPadding.toFloat()
        val bottom = info.viewportSize.height - info.afterContentPadding.toFloat()
        val y = finger.y
        autoScrollSpeed = when {
            edgeZone <= 0f -> 0f
            y < top + edgeZone -> -edgeSpeed * ((top + edgeZone - y) / edgeZone).coerceIn(0f, 1f)
            y > bottom - edgeZone -> edgeSpeed * ((y - (bottom - edgeZone)) / edgeZone).coerceIn(0f, 1f)
            else -> 0f
        }
    }
}

@Composable
fun rememberGridReorderState(gridState: LazyGridState): GridReorderState {
    val scope = rememberCoroutineScope()
    val state = remember(gridState) { GridReorderState(gridState, scope) }
    with(LocalDensity.current) {
        state.edgeZone = 56.dp.toPx()
        state.edgeSpeed = 420.dp.toPx()
    }
    val scrolling = state.autoScrollSpeed != 0f
    LaunchedEffect(state, scrolling) {
        if (!scrolling) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (state.autoScrollSpeed != 0f) {
            val now = withFrameNanos { it }
            val seconds = ((now - previous) / 1_000_000_000f).coerceAtMost(1f / 30f)
            previous = now
            val scrolled = gridState.scrollBy(state.autoScrollSpeed * seconds)
            if (scrolled == 0f) break
            state.onScrolled()
        }
    }
    return state
}
