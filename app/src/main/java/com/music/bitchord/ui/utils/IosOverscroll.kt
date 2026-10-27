/*
 * Adapted from Convx's IosOverscroll.kt (GPL-3.0):
 * https://github.com/cosmictaserdev-creator/Convx
 */
package com.music.bitchord.ui.utils

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

private const val rubberBandConstant = 0.55f
private const val fallbackContainerPx = 2_000f
private const val maxBounceVelocity = 10_000f
private const val highVelocityThreshold = 5_000f
private const val normalBounceStiffness = 247f
private const val fastBounceStiffness = 130f

/**
 * Converts raw pull distance to UIKit's self-limiting rubber-band curve.
 */
private fun rubberBand(rawDistance: Float, containerPx: Float): Float {
    val dimension = containerPx.takeIf { it > 0f } ?: fallbackContainerPx
    val distance = abs(rawDistance)
    val banded = (1f - 1f / (distance * rubberBandConstant / dimension + 1f)) *
        dimension / rubberBandConstant
    return banded * sign(rawDistance)
}

/** The inverse of [rubberBand]: the raw pull that shows as [bandedDistance] on screen. */
private fun inverseRubberBand(bandedDistance: Float, containerPx: Float): Float {
    val dimension = containerPx.takeIf { it > 0f } ?: fallbackContainerPx
    // The banded curve approaches dimension / rubberBandConstant; stay just under it.
    val k = (abs(bandedDistance) * rubberBandConstant / dimension).coerceAtMost(0.999f)
    val raw = k / (1f - k) * dimension / rubberBandConstant
    return raw * sign(bandedDistance)
}

/**
 * An iOS-style overscroll effect for Compose scroll containers.
 *
 * It replaces Android's stretch/glow with a UIScrollView-like rubber band during a
 * drag, then critically damps the content back to its resting position on release.
 */
private class IosOverscrollEffect(private val scope: CoroutineScope) : OverscrollEffect {
    private val rawPullX = mutableFloatStateOf(0f)
    private val rawPullY = mutableFloatStateOf(0f)
    private var containerWidthPx = 0f
    private var containerHeightPx = 0f

    /**
     * The spring back after a release, run on its own job so a new touch can
     * stop it. Run inline in [applyToFling], nothing stopped it: a drag that
     * began while a stretch was springing back fought the spring for the same
     * value every frame, and the spring won — the finger's first movement at
     * a list's end was swallowed, and the scroll started late and short.
     * Stopped, the finger catches the stretch where it is, as on iOS.
     */
    private var settleJob: Job? = null

    override val isInProgress: Boolean
        get() = rawPullX.floatValue != 0f || rawPullY.floatValue != 0f

    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset,
    ): Offset {
        if (source == NestedScrollSource.UserInput) settleJob?.cancel()
        // When pulling back from an existing stretch, settle the stretch before
        // allowing the list itself to scroll. This keeps the motion continuous.
        val paidX = payDown(rawPullX, delta.x, containerWidthPx)
        val paidY = payDown(rawPullY, delta.y, containerHeightPx)
        val remaining = Offset(delta.x - paidX, delta.y - paidY)
        val consumed = performScroll(remaining)
        val leftover = remaining - consumed

        // Absorb only touch deltas. Fling leftovers must be returned so Compose
        // cancels its decay and gives their velocity to applyToFling below.
        var stretchedX = 0f
        var stretchedY = 0f
        if (source == NestedScrollSource.UserInput) {
            if (leftover.x != 0f) {
                rawPullX.floatValue += leftover.x
                stretchedX = leftover.x
            }
            if (leftover.y != 0f) {
                rawPullY.floatValue += leftover.y
                stretchedY = leftover.y
            }
        }
        return Offset(consumed.x + paidX + stretchedX, consumed.y + paidY + stretchedY)
    }

    override suspend fun applyToFling(
        velocity: Velocity,
        performFling: suspend (Velocity) -> Velocity,
    ) {
        settleJob?.cancel()
        val consumed = performFling(velocity)
        val leftover = velocity - consumed
        if (!isInProgress && leftover == Velocity.Zero) return

        val job = scope.launch {
            launch { settle(rawPullX, leftover.x) }
            launch { settle(rawPullY, leftover.y) }
        }
        settleJob = job
        // Returns early, without throwing, when a new touch cancels the job.
        job.join()
    }

    /**
     * Pushes an existing stretch back by [delta], in on-screen pixels.
     *
     * The stretch is stored as a raw pull but drawn through [rubberBand], which
     * compresses it. Subtracting the finger's movement from the raw value made
     * the content move less than the finger on the way back (a deep stretch
     * barely moved at first), so a scroll or fling starting at a list's end
     * felt late and short. Here the finger moves the drawn offset 1:1, and
     * only what is left after the stretch is gone reaches the list.
     */
    private fun payDown(pull: MutableFloatState, delta: Float, containerPx: Float): Float {
        val current = pull.floatValue
        if (current == 0f || delta == 0f || sign(current) == sign(delta)) return 0f
        val shown = rubberBand(current, containerPx)
        val target = shown + delta
        return if (target == 0f || sign(target) != sign(shown)) {
            pull.floatValue = 0f
            -shown
        } else {
            pull.floatValue = inverseRubberBand(target, containerPx)
            delta
        }
    }

    private suspend fun settle(pull: MutableFloatState, leftoverVelocity: Float) {
        if (pull.floatValue == 0f && leftoverVelocity == 0f) return
        val velocity = leftoverVelocity.coerceIn(-maxBounceVelocity, maxBounceVelocity)
        animate(
            initialValue = pull.floatValue,
            targetValue = 0f,
            initialVelocity = velocity,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = if (abs(velocity) > highVelocityThreshold) {
                    fastBounceStiffness
                } else {
                    normalBounceStiffness
                },
            ),
        ) { value, _ -> pull.floatValue = value }
    }

    override val node: DelegatableNode = IosOverscrollNode(
        offsetX = { rubberBand(rawPullX.floatValue, containerWidthPx) },
        offsetY = { rubberBand(rawPullY.floatValue, containerHeightPx) },
        onMeasured = { width, height ->
            containerWidthPx = width
            containerHeightPx = height
        },
    )
}

private class IosOverscrollNode(
    private val offsetX: () -> Float,
    private val offsetY: () -> Float,
    private val onMeasured: (Float, Float) -> Unit,
) : Modifier.Node(), LayoutModifierNode {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        onMeasured(
            (if (constraints.hasBoundedWidth) constraints.maxWidth else placeable.width).toFloat(),
            (if (constraints.hasBoundedHeight) constraints.maxHeight else placeable.height).toFloat(),
        )
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0) {
                translationX = offsetX()
                translationY = offsetY()
            }
        }
    }
}

private class IosOverscrollFactory(
    private val density: Density,
    private val scope: CoroutineScope,
) : OverscrollFactory {
    override fun createOverscrollEffect(): OverscrollEffect = IosOverscrollEffect(scope)

    override fun equals(other: Any?): Boolean =
        other is IosOverscrollFactory && other.density == density && other.scope === scope

    override fun hashCode(): Int = 31 * density.hashCode() + scope.hashCode()
}

/** Provides iOS-style rubber-band overscroll to every descendant scroll container. */
@Composable
fun rememberIosOverscrollFactory(): OverscrollFactory {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    return remember(density, scope) { IosOverscrollFactory(density, scope) }
}
