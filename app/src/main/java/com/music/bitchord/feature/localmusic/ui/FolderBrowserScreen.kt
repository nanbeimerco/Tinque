package com.music.bitchord.feature.localmusic.ui

import com.music.bitchord.feature.localsongactions.ui.LocalSongActionsHelper
import com.music.bitchord.feature.localsongactions.ui.components.LocalDeleteConfirmDialog
import com.music.bitchord.feature.localsongactions.ui.components.LocalAddToPlaylistSheet
import com.music.bitchord.feature.localsongactions.ui.components.LocalSongDropdownMenu
import com.music.bitchord.ui.components.SelectionMark
import com.music.bitchord.ui.components.SelectionScopeEffect
import com.music.bitchord.ui.components.LocalSongSelection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import com.music.bitchord.data.model.Song
import com.music.bitchord.feature.localmusic.domain.FolderNode
import com.music.bitchord.feature.localmusic.domain.FolderTreeBuilder
import com.music.bitchord.feature.localmusic.ui.components.DrillDownSongList
import com.music.bitchord.feature.localmusic.ui.components.FolderArtworkThumbnail
import com.music.bitchord.ui.components.ExpressiveGroupDefaults
import com.music.bitchord.ui.components.SongRow
import com.music.bitchord.ui.components.TabLogoHeader
import com.music.bitchord.ui.components.expressiveItem
import com.music.bitchord.ui.theme.ArtworkPalette

data class FolderNavState(
    val node: FolderNode,
    val depth: Int,
)

/** Saveable path stack, so fold open/close or any recreation reopens at the same depth. */
private val PathStackSaver = listSaver<SnapshotStateList<String>, String>(
    save = { ArrayList(it) },
    restore = { it.toMutableStateList() },
)

/**
 * In-memory persistence of folder navigation stack and scroll positions keyed by root path,
 * so switching away from the Folders tab to another tab (e.g. Library) and returning keeps
 * the user at the exact folder level and scroll offset they were viewing.
 */
private val PersistedFolderPathStacks = mutableMapOf<String, List<String>>()
private val PersistedFolderScrollStates = mutableMapOf<String, Pair<Int, Int>>()

/** The last folder tree built, reused while the song list and root are unchanged. */
private object FolderTreeCache {
    private var songsRef: List<Song>? = null
    private var root: String? = null
    private var tree: FolderNode? = null

    @Synchronized
    fun get(songs: List<Song>, customRoot: String?): FolderNode? =
        tree?.takeIf { songsRef === songs && root == customRoot }

    @Synchronized
    fun put(songs: List<Song>, customRoot: String?, built: FolderNode) {
        songsRef = songs
        root = customRoot
        tree = built
    }
}

