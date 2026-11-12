package com.music.bitchord.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * How a glass menu opens and closes (mocks/popup_motion.html, the settings
 * chosen there): one clock in milliseconds, and every part of the motion read
 * off it.
 *
 * - The glass starts as a button-sized pill on the ⋮ and grows around its own
 *   centre, which travels from the button to the menu's place as it grows.
 * - While small it is a rectangle with round ends; its corners come back to
 *   the menu's own as it nears full size.
 * - The rows are laid out at full size and seen through the glass (not
 *   squeezed), slightly shrunk and blurred at first, sharpening as it lands.
 * - The top and bottom rows stretch for a moment along the edge (the lens).
 * - It overshoots a little and settles. Closing plays the same clock back.
 */
internal object PopupMotion {
    /** The clock runs 0..END on opening. */
    const val END = 420f
    /** A close starts from here at most (or from where an unfinished open is). */
    const val CLOSE_FROM = 260f

    val START_SIZE = 40.dp
    val CORNER = 24.dp
    /** The menu's first row sits on the button: its top this far above the button's centre. */
    val ACROSS_LIFT = 30.dp
    /** How far the ⋮ leans toward the opening menu. */
    val NUDGE = 3.dp
    /** The most a pull inside the menu stretches it. */
    val STRETCH = 12.dp
    val BLUR = 12.dp

    private const val ROUND = 0.8f          // round ends: 0.8 of a half-circle
    private const val MORPH_FROM = 0.45f    // corners start back at 45% grown
    private const val SHRINK = 0.1f         // rows start at 90%
    private const val WARP = 0.7f           // lens strength

    class Frame(
        /** Width and height progress, 0 = button-sized, 1 = full. */
        val width: Float,
        val height: Float,
        /** The centre's travel from the button to its place. */
        val travel: Float,
        /** 1 while round-ended, 0 with the menu's own corners. */
        val round: Float,
        /** The edge rows' stretch. */
        val warp: Float,
        /** Blur, 0..1 of [BLUR]. */
        val blur: Float,
        /** The glass's and the rows' opacity. */
        val glassAlpha: Float,
        val contentAlpha: Float,
        /** The rows' scale. */
        val contentScale: Float,
    )

    private val still = Frame(1f, 1f, 1f, 0f, 0f, 0f, 1f, 1f, 1f)

    fun frame(t: Float, reduced: Boolean): Frame {
        if (reduced) {
            val a = smooth(0f, 150f, t)
            return Frame(1f, 1f, 1f, 0f, 0f, 0f, a, a, 1f)
        }
        if (t >= END) return still
        val h = springStep(700f, 0.66f, t)
        val w = springStep(700f, 0.72f, t)
        val travel = springStep(2025f, 0.78f, t)
        val grown = minOf(w, h).coerceIn(0f, 1f)
        val warp = (if (t <= 150f) sin(t / 150f * PI.toFloat() / 2f) else exp(-((t - 150f) / 45f).pow(2))) * WARP
        val blur = (1f - smooth(0f, 220f, t)).pow(1.7f)
        return Frame(
            width = w,
            height = h,
            travel = travel,
            round = 1f - smooth(MORPH_FROM, 1f, grown),
            warp = warp,
            blur = blur,
            glassAlpha = smooth(0f, 90f, t),
            contentAlpha = smooth(40f, 180f, t),
            contentScale = 1f - SHRINK * (1f - grown),
        )
    }

    /** Corner radius for a [w] x [h] window at [round]. */
    fun cornerPx(w: Float, h: Float, round: Float, corner: Float): Float {
        val ends = minOf(w, h) / 2f * ROUND
        return minOf(lerp(corner, maxOf(corner, ends), round), w / 2f, h / 2f)
    }

    /** The ⋮'s lean: in quickly, then back with a small rebound. */
    fun nudge(t: Float): Float {
        if (t <= 0f) return 0f
        if (t < 38f) return sin(t / 38f * PI.toFloat() / 2f)
        val u = t - 38f
        return exp(-u / 62f) * cos(u / 40f)
    }

