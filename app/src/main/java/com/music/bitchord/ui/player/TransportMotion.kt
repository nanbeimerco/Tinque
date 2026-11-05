package com.music.bitchord.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.music.bitchord.ui.icons.BitChordIcons
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow

/**
 * The player's transport icons, moving when tapped (Settings › animated player
 * buttons). At rest each is drawn as its own icon, unchanged; only while it
 * moves is it drawn in pieces.
 *
 * - Skip: the two triangles roll one place along, like a conveyor. The front
 *   one shrinks away off the end, a new one grows in at the back, and the
 *   roll overshoots a little and settles. A triangle that has gone never comes
 *   back on the bounce, and only one is born per tap.
 * - Play/pause: the play triangle splits down the middle into the two pause
 *   bars, which spring apart a little; pause closes back into play. The shape
 *   stops at either icon — the spring's overshoot shows as a small swell of the
 *   whole glyph instead, so the shape never turns inside out.
 *
 * Pressing sinks the icon a little; letting go springs it back.
 */
@Composable
internal fun TransportMotionIcon(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    taps: Int,
    interaction: InteractionSource,
    modifier: Modifier = Modifier,
) {
    val pressed by interaction.collectIsPressedAsState()
    val press = remember { Animatable(1f) }
    LaunchedEffect(pressed) {
        if (pressed) {
            press.animateTo(0.92f, spring(dampingRatio = 1f, stiffness = 900f, visibilityThreshold = SETTLED))
        } else {
            press.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 420f, visibilityThreshold = SETTLED))
        }
    }
    val described = modifier.semantics {
        this.contentDescription = contentDescription
        role = Role.Image
    }
    when {
        icon === BitChordIcons.TransportNext || icon === BitChordIcons.TransportPrevious ->
            SkipConveyor(forward = icon === BitChordIcons.TransportNext, taps = taps, tint = tint, press = press, modifier = described)
        icon === BitChordIcons.TransportPlay || icon === BitChordIcons.TransportPause ->
            PlayPauseSplit(playing = icon === BitChordIcons.TransportPause, tint = tint, press = press, modifier = described)
        else -> Icon(icon, contentDescription, tint = tint, modifier = modifier)
    }
}

// TransportNext's geometry in its 24 box: its back triangle ends, and its
// front triangle starts, at x = 11.794; the back one starts 11.594 before that.
private const val SPLIT_X = 11.794f
private const val STEP = 11.594f
private const val FRONT_CENTER_X = 17.6f

/**
 * Where every spring here counts as settled. The default (1%) ends a spring
 * by jumping the last 1% of the way, which shows as a snap as it stops.
 */
private const val SETTLED = 0.0005f

@Composable
private fun SkipConveyor(
    forward: Boolean,
    taps: Int,
    tint: Color,
    press: Animatable<Float, AnimationVector1D>,
    modifier: Modifier,
) {
    // Previous is next mirrored, so both draw next's pieces (mirrored for back).
    val painter = rememberVectorPainter(BitChordIcons.TransportNext)
    val roll = remember { Animatable(taps.toFloat()) }
    LaunchedEffect(taps) { roll.animateTo(taps.toFloat(), spring(dampingRatio = 0.5f, stiffness = 420f, visibilityThreshold = SETTLED)) }
    // Each triangle's furthest point along so far, so one that has gone off
    // the front stays gone when the roll bounces back.
    val furthest = remember { HashMap<Int, Float>() }
    val filter = ColorFilter.tint(tint)
    Canvas(modifier) {
        val o = roll.value
        val unit = size.width / 24f
        val p = press.value
        val moving = abs(o - taps) > 0.0005f || abs(roll.velocity) > 0.01f
        withTransform({
            scale(p, p, center)
            if (!forward) scale(-1f, 1f, center)
        }) {
            if (!moving) {
                furthest.clear()
                with(painter) { draw(size, colorFilter = filter) }
                return@withTransform
            }
            // Stretched along the roll while it is quick, as if by its speed.
            val stretch = 1f + minOf(0.6f, abs(roll.velocity) * 0.09f)
            for (m in floor(minOf(o, taps.toFloat())).toInt() - 3..taps) {
                // Where triangle m is: 0 = the back place, 1 = the front one;
                // it grows in from -1 and shrinks away towards 2.
                val place = o - m
                val reached = maxOf(furthest[m] ?: -9f, place)
                furthest[m] = reached
                // Grown in by the furthest it has come, so the bounce never
                // shrinks a new one back; shrunk away only if it is on its way
                // out (bound past the front) — the one that stays at the front
                // overshoots at full size and is not shrunk by it.
                val leaving = taps - m >= 2
                val grown = (reached + 1f).coerceIn(0f, 1f) *
                    (if (leaving) 1f - (reached - 1f).coerceIn(0f, 1f) else 1f)
                if (grown <= 0.02f) continue
                withTransform({
                    translate(left = (place - 1f) * STEP * unit)
                    scale(grown * stretch, grown, Offset(FRONT_CENTER_X * unit, 12f * unit))
                    clipRect(left = SPLIT_X * unit)
                }) {
                    with(painter) { draw(size, colorFilter = filter) }
                }
            }
            furthest.keys.removeAll { it < taps - 4 }
        }
    }
}