/**
 * Screen providing hierarchical browsing of local music storage folders.
 * Intermediate folders display subfolders and direct tracks with thumbnail artwork.
 * Leaf folders (folders containing tracks directly with no subfolders) render using
 * [DrillDownSongList] with full-screen artwork and blur background matching the album view.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FolderBrowserScreen(
    songs: List<Song>,
    onSongClick: (List<Song>, Int) -> Unit,
    onSongLongPress: (Song) -> Unit,
    onSongSwipe: (Song) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    contentPadding: PaddingValues,
    currentSong: Song? = null,
    isPlaying: Boolean = false,
    customRootPath: String? = null,
    initialPath: String? = null,
    onInitialPathConsumed: (() -> Unit)? = null,
    onArtworkPaletteChanged: ((ArtworkPalette?) -> Unit)? = null,
    songDropdownMenu: (@Composable (Song) -> Unit)? = null,
    onNavigationChanged: ((Boolean, (() -> Unit)?) -> Unit)? = null,
    /** The open folder's name below the root, null at the root — for the top bar's title capsule. */
    onFolderTitleChanged: ((String?) -> Unit)? = null,
    // The ⋮ menu's actions that belong to the activity (queue, other pages, sheets).
    onPlayNext: ((Song) -> Unit)? = null,
    onAddToQueue: ((Song) -> Unit)? = null,
    onGoToAlbum: ((String, String?) -> Unit)? = null,
    onGoToArtist: ((String) -> Unit)? = null,
    onTagEditor: ((Song) -> Unit)? = null,
    onDetails: ((Song) -> Unit)? = null,
    onDeleteSong: ((Song) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Long-press picks tracks here exactly as on the Songs and Albums tabs; the
    // ⋮ on each row opens the track's menu.
    val selection = LocalSongSelection.current
    var menuSong by remember { mutableStateOf<Song?>(null) }
    var deleteSong by remember { mutableStateOf<Song?>(null) }
    var playlistSongs by remember { mutableStateOf<List<Song>?>(null) }
    val ownMenu: @Composable (Song) -> Unit = { song ->
        LocalSongDropdownMenu(
            expanded = menuSong?.videoId == song.videoId,
            onDismissRequest = { menuSong = null },
            song = song,
            onQueueNext = { onPlayNext?.invoke(song) ?: onSongSwipe(song) },
            onAddToQueue = { onAddToQueue?.invoke(song) ?: onSongSwipe(song) },
            onAddToPlaylist = { playlistSongs = listOf(song) },
            onGoToAlbum = onGoToAlbum?.let { go -> { album -> go(album, song.thumbnailUrl) } },
            onGoToArtist = onGoToArtist,
            onGoToFolder = null,
            onTagEditor = { onTagEditor?.invoke(song) },
            onShare = { LocalSongActionsHelper.shareSong(context, song) },
            onDeleteFromDevice = { deleteSong = song },
            onDetails = { onDetails?.invoke(song) },
        )
    }
    val rowMenu = songDropdownMenu ?: ownMenu
    val pickOrPlay: (List<Song>, Int) -> Unit = { list, index ->
        if (selection?.active == true) selection.toggle(list[index]) else onSongClick(list, index)
    }
    val pick: (Song) -> Unit = { song -> if (selection != null) selection.toggle(song) else onSongLongPress(song) }

    deleteSong?.let { song ->
        LocalDeleteConfirmDialog(
            song = song,
            onDismissRequest = { deleteSong = null },
            onDeleted = {
                deleteSong = null
                onDeleteSong?.invoke(song)
            },
        )
    }
    playlistSongs?.let { picked ->
        LocalAddToPlaylistSheet(songs = picked, onDismissRequest = { playlistSongs = null })
    }
    // The tree covers the whole library (path splitting plus a natural sort of
    // every folder), and this screen is disposed whenever another tab shows —
    // so building it here, on the main thread, on every visit stalled the
    // switch into Folders. It is now cached across visits and, when it does
    // need building, built off the main thread.
    val cachedTree = remember(songs, customRootPath) { FolderTreeCache.get(songs, customRootPath) }
    val loadedTree by produceState(cachedTree, songs, customRootPath) {
        // Keyed state is not reset when songs change, so check the cache
        // rather than `value`: a stale tree must still be rebuilt.
        value = FolderTreeCache.get(songs, customRootPath)
            ?: withContext(Dispatchers.Default) { FolderTreeBuilder.build(songs, customRootPath) }
                .also { FolderTreeCache.put(songs, customRootPath, it) }
    }
    val tree = loadedTree ?: run {
        Box(modifier = modifier.fillMaxSize())
        return
    }

    val rootKey = customRootPath.orEmpty()

    // Saved, not merely remembered: fold open/close used to start the stack
    // over at the root, because the hinge angle change (re)builds this
    // composable. Also seeded from [PersistedFolderPathStacks] so switching
    // between bottom-bar tabs preserves the current folder depth.
    val pathStack = rememberSaveable(rootKey, saver = PathStackSaver) {
        val persisted = PersistedFolderPathStacks[rootKey].orEmpty()
        mutableStateListOf<String>().apply { addAll(persisted) }
    }

    val currentStackSnapshot = pathStack.toList()
    SideEffect {
        PersistedFolderPathStacks[rootKey] = currentStackSnapshot
    }

    // When navigating to a specific folder initially
    LaunchedEffect(initialPath, tree) {
        if (!initialPath.isNullOrBlank()) {
            val targetNode = tree.findByPath(initialPath)
            if (targetNode != null && pathStack.lastOrNull() != targetNode.path) {
                pathStack.clear()
                // Reconstruct ancestors
                val segments = targetNode.relativePath.split('/').filter(String::isNotEmpty)
                var accumulated = tree.path
                for (seg in segments) {
                    accumulated = if (accumulated.isEmpty() || accumulated == "/") "/$seg" else "$accumulated/$seg"
                    pathStack.add(accumulated)
                }
            }
            if (targetNode != null) {
                onInitialPathConsumed?.invoke()
            }
        }
    }

    val currentNavState = remember(tree, currentStackSnapshot) {
        val currentPath = currentStackSnapshot.lastOrNull()
        val node = if (currentPath == null) {
            tree
        } else {
            tree.findByPath(currentPath) ?: tree
        }
        FolderNavState(node = node, depth = currentStackSnapshot.size)
    }

    val canGoBack = pathStack.isNotEmpty()
    val popBack: () -> Unit = {
        if (pathStack.isNotEmpty()) {
            pathStack.removeAt(pathStack.lastIndex)
        }
    }

    val navigationCallback = rememberUpdatedState(onNavigationChanged)
    val backHandler = remember { { popBack() } }

    LaunchedEffect(canGoBack) {
        navigationCallback.value?.invoke(canGoBack, backHandler.takeIf { canGoBack })
    }
    DisposableEffect(Unit) {
        onDispose { navigationCallback.value?.invoke(false, null) }
    }

    BackHandler(enabled = canGoBack) {
        popBack()
    }
    // While picking, back ends the selection first (registered later, so it wins).
    BackHandler(enabled = selection?.active == true) { selection?.clear() }
    // Moving to another folder ends a selection.
    LaunchedEffect(currentNavState.node.path) {
        selection?.clear()
        // Without this the menu rode the page out on its exit animation and
        // only closed once that had finished.
        menuSong = null
    }

    val folderTitleCallback = rememberUpdatedState(onFolderTitleChanged)
    val currentFolderTitle = currentNavState.node.name.takeIf { currentNavState.depth > 0 }
    LaunchedEffect(currentFolderTitle) { folderTitleCallback.value?.invoke(currentFolderTitle) }
    DisposableEffect(Unit) { onDispose { folderTitleCallback.value?.invoke(null) } }

    SideEffect {
        if (!currentNavState.node.isLeaf) {
            onArtworkPaletteChanged?.invoke(null)
        }
    }

    AnimatedContent(
        targetState = currentNavState,
        transitionSpec = {
            if (targetState.depth > initialState.depth) {
                // Diving in: slide in from right to left, slide out to left
                (slideInHorizontally { it } + fadeIn()) togetherWith
                    (slideOutHorizontally { -it / 3 } + fadeOut())
            } else {
                // Going back: slide in from left to right, slide out to right
                (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith
                    (slideOutHorizontally { it } + fadeOut())
            }
        },
        // Keyed by place, not by the node object: the tree is rebuilt whenever
        // the library is re-read (after a delete, say), and the same folder
        // must not slide in again as if it were another page.
        contentKey = { it.node.path },
        label = "folder_browser_transition",
        modifier = modifier.fillMaxSize(),
    ) { navState ->
        val node = navState.node
        // "Select all" covers the tracks listed on this level (not its subfolders').
        SelectionScopeEffect(key = node.path, songs = node.directSongs)
        if (node.isLeaf) {
            // Leaf folder: Render with full-screen artwork and blur wash, identical to album detail view
            DrillDownSongList(
                label = node.name,
                artworkUrl = (node.coverSongs.firstOrNull { !it.thumbnailUrl.isNullOrBlank() }
                    ?: node.directSongs.firstOrNull { !it.thumbnailUrl.isNullOrBlank() })?.thumbnailUrl,
                songs = node.directSongs,
                isArtist = false,
                fullScreenArtwork = true,
                currentSong = currentSong,
                isPlaying = isPlaying,
                selectedIds = selection?.ids ?: emptySet(),
                onSongClick = pickOrPlay,
                onSongLongPress = pick,
                onSongMore = { menuSong = it },
                onSongSwipe = onSongSwipe,
                onShuffle = onShuffle,
                onBack = popBack,
                contentPadding = contentPadding,
                songDropdownMenu = rowMenu,
                onArtworkPaletteChanged = onArtworkPaletteChanged,
            )
        } else {
            val scrollKey = "$rootKey:${node.path}"
            val savedScroll = remember(scrollKey) { PersistedFolderScrollStates[scrollKey] }
            val folderListState = rememberLazyListState(
                initialFirstVisibleItemIndex = savedScroll?.first ?: 0,
                initialFirstVisibleItemScrollOffset = savedScroll?.second ?: 0,
            )
            DisposableEffect(scrollKey, folderListState) {
                onDispose {
                    PersistedFolderScrollStates[scrollKey] =
                        folderListState.firstVisibleItemIndex to folderListState.firstVisibleItemScrollOffset
                }
            }
            // Intermediate folder: Breadcrumbs, Subfolders, and optional direct songs
            LazyColumn(
                state = folderListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = contentPadding.calculateTopPadding() + 8.dp,
                    bottom = contentPadding.calculateBottomPadding() + 16.dp,
                ),
                // No arrangement spacing: rows of a group sit a hairline apart
                // and headers carry their own padding.
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                // The tab's logo, at the Folders root only (deeper levels have a back button there).
                if (navState.depth == 0) {
                    item(key = "tab_logo") {
                        TabLogoHeader(
                            listTopPadding = contentPadding.calculateTopPadding() + 8.dp,
                            horizontalInset = 16.dp,
                        )
                    }
                }
                // Breadcrumbs & Header
                item(key = "header") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 12.dp),
                    ) {
                        // Breadcrumbs
                        val breadcrumbSegments = remember(node.relativePath, tree.name) {
                            val rel = node.relativePath
                            if (rel.isEmpty()) {
                                listOf(tree.name to "")
                            } else {
                                val list = mutableListOf(tree.name to "")
                                var curr = ""
                                for (part in rel.split('/').filter(String::isNotEmpty)) {
                                    curr = if (curr.isEmpty()) part else "$curr/$part"
                                    list.add(part to curr)
                                }
                                list
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            breadcrumbSegments.forEachIndexed { idx, (segName, segRel) ->
                                if (idx > 0) {
                                    Icon(
                                        imageVector = Icons.Rounded.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                                val isLast = idx == breadcrumbSegments.lastIndex
                                Text(
                                    text = segName,
                                    style = if (isLast) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isLast) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable(enabled = !isLast) {
                                            if (idx == 0) {
                                                pathStack.clear()
                                            } else {
                                                val targetPath = if (tree.path.isEmpty() || tree.path == "/") "/$segRel" else "${tree.path}/$segRel"
                                                val targetIdx = pathStack.indexOf(targetPath)
                                                if (targetIdx >= 0) {
                                                    while (pathStack.size > targetIdx + 1) {
                                                        pathStack.removeAt(pathStack.lastIndex)
                                                    }
                                                }
                                            }
                                        }
                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // Stats & Action buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${node.totalSongCount} ${if (node.totalSongCount == 1) "track" else "tracks"}" +
                                    if (node.subfolders.isNotEmpty()) " • ${node.subfolders.size} folders" else "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            if (node.totalSongCount > 0) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilledTonalButton(
                                        onClick = { onShuffle(node.allSongs) },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(12.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Shuffle,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("Shuffle", style = MaterialTheme.typography.labelMedium)
                                    }

                                    Button(
                                        onClick = { onSongClick(node.allSongs, 0) },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(12.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("Play", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }

                if (node.subfolders.isEmpty() && node.directSongs.isEmpty()) {
                    item(key = "empty") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Rounded.FolderOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(48.dp),
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "No songs found in this folder",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                // Subfolders list
                if (node.subfolders.isNotEmpty()) {
                    item(key = "subfolders_title") {
                        Text(
                            text = "Folders (${node.subfolders.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 8.dp),
                        )
                    }

                    // One rounded container for the whole set, rows split by
                    // thin gaps (Material 3 Expressive grouped list).
                    val folderCount = node.subfolders.size
                    itemsIndexed(node.subfolders, key = { _, folder -> folder.path }) { index, subfolder ->
                            // Long-press a folder to pick every track under it at once.
                            val groupKey = "folder:${subfolder.path}"
                            val folderPicked = selection != null && groupKey in selection.groupKeys
                            val selecting = selection?.active == true
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = if (index < folderCount - 1) ExpressiveGroupDefaults.Gap else 0.dp)
                                    .expressiveItem(index, folderCount)
                                    .background(
                                        androidx.compose.animation.animateColorAsState(
                                            if (folderPicked) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                                            label = "folderPicked",
                                        ).value,
                                    )
                                    .combinedClickable(
                                        onClick = {
                                            if (selecting) selection?.toggleGroup(groupKey, subfolder.allSongs)
                                            else pathStack.add(subfolder.path)
                                        },
                                        onLongClick = if (selection != null) {
                                            { selection.toggleGroup(groupKey, subfolder.allSongs) }
                                        } else null,
                                    )
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                FolderArtworkThumbnail(
                                    coverSongs = subfolder.coverSongs,
                                    modifier = Modifier.size(64.dp),
                                    shape = RoundedCornerShape(14.dp),
                                )

                                Spacer(Modifier.width(16.dp))

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Text(
                                        text = subfolder.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "${subfolder.totalSongCount} ${if (subfolder.totalSongCount == 1) "track" else "tracks"}" +
                                            if (subfolder.subfolders.isNotEmpty()) " • ${subfolder.subfolders.size} subfolders" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }

                                if (selecting) {
                                    SelectionMark(selected = folderPicked)
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }
                    }
                }

                // Direct tracks (if this non-leaf folder also contains songs directly)
                if (node.directSongs.isNotEmpty()) {
                    item(key = "tracks_title") {
                        Text(
                            text = "Tracks (${node.directSongs.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 8.dp, top = 20.dp, bottom = 8.dp),
                        )
                    }
                    val trackCount = node.directSongs.size

                    // The row's index comes with the item: asking the list for
                    // it (`directSongs.indexOf(song)`) ran a linear scan inside
                    // every row's composition — quadratic across a folder, and
                    // exactly why scrolling the top folder (the whole library)
                    // dropped frames and let the nav bar's fold lag the finger.
                    itemsIndexed(node.directSongs, key = { _, song -> song.videoId }) { index, song ->
                        val groupColor = ExpressiveGroupDefaults.containerColor()
                        SongRow(
                            song = song,
                            modifier = Modifier
                                .padding(bottom = if (index < trackCount - 1) ExpressiveGroupDefaults.Gap else 0.dp)
                                .expressiveItem(index, trackCount, groupColor),
                            rowBackground = groupColor,
                            isCurrent = currentSong?.videoId == song.videoId,
                            isPlaying = isPlaying,
                            selected = selection != null && song in selection,
                            onClick = { pickOrPlay(node.directSongs, index) },
                            onLongPress = { pick(song) },
                            onMore = { menuSong = song },
                            onSwipeToQueue = { onSongSwipe(song) },
                            dropdownMenu = { rowMenu(song) },
                        )
                    }
                }
            }
        }
    }
}
