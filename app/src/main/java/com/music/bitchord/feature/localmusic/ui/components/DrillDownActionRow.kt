package com.music.bitchord.feature.localmusic.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.LibraryViewType
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.icons.BitChordIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** M3 Expressive "fast spatial" spring, for the buttons' squash and the icon morph. */
private const val FAST_DAMPING = 0.6f
private const val FAST_STIFFNESS = 800f

private val BUTTON_HEIGHT = 52.dp
private val ROUND = 26.dp
private val SQUARED = 14.dp

/**
 * The buttons under a collection's header — shuffle, play, list/grid, move
 * (playlists) and more — as one M3 Expressive button group across the page.
 *
 * Play is the widest and the one solid button: white on a dark page, with the
 * page's own colour for its ink, and the other way round on a light one. A
 * press squashes the pressed button's corners and lets it grow a little while
 * its neighbours give way, springing back on release. Move is a toggle: it
 * squares off and takes the accent colour while on.
 *
 * The list/grid button looks the same in either view (neither is the special
 * one); its icon morphs between the two layouts — see [ViewTypeMorphIcon].
 */
@Composable
fun DrillDownActionRow(
    songs: List<Song>,
    viewType: LibraryViewType? = null,
    onViewTypeToggle: (() -> Unit)? = null,
    onSongClick: (List<Song>, Int) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    onMore: (() -> Unit)? = null,
    reorderEnabled: Boolean = false,
    onReorderToggle: (() -> Unit)? = null,
    /** The colour of the page behind the row; the play button is cut from it. */
    pageColor: Color? = null,
    /**
     * The list/grid icon's animation, kept by the page: switching views builds
     * this row anew, and the icon would otherwise start in its new shape.
     */
    viewMorph: List<Animatable<Float, androidx.compose.animation.core.AnimationVector1D>>? = null,
    modifier: Modifier = Modifier,
) {
    val page = pageColor ?: MaterialTheme.colorScheme.background
    val darkPage = page.luminance() < 0.5f
    val playContainer = if (darkPage) Color.White else androidx.compose.ui.graphics.lerp(page, Color.Black, 0.82f)
    val playInk = if (darkPage) androidx.compose.ui.graphics.lerp(page, Color.Black, 0.35f) else Color.White
    val tonal = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    val tonalBorder = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val tonalInk = MaterialTheme.colorScheme.onSurface

    val shuffleSource = remember { MutableInteractionSource() }
    val playSource = remember { MutableInteractionSource() }
    val viewSource = remember { MutableInteractionSource() }
    val moveSource = remember { MutableInteractionSource() }
    val moreSource = remember { MutableInteractionSource() }
    val pressed = listOf(
        shuffleSource.collectIsPressedAsState().value,
        playSource.collectIsPressedAsState().value,
        viewSource.collectIsPressedAsState().value,
        moveSource.collectIsPressedAsState().value,
        moreSource.collectIsPressedAsState().value,
    )
    val pressedIndex = pressed.indexOfFirst { it }

    // One element (row and the gap under it), so a grid, which overlays an
    // item's separate pieces instead of stacking them, keeps the gap too.
    androidx.compose.foundation.layout.Column(modifier = modifier) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PAGE_GUTTER, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GroupButton(
            index = 0, baseWeight = 1f, pressedIndex = pressedIndex, source = shuffleSource,
            container = tonal, border = tonalBorder,
            label = stringResource(R.string.shuffle),
            onClick = { if (songs.isNotEmpty()) onShuffle(songs) },
        ) {
            Icon(
                com.music.bitchord.ui.icons.PlayerControlIcons.Shuffle,
                contentDescription = null,
                tint = tonalInk,
                modifier = Modifier.size(18.dp),
            )
        }
        GroupButton(
            index = 1, baseWeight = 2.4f, pressedIndex = pressedIndex, source = playSource,
            container = playContainer, border = null,
            label = null,
            onClick = { if (songs.isNotEmpty()) onSongClick(songs, 0) },
        ) {
            Icon(BitChordIcons.TransportPlay, contentDescription = null, tint = playInk, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.play),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = playInk,
                maxLines = 1,
            )
        }
        if (onViewTypeToggle != null && viewType != null) {
            val grid = viewType == LibraryViewType.GRID
            GroupButton(
                index = 2, baseWeight = 1f, pressedIndex = pressedIndex, source = viewSource,
                container = tonal, border = tonalBorder,
                label = stringResource(if (grid) R.string.switch_to_list_view else R.string.switch_to_grid_view),
                onClick = onViewTypeToggle,
            ) {
                ViewTypeMorphIcon(grid = grid, color = tonalInk, modifier = Modifier.size(20.dp), state = viewMorph)
            }
        }
        if (onReorderToggle != null) {
            GroupButton(
                index = 3, baseWeight = 1f, pressedIndex = pressedIndex, source = moveSource,
                container = if (reorderEnabled) MaterialTheme.colorScheme.primaryContainer else tonal,
                border = if (reorderEnabled) null else tonalBorder,
                toggled = reorderEnabled,
                label = stringResource(R.string.toggle_playlist_reorder),
                onClick = onReorderToggle,
            ) {
                Icon(
                    Icons.Rounded.OpenWith,
                    contentDescription = null,
                    tint = if (reorderEnabled) MaterialTheme.colorScheme.onPrimaryContainer else tonalInk,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (onMore != null) {
            GroupButton(
                index = 4, baseWeight = 1f, pressedIndex = pressedIndex, source = moreSource,
                container = tonal, border = tonalBorder,
                label = stringResource(R.string.more),
                onClick = onMore,
            ) {
                Icon(Icons.Rounded.MoreHoriz, contentDescription = null, tint = tonalInk, modifier = Modifier.size(20.dp))
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    }
}

/** One button of [DrillDownActionRow]'s group. */
@Composable
private fun RowScope.GroupButton(
    index: Int,
    baseWeight: Float,
    pressedIndex: Int,
    source: MutableInteractionSource,
    container: Color,
    border: Color?,
    label: String?,
    onClick: () -> Unit,
    toggled: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val weight by animateFloatAsState(
        when (pressedIndex) {
            -1 -> baseWeight
            index -> baseWeight * 1.18f
            else -> baseWeight * 0.94f
        },
        spring(FAST_DAMPING, FAST_STIFFNESS),
        label = "groupButtonWeight",
    )
    val corner: Dp by animateDpAsState(
        if (pressedIndex == index || toggled) SQUARED else ROUND,
        spring(FAST_DAMPING, Spring.StiffnessMedium),
        label = "groupButtonCorner",
    )
    val shape = RoundedCornerShape(corner)
    Row(
        modifier = Modifier
            .weight(weight)
            .height(BUTTON_HEIGHT)
            .background(container, shape)
            .then(if (border != null) Modifier.border(0.5.dp, border, shape) else Modifier)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * The list/grid icon, drawn as four rounded rectangles that change places:
 * in the grid, four squares; in the list, the left two become small sleeves
 * and the right two the title lines beside them — the same items, laid out
 * the other way. Each moves a beat after the one before and settles on a
 * spring. The icon shows the view the page is in.
 */
@Composable
fun ViewTypeMorphIcon(
    grid: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    state: List<Animatable<Float, androidx.compose.animation.core.AnimationVector1D>>? = null,
) {
    val own = remember { List(4) { Animatable(if (grid) 0f else 1f) } }
    val progress = state ?: own
    LaunchedEffect(grid) {
        val target = if (grid) 0f else 1f
        progress.forEachIndexed { i, anim ->
            launch {
                delay(i * 30L)
                anim.animateTo(target, spring(0.5f, 700f))
            }
        }
    }
    Canvas(modifier) {
        val unit = size.minDimension / 22f
        for (i in 0 until 4) {
            val t = progress[i].value
            val from = GRID_RECTS[i]
            val to = LIST_RECTS[i]
            fun mix(a: Float, b: Float) = a + (b - a) * t
            val w = mix(from[2], to[2]).coerceAtLeast(0.5f)
            val h = mix(from[3], to[3]).coerceAtLeast(0.5f)
            drawRoundRect(
                color = color,
                topLeft = Offset(mix(from[0], to[0]) * unit, mix(from[1], to[1]) * unit),
                size = Size(w * unit, h * unit),
                cornerRadius = CornerRadius(mix(from[4], to[4]).coerceAtMost(minOf(w, h) / 2f) * unit),
            )
        }
    }
}

/** x, y, width, height, corner — in a 22-unit box. */
private val GRID_RECTS = listOf(
    floatArrayOf(0f, 0f, 10f, 10f, 3f),
    floatArrayOf(12f, 0f, 10f, 10f, 3f),
    floatArrayOf(0f, 12f, 10f, 10f, 3f),
    floatArrayOf(12f, 12f, 10f, 10f, 3f),
)
private val LIST_RECTS = listOf(
    floatArrayOf(0f, 1f, 8f, 8f, 2.5f),
    floatArrayOf(11f, 3.3f, 11f, 3.4f, 1.7f),
    floatArrayOf(0f, 13f, 8f, 8f, 2.5f),
    floatArrayOf(11f, 15.3f, 11f, 3.4f, 1.7f),
)