/**
 * Play and pause, one into the other, as the triangle splitting into the bars.
 *
 * Each half is four rounded corners, every corner the cubic from where it
 * leaves one edge to where it joins the next, mixed point by point: a straight
 * edge stays straight however far a mix has got, and the ends are the icons'
 * own outlines (the play corners' arcs, the tip's own curve split at its
 * point, the bars' arcs), so nothing snaps at rest.
 *
 * The triangle is cut through its centroid, where its weight divides. While
 * the halves touch they are drawn as one outline — the cut is inside the
 * shape, so the long edges never step at it. They part like a liquid: a neck
 * pinches in from top and bottom and holds thick for a while, thins to a
 * point, and its last trace draws back along the bars' inner edges; joining,
 * the same backwards. They also keep together while they reshape and only
 * part near the end, so there is little time with a gap to see at all.
 */
private object PlayPauseShapes {
    private const val CUT = 11.045f
    private const val SLOPE = 0.5051f
    private fun topAt(x: Float) = 2.511f + SLOPE * (x - 4.783f)

    // Control distances: the play corners' 116.8° arcs of r 1.79, and the
    // bars' quarter arcs of r 1.625, as cubics.
    private const val KA = 1.334f
    private const val KB = 0.8975f
    private const val EDGE_X = 0.8926f
    private const val EDGE_Y = 0.4508f

    /** How far apart the halves are when the neck has gone: just short of the bars' own gap. */
    private const val PART_AT = (13.094f - 10.906f) * 0.93f
    /** How round the neck's sides are (0 a V, 1 square into the edges). */
    private const val NECK_ROUND = 0.7f

    private fun sharp(x: Float, y: Float) = floatArrayOf(x, y, x, y, x, y, x, y)

    private val leftPlay = arrayOf(
        floatArrayOf(4.783f, 5.421f, 4.783f, 5.421f - KA, 7.381f - KA * EDGE_X, 3.823f - KA * EDGE_Y, 7.381f, 3.823f),
        sharp(CUT, topAt(CUT)),
        sharp(CUT, 24f - topAt(CUT)),
        floatArrayOf(7.381f, 20.177f, 7.381f - KA * EDGE_X, 20.177f + KA * EDGE_Y, 4.783f, 18.579f + KA, 4.783f, 18.579f),
    )
    private val rightPlay = arrayOf(
        sharp(CUT, topAt(CUT)),
        floatArrayOf(18.143f, 9.259f, 20.859f, 10.631f, 21.417f, 10.938f, 21.417f, 12f),
        floatArrayOf(21.417f, 12f, 21.417f, 13.062f, 20.859f, 13.369f, 18.143f, 14.741f),
        sharp(CUT, 24f - topAt(CUT)),
    )

