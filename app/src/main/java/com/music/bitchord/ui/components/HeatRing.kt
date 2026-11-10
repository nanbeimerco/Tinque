package com.music.bitchord.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Heat round a tile (the heat_tile v2 mock's E1, with v3's pattern 8):
 *
 *  - A line round the tile whose length is the level out of 5, starting at the
 *    top centre and running clockwise; the rest is a faint track, with a gap at
 *    each end of the line (an M3 progress indicator's shape). 0 is track only.
 *  - Straight below 4.8; from 4.8 it waves and the wave flows, on the shared
 *    [HeatClock] — like the meter.
 *  - It lives inside the tile's own bounds: the artwork is drawn [HeatRingInset]
 *    smaller, so the wave never reaches the next tile and grids keep their
 *    spacing. With heat off the modifier is not applied and the art is full size.
 */

/** How much smaller the artwork is drawn to make room for the ring. */
val HeatRingInset = 6.5.dp
private val RING_STROKE = 2.5.dp
private val RING_AMPLITUDE = 1.2.dp
private val RING_GAP = 7.dp
private val RING_WAVELENGTH_4 = 20.dp
private val RING_WAVELENGTH_5 = 16.dp

/**
 * Draws [level]'s ring in the outer [HeatRingInset] of this element and pads
 * what follows by it. [shape] is the artwork's shape (the ring follows it).
 * [level] null: nothing at all — heat is off.
 */
@Composable
fun Modifier.heatRing(
    level: Float?,
    shape: Shape,
    gated: Boolean = true,
    /**
     * The tile's own motion speaks for it (the playing song's turning cookie):
     * the line stays straight and still at any heat, its length still the level.
     */
    still: Boolean = false,
): Modifier {
    if (level == null) return this
    val hue = rememberHeatHue()
    val reduceAnimation by AppSettings.reduceAnimation.collectAsStateWithLifecycle()
    val flows = level >= HEAT_FLOW_FROM && !reduceAnimation && !still
    HeatFlowSubscription(flows, gated)
    val color = heatColor(level, hue)
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
    val period = heatFlowPeriod(level)
    return this
        .drawWithCache {
            val inset = HeatRingInset.toPx()
            val stroke = RING_STROKE.toPx()
            val amplitude = RING_AMPLITUDE.toPx()
            // The line's centre sits this far in from the edge, so the wave's
            // outer reach stays inside the bounds.
            val edge = amplitude + stroke / 2f
            val ring = Size(size.width - 2 * edge, size.height - 2 * edge)
            var generic = false
            val path = if (ring.width <= 0f || ring.height <= 0f) Path() else {
                // Concentric with the artwork: its corner radius plus the space between.
                val art = Size(size.width - 2 * inset, size.height - 2 * inset)
                when (val outline = shape.createOutline(art, layoutDirection, this)) {
                    is Outline.Rounded -> roundedRing(ring, outline.roundRect.topLeftCornerRadius.x + (inset - edge))
                    is Outline.Rectangle -> roundedRing(ring, inset - edge)
                    is Outline.Generic -> {
                        val outlined = shape.createOutline(ring, layoutDirection, this) as? Outline.Generic
                        generic = outlined != null
                        outlined?.path?.let { p -> Path().apply { addPath(p) } } ?: roundedRing(ring, inset - edge)
                    }
                }.apply { translate(Offset(edge, edge)) }
            }
            val measure = PathMeasure().apply { setPath(path, false) }
            val total = measure.length
            val gap = RING_GAP.toPx()
            val full = level >= 4.995f
            val lit = level > 0.05f
            val fill = if (full) total else total * (level / 5f).coerceIn(0f, 1f)

            // Every shape's line starts at the top centre and runs clockwise.
            // An M3 Expressive shape's own outline starts wherever its polygon
            // does (bottom right for the smooth square) and may run either way,
            // so find where a line straight up from the centre crosses it — not
            // the point nearest the top, which hops between a turning cookie's
            // lobes and makes the line jump.
            var origin = 0f
            var direction = 1f
            if (total > 0f && generic) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                fun offset(s: Float): Float {
                    val p = measure.getPosition(s)
                    var d = kotlin.math.atan2(p.y - cy, p.x - cx) + (PI.toFloat() / 2f)
                    while (d > PI) d -= 2f * PI.toFloat()
                    while (d < -PI) d += 2f * PI.toFloat()
                    return d
                }
                val probe = 2f
                var s = 0f
                var previous = offset(0f)
                while (s < total) {
                    val next = min(total, s + probe)
                    val current = offset(next)
                    // A sign change across "straight up" (not the wrap at straight down).
                    if (previous * current <= 0f && kotlin.math.abs(current - previous) < 1f) {
                        val t = if (current == previous) 0f else -previous / (current - previous)
                        origin = s + (next - s) * t
                        break
                    }
                    previous = current
                    s = next
                }
                // Clockwise on screen goes right from the top.
                direction = if (measure.getPosition(wrap(origin + probe, total)).x >= measure.getPosition(origin).x) 1f else -1f
            }
            fun along(d: Float): Offset = measure.getPosition(wrap(origin + direction * d, total))
            fun polyline(from: Float, to: Float, close: Boolean = false): Path = Path().apply {
                var d = from
                var first = true
                while (d < to) {
                    val p = along(d)
                    if (first) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                    first = false
                    d += 1.5f
                }
                val end = along(to)
                if (first) moveTo(end.x, end.y) else lineTo(end.x, end.y)
                if (close) close()
            }

            val trackPath = when {
                !lit -> polyline(0f, total, close = true)
                !full && fill + gap < total - gap -> polyline(fill + gap, total - gap)
                else -> Path()
            }
            val linePath = if (lit && !flows) polyline(0f, fill, close = full) else Path()

            // The wave: points and normals along the line, worked out once;
            // each frame only moves them.
            val step = 1.5f
            val count = if (lit && flows) max(2, (fill / step).toInt() + 1) else 0
            val xs = FloatArray(count)
            val ys = FloatArray(count)
            val nxs = FloatArray(count)
            val nys = FloatArray(count)
            val dist = FloatArray(count)
            for (i in 0 until count) {
                val s = min(fill, i * step)
                val p = along(s)
                val q = along(min(fill + 0.5f, s + 0.5f))
                val dx = q.x - p.x
                val dy = q.y - p.y
                val l = kotlin.math.sqrt(dx * dx + dy * dy).takeIf { it > 0f } ?: 1f
                xs[i] = p.x; ys[i] = p.y
                nxs[i] = -dy / l; nys[i] = dx / l
                dist[i] = s
            }
            val target = (if (level >= 5f - 1e-3f) RING_WAVELENGTH_5 else RING_WAVELENGTH_4).toPx()
            // A whole number of crests round a full ring, so it meets itself.
            val wavelength = if (full) total / max(1, (total / target).roundToInt()) else target
            val taperLen = stroke * 2f
            val wave = Path()
            val style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)

            onDrawWithContent {
                drawPath(trackPath, track, style = style)
                if (count > 0) {
                    val phase = HeatClock.phase(period, gated)
                    wave.reset()
                    for (i in 0 until count) {
                        val s = dist[i]
                        val taper = if (full) 1f else min(1f, min(s / taperLen, (fill - s) / taperLen)).coerceAtLeast(0f)
                        val off = sin(s / wavelength * 2f * PI.toFloat() - phase) * amplitude * taper
                        val x = xs[i] + nxs[i] * off
                        val y = ys[i] + nys[i] * off
                        if (i == 0) wave.moveTo(x, y) else wave.lineTo(x, y)
                    }
                    if (full) wave.close()
                    drawPath(wave, color, style = style)
                } else if (lit) {
                    drawPath(linePath, color, style = style)
                }
                drawContent()
            }
        }
        .padding(HeatRingInset)
}

private fun wrap(s: Float, total: Float): Float = if (total <= 0f) 0f else ((s % total) + total) % total

/** A rounded rectangle from its top centre, clockwise, so a part of it reads as progress. */
private fun roundedRing(size: Size, radius: Float): Path {
    val w = size.width
    val h = size.height
    val r = radius.coerceIn(0.01f, min(w, h) / 2f)
    return Path().apply {
        moveTo(w / 2f, 0f)
        lineTo(w - r, 0f)
        arcTo(Rect(w - 2 * r, 0f, w, 2 * r), -90f, 90f, false)
        lineTo(w, h - r)
        arcTo(Rect(w - 2 * r, h - 2 * r, w, h), 0f, 90f, false)
        lineTo(r, h)
        arcTo(Rect(0f, h - 2 * r, 2 * r, h), 90f, 90f, false)
        lineTo(0f, r)
        arcTo(Rect(0f, 0f, 2 * r, 2 * r), 180f, 90f, false)
        lineTo(w / 2f, 0f)
        close()
    }
}