    /** The ⋮ fades as the glass comes over it. */
    fun anchorAlpha(t: Float): Float = 1f - smooth(22f, 70f, t)

    /** A mass-1 spring's step response from 0 to 1 after [tMs]. */
    private fun springStep(stiffness: Float, damping: Float, tMs: Float): Float {
        if (tMs <= 0f) return 0f
        val t = tMs / 1000f
        val w = sqrt(stiffness)
        if (damping >= 1f) return 1f - exp(-w * t) * (1f + w * t)
        val wd = w * sqrt(1f - damping * damping)
        return 1f - exp(-damping * w * t) * (cos(wd * t) + (damping * w / wd) * sin(wd * t))
    }

    fun smooth(a: Float, b: Float, x: Float): Float {
        val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
}

/** A corner that reads its size at draw time, so one shape follows the morph. */
internal class LiveCornerSize(private val px: () -> Float) : CornerSize {
    override fun toPx(shapeSize: Size, density: Density): Float =
        px().coerceIn(0f, shapeSize.minDimension / 2f)
}

/**
 * A pull inside an open menu that its list does not use (it is at an end, or
 * too short to scroll) stretches the menu a little instead of reaching the
 * page, then springs back on release: the page under it stays put.
 */
internal class MenuStretch(private val scope: CoroutineScope, private val maxPx: Float) : NestedScrollConnection {
    val offset = Animatable(0f)
    private var raw = 0f
    private var settling: Job? = null

    fun reset() {
        raw = 0f
        settling?.cancel()
        scope.launch { offset.snapTo(0f) }
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (raw == 0f || available.y == 0f || source != NestedScrollSource.UserInput) return Offset.Zero
        // Moving back toward rest uses up the stretch before the list scrolls.
        if (available.y.sign != raw.sign) {
            val used = if (abs(available.y) > abs(raw)) -raw else available.y
            raw += used
            push()
            return Offset(0f, used)
        }
        return Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput && available.y != 0f) {
            raw += available.y
            push()
        }
        // Whatever is left stays here: nothing travels on to the page or the
        // player sheet under the menu.
        return available
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        release()
        return Velocity.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        release()
        return available
    }

    private fun push() {
        settling?.cancel()
        val value = raw.sign * maxPx * (1f - exp(-abs(raw) / (maxPx * 2.2f)))
        scope.launch { offset.snapTo(value) }
    }

    private fun release() {
        if (raw == 0f && offset.value == 0f) return
        raw = 0f
        settling?.cancel()
        settling = scope.launch { offset.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 500f)) }
    }
}

/**
 * Put on a button that opens a glass menu: as its menu opens, it shrinks a
 * touch, leans toward the menu and back, and fades as the glass comes over it.
 * Matched by position, so it needs nothing from the menu's caller.
 */
@Composable
fun Modifier.glassMenuAnchor(): Modifier {
    val menus = LocalGlassMenu.current ?: return this
    var center by remember { mutableStateOf(Offset.Unspecified) }
    val density = LocalDensity.current
    val tolerance = with(density) { 8.dp.toPx() }
    val travel = with(density) { PopupMotion.NUDGE.toPx() }
    return this
        .onGloballyPositioned { center = it.boundsInRoot().center }
        .graphicsLayer {
            val anchor = menus.motionAnchor ?: return@graphicsLayer
            if (menus.motionReduced || !center.isSpecified) return@graphicsLayer
            if ((anchor.center - center).getDistance() > tolerance) return@graphicsLayer
            val t = menus.clock.value
            if (t <= 0f) return@graphicsLayer
            val n = PopupMotion.nudge(t)
            val dir = menus.motionDirection
            translationX = dir.x * n * travel
            translationY = dir.y * n * travel
            val press = 1f - 0.1f * n.coerceIn(0f, 1f)
            scaleX = press
            scaleY = press
            alpha = PopupMotion.anchorAlpha(t)
        }
}
