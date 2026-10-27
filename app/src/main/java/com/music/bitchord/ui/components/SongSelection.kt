package com.music.bitchord.ui.components

import kotlinx.coroutines.delay
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.combinedClickable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.data.model.Song
import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.ui.haptics.rememberHaptics

/**
 * What "select all" covers on the page that is showing: its tracks, plus the
 * keys of any groups (albums) on it, so their cards read as selected too.
 */
@Immutable
class SelectionScope(val songs: List<Song>, val groupKeys: Set<String> = emptySet()) {
    companion object {
        val Empty = SelectionScope(emptyList())
    }
}

/**
 * The tracks picked by long-press, shared by every library page and the top
 * and bottom controls the activity draws for them.
 *
 * Held by the activity rather than by each page so the selection header (in
 * the top bar) and the action toolbar (over the bottom bar) can both act on it,
 * and so the Folders tab selects the same way the Songs and Albums tabs do.
 */
@Stable
class SongSelection {
    /** Picked tracks by id, in the order they were picked. */
    var selected by mutableStateOf<Map<String, Song>>(emptyMap())
        private set

    /** Albums picked as a whole (an album's card shows selected). */
    var groupKeys by mutableStateOf<Set<String>>(emptySet())
        private set

    /** The page's "select all" range — see [SelectionScopeEffect]. */
    var scope by mutableStateOf(SelectionScope.Empty)

    /** Set by a page that can delete what is picked (Downloads, a playlist). */
    var deleteAction by mutableStateOf<((List<Song>) -> Unit)?>(null)

    val active: Boolean by derivedStateOf { selected.isNotEmpty() }
    val count: Int get() = selected.size
    val ids: Set<String> get() = selected.keys
    val songs: List<Song> get() = selected.values.toList()

    val allInScopeSelected: Boolean by derivedStateOf {
        scope.songs.isNotEmpty() && scope.songs.all { it.videoId in selected }
    }

    operator fun contains(song: Song): Boolean = song.videoId in selected

    fun toggle(song: Song) {
        selected = if (song.videoId in selected) selected - song.videoId else selected + (song.videoId to song)
        if (selected.isEmpty()) groupKeys = emptySet()
    }

    fun add(songs: List<Song>) {
        val next = LinkedHashMap(selected)
        songs.forEach { next.putIfAbsent(it.videoId, it) }
        selected = next
    }

    fun remove(songs: List<Song>) {
        selected = selected - songs.map { it.videoId }.toSet()
        if (selected.isEmpty()) groupKeys = emptySet()
    }

    /** An album as a whole: on with all its tracks, or off with them. */
    fun toggleGroup(key: String, songs: List<Song>) {
        if (key in groupKeys) {
            groupKeys = groupKeys - key
            remove(songs)
        } else {
            groupKeys = groupKeys + key
            add(songs)
        }
    }

    /** "Select all" on the current page; again to clear it. */
    fun toggleAll() {
        if (allInScopeSelected) {
            groupKeys = groupKeys - scope.groupKeys
            remove(scope.songs)
        } else {
            groupKeys = groupKeys + scope.groupKeys
            add(scope.songs)
        }
    }

    fun clear() {
        selected = emptyMap()
        groupKeys = emptySet()
    }
}

/** The activity's selection; null outside it (pages then keep no selection). */
val LocalSongSelection = staticCompositionLocalOf<SongSelection?> { null }

/**
 * Declares what "select all" covers while this page is showing. Entering a page
 * replaces the range; a page sliding out keeps it only until the new one takes over.
 */
@Composable
fun SelectionScopeEffect(key: Any?, songs: List<Song>, groupKeys: Set<String> = emptySet()) {
    val selection = LocalSongSelection.current ?: return
    DisposableEffect(selection, key, songs, groupKeys) {
        val scope = SelectionScope(songs, groupKeys)
        selection.scope = scope
        onDispose {
            if (selection.scope === scope) selection.scope = SelectionScope.Empty
        }
    }
}

/** Offers a delete for the picked tracks while this page shows; see [SongSelection.deleteAction]. */
@Composable
fun SelectionDeleteEffect(action: ((List<Song>) -> Unit)?) {
    val selection = LocalSongSelection.current ?: return
    DisposableEffect(selection, action) {
        selection.deleteAction = action
        onDispose { if (selection.deleteAction === action) selection.deleteAction = null }
    }
}

/**
 * The selection's actions, in a compact floating Liquid Glass capsule over
 * the bottom bar (M3 Expressive floating toolbar, icon-only): equal round
 * buttons, so the row sits centred whatever language it is in. Holding a
 * button names it in a small bubble above.
 */
