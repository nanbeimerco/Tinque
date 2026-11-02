package com.music.bitchord.feature.localmusic.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** How many picked songs show behind the one held; the rest are a "+N". */
private const val STACK_SHOWN_BEHIND = 2

/**
 * A picked group dragged in the grid, drawn as one stack over the page.
 *
 * On lifting, the other picked songs leave their places (the grid closes up
 * behind them) and fly in under the finger, settling behind the held one,
 * each a little lower right and turned a little. On dropping, the stack comes
 * apart where it landed and each song springs, one after another, into its
 * new place. Positions are in the grid's own coordinates.
 */
class GroupDragStack {
    /** Held first, then the others in their order. Empty when not dragging a group. */
    var songs by mutableStateOf<List<Song>>(emptyList())
        private set
    var tileSize by mutableStateOf(IntSize.Zero)
        private set
    var finger by mutableStateOf(Offset.Zero)
    var landing by mutableStateOf(false)
        private set

    private var leadStart = Offset.Zero
    private var fingerStart = Offset.Zero
    private var starts: Map<String, Offset> = emptyMap()
    private val gather = Animatable(0f)
    private val land = mutableStateMapOf<String, Float>()
    private var landFrom: Map<String, Offset> = emptyMap()
    private var landTo: Map<String, Offset?> = emptyMap()
    var stepPx: Float = 0f

    val active: Boolean get() = songs.isNotEmpty()

    /** Songs drawn by the stack, so their own tiles stay hidden. */
    fun covers(key: String): Boolean = songs.any { keyOf(it) == key }

    suspend fun begin(
        held: Song,
        others: List<Song>,
        heldStart: Offset,
        size: IntSize,
        startOf: (String) -> Offset?,
        fingerAt: Offset,
    ) {
        leadStart = heldStart
        fingerStart = fingerAt
        finger = fingerAt
        tileSize = size
        starts = others.associate { keyOf(it) to (startOf(keyOf(it)) ?: heldStart) }
        land.clear()
        landing = false
        songs = listOf(held) + others
        gather.snapTo(0f)
        gather.animateTo(1f, spring(0.62f, 600f))
    }

    /**
     * Comes apart: each song goes from the stack to where [placeOf] says it now
     * sits (null: off screen, so it just fades), one after another. Runs
     * [onDone] once all have landed.
     */
    suspend fun drop(placeOf: (String) -> Offset?, onDone: () -> Unit) {
        landFrom = songs.mapIndexed { i, song -> keyOf(song) to stackPos(i) }.toMap()
        withFrameNanos { }
        withFrameNanos { }
        landTo = songs.associate { keyOf(it) to placeOf(keyOf(it)) }
        songs.forEach { land[keyOf(it)] = 0f }
        landing = true
        coroutineScope {
            songs.forEachIndexed { i, song ->
                val key = keyOf(song)
                launch {
                    delay(i * 45L)
                    val anim = Animatable(0f)
                    anim.animateTo(1f, spring(0.6f, 700f)) { land[key] = value }
                }
            }
        }
        songs = emptyList()
        landing = false
        land.clear()
        onDone()
    }

    private fun heldPos(): Offset = leadStart + (finger - fingerStart)

    private fun stackPos(i: Int): Offset {
        val behind = i.coerceAtMost(STACK_SHOWN_BEHIND)
        return heldPos() + Offset(behind * stepPx, behind * stepPx)
    }

    fun positionOf(i: Int, song: Song): Offset {
        val key = keyOf(song)
        if (landing) {
            val from = landFrom[key] ?: return heldPos()
            val to = landTo[key] ?: from
            val t = land[key] ?: 0f
            return from + (to - from) * t
        }
        if (i == 0) return heldPos()
        val from = starts[key] ?: heldPos()
        return from + (stackPos(i) - from) * gather.value
    }

    fun alphaOf(i: Int, song: Song): Float {
        val key = keyOf(song)
        if (landing) {
            val t = land[key] ?: 0f
            return if (landTo[key] == null) 1f - t else 1f
        }
        // Past the shown few, a song melts into the stack as it arrives.
        return if (i <= STACK_SHOWN_BEHIND) 1f else 1f - gather.value
    }

    private fun keyOf(song: Song) = song.localUri ?: song.videoId
}

/** Draws [stack] over the grid, with [tile] drawing one song's card. */
@Composable
fun GroupDragStackOverlay(
    stack: GroupDragStack,
    tile: @Composable (Song, Modifier) -> Unit,
) {
    if (!stack.active) return
    val density = LocalDensity.current
    stack.stepPx = with(density) { 7.dp.toPx() }
    val songs = stack.songs
    val width = with(density) { stack.tileSize.width.toDp() }
    val height = with(density) { stack.tileSize.height.toDp() }
    Box(Modifier.fillMaxSize().zIndex(5f)) {
        // Back to front, so the held song is on top.
        for (i in songs.indices.reversed()) {
            val song = songs[i]
            val behind = i.coerceAtMost(STACK_SHOWN_BEHIND)
            tile(
                song,
                Modifier
                    .offset {
                        val p = stack.positionOf(i, song)
                        IntOffset(p.x.roundToInt(), p.y.roundToInt())
                    }
                    .size(width, height)
                    .graphicsLayer {
                        alpha = stack.alphaOf(i, song)
                        val lifted = !stack.landing
                        val lift = if (lifted) 1.06f else 1f
                        scaleX = lift
                        scaleY = lift
                        rotationZ = if (lifted) behind * (if (i % 2 == 0) -3f else 3f) else 0f
                        shadowElevation = if (lifted) (12 - behind * 3).dp.toPx() else 0f
                        shape = RoundedCornerShape(16.dp)
                    },
            )
        }
        val extra = songs.size - 1 - STACK_SHOWN_BEHIND
        if (extra > 0 && !stack.landing) {
            val p = stack.positionOf(0, songs[0])
            Box(
                modifier = Modifier
                    .offset { IntOffset((p.x + stack.tileSize.width).roundToInt() - 22.dp.roundToPx(), p.y.roundToInt() - 8.dp.roundToPx()) }
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+$extra",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

/** Where [coordinates]' top left sits in [grid]'s coordinates, if both are attached. */
fun positionIn(grid: LayoutCoordinates?, coordinates: LayoutCoordinates?): Offset? {
    if (grid == null || coordinates == null || !grid.isAttached || !coordinates.isAttached) return null
    return grid.localPositionOf(coordinates, Offset.Zero)
}