    private fun bar(x0: Float): Array<FloatArray> {
        val x1 = x0 + 5.532f
        val r = 1.625f
        val y0 = 3.91f
        val y1 = 20.09f
        return arrayOf(
            floatArrayOf(x0, y0 + r, x0, y0 + r - KB, x0 + r - KB, y0, x0 + r, y0),
            floatArrayOf(x1 - r, y0, x1 - r + KB, y0, x1, y0 + r - KB, x1, y0 + r),
            floatArrayOf(x1, y1 - r, x1, y1 - r + KB, x1 - r + KB, y1, x1 - r, y1),
            floatArrayOf(x0 + r, y1, x0 + r - KB, y1, x0, y1 - r + KB, x0, y1 - r),
        )
    }

    private val leftBar = bar(5.374f)
    private val rightBar = bar(13.094f)

    private fun centreX(shape: Array<FloatArray>): Float {
        var sum = 0f
        shape.forEach { c -> for (k in 0 until 8 step 2) sum += c[k] }
        return sum / (shape.size * 4)
    }

    // How far each half drifts before it reshapes: from its own centre to its bar's.
    private val leftDrift = centreX(leftBar) - centreX(leftPlay)
    private val rightDrift = centreX(rightBar) - centreX(rightPlay)

    private val left = Array(4) { FloatArray(8) }
    private val right = Array(4) { FloatArray(8) }

