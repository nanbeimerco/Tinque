package com.music.bitchord.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.GridKind
import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.ui.haptics.rememberHaptics
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

/** True inside a grid of 4+ columns: cards drop to a smaller title and no subtitle. */
val LocalGridCompact = compositionLocalOf { false }

/** Narrower than this, a grid card is "compact" (about 4+ columns on a phone). */
private val COMPACT_CARD_WIDTH = 100.dp

/** Whether a grid card is narrow enough to go compact, plus the modifier that measures it. */
class CardCompact(val compact: Boolean, val measure: Modifier)

/**
 * Lets a grid card shrink its text when the grid gets dense: a smaller title
 * and no subtitle once the card is under [COMPACT_CARD_WIDTH] wide. Put
 * [CardCompact.measure] on the card's root.
 */
@Composable
fun rememberCardCompact(): CardCompact {
    val density = androidx.compose.ui.platform.LocalDensity.current
    var compact by remember { mutableStateOf(false) }
    val measure = remember(density) {
        Modifier.onSizeChanged { compact = with(density) { it.width.toDp() } < COMPACT_CARD_WIDTH }
    }
    return CardCompact(compact, measure)
}

/** The user's column count for [kind] (pinch or Settings), or its default. */
@Composable
fun rememberGridColumns(kind: GridKind): Int {
    val all by AppSettings.gridColumns.collectAsStateWithLifecycle()
    return all[kind.key] ?: kind.defaultColumns
}

/**
 * [columns] across a phone-width grid, and proportionally more on a wider
 * one (tablet, landscape), so an item keeps about the same size everywhere.
 */
class ScaledGridCells(private val columns: Int) : GridCells {
    override fun Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int> {
        val reference = PHONE_WIDTH.toPx()
        val count = max(columns, (columns * availableSize / reference).roundToInt()).coerceAtLeast(1)
        val usable = (availableSize - spacing * (count - 1)).coerceAtLeast(0)
        val base = usable / count
        val remainder = usable % count
        return List(count) { base + if (it < remainder) 1 else 0 }
    }

    override fun equals(other: Any?): Boolean = other is ScaledGridCells && other.columns == columns
    override fun hashCode(): Int = columns

    private companion object {
        val PHONE_WIDTH = 400.dp
    }
}

/** Pinch ratio past which the grid steps one column. */
private const val STEP_IN = 1.22f
private const val STEP_OUT = 0.82f

/**
 * Two-finger pinch on a grid to change its column count, like Google Photos.
 *
 * While pinching, the whole grid scales with the fingers (around their
 * midpoint, within a small range) so the gesture has an immediate response.
 * Spreading past [STEP_IN] takes a column away (bigger items), pinching past
 * [STEP_OUT] adds one; each step ticks, saves, and resets the scale, and the
 * grid's items — given `Modifier.animateItem()` — glide to their new places.
 * One-finger scrolling is untouched: only events with two pointers down are
 * consumed, on the Initial pass so the list does not scroll mid-pinch.
 */
@Composable
fun Modifier.pinchToResizeGrid(kind: GridKind, columns: Int): Modifier {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var pivot by remember { mutableStateOf(Offset.Zero) }
    val currentColumns by rememberUpdatedState(columns)

    return this
        .onSizeChanged { size = it }
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
            transformOrigin = if (size.width > 0 && size.height > 0) {
                TransformOrigin(pivot.x / size.width, pivot.y / size.height)
            } else {
                TransformOrigin.Center
            }
        }
        .pointerInput(kind) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var baseline = 0f
                var pinching = false
                var cols = currentColumns
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val pressed = event.changes.filter { it.pressed }
                    if (pressed.isEmpty()) break
                    if (pressed.size < 2) continue
                    val a = pressed[0].position
                    val b = pressed[1].position
                    val distance = (a - b).getDistance()
                    if (!pinching) {
                        pinching = true
                        baseline = distance.coerceAtLeast(1f)
                        pivot = (a + b) / 2f
                    } else {
                        val ratio = distance / baseline
                        when {
                            ratio > STEP_IN && cols > GridKind.MIN_COLUMNS -> {
                                cols--
                                AppSettings.setGridColumns(kind, cols)
                                haptics.play(Haptic.Tick)
                                baseline = distance
                                scope.launch { scale.snapTo(1f) }
                            }
                            ratio < STEP_OUT && cols < GridKind.MAX_COLUMNS -> {
                                cols++
                                AppSettings.setGridColumns(kind, cols)
                                haptics.play(Haptic.Tick)
                                baseline = distance
                                scope.launch { scale.snapTo(1f) }
                            }
                            else -> {
                                // Live feedback within one step; damped at the ends of the range.
                                val live = ratio.coerceIn(0.88f, 1.14f)
                                scope.launch { scale.snapTo(live) }
                            }
                        }
                    }
                    event.changes.forEach { it.consume() }
                }
                if (pinching) {
                    scope.launch { scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
                }
            }
        }
}
