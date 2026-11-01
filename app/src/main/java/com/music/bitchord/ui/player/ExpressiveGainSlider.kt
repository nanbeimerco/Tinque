package com.music.bitchord.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** The trim's warm fill, apart from the white of the system volume. */
val GainSliderActive = Color(0xFFFFE4C4)

/**
 * A Material 3 Expressive slider for a gain in dB: a thick track split around
 * a slim upright handle, large outer corners and small ones by the handle, a
 * stop dot at the top end, and a "−" set into the filled part.
 *
 * Moves by how far the finger moves (never jumping to it), in 0.1 dB steps, or
 * 0.01 dB once the finger strays well above or below the bar — a value bubble
 * over the handle says which. [onLongPress] etc. make a resting finger a long
 * press instead, as on the volume bar.
 */
@Composable
fun ExpressiveGainSlider(
    valueDb: Float,
    minDb: Float,
    maxDb: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    trackHeight: Dp = 20.dp,
    handleHeight: Dp = 34.dp,
    /** Where to put a small tick on the track (the trim's own 0 over levelling), if anywhere. */
    tickDb: Float? = null,
    fineLabel: String = "",
    activeColor: Color = GainSliderActive,
    inactiveColor: Color = Color.White.copy(alpha = 0.24f),
    handleColor: Color = Color.White,
    /** The "−" set into the filled part: for a cut, not for a level. */
    showCutIcon: Boolean = true,
    /** Text shown in the value bubble; the dB value by default. */
    bubble: (Float) -> String = ::formatDb,
    onLongPress: (() -> Unit)? = null,
    onLongPressDrag: ((deltaFraction: Float, fineDistancePx: Float) -> Unit)? = null,
    onLongPressEnd: (() -> Unit)? = null,
) {
    val density = LocalDensity.current
    var dragging by remember { mutableStateOf(false) }
    var fine by remember { mutableStateOf(false) }
    val latest = rememberUpdatedState(valueDb)
    val range = rememberUpdatedState(minDb to maxDb)
    val change = rememberUpdatedState(onValueChange)
    val finished = rememberUpdatedState(onValueChangeFinished)
    val longPress = rememberUpdatedState(onLongPress)
    val longPressDrag = rememberUpdatedState(onLongPressDrag)
    val longPressEnd = rememberUpdatedState(onLongPressEnd)
    val listener = remember {
        object : RelativeSliderListener {
            var held = 0f
            override fun onDragStart() {
                dragging = true
                held = latest.value
            }

            override fun onDrag(deltaFraction: Float, fineDistancePx: Float) {
                val (lo, hi) = range.value
                val factor = fineFactor(fineDistancePx, density.density)
                fine = factor < 1f
                held = (held + deltaFraction * (hi - lo) * factor).coerceIn(lo, hi)
                val step = if (fine) 100f else 10f
                change.value((held * step).roundToInt() / step)
            }

            override fun onDragEnd() {
                dragging = false
                fine = false
                finished.value?.invoke()
            }

            override fun onLongPress() { longPress.value?.invoke() }
            override fun onLongPressDrag(deltaFraction: Float, fineDistancePx: Float) {
                longPressDrag.value?.invoke(deltaFraction, fineDistancePx)
            }
            override fun onLongPressEnd() { longPressEnd.value?.invoke() }
        }
    }
    val handleWidth by animateFloatAsState(if (dragging) 3f else 4f, tween(120), label = "handle")
    val measurer = rememberTextMeasurer()
    val bubbleText = bubble(valueDb) + if (fine && fineLabel.isNotEmpty()) " · $fineLabel" else ""

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(handleHeight + 14.dp)
            .relativeSliderGestures(listener, longPressEnabled = onLongPress != null),
    ) {
        Canvas(Modifier.fillMaxWidth().height(handleHeight + 14.dp)) {
            val span = (maxDb - minDb).takeIf { it > 0f } ?: 1f
            val frac = ((valueDb - minDb) / span).coerceIn(0f, 1f)
            val th = trackHeight.toPx()
            val cy = size.height / 2f
            val gap = 6.dp.toPx()
            val hw = handleWidth.dp.toPx()
            val outer = CornerRadius(th / 2f)
            val inner = CornerRadius(2.dp.toPx())
            val hx = (size.width * frac).coerceIn(hw / 2f, size.width - hw / 2f)

            fun bar(left: Float, right: Float, leftRadius: CornerRadius, rightRadius: CornerRadius, color: Color) {
                if (right - left < 1f) return
                val rr = RoundRect(
                    left = left, top = cy - th / 2f, right = right, bottom = cy + th / 2f,
                    topLeftCornerRadius = leftRadius, topRightCornerRadius = rightRadius,
                    bottomRightCornerRadius = rightRadius, bottomLeftCornerRadius = leftRadius,
                )
                drawPath(Path().apply { addRoundRect(rr) }, color)
            }
            bar(0f, hx - gap - hw / 2f, outer, inner, activeColor)
            bar(hx + gap + hw / 2f, size.width, inner, outer, inactiveColor)
            // stop dot at the top end
            val dot = 2.dp.toPx()
            if (size.width - th / 2f > hx + gap + hw) {
                drawCircle(handleColor.copy(alpha = 0.8f), dot, Offset(size.width - th / 2f, cy))
            }
            tickDb?.let { t ->
                val tx = size.width * ((t - minDb) / span).coerceIn(0f, 1f)
                if (kotlin.math.abs(tx - hx) > gap * 2) {
                    drawRect(handleColor.copy(alpha = 0.55f), Offset(tx - 1.dp.toPx(), cy - th / 2f - 3.dp.toPx()), Size(2.dp.toPx(), 3.dp.toPx()))
                }
            }
            // the cut's "−", set into the filled part
            if (showCutIcon && hx - gap > 40.dp.toPx()) {
                val ix = 14.dp.toPx()
                drawRoundRect(
                    Color(0xFF5A3C28),
                    topLeft = Offset(ix - 6.dp.toPx(), cy - 1.2.dp.toPx()),
                    size = Size(12.dp.toPx(), 2.4.dp.toPx()),
                    cornerRadius = CornerRadius(1.2.dp.toPx()),
                )
            }
            // handle
            val hh = handleHeight.toPx()
            drawRoundRect(
                handleColor,
                topLeft = Offset(hx - hw / 2f, cy - hh / 2f),
                size = Size(hw, hh),
                cornerRadius = CornerRadius(hw / 2f),
            )
            if (dragging) {
                val layout = measurer.measure(bubbleText, TextStyle(color = Color(0xFFFFF0E1), fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
                val bw = layout.size.width + 20.dp.toPx()
                val bh = 30.dp.toPx()
                val bx = (hx - bw / 2f).coerceIn(0f, size.width - bw)
                val by = cy - hh / 2f - 4.dp.toPx() - bh
                drawRoundRect(Color(0xEB28221E), Offset(bx, by), Size(bw, bh), CornerRadius(bh / 2f))
                drawText(layout, topLeft = Offset(bx + (bw - layout.size.width) / 2f, by + (bh - layout.size.height) / 2f))
            }
        }
    }
}

/** "−3.27 dB", "0.00 dB", "+2.00 dB". */
fun formatDb(db: Float): String {
    val r = (db * 100f).roundToInt() / 100f
    return when {
        r < 0f -> "−%.2f dB".format(-r)
        r > 0f -> "+%.2f dB".format(r)
        else -> "0.00 dB"
    }
}
