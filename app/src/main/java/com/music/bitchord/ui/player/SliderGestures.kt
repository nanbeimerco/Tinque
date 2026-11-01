package com.music.bitchord.ui.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * What a relative slider hears from a finger. Fractions are of the slider's
 * own width; [fineDistancePx] is how far the finger has strayed from the
 * slider's centre line, up or down.
 */
interface RelativeSliderListener {
    /** A drag began; the value should be held where it is, not moved to the finger. */
    fun onDragStart() {}
    fun onDrag(deltaFraction: Float, fineDistancePx: Float)
    fun onDragEnd() {}
    /** The finger rested without moving long enough to count as a long press. */
    fun onLongPress() {}
    /** The finger, still down after the long press, moved. */
    fun onLongPressDrag(deltaFraction: Float, fineDistancePx: Float) {}
    fun onLongPressEnd() {}
}

/** How long a finger has to rest on the bar for a long press. */
const val SLIDER_LONG_PRESS_MS = 400L

/**
 * A slider that moves by how far the finger moves, never jumping to where it
 * lands — so resting a finger on it (to long-press it, or by accident) changes
 * nothing. Nothing moves until the finger has gone a few dp sideways; a finger
 * that rests [SLIDER_LONG_PRESS_MS] without doing so is a long press instead,
 * and keeps reporting its movement until it lifts so it can go straight on to
 * whatever the long press opened.
 */
fun Modifier.relativeSliderGestures(
    listener: RelativeSliderListener,
    longPressEnabled: Boolean,
): Modifier = pointerInput(listener, longPressEnabled) {
    val slop = 8.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        val startedAt = System.currentTimeMillis()
        var last: Offset = down.position
        // 0: undecided, 1: dragging, 2: long press
        var mode = 0
        try {
            while (true) {
                val event: PointerEvent? = if (mode == 0 && longPressEnabled) {
                    val left = SLIDER_LONG_PRESS_MS - (System.currentTimeMillis() - startedAt)
                    if (left <= 0) null else withTimeoutOrNull(left) { awaitPointerEvent() }
                } else {
                    awaitPointerEvent()
                }
                if (event == null) {
                    mode = 2
                    listener.onLongPress()
                    continue
                }
                val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!pointer.pressed) {
                    pointer.consume()
                    break
                }
                val fine = pointer.position.y - size.height / 2f
                when (mode) {
                    0 -> if (abs(pointer.position.x - down.position.x) > slop) {
                        mode = 1
                        listener.onDragStart()
                    }
                    1 -> listener.onDrag((pointer.position.x - last.x) / size.width.coerceAtLeast(1), fine)
                    2 -> listener.onLongPressDrag((pointer.position.x - last.x) / size.width.coerceAtLeast(1), fine)
                }
                if (mode != 0) last = pointer.position
                pointer.consume()
            }
        } finally {
            when (mode) {
                1 -> listener.onDragEnd()
                2 -> listener.onLongPressEnd()
            }
        }
    }
}

/** How much a step of finger movement counts for, by how far it has strayed off the bar. */
fun fineFactor(fineDistancePx: Float, density: Float): Float =
    if (abs(fineDistancePx) > 48f * density) 0.1f else 1f
