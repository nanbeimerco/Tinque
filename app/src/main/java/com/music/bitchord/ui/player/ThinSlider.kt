package com.music.bitchord.ui.player

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Apple Music's scrubber: a hairline capsule with no thumb knob, which
 * thickens under your finger and settles back when you let go. Material's
 * Slider can't be shaped like this — it always draws a thumb and a tall
 * track — so this is drawn directly.
 */
@Composable
fun ThinSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    idleHeight: Dp = 7.dp,
    activeHeight: Dp = 12.dp,
    activeColor: Color = Color.White.copy(alpha = 0.92f),
    inactiveColor: Color = Color.White.copy(alpha = 0.26f),
    /**
     * Moves by how far the finger moves instead of jumping to it, so a finger
     * resting on the bar changes nothing — see [relativeSliderGestures]. The
     * long press callbacks only apply here.
     */
    relative: Boolean = false,
    onLongPress: (() -> Unit)? = null,
    onLongPressDrag: ((deltaFraction: Float, fineDistancePx: Float) -> Unit)? = null,
    onLongPressEnd: (() -> Unit)? = null,
) {
    var dragging by remember { mutableStateOf(false) }
    val latestValue = androidx.compose.runtime.rememberUpdatedState(value)
    val latestChange = androidx.compose.runtime.rememberUpdatedState(onValueChange)
    val latestFinished = androidx.compose.runtime.rememberUpdatedState(onValueChangeFinished)
    val latestLongPress = androidx.compose.runtime.rememberUpdatedState(onLongPress)
    val latestLongPressDrag = androidx.compose.runtime.rememberUpdatedState(onLongPressDrag)
    val latestLongPressEnd = androidx.compose.runtime.rememberUpdatedState(onLongPressEnd)
    val listener = remember {
        object : RelativeSliderListener {
            var held = 0f
            override fun onDragStart() {
                dragging = true
                held = latestValue.value
            }
            override fun onDrag(deltaFraction: Float, fineDistancePx: Float) {
                held = (held + deltaFraction).coerceIn(0f, 1f)
                latestChange.value(held)
            }
            override fun onDragEnd() {
                dragging = false
                latestFinished.value?.invoke()
            }
            override fun onLongPress() { latestLongPress.value?.invoke() }
            override fun onLongPressDrag(deltaFraction: Float, fineDistancePx: Float) {
                latestLongPressDrag.value?.invoke(deltaFraction, fineDistancePx)
            }
            override fun onLongPressEnd() { latestLongPressEnd.value?.invoke() }
        }
    }
    val height by animateDpAsState(
        targetValue = if (dragging) activeHeight else idleHeight,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "sliderHeight",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            // Generous invisible touch target — the visible bar is only ~7dp.
            .height(activeHeight + 22.dp)
            // One gesture loop for both taps and drags. Two separate detectors
            // — a drag one plus a tap one — meant taps never landed: the drag
            // detector took the pointer and a tap has no drag to report.
            .then(
                if (relative) Modifier.relativeSliderGestures(listener, longPressEnabled = onLongPress != null)
                else Modifier,
            )
            .pointerInput(relative) {
                if (relative) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    dragging = true
                    onValueChange((down.position.x / size.width).coerceIn(0f, 1f))

                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!pointer.pressed) {
                                pointer.consume()
                                break
                            }
                            if (pointer.positionChanged()) {
                                onValueChange((pointer.position.x / size.width).coerceIn(0f, 1f))
                                pointer.consume()
                            }
                        }
                    } finally {
                        if (dragging) {
                            dragging = false
                            onValueChangeFinished?.invoke()
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height),
        ) {
            val radius = CornerRadius(size.height / 2f)
            drawRoundRect(color = inactiveColor, cornerRadius = radius)
            val filled = size.width * value.coerceIn(0f, 1f)
            if (filled > 0f) {
                drawRoundRect(
                    color = activeColor,
                    size = Size(filled.coerceAtLeast(size.height), size.height),
                    cornerRadius = radius,
                )
            }
        }
    }
}
