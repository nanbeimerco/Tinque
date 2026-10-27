package com.music.bitchord.feature.library.ui

import com.music.bitchord.feature.localsongactions.ui.components.LocalDeleteConfirmDialog
import com.music.bitchord.ui.components.SelectionScopeEffect
import com.music.bitchord.ui.components.LocalSongSelection
import com.music.bitchord.ui.components.GlassAlertDialog
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlaylistRemove
import androidx.compose.material.icons.rounded.Reorder
import androidx.compose.material.icons.rounded.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.LibraryViewType
import com.music.bitchord.ui.theme.ArtworkPalette
import com.music.bitchord.feature.library.ui.components.LibraryFilter
import com.music.bitchord.feature.library.ui.components.LibraryGridContent
import com.music.bitchord.feature.library.ui.components.LibraryHeaderBar
import com.music.bitchord.feature.library.ui.components.LibraryListContent
import com.music.bitchord.feature.localmusic.data.LocalFavoritesStore
import com.music.bitchord.feature.localmusic.data.LocalPlaylistStore
import com.music.bitchord.feature.localmusic.data.resolvePlaylistSongs
import com.music.bitchord.feature.localsongactions.data.LocalPlayStatsStore
import com.music.bitchord.feature.localmusic.domain.model.LocalPlaylist
import com.music.bitchord.feature.localmusic.ui.components.CreatePlaylistDialog
import com.music.bitchord.feature.localsongactions.ui.components.LocalAddToPlaylistSheet
import com.music.bitchord.feature.localsongactions.ui.components.LocalSongDropdownMenu
import com.music.bitchord.feature.localsongactions.ui.LocalSongActionsHelper
import com.music.bitchord.feature.tageditor.data.TagLibWriter
import kotlinx.coroutines.launch
import com.music.bitchord.feature.localmusic.ui.components.DrillDownSongList
import com.music.bitchord.feature.localmusic.ui.components.rememberPlaylistCoverPicker
import com.music.bitchord.ui.components.FastScroller
import com.music.bitchord.ui.components.depthTransition
import com.music.bitchord.ui.components.SectionIndexer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
private fun PlaylistSelectionToolbar(
    count: Int,
    contentColor: Color,
    onAddToPlaylist: () -> Unit,
    onRemoveSelected: (() -> Unit)? = null,
    onEditTags: () -> Unit,
    onShare: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMoveToTop: () -> Unit,
    onMoveToBottom: () -> Unit,
    reorderEnabled: Boolean,
    onToggleReorder: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "$count ${stringResource(R.string.selected)}",
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            modifier = Modifier.padding(end = 4.dp),
            maxLines = 1,
        )
        IconButton(onClick = onAddToPlaylist) {
            Icon(Icons.Rounded.PlaylistAdd, contentDescription = stringResource(R.string.add_to_playlist), tint = contentColor)
        }
        if (onRemoveSelected != null) {
            IconButton(onClick = onRemoveSelected) {
                Icon(
                    Icons.Rounded.PlaylistRemove,
                    contentDescription = stringResource(R.string.remove_from_playlist),
                    tint = contentColor,
                )
            }
        }
        IconButton(onClick = onEditTags) {
            Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.selection_edit_tags), tint = contentColor)
        }
        IconButton(onClick = onShare) {
            Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.selection_share), tint = contentColor)
        }
        IconButton(onClick = onToggleReorder) {
            Icon(
                Icons.Rounded.Reorder,
                contentDescription = stringResource(R.string.toggle_playlist_reorder),
                tint = contentColor,
            )
        }
        IconButton(onClick = onMoveUp) {
            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = stringResource(R.string.selection_move_up), tint = contentColor)
        }
        IconButton(onClick = onMoveDown) {
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = stringResource(R.string.selection_move_down), tint = contentColor)
        }
        TextButton(onClick = onMoveToTop) {
            Icon(Icons.Rounded.ArrowUpward, contentDescription = null, tint = contentColor)
            Text(stringResource(R.string.selection_move_to_top), color = contentColor)
        }
        TextButton(onClick = onMoveToBottom) {
            Icon(Icons.Rounded.ArrowDownward, contentDescription = null, tint = contentColor)
            Text(stringResource(R.string.selection_move_to_bottom), color = contentColor)
        }
        IconButton(onClick = onCancel) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cancel), tint = contentColor)
        }
    }
}

/**
 * Modern Library screen unifying user Favorites and Playlists with list/grid toggle.
 */