    private fun ease(t: Float): Float {
        val c = t.coerceIn(0f, 1f)
        return if (c < 0.5f) 2f * c * c else 1f - (-2f * c + 2f).let { it * it } / 2f
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    private fun mix(out: Array<FloatArray>, from: Array<FloatArray>, to: Array<FloatArray>, t: Float, drift: Float) {
        for (c in 0 until 4) for (k in 0 until 8 step 2) {
            out[c][k] = (from[c][k] + drift) * (1f - t) + to[c][k] * t
            out[c][k + 1] = from[c][k + 1] * (1f - t) + to[c][k + 1] * t
        }
    }

    private fun shift(shape: Array<FloatArray>, dx: Float) {
        for (c in shape) for (k in 0 until 8 step 2) c[k] += dx
    }

    /** Both halves at [a] (0 = play, 1 = pause), pushed [spread] apart, into [path] in icon units. */
    fun build(a: Float, spread: Float, path: Path) {
        mix(left, leftPlay, leftBar, ease((a - 0.35f) / 0.65f), leftDrift * ease(a / 0.45f))
        mix(right, rightPlay, rightBar, ease((a - 0.35f) / 0.65f), rightDrift * ease(a / 0.45f))
        // Held together while they reshape; they let go only over the last stretch.
        val gapNow = minOf(right[0][0] - left[1][6], right[3][6] - left[2][0])
        val hold = gapNow.coerceAtLeast(0f) * (1f - ease((a - 0.62f) / 0.38f)) / 2f
        shift(left, hold - spread)
        shift(right, -hold + spread)

        // The inner corners: left's top and bottom, right's top and bottom.
        val ltx = left[1][6]; val lty = left[1][7]
        val lbx = left[2][0]; val lby = left[2][1]
        val rtx = right[0][0]; val rty = right[0][1]
        val rbx = right[3][6]; val rby = right[3][7]
        val gap = minOf(rtx - ltx, rbx - lbx)
        val n = (gap / PART_AT).coerceIn(0f, 1f) * 2f

        path.reset()
        if (n >= 1.999f) {
            addClosed(path, left)
            addClosed(path, right)
            return
        }
        val pinch = n.coerceIn(0f, 1f)
        val slide = ease(n - 1f)
        // Thick for a while, then thinning quickly to the point.
        val depth = pinch.pow(2.2f)
        val bend = maxOf(depth, pinch * 0.6f)
        val apexTop = lerp((lty + rty) / 2f, 12f, depth)
        val apexBottom = lerp((lby + rby) / 2f, 12f, depth)
        val laty = lerp(lty, 12f, slide)
        val raty = lerp(rty, 12f, slide)
        val laby = lerp(lby, 12f, slide)
        val raby = lerp(rby, 12f, slide)

        path.moveTo(left[0][0], left[0][1]); cubic(path, left[0])
        path.lineTo(left[1][0], left[1][1]); cubic(path, left[1])
        path.lineTo(ltx, laty)
        neck(path, ltx, laty, rtx, raty, apexTop, bend)
        path.lineTo(right[0][0], right[0][1]); cubic(path, right[0])
        for (c in 1..3) { path.lineTo(right[c][0], right[c][1]); cubic(path, right[c]) }
        path.lineTo(rbx, raby)
        neck(path, rbx, raby, lbx, laby, apexBottom, bend)
        path.lineTo(left[2][0], left[2][1]); cubic(path, left[2])
        path.lineTo(left[3][0], left[3][1]); cubic(path, left[3])
        path.close()
    }

    private fun cubic(path: Path, c: FloatArray) = path.cubicTo(c[2], c[3], c[4], c[5], c[6], c[7])

    private fun addClosed(path: Path, shape: Array<FloatArray>) {
        for (c in 0 until 4) {
            if (c == 0) path.moveTo(shape[c][0], shape[c][1]) else path.lineTo(shape[c][0], shape[c][1])
            cubic(path, shape[c])
        }
        path.close()
    }

    /**
     * One side of the neck, from (x0, y0) to (x1, y1) through its middle at
     * [apexY]: a straight line at [bend] 0, a rounded pinch at 1.
     */
    private fun neck(path: Path, x0: Float, y0: Float, x1: Float, y1: Float, apexY: Float, bend: Float) {
        val mid = (x0 + x1) / 2f
        fun along(t: Float, x: Boolean) = if (x) lerp(x0, x1, t) else lerp(y0, y1, t)
        path.cubicTo(
            lerp(along(1f / 6f, true), x0, bend), lerp(along(1f / 6f, false), lerp(y0, apexY, NECK_ROUND), bend),
            lerp(along(1f / 3f, true), lerp(x0, mid, 1f - NECK_ROUND), bend), lerp(along(1f / 3f, false), apexY, bend),
            mid, apexY,
        )
        path.cubicTo(
            lerp(along(2f / 3f, true), lerp(x1, mid, 1f - NECK_ROUND), bend), lerp(along(2f / 3f, false), apexY, bend),
            lerp(along(5f / 6f, true), x1, bend), lerp(along(5f / 6f, false), lerp(y1, apexY, NECK_ROUND), bend),
            x1, y1,
        )
    }
}

@Composable
private fun PlayPauseSplit(
    playing: Boolean,
    tint: Color,
    press: Animatable<Float, AnimationVector1D>,
    modifier: Modifier,
) {
    val playPainter = rememberVectorPainter(BitChordIcons.TransportPlay)
    val pausePainter = rememberVectorPainter(BitChordIcons.TransportPause)
    // 0 = play, 1 = pause.
    val shape = remember { Animatable(if (playing) 1f else 0f) }
    // How far the bars have sprung apart, in the icon's 24 units.
    val apart = remember { Animatable(0f) }
    val first = remember { booleanArrayOf(true) }
    LaunchedEffect(playing) {
        if (first[0]) {
            first[0] = false
            return@LaunchedEffect
        }
        launch { shape.animateTo(if (playing) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 900f, visibilityThreshold = SETTLED)) }
        if (playing) {
            // The bars' spring-apart, kept in step with the quicker shape spring.
            apart.snapTo(0f)
            apart.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = 710f, visibilityThreshold = SETTLED), initialVelocity = 18f)
        }
    }
    val joined = remember { Path() }
    Canvas(modifier) {
        val raw = shape.value
        val a = raw.coerceIn(0f, 1f)
        val target = if (playing) 1f else 0f
        val moving = abs(raw - target) > 0.0005f || abs(shape.velocity) > 0.01f ||
            abs(apart.value) > 0.01f || abs(apart.velocity) > 0.01f
        val swell = 1f + abs(raw - a) * 0.6f
        val s = press.value * swell
        withTransform({ scale(s, s, center) }) {
            if (!moving) {
                with(if (playing) pausePainter else playPainter) { draw(size, colorFilter = ColorFilter.tint(tint)) }
                return@withTransform
            }
            val unit = size.width / 24f
            val spread = apart.value.coerceAtLeast(0f) * a
            // Both halves in one path, filled once.
            PlayPauseShapes.build(a, spread, joined)
            withTransform({ scale(unit, unit, Offset.Zero) }) {
                drawPath(joined, tint)
            }
        }
    }
}
