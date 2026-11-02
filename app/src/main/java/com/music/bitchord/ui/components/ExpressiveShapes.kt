package com.music.bitchord.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.takeOrElse
import kotlinx.coroutines.launch
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.rectangle
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath

/**
 * The M3 Expressive shapes grid artwork uses, all normalised to a 0..1 box.
 *
 * - [smoothSquare]: every album, playlist and song at rest — a rounded square
 *   whose corners blend into the sides instead of meeting them at a seam.
 * - [selected]: a picked tile, rounder (it also shrinks, see [selectionArtScale]).
 * - [cookie]: the song playing now, a nine-lobed "cookie" that slowly turns.
 */
object ExpressiveShapes {
    val smoothSquare: RoundedPolygon =
        RoundedPolygon.rectangle(width = 2f, height = 2f, rounding = CornerRounding(0.36f, 0.8f)).normalized()
    val selected: RoundedPolygon =
        RoundedPolygon.rectangle(width = 2f, height = 2f, rounding = CornerRounding(0.78f, 1f)).normalized()
    val cookie: RoundedPolygon =
        RoundedPolygon.star(9, radius = 1f, innerRadius = 0.84f, rounding = CornerRounding(0.3f)).normalized()

    val toSelected = Morph(smoothSquare, selected)
    val toPlaying = Morph(smoothSquare, cookie)

    /** The resting shape, built once: most tiles never animate. */
    val Rest: Shape = PolygonShape(smoothSquare)
}

/** A fixed [RoundedPolygon] (0..1 box) stretched to whatever it clips. */
class PolygonShape(private val polygon: RoundedPolygon) : Shape {
    private val unit = polygon.toPath()
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = android.graphics.Path(unit)
        path.transform(android.graphics.Matrix().apply { setScale(size.width, size.height) })
        return Outline.Generic(path.asComposePath())
    }
}

/** [morph] at [progress], stretched to the box and turned by [rotation] degrees. */
class MorphShape(
    private val morph: Morph,
    private val progress: Float,
    private val rotation: Float = 0f,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress, android.graphics.Path())
        path.transform(
            android.graphics.Matrix().apply {
                setScale(size.width, size.height)
                if (rotation != 0f) postRotate(rotation, size.width / 2f, size.height / 2f)
            },
        )
        return Outline.Generic(path.asComposePath())
    }

    override fun equals(other: Any?): Boolean =
        other is MorphShape && other.morph === morph && other.progress == progress && other.rotation == rotation

    override fun hashCode(): Int = (morph.hashCode() * 31 + progress.hashCode()) * 31 + rotation.hashCode()
}

/** One full turn of the playing tile's cookie, in milliseconds. */
private const val SPIN_MS = 6_000

/**
 * The shape of a grid tile's artwork for its state: a smooth square at rest,
 * morphing (with a spring) to a rounder one when picked, or to a turning
 * cookie while it is the song playing. [spinning] turns the cookie; it holds
 * its angle when playback pauses.
 */
@Composable
fun rememberArtShape(selected: Boolean, playing: Boolean, spinning: Boolean): Shape {
    // Into a state: a spring with a little bounce. Back to rest: no overshoot,
    // so the shape never swings past the square and back (which read as a
    // flicker between two shapes).
    val selectedProgress = remember { Animatable(if (selected) 1f else 0f) }
    LaunchedEffect(selected) {
        selectedProgress.animateTo(
            if (selected) 1f else 0f,
            if (selected) spring(0.6f, Spring.StiffnessMediumLow) else spring(1f, Spring.StiffnessMediumLow),
        )
    }
    val showPlaying = playing && !selected
    val playingProgress = remember { Animatable(if (showPlaying) 1f else 0f) }
    val angle = remember { Animatable(0f) }
    LaunchedEffect(showPlaying) {
        if (showPlaying) {
            playingProgress.animateTo(1f, spring(0.6f, Spring.StiffnessMediumLow))
        } else {
            // Turn back square to the page while morphing: the nearest quarter
            // turn looks the same as no turn for the square it ends as.
            val rest = Math.round(angle.value / 90f) * 90f
            launch { angle.animateTo(rest, spring(1f, Spring.StiffnessMediumLow)) }
            playingProgress.animateTo(0f, spring(1f, Spring.StiffnessMediumLow))
            angle.snapTo(0f)
        }
    }
    LaunchedEffect(spinning && showPlaying) {
        if (!(spinning && showPlaying)) return@LaunchedEffect
        while (true) {
            val start = angle.value % 360f
            angle.snapTo(start)
            angle.animateTo(start + 360f, tween(SPIN_MS, easing = LinearEasing))
        }
    }
    val selectedNow = selectedProgress.value.coerceIn(0f, 1.2f)
    val playingNow = playingProgress.value.coerceIn(0f, 1.2f)
    return when {
        selectedProgress.isRunning || selectedNow > 0.001f -> MorphShape(ExpressiveShapes.toSelected, selectedNow)
        playingProgress.isRunning || angle.isRunning || playingNow > 0.001f ->
            MorphShape(ExpressiveShapes.toPlaying, playingNow, angle.value)
        else -> ExpressiveShapes.Rest
    }
}

/**
 * Shrinks a little while a finger is down and springs back on release — the
 * M3 Expressive press. Watches the touch without consuming it, so the
 * element's own click and long press still work.
 */
fun Modifier.pressBounce(pressedScale: Float = 0.94f): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (pressed) pressedScale else 1f,
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "pressBounce",
    )
    this
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                pressed = true
                // Let go on release, or as soon as anything else takes the
                // touch (the list starting to scroll, a drag): read on the
                // Final pass, after they have had their say.
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed || change.isConsumed) break
                }
                pressed = false
            }
        }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
}

/** The pinch ripple and the press bounce together, for a grid tile's root. */
val GridTileFeel: Modifier = Modifier.gridPulse().pressBounce()

/**
 * The colour a picked grid tile sits on. The theme's container colour by
 * default; a page tinted from its artwork provides one from the artwork, so
 * the platform does not clash with the sleeve behind it.
 */
val LocalSelectionPlatformColor = androidx.compose.runtime.compositionLocalOf { androidx.compose.ui.graphics.Color.Unspecified }

/**
 * A picked grid tile's platform (M3 Expressive selected state): a rounded
 * slab of colour behind the artwork that springs out as the tile is picked,
 * the artwork shrinking and rounding on top of it (see [selectionArtScale]
 * and [rememberArtShape]). Drawn, not laid out, so picking moves nothing.
 */
@Composable
fun Modifier.selectionPlatform(selected: Boolean): Modifier {
    val progress by animateFloatAsState(
        if (selected) 1f else 0f,
        spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label = "selectionPlatform",
    )
    val color = LocalSelectionPlatformColor.current.takeOrElse { androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer }
    return this.drawBehind {
        val p = progress.coerceIn(0f, 1.15f)
        if (p <= 0.01f) return@drawBehind
        val inset = (1f - p) * size.minDimension * 0.08f
        drawRoundRect(
            color = color.copy(alpha = color.alpha * p.coerceAtMost(1f)),
            topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
            size = Size(size.width - inset * 2, size.height - inset * 2),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.minDimension * 0.17f),
        )
    }
}

/** [selectionPlatform] for call sites that cannot import the extension by name. */
@Composable
fun selectionPlatformFeel(selected: Boolean): Modifier = Modifier.selectionPlatform(selected)