@Composable
fun SelectionActionToolbar(
    selection: SongSelection,
    glassBackdrop: Boolean,
    onPlayNext: (List<Song>) -> Unit,
    onAddToQueue: (List<Song>) -> Unit,
    onAddToPlaylist: (List<Song>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(percent = 50)
    val palette = glassPalette()
    val glassEnabled = LocalLiquidGlassEnabled.current && isGlassSupported()
    val surface = if (glassBackdrop && glassEnabled) {
        Modifier.liquidGlass(shape = shape, surfaceAlpha = 0.55f)
    } else {
        Modifier.lightweightLiquidGlass(shape = shape, fallbackColor = palette.fallbackFill)
    }
    val delete = selection.deleteAction
    var tip by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(tip) {
        if (tip != null) {
            delay(1_400)
            tip = null
        }
    }
    fun act(block: (List<Song>) -> Unit) {
        val picked = selection.songs
        selection.clear()
        block(picked)
    }
    Box(modifier = modifier, contentAlignment = Alignment.BottomCenter) {
        Row(
            modifier = Modifier
                .then(surface)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val playNext = stringResource(R.string.queue_next)
            val addToQueue = stringResource(R.string.add_to_queue)
            val addToPlaylist = stringResource(R.string.add_to_playlist)
            val deleteLabel = stringResource(R.string.delete)
            ToolbarAction(com.music.bitchord.ui.icons.BitChordIcons.PlayNext, playNext, palette.content, onHold = { tip = playNext }) { act(onPlayNext) }
            ToolbarAction(Icons.AutoMirrored.Rounded.QueueMusic, addToQueue, palette.content, onHold = { tip = addToQueue }) { act(onAddToQueue) }
            ToolbarAction(Icons.Rounded.PlaylistAdd, addToPlaylist, palette.content, onHold = { tip = addToPlaylist }) { act(onAddToPlaylist) }
            if (delete != null) {
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .size(width = 1.dp, height = 24.dp)
                        .background(palette.divider),
                )
                ToolbarAction(Icons.Rounded.DeleteOutline, deleteLabel, palette.destructive, onHold = { tip = deleteLabel }) { act(delete) }
            }
        }
        // The held button's name, floating just above the toolbar.
        androidx.compose.animation.AnimatedVisibility(
            visible = tip != null,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.9f),
            exit = androidx.compose.animation.fadeOut(),
            modifier = Modifier.offset(y = (-60).dp),
        ) {
            var shown by remember { mutableStateOf("") }
            tip?.let { shown = it }
            Text(
                text = shown,
                style = MaterialTheme.typography.labelLarge,
                color = palette.content,
                maxLines = 1,
                modifier = Modifier
                    .lightweightLiquidGlass(shape = shape, fallbackColor = palette.fallbackFill)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ToolbarAction(
    icon: ImageVector,
    label: String,
    tint: Color,
    onHold: () -> Unit,
    onClick: () -> Unit,
) {
    val haptics = rememberHaptics()
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .combinedClickable(
                onClick = {
                    haptics.play(Haptic.Select)
                    onClick()
                },
                onLongClick = {
                    haptics.play(Haptic.Tick)
                    onHold()
                },
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
    }
}

/**
 * The mark on a track's artwork in selection mode (Google Photos style): an
 * empty ring on every row, so it is plain that a tap now picks, and a filled
 * check on the ones picked.
 */
@Composable
fun SelectionMark(selected: Boolean, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    val fill by animateFloatAsState(
        if (selected) 1f else 0f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "selectionMark",
    )
    val primary = MaterialTheme.colorScheme.primary
    val ring = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) Color.White else Color.White.copy(alpha = 0.92f)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.28f * (1f - fill)))
            .border(1.5.dp, ring, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size)
                .graphicsLayer {
                    scaleX = fill
                    scaleY = fill
                    alpha = fill.coerceIn(0f, 1f)
                }
                .clip(CircleShape)
                .background(primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = stringResource(R.string.selected),
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(size * 0.7f),
            )
        }
    }
}

/** How much a picked track's artwork shrinks, so its check reads as "in" the row. */
@Composable
fun selectionArtScale(selected: Boolean): Float =
    animateFloatAsState(
        if (selected) 0.86f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "selectionArtScale",
    ).value

/** Corner radius of a picked card's artwork: rounder as it shrinks. */
@Composable
fun selectionArtCorner(selected: Boolean, rest: Dp): Dp =
    animateDpAsState(
        if (selected) rest + 8.dp else rest,
        spring(stiffness = Spring.StiffnessMediumLow),
        label = "selectionArtCorner",
    ).value