@Composable
fun LibraryScreen(
    songs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    onSongClick: (List<Song>, Int) -> Unit,
    onSongLongPress: (Song) -> Unit,
    onSongSwipe: (Song) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onPlayNext: ((Song) -> Unit)? = null,
    onAddToQueue: ((Song) -> Unit)? = null,
    // The ⋮ menu's actions that belong to the activity (other pages, editors).
    onGoToAlbum: ((String, String?) -> Unit)? = null,
    onGoToArtist: ((String) -> Unit)? = null,
    onOpenTagEditor: ((Song) -> Unit)? = null,
    onOpenDetails: ((Song) -> Unit)? = null,
    onDeleteSong: ((Song) -> Unit)? = null,
    onSongTagsOrLyricsSaved: ((Song) -> Unit)? = null,
    songDropdownMenu: (@Composable (Song) -> Unit)? = null,
    initialFilterRequest: LibraryFilter? = null,
    onInitialFilterApplied: () -> Unit = {},
    onArtworkPaletteChanged: ((ArtworkPalette?) -> Unit)? = null,
    onCollectionNavigationChanged: ((Boolean, (() -> Unit)?) -> Unit)? = null,
    /** Bumped when the Library tab is tapped again while showing: back to the top, "All". */
    resetToTopSignal: Int = 0,
    /** Whether the large "Library" title has scrolled away — drives the top bar's title capsule. */
    onTitleScrolledAway: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current.applicationContext
    val playlists by LocalPlaylistStore.playlists.collectAsStateWithLifecycle()
    val favoriteIds by LocalFavoritesStore.favoriteIds.collectAsStateWithLifecycle()
    val libraryViewType by AppSettings.libraryPlaylistsViewType.collectAsStateWithLifecycle()
    val songsViewType by AppSettings.librarySongsViewType.collectAsStateWithLifecycle()
    val showPlaylistSongArtwork by AppSettings.showPlaylistSongArtwork.collectAsStateWithLifecycle()
    val fullScreenCollectionArtwork by AppSettings.fullScreenCollectionArtwork.collectAsStateWithLifecycle()
    val playStatsRevision by LocalPlayStatsStore.revision.collectAsStateWithLifecycle()
    var recentlyPlayedSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var mostPlayedSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var playCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    val choosePlaylistCover = rememberPlaylistCoverPicker { playlistId, uri ->
        LocalPlaylistStore.setPlaylistCover(playlistId, uri)
    }

    LaunchedEffect(songs, playStatsRevision) {
        val ranked = withContext(Dispatchers.IO) {
            songs.map { song ->
                song to LocalPlayStatsStore.getStats(context, song.localUri ?: song.videoId)
            }.filter { (_, stats) -> stats.playedCount > 0 }
        }
        playCounts = ranked.associate { (song, stats) -> (song.localUri ?: song.videoId) to stats.playedCount }
        recentlyPlayedSongs = ranked
            .sortedByDescending { it.second.lastPlayedTimestamp }
            .map { it.first }
        mostPlayedSongs = ranked
            .sortedWith(compareByDescending<Pair<Song, com.music.bitchord.feature.localsongactions.domain.model.LocalPlayStats>> { it.second.playedCount }
                .thenByDescending { it.second.lastPlayedTimestamp })
            .map { it.first }
    }

    var selectedFilter by rememberSaveable { mutableStateOf(LibraryFilter.ALL) }
    val collectionListState = rememberLazyListState()
    val collectionGridState = rememberLazyGridState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var playlistToRename by remember { mutableStateOf<LocalPlaylist?>(null) }

    // Drill down navigation inside Library (e.g. for Favorites or specific Playlist)
    // Saved, so leaving for another tab and coming back reopens the same
    // playlist / Favorites. The song list itself is not saved; it is rebuilt
    // from the stores on the way back in.
    var drillDownLabel by rememberSaveable { mutableStateOf<String?>(null) }
    var drillDownArt by rememberSaveable { mutableStateOf<String?>(null) }
    var drillDownPlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    var drillDownSongs by remember {
        mutableStateOf(
            when {
                drillDownLabel == null -> emptyList()
                drillDownPlaylistId != null -> LocalPlaylistStore.getPlaylist(drillDownPlaylistId!!)
                    ?.let { resolvePlaylistSongs(it, songs) }
                    .orEmpty()
                else -> songs.filter { s -> (s.localUri ?: s.videoId) in favoriteIds || s.videoId in favoriteIds }
            },
        )
    }
    // Songs may still be loading on the first frame back; fill in once they arrive.
    LaunchedEffect(songs.isEmpty()) {
        if (drillDownLabel != null && drillDownSongs.isEmpty() && songs.isNotEmpty()) {
            drillDownSongs = drillDownPlaylistId
                ?.let { id -> LocalPlaylistStore.getPlaylist(id)?.let { resolvePlaylistSongs(it, songs) } }
                ?: if (drillDownPlaylistId == null) {
                    songs.filter { s -> (s.localUri ?: s.videoId) in favoriteIds || s.videoId in favoriteIds }
                } else emptyList()
        }
    }
    var playlistReorderEnabled by rememberSaveable { mutableStateOf(false) }
    var playlistSelectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedPlaylistSongIds by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var showDeleteFromPlaylistDialog by remember { mutableStateOf(false) }
    var activeMenuSong by remember { mutableStateOf<Song?>(null) }
    // Which list the open menu belongs to. The same song can sit in both Recently
    // and Most played; keyed by the song alone, both rows' menus opened together
    // and fought over the one floating menu, often leaving it on the other row.
    var activeMenuSection by remember { mutableStateOf<String?>(null) }
    // The ⋮ menu (the Songs tab's, everywhere in the Library) and its sheets.
    var menuPlaylistSongs by remember { mutableStateOf<List<Song>?>(null) }
    var menuDeleteSong by remember { mutableStateOf<Song?>(null) }
    // Long-press picks tracks, as on the Songs tab (a playlist keeps its own
    // selection, which can also reorder and bulk-edit).
    val selection = LocalSongSelection.current
    var showAddSelectedToPlaylist by remember { mutableStateOf(false) }
    var showBulkTagEditor by remember { mutableStateOf(false) }
    var bulkArtist by remember { mutableStateOf("") }
    var bulkAlbumArtist by remember { mutableStateOf("") }
    var bulkAlbum by remember { mutableStateOf("") }
    var bulkGenre by remember { mutableStateOf("") }
    var bulkYear by remember { mutableStateOf("") }
    var bulkComposer by remember { mutableStateOf("") }
    var pendingBulkTagSongs by remember { mutableStateOf<List<Song>?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun writeBulkTags(targets: List<Song>) {
        val updated = withContext(Dispatchers.IO) {
            targets.count { song ->
                runCatching {
                    val current = TagLibWriter.readTags(context, song)
                    TagLibWriter.writeTags(
                        context,
                        song,
                        current.copy(
                            artist = bulkArtist.trim().ifBlank { current.artist },
                            albumArtist = bulkAlbumArtist.trim().ifBlank { current.albumArtist },
                            album = bulkAlbum.trim().ifBlank { current.album },
                            genre = bulkGenre.trim().ifBlank { current.genre },
                            year = bulkYear.trim().ifBlank { current.year },
                            composer = bulkComposer.trim().ifBlank { current.composer },
                        ),
                    )
                }.getOrDefault(false)
            }
        }
        Toast.makeText(context, context.getString(R.string.bulk_tag_saved, updated, targets.size), Toast.LENGTH_LONG).show()
        targets.forEach { onSongTagsOrLyricsSaved?.invoke(it) }
        showBulkTagEditor = false
        bulkArtist = ""
        bulkAlbumArtist = ""
        bulkAlbum = ""
        bulkGenre = ""
        bulkYear = ""
        bulkComposer = ""
    }

    val bulkTagWritePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val targets = pendingBulkTagSongs.orEmpty()
        pendingBulkTagSongs = null
        if (result.resultCode == Activity.RESULT_OK && targets.isNotEmpty()) {
            scope.launch { writeBulkTags(targets) }
        } else if (targets.isNotEmpty()) {
            Toast.makeText(context, context.getString(R.string.bulk_tag_permission_denied), Toast.LENGTH_LONG).show()
        }
    }

    fun saveBulkTags(targets: List<Song>) {
        val contentUris = targets.mapNotNull { song ->
            (song.localUri ?: song.videoId)
                .takeIf { it.startsWith("content://") }
                ?.let { runCatching { Uri.parse(it) }.getOrNull() }
        }.distinct()
        val needsApproval = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            contentUris.filter { uri ->
                context.checkUriPermission(
                    uri,
                    Process.myPid(),
                    Process.myUid(),
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            }
        } else emptyList()
        // Keep the API guard next to the API 30 call as well as around the
        // query above. This avoids invoking createWriteRequest on API 26–29.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && needsApproval.isNotEmpty()) {
            val request = runCatching { MediaStore.createWriteRequest(context.contentResolver, needsApproval) }.getOrNull()
            if (request != null) {
                pendingBulkTagSongs = targets
                bulkTagWritePermission.launch(IntentSenderRequest.Builder(request.intentSender).build())
                return
            }
        }
        scope.launch { writeBulkTags(targets) }
    }

    LaunchedEffect(drillDownPlaylistId) {
        playlistSelectionMode = false
        selectedPlaylistSongIds = emptySet()
    }

    LaunchedEffect(initialFilterRequest) {
        val requestedFilter = initialFilterRequest ?: return@LaunchedEffect
        selectedFilter = requestedFilter
        onInitialFilterApplied()
    }

    val inDrillDown = drillDownLabel != null
    SideEffect {
        if (!inDrillDown || !fullScreenCollectionArtwork) {
            onArtworkPaletteChanged?.invoke(null)
        }
    }
    val inHistoryView = selectedFilter == LibraryFilter.RECENTLY_PLAYED ||
        selectedFilter == LibraryFilter.MOST_PLAYED
    val selectedPlaylistSongs = remember(drillDownSongs, selectedPlaylistSongIds) {
        drillDownSongs.filter { (it.localUri ?: it.videoId) in selectedPlaylistSongIds }
    }
    fun togglePlaylistSongSelection(song: Song) {
        val key = song.localUri ?: song.videoId
        selectedPlaylistSongIds = if (key in selectedPlaylistSongIds) {
            selectedPlaylistSongIds - key
        } else {
            selectedPlaylistSongIds + key
        }
        if (selectedPlaylistSongIds.isEmpty()) playlistSelectionMode = false
    }
    fun isPlaylistSongSelected(song: Song): Boolean =
        (song.localUri ?: song.videoId) in selectedPlaylistSongIds || song.videoId in selectedPlaylistSongIds

    fun persistPlaylistSongOrder(playlistId: String, orderedSongs: List<Song>) {
        val storedIds = LocalPlaylistStore.getPlaylist(playlistId)?.songIds ?: return
        val usedIndices = mutableSetOf<Int>()
        val reorderedResolvedIds = orderedSongs.mapNotNull { song ->
            val storedIndex = storedIds.indices.firstOrNull { index ->
                index !in usedIndices && storedIds[index] in setOf(song.localUri, song.videoId)
            } ?: return@mapNotNull null
            usedIndices += storedIndex
            storedIds[storedIndex]
        }
        if (reorderedResolvedIds.isNotEmpty()) {
            // Keep entries that could not be resolved in their existing slots;
            // reordering playable tracks must never delete inaccessible playlist entries.
            val resolvedSlots = usedIndices.sorted()
            val persistedOrder = storedIds.toMutableList()
            resolvedSlots.forEachIndexed { order, slot ->
                persistedOrder[slot] = reorderedResolvedIds[order]
            }
            if (persistedOrder != storedIds) LocalPlaylistStore.setSongOrder(playlistId, persistedOrder)
        }
    }
    fun persistSelectedOrder(toTop: Boolean) {
        val playlistId = drillDownPlaylistId ?: return
        val selected = drillDownSongs.filter(::isPlaylistSongSelected)
        if (selected.isEmpty()) return
        val remaining = drillDownSongs.filterNot(::isPlaylistSongSelected)
        val reordered = if (toTop) selected + remaining else remaining + selected
        if (reordered == drillDownSongs) return
        drillDownSongs = reordered
        persistPlaylistSongOrder(playlistId, reordered)
    }
    fun moveSelectedSongsByOne(direction: Int) {
        if (direction != -1 && direction != 1) return
        val playlistId = drillDownPlaylistId ?: return
        if (selectedPlaylistSongIds.isEmpty()) return
        val reordered = drillDownSongs.toMutableList()
        if (direction < 0) {
            for (index in 1..reordered.lastIndex) {
                if (isPlaylistSongSelected(reordered[index]) && !isPlaylistSongSelected(reordered[index - 1])) {
                    val moving = reordered[index]
                    reordered[index] = reordered[index - 1]
                    reordered[index - 1] = moving
                }
            }
        } else {
            for (index in reordered.lastIndex - 1 downTo 0) {
                if (isPlaylistSongSelected(reordered[index]) && !isPlaylistSongSelected(reordered[index + 1])) {
                    val moving = reordered[index]
                    reordered[index] = reordered[index + 1]
                    reordered[index + 1] = moving
                }
            }
        }
        if (reordered == drillDownSongs) return
        drillDownSongs = reordered
        persistPlaylistSongOrder(playlistId, reordered)
    }
    val leaveDrillDown = {
        drillDownLabel = null
        drillDownSongs = emptyList()
        drillDownArt = null
        drillDownPlaylistId = null
        playlistReorderEnabled = false
        playlistSelectionMode = false
        selectedPlaylistSongIds = emptySet()
    }

    // Tab re-tap: close any drill-down, return to "All" and the top of the list.
    var lastResetSignal by rememberSaveable { mutableStateOf(resetToTopSignal) }
    LaunchedEffect(resetToTopSignal) {
        if (resetToTopSignal != lastResetSignal) {
            lastResetSignal = resetToTopSignal
            // Already on the main list with "All" showing: nothing changes
            // place, so the list glides back to the top (iOS's tab re-tap)
            // instead of jumping there. A long way down it jumps most of the
            // way first, so the glide never takes long.
            val alreadyAtRoot = !inDrillDown && !inHistoryView && selectedFilter == LibraryFilter.ALL
            leaveDrillDown()
            selectedFilter = LibraryFilter.ALL
            if (alreadyAtRoot && collectionListState.layoutInfo.totalItemsCount > 0) {
                if (collectionListState.firstVisibleItemIndex > 8) collectionListState.scrollToItem(8)
                collectionListState.animateScrollToItem(0)
            } else if (alreadyAtRoot && collectionGridState.layoutInfo.totalItemsCount > 0) {
                if (collectionGridState.firstVisibleItemIndex > 12) collectionGridState.scrollToItem(12)
                collectionGridState.animateScrollToItem(0)
            } else {
                // Non-suspending: the list may not be laid out (e.g. a drill-down is showing).
                collectionListState.requestScrollToItem(0)
                collectionGridState.requestScrollToItem(0)
            }
        }
    }

    // Report when the large title has scrolled out of view (main page only).
    val titleThresholdPx = with(androidx.compose.ui.platform.LocalDensity.current) { 48.dp.toPx() }
    val titleScrolledAway by remember(titleThresholdPx, inDrillDown, inHistoryView) {
        androidx.compose.runtime.derivedStateOf {
            !inDrillDown && !inHistoryView && (
                collectionListState.firstVisibleItemIndex > 0 ||
                    collectionListState.firstVisibleItemScrollOffset > titleThresholdPx ||
                    collectionGridState.firstVisibleItemIndex > 0 ||
                    collectionGridState.firstVisibleItemScrollOffset > titleThresholdPx
                )
        }
    }
    val titleCallback = rememberUpdatedState(onTitleScrolledAway)
    LaunchedEffect(titleScrolledAway) { titleCallback.value(titleScrolledAway) }
    DisposableEffect(Unit) { onDispose { titleCallback.value(false) } }

    // Drill-downs and the full Recently / Most played lists are sub-pages: the
    // top bar shows a back button for them.
    val inSubPage = inDrillDown || inHistoryView
    val collectionNavigationCallback = rememberUpdatedState(onCollectionNavigationChanged)
    val currentCollectionBackHandler = rememberUpdatedState<() -> Unit>({
        if (inDrillDown) leaveDrillDown() else { selectedFilter = LibraryFilter.ALL }
    })
    val collectionBackHandler = remember { { currentCollectionBackHandler.value() } }
    LaunchedEffect(inSubPage) {
        collectionNavigationCallback.value?.invoke(
            inSubPage,
            collectionBackHandler.takeIf { inSubPage },
        )
    }
    DisposableEffect(Unit) {
        onDispose { collectionNavigationCallback.value?.invoke(false, null) }
    }

    BackHandler(enabled = inDrillDown || inHistoryView) {
        if (inDrillDown) leaveDrillDown() else selectedFilter = LibraryFilter.ALL
    }

    val libraryMenu: @Composable (Song, String?, String?) -> Unit = { song, playlistId, section ->
        LocalSongDropdownMenu(
            expanded = activeMenuSection == section &&
                (activeMenuSong?.let { it.localUri ?: it.videoId } == (song.localUri ?: song.videoId)),
            onDismissRequest = { activeMenuSong = null },
            song = song,
            onQueueNext = {
                activeMenuSong = null
                onPlayNext?.invoke(song) ?: onSongSwipe(song)
            },
            onAddToQueue = {
                activeMenuSong = null
                onAddToQueue?.invoke(song) ?: onSongSwipe(song)
            },
            onAddToPlaylist = {
                activeMenuSong = null
                menuPlaylistSongs = listOf(song)
            },
            onRemoveFromPlaylist = if (playlistId != null) {
                {
                    activeMenuSong = null
                    val songId = song.localUri ?: song.videoId
                    LocalPlaylistStore.removeSongFromPlaylist(playlistId, songId)
                    drillDownSongs = drillDownSongs.filterNot { (it.localUri ?: it.videoId) == songId }
                    Toast.makeText(context, R.string.remove_from_playlist, Toast.LENGTH_SHORT).show()
                }
            } else null,
            onGoToAlbum = onGoToAlbum?.let { go -> { album -> activeMenuSong = null; go(album, song.thumbnailUrl) } },
            onGoToArtist = onGoToArtist?.let { go -> { artist -> activeMenuSong = null; go(artist) } },
            onGoToFolder = null,
            onTagEditor = {
                activeMenuSong = null
                onOpenTagEditor?.invoke(song) ?: onSongLongPress(song)
            },
            onShare = {
                activeMenuSong = null
                LocalSongActionsHelper.shareSong(context, song)
            },
            onDeleteFromDevice = {
                activeMenuSong = null
                menuDeleteSong = song
            },
            onDetails = {
                activeMenuSong = null
                onOpenDetails?.invoke(song) ?: onSongLongPress(song)
            },
        )
    }
    val openMenu: (Song) -> Unit = { song -> activeMenuSection = null; activeMenuSong = song }
    val openSectionMenu: (Song, String?) -> Unit = { song, section -> activeMenuSection = section; activeMenuSong = song }
    val pickOrPlay: (List<Song>, Int) -> Unit = { list, index ->
        if (selection?.active == true) selection.toggle(list[index]) else onSongClick(list, index)
    }
    val pick: (Song) -> Unit = { song -> if (selection != null) selection.toggle(song) else onSongLongPress(song) }
    // Moving to another page of the Library ends a selection.
    LaunchedEffect(drillDownLabel, selectedFilter) {
        selection?.clear()
        // A menu left open on a page must neither ride it out on its exit
        // animation nor be waiting there when the page is opened again.
        activeMenuSong = null
        activeMenuSection = null
    }
    menuPlaylistSongs?.let { picked ->
        LocalAddToPlaylistSheet(songs = picked, onDismissRequest = { menuPlaylistSongs = null })
    }
    menuDeleteSong?.let { song ->
        LocalDeleteConfirmDialog(
            song = song,
            onDismissRequest = { menuDeleteSong = null },
            onDeleted = {
                menuDeleteSong = null
                onDeleteSong?.invoke(song)
            },
        )
    }

    // Filter favorite songs matching persisted favorite IDs
    val favoriteSongs = remember(songs, favoriteIds) {
        songs.filter { s ->
            val localKey = s.localUri ?: s.videoId
            localKey in favoriteIds || s.videoId in favoriteIds
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = if (inDrillDown) "drill:$drillDownLabel" else when (selectedFilter) {
                LibraryFilter.RECENTLY_PLAYED -> "history:recent"
                LibraryFilter.MOST_PLAYED -> "history:most"
                else -> "library_main"
            },
            // Library top = 0; a playlist, Favorites, or a full Recently / Most
            // played list = 1.
            transitionSpec = {
                depthTransition { state -> if (state == "library_main") 0 else 1 }
            },
            label = "library_content_transition",
            modifier = Modifier.fillMaxSize(),
        ) { key ->
            if (key.startsWith("drill:")) {
                DrillDownSongList(
                    label = drillDownLabel.orEmpty(),
                    artworkUrl = drillDownArt,
                    songs = drillDownSongs,
                    isArtist = false,
                    isPlaylist = drillDownPlaylistId != null,
                    showArtworkInList = showPlaylistSongArtwork,
                    fullScreenArtwork = fullScreenCollectionArtwork,
                    viewType = songsViewType,
                    onViewTypeToggle = {
                        playlistReorderEnabled = false
                        val next = if (songsViewType == LibraryViewType.GRID) LibraryViewType.LIST else LibraryViewType.GRID
                        AppSettings.setLibrarySongsViewType(next)
                    },
                    selectionMode = playlistSelectionMode && drillDownPlaylistId != null,
                    onSelectionToggle = ::togglePlaylistSongSelection,
                    selectionToolbar = if (playlistSelectionMode && drillDownPlaylistId != null) {
                        { contentColor ->
                            PlaylistSelectionToolbar(
                                count = selectedPlaylistSongIds.size,
                                contentColor = contentColor,
                                onAddToPlaylist = { showAddSelectedToPlaylist = true },
                                onRemoveSelected = { showDeleteFromPlaylistDialog = true },
                                onEditTags = { showBulkTagEditor = true },
                                onShare = { LocalSongActionsHelper.shareSongs(context, selectedPlaylistSongs) },
                                onMoveUp = { moveSelectedSongsByOne(-1) },
                                onMoveDown = { moveSelectedSongsByOne(1) },
                                onMoveToTop = { persistSelectedOrder(toTop = true) },
                                onMoveToBottom = { persistSelectedOrder(toTop = false) },
                                reorderEnabled = playlistReorderEnabled,
                                onToggleReorder = {
                                    if (playlistReorderEnabled) {
                                        playlistReorderEnabled = false
                                    } else {
                                        if (songsViewType != LibraryViewType.LIST) {
                                            AppSettings.setLibrarySongsViewType(LibraryViewType.LIST)
                                        }
                                        playlistReorderEnabled = true
                                    }
                                },
                                onCancel = {
                                    playlistSelectionMode = false
                                    selectedPlaylistSongIds = emptySet()
                                },
                            )
                        }
                    } else null,
                    selectedIds = if (drillDownPlaylistId != null) selectedPlaylistSongIds else selection?.ids ?: emptySet(),
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    onSongClick = { list, idx ->
                        if (drillDownPlaylistId == null) pickOrPlay(list, idx) else onSongClick(list, idx)
                    },
                    onSongLongPress = { song ->
                        if (drillDownPlaylistId != null) {
                            val key = song.localUri ?: song.videoId
                            if (!playlistSelectionMode) {
                                playlistReorderEnabled = false
                                playlistSelectionMode = true
                                selectedPlaylistSongIds = setOf(key)
                            } else {
                                togglePlaylistSongSelection(song)
                            }
                        } else {
                            pick(song)
                        }
                    },
                    // Selection changes the row tap and long-press behavior,
                    // but the trailing details/actions button stays available.
                    onSongMore = openMenu,
                    onSongSwipe = onSongSwipe,
                    onShuffle = onShuffle,
                    onMore = null,
                    reorderEnabled = playlistReorderEnabled && drillDownPlaylistId != null,
                    onReorderToggle = if (drillDownPlaylistId != null) {
                        {
                            if (playlistReorderEnabled) {
                                playlistReorderEnabled = false
                            } else {
                                if (songsViewType != LibraryViewType.LIST) {
                                    AppSettings.setLibrarySongsViewType(LibraryViewType.LIST)
                                }
                                playlistReorderEnabled = true
                            }
                        }
                    } else null,
                    onReorderComplete = { reorderedSongs ->
                        val playlistId = drillDownPlaylistId
                        if (playlistId != null) {
                            drillDownSongs = reorderedSongs
                            persistPlaylistSongOrder(playlistId, reorderedSongs)
                        }
                    },
                    onBack = leaveDrillDown,
                    contentPadding = contentPadding,
                    songDropdownMenu = { song -> libraryMenu(song, drillDownPlaylistId, null) },
                    onArtworkPaletteChanged = onArtworkPaletteChanged,
                )
                if (drillDownPlaylistId == null) SelectionScopeEffect(key = key, songs = drillDownSongs)
            } else if (key.startsWith("history:")) {
                val isRecent = key == "history:recent"
                val historySongs = if (isRecent) recentlyPlayedSongs else mostPlayedSongs
                // A sub-page now (back button in the top bar), so no filter
                // header here: the list's own title says what it is.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = contentPadding.calculateTopPadding()),
                ) {
                    if (historySongs.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                            Text(
                                text = stringResource(R.string.no_play_history),
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        SelectionScopeEffect(key = key, songs = historySongs)
                        DrillDownSongList(
                            label = stringResource(if (isRecent) R.string.recently_played else R.string.most_played),
                            artworkUrl = null,
                            songs = historySongs,
                            fullScreenArtwork = false,
                            viewType = songsViewType,
                            onViewTypeToggle = {
                                val next = if (songsViewType == LibraryViewType.GRID) LibraryViewType.LIST else LibraryViewType.GRID
                                AppSettings.setLibrarySongsViewType(next)
                            },
                            currentSong = currentSong,
                            isPlaying = isPlaying,
                            selectedIds = selection?.ids ?: emptySet(),
                            onSongClick = pickOrPlay,
                            onSongLongPress = pick,
                            onSongMore = openMenu,
                            onSongSwipe = onSongSwipe,
                            onShuffle = onShuffle,
                            showArtworkInList = showPlaylistSongArtwork,
                            onBack = { selectedFilter = LibraryFilter.ALL },
                            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
                            songDropdownMenu = { song -> libraryMenu(song, null, null) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Large title + filters: the first item of the list, scrolling with it.
                    val header: @Composable () -> Unit = {
                        LibraryHeaderBar(
                            selectedFilter = selectedFilter,
                            onFilterSelect = { selectedFilter = it },
                            viewType = libraryViewType,
                            onToggleViewType = {
                                val next = if (libraryViewType == LibraryViewType.GRID) LibraryViewType.LIST else LibraryViewType.GRID
                                AppSettings.setLibraryPlaylistsViewType(next)
                            },
                        )
                    }

                    val onFavoritesClick = {
                        drillDownPlaylistId = null
                        playlistReorderEnabled = false
                        drillDownLabel = context.getString(R.string.favorites)
                        drillDownSongs = favoriteSongs
                        drillDownArt = "favorites"
                    }

                    val onFavoritesPlay = {
                        if (favoriteSongs.isNotEmpty()) {
                            onSongClick(favoriteSongs, 0)
                        }
                    }

                    val onPlaylistClick: (LocalPlaylist) -> Unit = { playlist ->
                        drillDownPlaylistId = playlist.id
                        playlistReorderEnabled = false
                        drillDownLabel = playlist.name
                        val pSongs = resolvePlaylistSongs(playlist, songs)
                        drillDownSongs = pSongs
                        drillDownArt = playlist.customCoverUrl ?: playlist.coverUrl ?: pSongs.firstNotNullOfOrNull { it.thumbnailUrl }
                    }

                    val onPlaylistPlay: (LocalPlaylist) -> Unit = { playlist ->
                        val pSongs = resolvePlaylistSongs(playlist, songs)
                        if (pSongs.isNotEmpty()) {
                            onSongClick(pSongs, 0)
                        }
                    }

                    // Grid applies to the Playlists filter only; "All" is always the sectioned list.
                    if (libraryViewType == LibraryViewType.GRID && selectedFilter == LibraryFilter.PLAYLISTS) {
                        LibraryGridContent(
                            header = header,
                            filter = selectedFilter,
                            favoriteSongCount = favoriteSongs.size,
                            playlists = playlists,
                            onFavoritesClick = onFavoritesClick,
                            onFavoritesPlay = onFavoritesPlay,
                            onPlaylistClick = onPlaylistClick,
                            onPlaylistPlay = onPlaylistPlay,
                            onRenamePlaylist = { playlistToRename = it },
                            onDeletePlaylist = { LocalPlaylistStore.deletePlaylist(it.id) },
                            onChangePlaylistCover = { choosePlaylistCover(it.id) },
                            onResetPlaylistCover = { LocalPlaylistStore.setPlaylistCover(it.id, null) },
                            onMovePlaylist = { playlist, offset -> LocalPlaylistStore.movePlaylist(playlist.id, offset) },
                            onCreatePlaylist = { showCreateDialog = true },
                            librarySongs = songs,
                            contentPadding = contentPadding,
                            state = collectionGridState,
                        )
                    } else {
                        LibraryListContent(
                            header = header,
                            recentlyPlayed = recentlyPlayedSongs,
                            mostPlayed = mostPlayedSongs,
                            playCountOf = { song -> playCounts[song.localUri ?: song.videoId] ?: 0 },
                            onShowAllRecent = { selectedFilter = LibraryFilter.RECENTLY_PLAYED },
                            onShowAllMost = { selectedFilter = LibraryFilter.MOST_PLAYED },
                            currentSong = currentSong,
                            isPlaying = isPlaying,
                            onSongClick = pickOrPlay,
                            onSongLongPress = pick,
                            onSongMore = openSectionMenu,
                            songMenu = { song, section -> libraryMenu(song, null, section) },
                            selectedIds = selection?.ids ?: emptySet(),
                            onSongSwipe = onSongSwipe,
                            filter = selectedFilter,
                            favoriteSongCount = favoriteSongs.size,
                            playlists = playlists,
                            onFavoritesClick = onFavoritesClick,
                            onFavoritesPlay = onFavoritesPlay,
                            onPlaylistClick = onPlaylistClick,
                            onPlaylistPlay = onPlaylistPlay,
                            onRenamePlaylist = { playlistToRename = it },
                            onDeletePlaylist = { LocalPlaylistStore.deletePlaylist(it.id) },
                            onChangePlaylistCover = { choosePlaylistCover(it.id) },
                            onResetPlaylistCover = { LocalPlaylistStore.setPlaylistCover(it.id, null) },
                            onMovePlaylist = { playlist, offset -> LocalPlaylistStore.movePlaylist(playlist.id, offset) },
                            onCreatePlaylist = { showCreateDialog = true },
                            librarySongs = songs,
                            contentPadding = contentPadding,
                            state = collectionListState,
                        )
                    }
                }
            }
        }
        // Only the Playlists filter is a long single list worth a fast scroller;
        // "All" is a handful of short sections. Index 0 is the page header.
        val createPlaylistLabel = stringResource(R.string.new_playlist)
        val listCollectionLabels = playlists.map { it.name }
        val gridCollectionLabels = buildList {
            add("")
            addAll(listCollectionLabels)
            add(createPlaylistLabel)
        }
        if (!inDrillDown && !inHistoryView && selectedFilter == LibraryFilter.PLAYLISTS && listCollectionLabels.isNotEmpty()) {
            if (libraryViewType == LibraryViewType.GRID) {
                FastScroller(
                    gridState = collectionGridState,
                    itemCount = gridCollectionLabels.size,
                    sectionNameForIndex = { index ->
                        SectionIndexer.getSectionName(gridCollectionLabels.getOrNull(index))
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd),
                    contentPadding = PaddingValues(
                        top = contentPadding.calculateTopPadding() + 8.dp,
                        bottom = contentPadding.calculateBottomPadding() + 80.dp,
                    ),
                )
            } else {
                FastScroller(
                    listState = collectionListState,
                    itemCount = listCollectionLabels.size,
                    // Page header + the "Playlists" section title.
                    headerCount = 2,
                    sectionNameForIndex = { index ->
                        SectionIndexer.getSectionName(listCollectionLabels.getOrNull(index))
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd),
                    contentPadding = PaddingValues(
                        top = contentPadding.calculateTopPadding() + 8.dp,
                        bottom = contentPadding.calculateBottomPadding() + 80.dp,
                    ),
                )
            }
        }
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            title = "New Playlist",
            confirmText = "Create",
            onConfirm = { name ->
                LocalPlaylistStore.createPlaylist(name)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false },
        )
    }

    playlistToRename?.let { target ->
        CreatePlaylistDialog(
            initialName = target.name,
            title = "Rename Playlist",
            confirmText = "Save",
            onConfirm = { newName ->
                LocalPlaylistStore.renamePlaylist(target.id, newName)
                playlistToRename = null
            },
            onDismiss = { playlistToRename = null },
        )
    }

    if (showAddSelectedToPlaylist && selectedPlaylistSongs.isNotEmpty()) {
        LocalAddToPlaylistSheet(
            songs = selectedPlaylistSongs,
            onDismissRequest = {
                showAddSelectedToPlaylist = false
                playlistSelectionMode = false
                selectedPlaylistSongIds = emptySet()
            },
        )
    }

    if (showBulkTagEditor) {
        GlassAlertDialog(
            onDismissRequest = { showBulkTagEditor = false },
            title = { Text(stringResource(R.string.bulk_tag_edit)) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 440.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.bulk_tag_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(bulkArtist, { bulkArtist = it }, label = { Text(stringResource(R.string.bulk_tag_artist)) }, singleLine = true)
                    OutlinedTextField(bulkAlbumArtist, { bulkAlbumArtist = it }, label = { Text(stringResource(R.string.bulk_tag_album_artist)) }, singleLine = true)
                    OutlinedTextField(bulkAlbum, { bulkAlbum = it }, label = { Text(stringResource(R.string.bulk_tag_album)) }, singleLine = true)
                    OutlinedTextField(bulkGenre, { bulkGenre = it }, label = { Text(stringResource(R.string.bulk_tag_genre)) }, singleLine = true)
                    OutlinedTextField(bulkYear, { bulkYear = it }, label = { Text(stringResource(R.string.bulk_tag_year)) }, singleLine = true)
                    OutlinedTextField(bulkComposer, { bulkComposer = it }, label = { Text(stringResource(R.string.bulk_tag_composer)) }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = listOf(bulkArtist, bulkAlbumArtist, bulkAlbum, bulkGenre, bulkYear, bulkComposer).any { it.isNotBlank() } && selectedPlaylistSongs.isNotEmpty(),
                    onClick = { saveBulkTags(selectedPlaylistSongs) },
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkTagEditor = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showDeleteFromPlaylistDialog && drillDownPlaylistId != null) {
        val targetPlaylistId = drillDownPlaylistId!!
        val count = selectedPlaylistSongIds.size
        GlassAlertDialog(
            onDismissRequest = { showDeleteFromPlaylistDialog = false },
            title = { Text(stringResource(R.string.remove_from_playlist)) },
            text = {
                Text(
                    text = if (count == 1) {
                        "${stringResource(R.string.remove_from_playlist)}?"
                    } else {
                        "$count ${stringResource(R.string.selected)}\n${stringResource(R.string.remove_from_playlist)}?"
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        LocalPlaylistStore.removeSongsFromPlaylist(targetPlaylistId, selectedPlaylistSongIds)
                        drillDownSongs = drillDownSongs.filterNot(::isPlaylistSongSelected)
                        selectedPlaylistSongIds = emptySet()
                        playlistSelectionMode = false
                        showDeleteFromPlaylistDialog = false
                        Toast.makeText(context, R.string.remove_from_playlist, Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteFromPlaylistDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
