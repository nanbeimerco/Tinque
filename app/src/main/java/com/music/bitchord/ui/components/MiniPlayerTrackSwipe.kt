package com.music.bitchord.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import com.music.bitchord.data.model.Song
import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.ui.haptics.rememberHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Past this share of the width (or a fling), releasing commits the change. */
private const val COMMIT_FRACTION = 0.35f

/** Fling speed (px/s) that commits regardless of distance. */
private const val COMMIT_VELOCITY = 900f

/** How far the content follows the finger when there is no track that way. */
private const val EDGE_RESISTANCE = 0.25f

/** How wide the soft fade is where the swiping tracks meet the buttons. */
private val END_FADE = 20.dp

/**
 * The mini player's artwork + title area, swipeable through the queue.
 *
 * The current track follows the finger sideways and the neighbouring track —
 * [next] when dragging left, [previous] when dragging right — slides in from
 * the opposite edge, all clipped to this area so the buttons beside it stay
 * put. Let go past a third of the width (or with a flick) and it carries on
 * to the neighbour and changes track; otherwise it springs back.
 *
 * With no track that way the content only stretches a little and springs
 * back; a right swipe with no [previous] still calls [onPrevious] when it
 * crosses the threshold, since "previous" also restarts the current track.
 *
 * After a committed swipe the incoming track keeps showing until [current]
 * actually becomes it, so the area never flashes back to the old track while
 * the player catches up.
 *
 * @param content draws one track's artwork and titles; [isCurrent] is true for
 * the track that is really playing (the one the shared-element dock follows).
 */
@Composable
internal fun MiniPlayerTrackSwipe(
    current: Song,
    previous: Song?,
    next: Song?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * The bar's own padding left of this area. The area reaches out over it,
     * so a sliding track runs on to the bar's rounded end (where the bar's own
     * clip takes it) instead of stopping at a straight line inside it.
     */
    startBleed: Dp = 0.dp,
    content: @Composable (song: Song, isCurrent: Boolean) -> Unit,
) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var widthPx by remember { mutableIntStateOf(0) }
    // The track shown at rest: normally [current], or the one just swiped to.
    var pending by remember { mutableStateOf<Song?>(null) }
    val shown = pending ?: current

    val currentPrevious by rememberUpdatedState(previous)
    val currentNext by rememberUpdatedState(next)
    val currentOnPrevious by rememberUpdatedState(onPrevious)
    val currentOnNext by rememberUpdatedState(onNext)

    // The player caught up: drop the placeholder. A timeout covers a change
    // that never lands (an error, or the queue changing under us).
    LaunchedEffect(current.videoId, pending?.videoId) {
        val target = pending ?: return@LaunchedEffect
        if (current.videoId == target.videoId) {
            pending = null
        } else {
            delay(2_500)
            pending = null
        }
    }

    // Only while a track is moving do its edges fade; at rest the title ends
    // in its ellipsis like any other text.
    val moving = offset.value != 0f || offset.isRunning
    val fade by animateFloatAsState(if (moving) 1f else 0f, label = "swipeEdgeFade")
    val density = LocalDensity.current
    val bleedPx = with(density) { startBleed.roundToPx() }
    val endFadePx = with(density) { END_FADE.toPx() }

    Layout(
        modifier = modifier
            // Grow left over the bar's padding; the pages are inset by the same
            // amount, so at rest nothing moves.
            .layout { measurable, constraints ->
                val wide = if (constraints.hasBoundedWidth) constraints.maxWidth + bleedPx else constraints.maxWidth
                val placeable = measurable.measure(
                    constraints.copy(
                        minWidth = (constraints.minWidth + bleedPx).coerceAtMost(wide),
                        maxWidth = wide,
                    ),
                )
                layout((placeable.width - bleedPx).coerceAtLeast(0), placeable.height) {
                    placeable.place(-bleedPx, 0)
                }
            }
            .clipToBounds()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                if (fade > 0f) {
                    val w = size.width
                    val left = bleedPx.toFloat().coerceAtLeast(1f)
                    val edge = Color.Black.copy(alpha = 1f - fade)
                    // Left: thin out across the bleed, into the bar's round end.
                    drawRect(
                        brush = Brush.horizontalGradient(0f to edge, 1f to Color.Black, startX = 0f, endX = left),
                        topLeft = Offset.Zero,
                        size = size.copy(width = left),
                        blendMode = BlendMode.DstIn,
                    )
                    // Right: a soft run into the transport buttons.
                    drawRect(
                        brush = Brush.horizontalGradient(
                            0f to Color.Black,
                            1f to edge,
                            startX = w - endFadePx,
                            endX = w,
                        ),
                        topLeft = Offset(w - endFadePx, 0f),
                        size = size.copy(width = endFadePx),
                        blendMode = BlendMode.DstIn,
                    )
                }
            }
            // A page travels the whole area, bleed included, so one leaving fully clears it.
            .onSizeChanged { widthPx = it.width }
            .pointerInput(Unit) {
                val tracker = VelocityTracker()
                detectHorizontalDragGestures(
                    onDragStart = { tracker.resetTracking() },
                    onDragEnd = {
                        val width = widthPx.toFloat().coerceAtLeast(1f)
                        val velocity = tracker.calculateVelocity().x
                        val dragged = offset.value
                        val wantsNext = dragged < 0f &&
                            (abs(dragged) > width * COMMIT_FRACTION || velocity < -COMMIT_VELOCITY)
                        val wantsPrevious = dragged > 0f &&
                            (abs(dragged) > width * COMMIT_FRACTION * (if (currentPrevious == null) EDGE_RESISTANCE else 1f) ||
                                velocity > COMMIT_VELOCITY)
                        scope.launch {
                            when {
                                wantsNext && currentNext != null -> {
                                    haptics.play(Haptic.SkipNext)
                                    offset.animateTo(-width, tween(180))
                                    pending = currentNext
                                    currentOnNext()
                                    offset.snapTo(0f)
                                }
                                wantsPrevious && currentPrevious != null -> {
                                    haptics.play(Haptic.SkipPrevious)
                                    offset.animateTo(width, tween(180))
                                    pending = currentPrevious
                                    currentOnPrevious()
                                    offset.snapTo(0f)
                                }
                                wantsPrevious -> {
                                    // Nothing before this track: "previous" restarts it.
                                    haptics.play(Haptic.SkipPrevious)
                                    currentOnPrevious()
                                    offset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                }
                                else -> offset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch { offset.animateTo(0f, spring()) }
                    },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        tracker.addPosition(change.uptimeMillis, change.position)
                        val towardsNext = offset.value + amount < 0f
                        val hasNeighbour = if (towardsNext) currentNext != null else currentPrevious != null
                        val step = if (hasNeighbour) amount else amount * EDGE_RESISTANCE
                        scope.launch { offset.snapTo(offset.value + step) }
                    },
                )
            },
        content = {
            val width = widthPx.toFloat()
            val dx = offset.value
            val inset = Modifier.padding(start = startBleed)
            // Page 0 is always the track at rest: it alone sets the height.
            Box(inset.fillMaxWidth().graphicsLayer { translationX = dx }) {
                content(shown, pending == null)
            }
            if (dx < 0f && next != null && pending == null) {
                Box(inset.fillMaxWidth().graphicsLayer { translationX = dx + width }) { content(next, false) }
            }
            if (dx > 0f && previous != null && pending == null) {
                Box(inset.fillMaxWidth().graphicsLayer { translationX = dx - width }) { content(previous, false) }
            }
        },
    ) { measurables, constraints ->
        // Sized by the track at rest only: a neighbour whose title happens to
        // set a taller line (another script, an emoji) cannot stretch the bar
        // while it slides through.
        val loose = constraints.copy(minHeight = 0)
        val placeables = measurables.map { it.measure(loose) }
        val height = (placeables.firstOrNull()?.height ?: 0)
            .coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(constraints.maxWidth, height) {
            placeables.forEach { it.place(0, (height - it.height) / 2) }
        }
    }
}

/**
 * One line of text at a fixed height, the style's own line height, centred
 * in it, so a title in another script or with an emoji takes exactly the same
 * room as any other and the mini player never changes height.
 */
@Composable
internal fun Modifier.fixedLineHeight(style: TextStyle): Modifier {
    val density = LocalDensity.current
    val height = with(density) {
        (if (style.lineHeight.isSpecified) style.lineHeight else style.fontSize * 1.4f).toDp()
    }
    return this.height(height).wrapContentHeight(Alignment.CenterVertically, unbounded = true)
}

/** [this] style with its line box centred and untrimmed, for [fixedLineHeight]. */
internal fun TextStyle.centeredLine(): TextStyle = copy(
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)
