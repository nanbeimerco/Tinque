package com.music.bitchord.ui.screens

import com.music.bitchord.ui.components.GlassDropdownMenu
import com.music.bitchord.ui.components.SelectionDeleteEffect
import com.music.bitchord.ui.components.SelectionScopeEffect
import com.music.bitchord.ui.components.SongSelection
import com.music.bitchord.ui.components.LocalSongSelection
import androidx.compose.animation.animateColorAsState
import com.music.bitchord.data.settings.GridKind
import com.music.bitchord.ui.components.ScaledGridCells
import com.music.bitchord.ui.components.pinchToResizeGrid
import com.music.bitchord.ui.components.rememberGridColumns
import com.music.bitchord.ui.components.rememberCardCompact
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import com.music.bitchord.data.LocalMediaRepository
import com.music.bitchord.data.model.durationMillis
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.data.model.sortedByAlbumTrackOrder
import com.music.bitchord.feature.artistimage.model.ArtistEntry
import com.music.bitchord.feature.artistimage.model.ArtistImage
import com.music.bitchord.feature.artistimage.model.ArtistSort
import com.music.bitchord.feature.artistimage.util.ArtistSplitter
import com.music.bitchord.ui.components.ArtistViewAndSortControls
import com.music.bitchord.ui.components.depthTransition
import com.music.bitchord.ui.components.TabLogoHeader
import com.music.bitchord.ui.components.ExpressiveGroupDefaults
import com.music.bitchord.ui.components.expressiveItem
import com.music.bitchord.feature.localmusic.domain.model.LocalFolder
import com.music.bitchord.feature.localmusic.domain.model.LocalPlaylist
import com.music.bitchord.feature.localmusic.data.LocalPlaylistStore
import com.music.bitchord.feature.localmusic.data.resolvePlaylistSongs
import com.music.bitchord.feature.localmusic.ui.components.LocalFoldersTab
import com.music.bitchord.feature.localmusic.ui.components.LocalPlaylistsTab
import com.music.bitchord.feature.localmusic.ui.components.rememberPlaylistCoverPicker
import com.music.bitchord.feature.localmusic.ui.components.BlacklistedFoldersSheet
import com.music.bitchord.feature.localmusic.ui.components.CreatePlaylistDialog
import com.music.bitchord.feature.localmusic.ui.components.DrillDownSongList
import com.music.bitchord.feature.localmusic.ui.components.SongGridCard
import com.music.bitchord.ui.components.FastScroller
import com.music.bitchord.ui.components.SectionIndexer
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import coil3.compose.AsyncImage
import com.music.bitchord.data.model.CARD_ART_PX
import com.music.bitchord.data.model.ROW_ART_PX
import com.music.bitchord.data.model.Song
import com.music.bitchord.R
import com.music.bitchord.ui.components.ExplicitSongTitle
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.data.model.isSameTrackAs
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.LibraryViewType
import com.music.bitchord.ui.theme.ArtworkPalette
import com.music.bitchord.data.settings.LocalMusicSort
import com.music.bitchord.data.settings.LocalLibraryTab
import com.music.bitchord.download.DownloadedCollection
import com.music.bitchord.ui.components.MessageState
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.components.ROW_DIVIDER_INSET
import com.music.bitchord.ui.components.SongRow
import com.music.bitchord.ui.components.thumbnailBorder
import com.music.bitchord.ui.components.TopBarContentGap
import com.music.bitchord.ui.components.topBarHeight
import com.music.bitchord.feature.localsongactions.ui.LocalSongActionsHelper
import com.music.bitchord.feature.localsongactions.ui.components.LocalAddToPlaylistSheet
import com.music.bitchord.feature.localsongactions.ui.components.LocalDeleteConfirmDialog
import com.music.bitchord.feature.localsongactions.ui.components.LocalSongDetailsSheet
import com.music.bitchord.feature.localsongactions.ui.components.LocalSongDropdownMenu
import com.music.bitchord.feature.lyricseditor.ui.LyricsEditorScreen
import com.music.bitchord.feature.tageditor.ui.TagEditorScreen
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.ui.haptics.rememberHaptics
import com.music.bitchord.ui.icons.BitChordIcons
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

const val LOCAL_TAB_SONGS = 0
const val LOCAL_TAB_ALBUMS = 1
const val LOCAL_TAB_ARTISTS = 2
const val LOCAL_TAB_FOLDERS = 3
const val LOCAL_TAB_PLAYLISTS = 4
const val LOCAL_TAB_ALBUM_ARTISTS = 5

private fun shuffleAlbumGroupsKeepTrackOrder(songs: List<Song>): List<Song> {
    val indexed = songs.withIndex()
    val groups = indexed.groupBy { indexedSong ->
        val song = indexedSong.value
        val album = song.albumName?.trim()?.takeIf(String::isNotEmpty)
        if (album == null) "single:${song.localUri ?: song.videoId}"
        else "${song.albumArtist.orEmpty().trim().lowercase(Locale.ROOT)}\u0000${album.lowercase(Locale.ROOT)}"
    }.values.shuffled()

    return groups.flatMap { group -> group.map { it.value }.sortedByAlbumTrackOrder() }
}

/**
 * Local Music folder view with three tabs: Songs (default), Artists, Albums.
 *
 * Also the Downloads folder — the two are the same thing from here, a flat list
 * of tracks on this device, and they read as the same page because they are the
 * same page. What differs is only where the list came from, what to say when it
 * is empty, and whether anything knows how those tracks were *asked* for: see
 * [collections], which is the Downloads folder's alone.
 *
 * Tapping an artist or album name slides in a filtered song list inline, so
 * the tab bar stays visible and Back returns to the grid rather than leaving
 * the screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalMusicScreen(
    songs: List<Song>,
    onSongClick: (List<Song>, Int) -> Unit,
    onSongLongPress: (Song) -> Unit,
    onSongSwipe: (Song) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    onShuffleArtist: ((List<Song>) -> Unit)? = null,
    contentPadding: PaddingValues,
    /**
     * Shown in place of the tab content when there are no songs at all — the
     * reason there are none, which "0 songs" on its own doesn't give.
     */
    emptyMessage: String? = null,
    /**
     * One of the Artists / Albums groupings, held rather than tapped — the
     * album/playlist menu, with the rows it covers already in hand. Nothing
     * here has a browse id to fetch, so this is the only way these get one.
     */
    onCollectionLongPress: ((String, List<Song>) -> Unit)? = null,
    /**
     * The albums and playlists that were downloaded *as* albums and playlists.
     *
     * They lead the Albums tab, because they are the only entries on it that the
     * user actually asked for by name — the rest are groupings this screen
     * derived from whatever album tag each file happens to carry, which is a
     * good guess and nothing more. A playlist cannot be derived that way at all:
     * its tracks are off forty different releases and no tag on any of them says
     * which playlist they were pulled from, so without this a downloaded
     * playlist simply scattered.
     *
     * Empty for Local Music, where nothing was asked for through this app and
     * the tags are all there is.
     */
    collections: List<DownloadedCollection> = emptyList(),
    isDownloads: Boolean = false,
    currentSong: Song? = null,
    isPlaying: Boolean = false,
    playbackPositionMs: Long = 0L,
    playbackDurationMs: Long = 0L,
    onLyricsEditorPlaybackToggle: ((Song) -> Unit)? = null,
    onLyricsEditorSeek: ((Long) -> Unit)? = null,
    onLyricsEditorPrevious: (() -> Unit)? = null,
    onLyricsEditorNext: (() -> Unit)? = null,
    onArtworkPaletteChanged: ((ArtworkPalette?) -> Unit)? = null,
    /** Deletes the Downloads rows selected through this screen's long-press mode. */
    onDeleteDownloads: ((List<Song>) -> Unit)? = null,
    initialTab: Int = LOCAL_TAB_SONGS,
    showTabRow: Boolean = false,
    modifier: Modifier = Modifier,
    onPlayNext: ((Song) -> Unit)? = null,
    onAddToQueue: ((Song) -> Unit)? = null,
    onDeleteSong: ((Song) -> Unit)? = null,
    onSongTagsOrLyricsSaved: ((Song) -> Unit)? = null,
    isRefreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    initialDrillDownLabel: String? = null,
    onDrillDownDismiss: (() -> Unit)? = null,
    onCollectionNavigationChanged: ((Boolean, (() -> Unit)?) -> Unit)? = null,
) {
    val context = LocalContext.current
    val choosePlaylistCover = rememberPlaylistCoverPicker { playlistId, uri ->
        LocalPlaylistStore.setPlaylistCover(playlistId, uri)
    }
    var activeMenuSong by remember { mutableStateOf<Song?>(null) }
    var detailsSong by remember { mutableStateOf<Song?>(null) }
    var tagEditorSong by remember { mutableStateOf<Song?>(null) }
    var lyricsEditorSong by remember { mutableStateOf<Song?>(null) }
    var deleteSong by remember { mutableStateOf<Song?>(null) }
    var addToPlaylistSongs by remember { mutableStateOf<List<Song>?>(null) }

    // Which top-level tab is selected.
    var selectedTab by rememberSaveable { mutableIntStateOf(initialTab) }
    val visibleLocalLibraryTabs by AppSettings.visibleLocalLibraryTabs.collectAsStateWithLifecycle()
    val localLibraryTabOrder by AppSettings.localLibraryTabOrder.collectAsStateWithLifecycle()
    val displayedLocalTabs = remember(visibleLocalLibraryTabs, localLibraryTabOrder, isDownloads, showTabRow) {
        val allowedTabs = if (showTabRow) visibleLocalLibraryTabs else LocalLibraryTab.entries.toSet()
        localLibraryTabOrder
            .filter { it in allowedTabs }
            .filterNot { isDownloads && it in setOf(LocalLibraryTab.FOLDERS, LocalLibraryTab.PLAYLISTS) }
            .map(LocalLibraryTab::ordinal)
    }

    LaunchedEffect(initialTab) {
        selectedTab = initialTab.takeIf { it in displayedLocalTabs }
            ?: displayedLocalTabs.firstOrNull()
            ?: LOCAL_TAB_SONGS
    }
    LaunchedEffect(displayedLocalTabs) {
        if (selectedTab !in displayedLocalTabs) {
            selectedTab = displayedLocalTabs.firstOrNull() ?: LOCAL_TAB_SONGS
        }
    }

    // Narrows whichever tab is showing — songs by title/artist/album, artists
    // and albums by name. Not saved across process death: a filter left on a
    val sortOrder by if (isDownloads) {
        AppSettings.downloadedMusicSort.collectAsStateWithLifecycle()
    } else {
        AppSettings.localMusicSort.collectAsStateWithLifecycle()
    }
    val artistSort by if (isDownloads) {
        AppSettings.downloadedArtistSort.collectAsStateWithLifecycle()
    } else {
        AppSettings.localArtistSort.collectAsStateWithLifecycle()
    }
    val songsViewType by if (isDownloads) {
        AppSettings.downloadedSongsViewType.collectAsStateWithLifecycle()
    } else {
        AppSettings.localSongsViewType.collectAsStateWithLifecycle()
    }
    val albumsViewType by if (isDownloads) {
        AppSettings.downloadedAlbumsViewType.collectAsStateWithLifecycle()
    } else {
        AppSettings.localAlbumsViewType.collectAsStateWithLifecycle()
    }
    val artistsViewType by if (isDownloads) {
        AppSettings.downloadedArtistsViewType.collectAsStateWithLifecycle()
    } else {
        AppSettings.localArtistsViewType.collectAsStateWithLifecycle()
    }
    val localFolderHierarchy by AppSettings.localFolderHierarchy.collectAsStateWithLifecycle()
    val fullScreenCollectionArtwork by AppSettings.fullScreenCollectionArtwork.collectAsStateWithLifecycle()
    val artistsOpenAlbums by AppSettings.artistsOpenAlbums.collectAsStateWithLifecycle()
    val shuffleArtistAlbums by AppSettings.shuffleArtistAlbums.collectAsStateWithLifecycle()
    val onlineAlbumArtistArtwork by AppSettings.onlineAlbumArtistArtwork.collectAsStateWithLifecycle()
    val offlineMode by AppSettings.offlineMode.collectAsStateWithLifecycle()
    val drillDownViewType by if (isDownloads) {
        AppSettings.downloadedDrillDownSongsViewType.collectAsStateWithLifecycle()
    } else {
        AppSettings.localDrillDownSongsViewType.collectAsStateWithLifecycle()
    }
    // Cached across tab visits and sorted off the main thread — see [LibraryIndexCache].
    val cachePrefix = if (isDownloads) "downloads" else "local"
    val sortedSongsOrNull = rememberLibraryIndex("$cachePrefix:sorted", songs, sortOrder) {
        songs.sortedForLibrary(sortOrder)
    }
    val sortedSongs = sortedSongsOrNull ?: emptyList()
    // The activity's selection (shared with its top header and bottom toolbar).
    // Albums picked as a whole are kept apart from the tracks: different albums
    // can share tracks, so inferring an album's selection from those track ids
    // made one held playlist appear to select unrelated albums.
    val selection = LocalSongSelection.current ?: remember { SongSelection() }
    val selectedSongIds = selection.ids
    val selectedAlbumKeys = selection.groupKeys
    val scope = rememberCoroutineScope()
    val blacklistedFolders by AppSettings.blacklistedFolders.collectAsStateWithLifecycle()
    val localPlaylists by LocalPlaylistStore.playlists.collectAsStateWithLifecycle()
    var discoveredFolders by remember { mutableStateOf<List<LocalFolder>>(emptyList()) }
    var showManageFoldersSheet by remember { mutableStateOf(false) }
    val manageFoldersSheetState = rememberModalBottomSheetState()
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var playlistToRename by remember { mutableStateOf<LocalPlaylist?>(null) }

    LaunchedEffect(Unit) {
        discoveredFolders = LocalMediaRepository.getAllDiscoveredFolders(context)
    }
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            discoveredFolders = LocalMediaRepository.getAllDiscoveredFolders(context)
        }
    }

    val selectingSongs = selection.active

    fun toggleDownloadSelection(song: Song) = selection.toggle(song)

    fun toggleAlbumSelection(entry: AlbumEntry) = selection.toggleGroup(entry.key, entry.songs)

    // When non-null, we are showing a drill-down list for that artist or album.
    var drillDownLabel by remember { mutableStateOf<String?>(null) }
    var drillDownPlaylistId by remember { mutableStateOf<String?>(null) }
    var drillDownSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    // The release's own cover, for the drill-down header. Only a downloaded
    // album or playlist has one worth showing — a tag-derived grouping's
    // "artwork" is just whichever of its rows happened to be first.
    var drillDownArt by remember { mutableStateOf<String?>(null) }
    var drillDownIsArtist by remember { mutableStateOf(false) }
    var drillDownShowsAlbums by remember { mutableStateOf(false) }
    var drillDownFullScreenArtwork by remember { mutableStateOf(false) }
    var drillDownAlbumEntries by remember { mutableStateOf<List<AlbumEntry>?>(null) }
    var parentArtistAlbums by remember { mutableStateOf<List<AlbumEntry>?>(null) }
    var parentArtistLabel by remember { mutableStateOf<String?>(null) }

    val inDrillDown = drillDownLabel != null
    SideEffect {
        if (!inDrillDown || drillDownShowsAlbums || !fullScreenCollectionArtwork || !drillDownFullScreenArtwork) {
            onArtworkPaletteChanged?.invoke(null)
        }
    }
    val leaveDrillDown: () -> Unit = {
        drillDownLabel = null
        drillDownPlaylistId = null
        drillDownSongs = emptyList()
        drillDownArt = null
        drillDownIsArtist = false
        drillDownShowsAlbums = false
        drillDownFullScreenArtwork = false
        drillDownAlbumEntries = null
        parentArtistAlbums = null
        parentArtistLabel = null
        onDrillDownDismiss?.invoke()
    }

    val backFromDrillDown: () -> Unit = {
        val parent = parentArtistAlbums
        if (!drillDownShowsAlbums && parent != null) {
            drillDownShowsAlbums = true
            drillDownAlbumEntries = parent
            drillDownSongs = parent.flatMap(AlbumEntry::songs)
            drillDownLabel = parentArtistLabel
            drillDownArt = null
            drillDownFullScreenArtwork = false
            parentArtistAlbums = null
        } else {
            leaveDrillDown()
        }
    }

    val collectionNavigationCallback = rememberUpdatedState(onCollectionNavigationChanged)
    val currentCollectionBackHandler = rememberUpdatedState(backFromDrillDown)
    val collectionBackHandler = remember { { currentCollectionBackHandler.value() } }
    LaunchedEffect(inDrillDown) {
        collectionNavigationCallback.value?.invoke(
            inDrillDown,
            collectionBackHandler.takeIf { inDrillDown },
        )
    }
    DisposableEffect(Unit) {
        onDispose { collectionNavigationCallback.value?.invoke(false, null) }
    }

    LaunchedEffect(initialDrillDownLabel, songs, selectedTab, artistsOpenAlbums) {
        if (!initialDrillDownLabel.isNullOrBlank()) {
            val target = initialDrillDownLabel.trim()
            if (selectedTab == LOCAL_TAB_ARTISTS) {
                drillDownLabel = target
                drillDownSongs = songs.filter { ArtistSplitter.matchesArtist(it.artist, target) }
                drillDownArt = null
                drillDownIsArtist = true
                drillDownFullScreenArtwork = false
                drillDownAlbumEntries = albumEntries(drillDownSongs, emptyList())
                drillDownShowsAlbums = artistsOpenAlbums
                parentArtistLabel = target
            } else {
                val matching = songs
                    .filter { it.albumName?.trim().equals(target, ignoreCase = true) }
                    .sortedByAlbumTrackOrder()
                drillDownLabel = target
                drillDownSongs = matching
                drillDownArt = matching.firstNotNullOfOrNull { it.thumbnailUrl?.takeIf { url -> url.isNotBlank() } }
                drillDownIsArtist = false
                drillDownFullScreenArtwork = true
            }
        }
    }

    val songDropdownMenu: @Composable (Song) -> Unit = { song ->
        LocalSongDropdownMenu(
            expanded = (activeMenuSong?.let { it.localUri ?: it.videoId } == (song.localUri ?: song.videoId)),
            onDismissRequest = { activeMenuSong = null },
            song = song,
            onQueueNext = {
                activeMenuSong = null
                onPlayNext?.invoke(song) ?: onSongSwipe(song)
                Toast.makeText(context, "Queued next", Toast.LENGTH_SHORT).show()
            },
            onAddToQueue = {
                activeMenuSong = null
                onAddToQueue?.invoke(song) ?: onSongSwipe(song)
                Toast.makeText(context, "Added to playing queue", Toast.LENGTH_SHORT).show()
            },
            onAddToPlaylist = {
                activeMenuSong = null
                addToPlaylistSongs = listOf(song)
            },
            onRemoveFromPlaylist = if (drillDownPlaylistId != null) {
                {
                    activeMenuSong = null
                    val playlistId = drillDownPlaylistId ?: return@LocalSongDropdownMenu
                    val songId = song.localUri ?: song.videoId
                    LocalPlaylistStore.removeSongFromPlaylist(playlistId, songId)
                    drillDownSongs = drillDownSongs.filterNot { (it.localUri ?: it.videoId) == songId }
                    Toast.makeText(context, R.string.remove_from_playlist, Toast.LENGTH_SHORT).show()
                }
            } else null,
            onGoToAlbum = if (!song.albumName.isNullOrBlank()) {
                { targetAlbum ->
                    activeMenuSong = null
                    drillDownLabel = targetAlbum
                    drillDownSongs = songs.filter { it.albumName == targetAlbum }.sortedByAlbumTrackOrder()
                    drillDownArt = songs.find { it.albumName == targetAlbum }?.thumbnailUrl
                    drillDownIsArtist = false
                    drillDownShowsAlbums = false
                    drillDownFullScreenArtwork = true
                    parentArtistAlbums = null
                }
            } else null,
            onGoToArtist = if (song.artist.isNotBlank()) {
                { targetArtist ->
                    activeMenuSong = null
                    drillDownLabel = targetArtist
                    drillDownSongs = songs.filter { s -> ArtistSplitter.matchesArtist(s.artist, targetArtist) }
                    drillDownArt = null
                    drillDownIsArtist = true
                    drillDownFullScreenArtwork = false
                    drillDownAlbumEntries = albumEntries(drillDownSongs, emptyList())
                    drillDownShowsAlbums = artistsOpenAlbums
                    parentArtistLabel = targetArtist
                    parentArtistAlbums = null
                }
            } else null,
            onGoToFolder = if (!song.localPath.isNullOrBlank()) {
                { targetFolder ->
                    activeMenuSong = null
                    drillDownLabel = targetFolder.substringAfterLast('/')
                    drillDownSongs = songs.filter { (it.localPath ?: "").substringBeforeLast('/') == targetFolder }
                    drillDownArt = null
                    drillDownFullScreenArtwork = false
                }
            } else null,
            onTagEditor = {
                activeMenuSong = null
                tagEditorSong = song
            },
            onShare = {
                activeMenuSong = null
                LocalSongActionsHelper.shareSong(context, song)
            },
            onDeleteFromDevice = {
                activeMenuSong = null
                deleteSong = song
            },
            onDetails = {
                activeMenuSong = null
                detailsSong = song
            },
        )
    }

    BackHandler(enabled = selectingSongs) { selection.clear() }
    BackHandler(enabled = inDrillDown && !selectingSongs) { backFromDrillDown() }

    // A page that can delete what is picked offers it on the selection toolbar:
    // a playlist removes the tracks from itself, Downloads deletes the files.
    val deleteSelected: ((List<Song>) -> Unit)? = remember(isDownloads, drillDownPlaylistId) {
        val playlistId = drillDownPlaylistId
        when {
            playlistId != null -> { chosen ->
                val ids = chosen.map { it.videoId }.toSet()
                LocalPlaylistStore.removeSongsFromPlaylist(playlistId, ids)
                drillDownSongs = drillDownSongs.filterNot { (it.localUri ?: it.videoId) in ids || it.videoId in ids }
                Toast.makeText(context, R.string.remove_from_playlist, Toast.LENGTH_SHORT).show()
            }
            isDownloads -> { chosen -> onDeleteDownloads?.invoke(chosen) }
            else -> null
        }
    }
    SelectionDeleteEffect(deleteSelected)
    // Moving to another page ends a selection, as the back button would.
    LaunchedEffect(selectedTab, drillDownLabel, drillDownShowsAlbums) {
        selection.clear()
        activeMenuSong = null
    }

    // When tab row is showing, content scrolls beneath it. When tab row is hidden (bottom bar handles tabs),
    // content takes the full content padding including top bar padding.
    val bodyContentPadding = if (showTabRow) {
        PaddingValues(bottom = contentPadding.calculateBottomPadding())
    } else {
        contentPadding
    }

    val barHeight = topBarHeight()
    // The scrolling tab logo, at a tab's root — not on Downloads (a pushed page
    // with a back button) and not when the fixed top tab row is showing.
    val showTabLogo = !showTabRow && !isDownloads

    Column(modifier = modifier.fillMaxSize()) {
        // ── Tab row ──────────────────────────────────────────────────────────
        val selectedTabIndex = displayedLocalTabs.indexOf(selectedTab).coerceAtLeast(0)
        if (showTabRow) {
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary,
                edgePadding = PAGE_GUTTER,
                divider = {},
                indicator = { tabPositions ->
                    if (selectedTabIndex in tabPositions.indices) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                modifier = Modifier.padding(top = barHeight + 4.dp),
            ) {
                displayedLocalTabs.forEach { tab ->
                    val (icon, label) = when (tab) {
                        LOCAL_TAB_SONGS -> Icons.Rounded.MusicNote to stringResource(R.string.songs)
                        LOCAL_TAB_ALBUMS -> Icons.Rounded.Album to stringResource(R.string.albums)
                        LOCAL_TAB_ARTISTS -> Icons.Rounded.Person to stringResource(R.string.artists)
                        LOCAL_TAB_FOLDERS -> Icons.Rounded.Folder to stringResource(R.string.folders)
                        LOCAL_TAB_PLAYLISTS -> Icons.AutoMirrored.Rounded.QueueMusic to stringResource(R.string.playlists)
                        LOCAL_TAB_ALBUM_ARTISTS -> Icons.Rounded.Person to stringResource(R.string.album_artists)
                        else -> Icons.Rounded.MusicNote to stringResource(R.string.songs)
                    }
                    LocalTab(
                        icon = icon,
                        label = label,
                        selected = selectedTab == tab,
                        onClick = {
                            selectedTab = tab
                            leaveDrillDown()
                        },
                    )
                }
            }
        }

        // ── Content ──────────────────────────────────────────────────────────
        val pullToRefreshState = rememberPullToRefreshState()
        val topIndicatorPadding = if (showTabRow) 8.dp else contentPadding.calculateTopPadding()

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { onRefresh?.invoke() },
            state = pullToRefreshState,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                if (onRefresh != null) {
                    PullToRefreshDefaults.Indicator(
                        state = pullToRefreshState,
                        isRefreshing = isRefreshing,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = topIndicatorPadding),
                    )
                }
            },
        ) {
            val density = LocalDensity.current
            val maxPullDistancePx = with(density) { 80.dp.toPx() }

            // A drill-down page carries its own content in the key, so while one
            // slides out and the next slides in (artist → album, album → back
            // to the artist) each keeps drawing what it showed, and the depth
            // decides the direction.
            val drillPage: Any = if (inDrillDown) {
                DrillPage(
                    depth = if (!drillDownShowsAlbums && parentArtistAlbums != null) 2 else 1,
                    label = drillDownLabel ?: "",
                    songs = drillDownSongs,
                    art = drillDownArt,
                    isArtist = drillDownIsArtist,
                    showsAlbums = drillDownShowsAlbums,
                    albumEntries = drillDownAlbumEntries,
                    fullScreenArtwork = drillDownFullScreenArtwork,
                )
            } else {
                "tab:$selectedTab"
            }
            AnimatedContent(
                targetState = drillPage,
                transitionSpec = {
                    depthTransition { state -> (state as? DrillPage)?.depth ?: 0 }
                },
                contentKey = { state -> if (state is DrillPage) "drill:${state.depth}:${state.label}:${state.showsAlbums}" else state },
                label = "local_music_content",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val pullFraction = pullToRefreshState.distanceFraction
                        translationY = if (pullFraction > 0f) {
                            if (pullFraction <= 1f) {
                                pullFraction * maxPullDistancePx
                            } else {
                                maxPullDistancePx + (pullFraction - 1f) * maxPullDistancePx * 0.4f
                            }
                        } else {
                            0f
                        }
                    },
            ) { key ->
                when {
                    // Nothing to tab through. The tab row stays put rather than
                    // being swapped out with the list, so the page still reads as
                    // itself while it says why it's empty.
                    // First visit, still sorting: an empty page for a moment, not "no songs".
                    sortedSongsOrNull == null && songs.isNotEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize())
                    }

                    sortedSongs.isEmpty() && emptyMessage != null -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(bodyContentPadding),
                        ) {
                            MessageState(message = emptyMessage)
                        }
                    }

                key is DrillPage -> {
                    if (key.showsAlbums) {
                        val artistAlbums = key.albumEntries.orEmpty()
                        SelectionScopeEffect(
                            key = key,
                            songs = remember(artistAlbums) { artistAlbums.flatMap(AlbumEntry::songs).distinctBy { it.videoId } },
                            groupKeys = remember(artistAlbums) { artistAlbums.mapTo(HashSet()) { it.key } },
                        )
                        AlbumsTab(
                            albums = artistAlbums,
                            viewType = albumsViewType,
                            onViewTypeToggle = {
                                val next = if (albumsViewType == LibraryViewType.GRID) LibraryViewType.LIST else LibraryViewType.GRID
                                if (isDownloads) AppSettings.setDownloadedAlbumsViewType(next)
                                else AppSettings.setLocalAlbumsViewType(next)
                            },
                            onAlbumClick = { entry ->
                                parentArtistAlbums = artistAlbums
                                parentArtistLabel = key.label
                                drillDownLabel = entry.title
                                drillDownSongs = entry.songs
                                drillDownArt = entry.thumbnailUrl
                                drillDownIsArtist = false
                                drillDownShowsAlbums = false
                                drillDownFullScreenArtwork = true
                            },
                            onAlbumLongPress = { entry ->
                                if (isDownloads) toggleAlbumSelection(entry)
                                else onCollectionLongPress?.invoke(entry.title, entry.songs)
                            },
                            contentPadding = bodyContentPadding,
                        )
                    } else {
                        SelectionScopeEffect(key = key, songs = key.songs)
                        DrillDownSongList(
                            label = key.label,
                            artworkUrl = key.art,
                            songs = key.songs,
                            isArtist = key.isArtist,
                            fullScreenArtwork = fullScreenCollectionArtwork && key.fullScreenArtwork,
                            viewType = drillDownViewType,
                            onViewTypeToggle = {
                                val next = if (drillDownViewType == LibraryViewType.GRID) LibraryViewType.LIST else LibraryViewType.GRID
                                if (isDownloads) AppSettings.setDownloadedDrillDownSongsViewType(next)
                                else AppSettings.setLocalDrillDownSongsViewType(next)
                            },
                            selectedIds = selectedSongIds,
                            currentSong = currentSong,
                            isPlaying = isPlaying,
                            onSongClick = { tracks, index ->
                                val song = tracks[index]
                                if (selectingSongs) toggleDownloadSelection(song) else onSongClick(tracks, index)
                            },
                            onSongLongPress = { song -> toggleDownloadSelection(song) },
                            onSongMore = { song -> activeMenuSong = song },
                            onSongSwipe = onSongSwipe,
                            onShuffle = if (key.isArtist && shuffleArtistAlbums) {
                                { tracks ->
                                    val ordered = shuffleAlbumGroupsKeepTrackOrder(tracks)
                                    onShuffleArtist?.invoke(ordered) ?: onShuffle(ordered)
                                }
                            } else onShuffle,
                            onMore = onCollectionLongPress?.let { more ->
                                { more(key.label, key.songs) }
                            },
                            onBack = backFromDrillDown,
                            contentPadding = bodyContentPadding,
                            songDropdownMenu = if (isDownloads) null else songDropdownMenu,
                            onArtworkPaletteChanged = onArtworkPaletteChanged,
                        )
                    }
                }

                key == "tab:$LOCAL_TAB_SONGS" -> {
                    SelectionScopeEffect(key = key, songs = sortedSongs)
                    SongsTab(
                        songs = sortedSongs,
                        viewType = songsViewType,
                        sortOrder = sortOrder,
                        onSortOrderChange = {
                            if (isDownloads) AppSettings.setDownloadedMusicSort(it)
                            else AppSettings.setLocalMusicSort(it)
                        },
                        onViewTypeToggle = {
                            val next = if (songsViewType == LibraryViewType.GRID) LibraryViewType.LIST else LibraryViewType.GRID
                            if (isDownloads) AppSettings.setDownloadedSongsViewType(next)
                            else AppSettings.setLocalSongsViewType(next)
                        },
                        selectedIds = selectedSongIds,
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                        onSongClick = { tracks, index ->
                            val song = tracks[index]
                            if (selectingSongs) toggleDownloadSelection(song) else onSongClick(tracks, index)
                        },
                        onSongLongPress = { song ->
                            toggleDownloadSelection(song)
                        },
                        onSongMore = { song ->
                            activeMenuSong = song
                        },
                        onSongSwipe = onSongSwipe,
                        contentPadding = bodyContentPadding,
                        songDropdownMenu = if (isDownloads) null else songDropdownMenu,
                        showLogo = showTabLogo,
                    )
                }

                key == "tab:$LOCAL_TAB_ARTISTS" -> {
                    val unknownArtistStr = stringResource(R.string.unknown_artist)
                    val artists = rememberLibraryIndex("$cachePrefix:artists", sortedSongs, unknownArtistStr, artistSort) {
                        val grouped = ArtistSplitter.groupSongsByArtist(sortedSongs, unknownArtistStr)
                        when (artistSort) {
                            ArtistSort.MOST_SONGS -> grouped.sortedWith(
                                compareByDescending<ArtistEntry> { it.songs.size }
                                    .thenBy { it.name.lowercase(Locale.ROOT) }
                            )
                            ArtistSort.NAME_ASC -> grouped.sortedBy { it.name.lowercase(Locale.ROOT) }
                            ArtistSort.NAME_DESC -> grouped.sortedByDescending { it.name.lowercase(Locale.ROOT) }
                        }
                    }
                    if (artists == null) {
                        Box(modifier = Modifier.fillMaxSize())
                    } else ArtistsTab(
                        artists = artists,
                        viewType = artistsViewType,
                        onViewTypeToggle = {
                            val next = if (artistsViewType == LibraryViewType.GRID) LibraryViewType.LIST else LibraryViewType.GRID
                            if (isDownloads) AppSettings.setDownloadedArtistsViewType(next)
                            else AppSettings.setLocalArtistsViewType(next)
                        },
                        artistSort = artistSort,
                        onArtistSortChange = {
                            if (isDownloads) AppSettings.setDownloadedArtistSort(it)
                            else AppSettings.setLocalArtistSort(it)
                        },
                        onArtistClick = { artist, artistSongs ->
                            drillDownLabel = artist
                            drillDownSongs = artistSongs
                            drillDownArt = null
                            drillDownIsArtist = true
                            drillDownFullScreenArtwork = false
                            val groupedAlbums = albumEntries(artistSongs, emptyList())
                            drillDownAlbumEntries = groupedAlbums
                            drillDownShowsAlbums = artistsOpenAlbums
                            parentArtistAlbums = null
                            parentArtistLabel = artist
                        },
                        onArtistLongPress = onCollectionLongPress,
                        contentPadding = bodyContentPadding,
                        showLogo = showTabLogo,
                    )
                }

                key == "tab:$LOCAL_TAB_ALBUM_ARTISTS" -> {
                    val albumArtists = rememberLibraryIndex("$cachePrefix:albumArtists", sortedSongs, artistSort) {
                        val grouped = sortedSongs
                            .filter { !it.albumArtist.isNullOrBlank() }
                            .groupBy { it.albumArtist!!.trim().lowercase(Locale.ROOT) }
                            .map { (_, tracks) ->
                                val displayName = tracks.first().albumArtist!!.trim()
                                ArtistEntry(
                                    name = displayName,
                                    songs = tracks,
                                    thumbnailUrl = tracks.firstNotNullOfOrNull { it.thumbnailUrl },
                                    key = "album-artist:${displayName.lowercase(Locale.ROOT)}",
                                )
                            }
                        when (artistSort) {
                            ArtistSort.MOST_SONGS -> grouped.sortedWith(
                                compareByDescending<ArtistEntry> { it.songs.size }
                                    .thenBy { it.name.lowercase(Locale.ROOT) }
                            )
                            ArtistSort.NAME_ASC -> grouped.sortedBy { it.name.lowercase(Locale.ROOT) }
                            ArtistSort.NAME_DESC -> grouped.sortedByDescending { it.name.lowercase(Locale.ROOT) }
                        }
                    }
                    if (albumArtists == null) {
                        Box(modifier = Modifier.fillMaxSize())
                    } else ArtistsTab(
                        artists = albumArtists,
                        allowRemoteArtwork = onlineAlbumArtistArtwork && !offlineMode,
                        viewType = artistsViewType,
                        onViewTypeToggle = {
                            val next = if (artistsViewType == LibraryViewType.GRID) LibraryViewType.LIST else LibraryViewType.GRID
                            if (isDownloads) AppSettings.setDownloadedArtistsViewType(next)
                            else AppSettings.setLocalArtistsViewType(next)
                        },
                        artistSort = artistSort,
                        onArtistSortChange = {
                            if (isDownloads) AppSettings.setDownloadedArtistSort(it)
                            else AppSettings.setLocalArtistSort(it)
                        },
                        onArtistClick = { artist, artistSongs ->
                            drillDownLabel = artist
                            drillDownSongs = artistSongs
                            drillDownArt = null
                            drillDownIsArtist = true
                            drillDownFullScreenArtwork = false
                            val groupedAlbums = albumEntries(artistSongs, emptyList())
                            drillDownAlbumEntries = groupedAlbums
                            drillDownShowsAlbums = artistsOpenAlbums
                            parentArtistAlbums = null
                            parentArtistLabel = artist
                        },
                        onArtistLongPress = onCollectionLongPress,
                        contentPadding = bodyContentPadding,
                        showLogo = showTabLogo,
                    )
                }

                key == "tab:$LOCAL_TAB_ALBUMS" -> {
                    val albums = rememberLibraryIndex("$cachePrefix:albums", sortedSongs, collections) {
                        albumEntries(sortedSongs, collections)
                    }
                    if (albums != null) {
                        SelectionScopeEffect(
                            key = key,
                            songs = remember(albums) { albums.flatMap(AlbumEntry::songs).distinctBy { it.videoId } },
                            groupKeys = remember(albums) { albums.mapTo(HashSet()) { it.key } },
                        )
                    }
                    if (albums == null) {
                        Box(modifier = Modifier.fillMaxSize())
                    } else AlbumsTab(
                        albums = albums,
                        viewType = albumsViewType,
                        onViewTypeToggle = {
                            val next = if (albumsViewType == LibraryViewType.GRID) LibraryViewType.LIST else LibraryViewType.GRID
                            if (isDownloads) AppSettings.setDownloadedAlbumsViewType(next)
                            else AppSettings.setLocalAlbumsViewType(next)
                        },
                        selectedKeys = selectedAlbumKeys,
                        onAlbumClick = { entry ->
                            if (selectingSongs) {
                                toggleAlbumSelection(entry)
                            } else {
                                drillDownLabel = entry.title
                                drillDownSongs = entry.songs
                                drillDownArt = entry.thumbnailUrl
                                drillDownIsArtist = false
                                drillDownFullScreenArtwork = true
                            }
                        },
                        onAlbumLongPress = { entry ->
                            if (isDownloads) {
                                if (entry.key !in selection.groupKeys) selection.toggleGroup(entry.key, entry.songs)
                            } else onCollectionLongPress?.invoke(entry.title, entry.songs)
                        },
                        contentPadding = bodyContentPadding,
                        showLogo = showTabLogo,
                    )
                }

                key == "tab:$LOCAL_TAB_FOLDERS" -> {
                    val nonBlacklistedFolders = remember(discoveredFolders, blacklistedFolders) {
                        discoveredFolders.filter { !it.isBlacklisted }
                    }
                    LocalFoldersTab(
                        folders = nonBlacklistedFolders,
                        onFolderClick = { folder ->
                            drillDownLabel = folder.name
                            val folderSongs = songs.filter { (it.localPath ?: "").substringBeforeLast('/') == folder.path }
                            drillDownSongs = folderSongs
                            drillDownArt = folderSongs.firstOrNull { !it.thumbnailUrl.isNullOrBlank() }?.thumbnailUrl
                            drillDownFullScreenArtwork = true
                        },
                        onToggleBlacklist = { path, isBlacklisted ->
                            AppSettings.setFolderBlacklisted(path, isBlacklisted)
                            scope.launch {
                                discoveredFolders = LocalMediaRepository.getAllDiscoveredFolders(context)
                            }
                        },
                        onOpenManageFolders = { showManageFoldersSheet = true },
                        contentPadding = bodyContentPadding,
                        hierarchical = localFolderHierarchy,
                    )
                }

                key == "tab:$LOCAL_TAB_PLAYLISTS" -> {
                    LocalPlaylistsTab(
                        playlists = localPlaylists,
                        onPlaylistClick = { playlist ->
                            drillDownLabel = playlist.name
                            drillDownPlaylistId = playlist.id
                            drillDownSongs = resolvePlaylistSongs(playlist, songs)
                            drillDownArt = playlist.customCoverUrl ?: playlist.coverUrl
                            // The art-led layout is scoped to album details and
                            // the dedicated Library screen, not this playlist
                            // browser or other local music lists.
                            drillDownFullScreenArtwork = false
                        },
                        onPlaylistPlay = { playlist ->
                            val pSongs = resolvePlaylistSongs(playlist, songs)
                            if (pSongs.isNotEmpty()) onSongClick(pSongs, 0)
                        },
                        onCreatePlaylist = { showCreatePlaylistDialog = true },
                        onRenamePlaylist = { playlistToRename = it },
                        onDeletePlaylist = { LocalPlaylistStore.deletePlaylist(it.id) },
                        onChangePlaylistCover = { choosePlaylistCover(it.id) },
                        onResetPlaylistCover = { LocalPlaylistStore.setPlaylistCover(it.id, null) },
                        onMovePlaylist = { playlist, offset -> LocalPlaylistStore.movePlaylist(playlist.id, offset) },
                        librarySongs = songs,
                        contentPadding = bodyContentPadding,
                    )
                }
            }
        }
    }
    }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onConfirm = { name ->
                LocalPlaylistStore.createPlaylist(name)
                showCreatePlaylistDialog = false
            },
            onDismiss = { showCreatePlaylistDialog = false },
        )
    }

    if (playlistToRename != null) {
        CreatePlaylistDialog(
            initialName = playlistToRename!!.name,
            title = "Rename Playlist",
            confirmText = "Rename",
            onConfirm = { name ->
                LocalPlaylistStore.renamePlaylist(playlistToRename!!.id, name)
                playlistToRename = null
            },
            onDismiss = { playlistToRename = null },
        )
    }

    if (showManageFoldersSheet) {
        BlacklistedFoldersSheet(
            folders = discoveredFolders,
            onToggleBlacklist = { path, isBlacklisted ->
                AppSettings.setFolderBlacklisted(path, isBlacklisted)
                scope.launch {
                    discoveredFolders = LocalMediaRepository.getAllDiscoveredFolders(context)
                }
            },
            sheetState = manageFoldersSheetState,
            onDismiss = { showManageFoldersSheet = false },
        )
    }

    detailsSong?.let { song ->
        LocalSongDetailsSheet(
            song = song,
            onDismissRequest = { detailsSong = null },
            onLyricsEditorClick = {
                val target = detailsSong
                detailsSong = null
                lyricsEditorSong = target
            },
            onTagEditorClick = {
                val target = detailsSong
                detailsSong = null
                tagEditorSong = target
            },
        )
    }

    tagEditorSong?.let { song ->
        Dialog(
            onDismissRequest = { tagEditorSong = null },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            com.music.bitchord.ui.components.NoDialogDim()
            TagEditorScreen(
                song = song,
                onNavigateBack = { tagEditorSong = null },
                onTagsSaved = {
                    tagEditorSong = null
                    onDeleteSong?.invoke(song)
                    onSongTagsOrLyricsSaved?.invoke(song)
                },
            )
        }
    }

    lyricsEditorSong?.let { song ->
        val isActiveLyricsTrack = currentSong?.videoId == song.videoId ||
            (song.localUri != null && currentSong?.localUri == song.localUri)
        Dialog(
            onDismissRequest = { lyricsEditorSong = null },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            com.music.bitchord.ui.components.NoDialogDim()
            LyricsEditorScreen(
                song = song,
                onBackClick = { lyricsEditorSong = null },
                playbackPositionMs = if (isActiveLyricsTrack) playbackPositionMs else 0L,
                playbackDurationMs = if (isActiveLyricsTrack) playbackDurationMs else 0L,
                playbackIsPlaying = isPlaying && isActiveLyricsTrack,
                onSyncPlaybackToggle = { onLyricsEditorPlaybackToggle?.invoke(song) },
                onSyncSeek = if (isActiveLyricsTrack) onLyricsEditorSeek else null,
                onSyncPrevious = if (isActiveLyricsTrack) onLyricsEditorPrevious else null,
                onSyncNext = if (isActiveLyricsTrack) onLyricsEditorNext else null,
                onLyricsSaved = {
                    onSongTagsOrLyricsSaved?.invoke(song)
                },
            )
        }
    }

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

    addToPlaylistSongs?.let { selectedSongs ->
        LocalAddToPlaylistSheet(
            songs = selectedSongs,
            onDismissRequest = { addToPlaylistSongs = null },
        )
    }
}

/**
 * Where row [index] of an alphabetical list sits in its letter group: its
 * position and the group's size (for the group's rounded corners), and the
 * letter when it is the first row of the group.
 */
private data class SectionPos(val index: Int, val count: Int, val title: String?)

/** Groups consecutive [labels] by their index letter (the fast scroller's sections). */
private fun sectionPositions(labels: List<String>): List<SectionPos> {
    val letters = labels.map { SectionIndexer.getSectionName(it) }
    val result = ArrayList<SectionPos>(labels.size)
    var start = 0
    while (start < letters.size) {
        var end = start
        while (end + 1 < letters.size && letters[end + 1] == letters[start]) end++
        val count = end - start + 1
        for (i in 0 until count) result += SectionPos(i, count, if (i == 0) letters[start] else null)
        start = end + 1
    }
    return result
}

/** The letter above a group of rows. */
@Composable
private fun SectionLetter(letter: String) {
    Text(
        text = letter,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = PAGE_GUTTER + 8.dp, top = 14.dp, bottom = 6.dp),
    )
}

/** Gutters, gap and rounded group shape for a grouped row; nothing for a plain one. */
@Composable
private fun Modifier.groupedRow(group: SectionPos?): Modifier =
    if (group == null) this else this
        .padding(horizontal = PAGE_GUTTER)
        .padding(bottom = if (group.index < group.count - 1) ExpressiveGroupDefaults.Gap else 0.dp)
        .expressiveItem(group.index, group.count)

/** A tab list's first item: the scrolling tab logo (at a tab's root) above its header. */
@Composable
private fun LogoThenHeader(
    showLogo: Boolean,
    listTopPadding: Dp,
    horizontalInset: Dp = 0.dp,
    header: @Composable () -> Unit,
) {
    Column {
        if (showLogo) TabLogoHeader(listTopPadding = listTopPadding, horizontalInset = horizontalInset)
        header()
    }
}

// ── Songs tab ─────────────────────────────────────────────────────────────────

@Composable
private fun SongsTab(
    songs: List<Song>,
    viewType: LibraryViewType,
    sortOrder: LocalMusicSort,
    onSortOrderChange: (LocalMusicSort) -> Unit,
    onViewTypeToggle: () -> Unit,
    selectedIds: Set<String> = emptySet(),
    currentSong: Song? = null,
    isPlaying: Boolean = false,
    onSongClick: (List<Song>, Int) -> Unit,
    onSongLongPress: (Song) -> Unit,
    /** The row's ⋮, where holding it does something else — see [SongRow]. */
    onSongMore: ((Song) -> Unit)? = null,
    onSongSwipe: (Song) -> Unit,
    contentPadding: PaddingValues,
    songDropdownMenu: (@Composable (Song) -> Unit)? = null,
    showLogo: Boolean = false,
) {
    if (viewType == LibraryViewType.GRID) {
        val gridState = rememberLazyGridState()
        Box(modifier = Modifier.fillMaxSize()) {
            val gridCols = rememberGridColumns(GridKind.SONGS)
            val gridSpacing = com.music.bitchord.ui.components.gridSpacingFor(gridCols)
            androidx.compose.runtime.CompositionLocalProvider(com.music.bitchord.ui.components.LocalGridSpacing provides gridSpacing) {
            LazyVerticalGrid(
                columns = ScaledGridCells(gridCols),
                state = gridState,
                modifier = Modifier.fillMaxSize().pinchToResizeGrid(GridKind.SONGS, gridCols),
                contentPadding = PaddingValues(
                    start = PAGE_GUTTER,
                    end = PAGE_GUTTER,
                    top = contentPadding.calculateTopPadding() + 6.dp,
                    bottom = contentPadding.calculateBottomPadding() + 8.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(gridSpacing.horizontal),
                verticalArrangement = Arrangement.spacedBy(gridSpacing.vertical),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LogoThenHeader(showLogo, contentPadding.calculateTopPadding() + 6.dp, horizontalInset = PAGE_GUTTER) {
                    SectionHeader(
                        icon = Icons.Rounded.LibraryMusic,
                        title = pluralStringResource(R.plurals.song_count_plural, songs.size, songs.size),
                        modifier = Modifier.padding(horizontal = 0.dp, vertical = 6.dp),
                        actions = {
                            LocalViewAndSortControls(
                                sortOrder = sortOrder,
                                onSortOrderChange = onSortOrderChange,
                                viewType = viewType,
                                onViewTypeToggle = onViewTypeToggle,
                            )
                        },
                    )
                    }
                }
                itemsIndexed(songs) { index, song ->
                    SongGridCard(
                        modifier = Modifier.animateItem(),
                        song = song,
                        selected = song.videoId in selectedIds,
                        isCurrent = song.isSameTrackAs(currentSong),
                        onClick = { onSongClick(songs, index) },
                        onLongPress = { onSongLongPress(song) },
                    )
                }
            }
            }
            FastScroller(
                gridState = gridState,
                itemCount = songs.size,
                sectionNameForIndex = { index ->
                    val song = songs[index]
                    if (sortOrder == LocalMusicSort.ARTIST_ASC) SectionIndexer.getSectionName(song.artist)
                    else SectionIndexer.getSectionName(song.title)
                },
                contentPadding = PaddingValues(
                    top = contentPadding.calculateTopPadding() + 6.dp,
                    bottom = contentPadding.calculateBottomPadding() + 8.dp,
                ),
            )
        }
    } else {
        val listState = rememberLazyListState()
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
            ) {
                item {
                    LogoThenHeader(showLogo, contentPadding.calculateTopPadding()) {
                    SectionHeader(
                        icon = Icons.Rounded.LibraryMusic,
                        title = pluralStringResource(R.plurals.song_count_plural, songs.size, songs.size),
                        actions = {
                            LocalViewAndSortControls(
                                sortOrder = sortOrder,
                                onSortOrderChange = onSortOrderChange,
                                viewType = viewType,
                                onViewTypeToggle = onViewTypeToggle,
                            )
                        },
                    )
                    }
                }
                itemsIndexed(songs) { index, song ->
                    SongRow(
                        song = song,
                        selected = song.videoId in selectedIds,
                        isCurrent = song.isSameTrackAs(currentSong),
                        isPlaying = song.isSameTrackAs(currentSong) && isPlaying,
                        onClick = { onSongClick(songs, index) },
                        onLongPress = { onSongLongPress(song) },
                        onMore = onSongMore?.let { more -> { more(song) } },
                        onSwipeToQueue = { onSongSwipe(song) },
                        dropdownMenu = songDropdownMenu?.let { menu -> { menu(song) } },
                    )
                    // Flat rows, no divider lines: a long single list reads as
                    // one surface; spacing does the separating.
                }
            }
            FastScroller(
                listState = listState,
                itemCount = songs.size,
                sectionNameForIndex = { index ->
                    val song = songs[index]
                    if (sortOrder == LocalMusicSort.ARTIST_ASC) SectionIndexer.getSectionName(song.artist)
                    else SectionIndexer.getSectionName(song.title)
                },
                contentPadding = contentPadding,
            )
        }
    }
}

// ── Artists tab ───────────────────────────────────────────────────────────────

@Composable
private fun ArtistsTab(
    artists: List<ArtistEntry>,
    allowRemoteArtwork: Boolean = true,
    viewType: LibraryViewType,
    onViewTypeToggle: () -> Unit,
    artistSort: ArtistSort,
    onArtistSortChange: (ArtistSort) -> Unit,
    onArtistClick: (String, List<Song>) -> Unit,
    onArtistLongPress: ((String, List<Song>) -> Unit)?,
    contentPadding: PaddingValues,
    showLogo: Boolean = false,
) {
    if (viewType == LibraryViewType.GRID) {
        val gridState = rememberLazyGridState()
        Box(modifier = Modifier.fillMaxSize()) {
            val gridCols = rememberGridColumns(GridKind.ARTISTS)
            val gridSpacing = com.music.bitchord.ui.components.gridSpacingFor(gridCols)
            androidx.compose.runtime.CompositionLocalProvider(com.music.bitchord.ui.components.LocalGridSpacing provides gridSpacing) {
            LazyVerticalGrid(
                columns = ScaledGridCells(gridCols),
                state = gridState,
                modifier = Modifier.fillMaxSize().pinchToResizeGrid(GridKind.ARTISTS, gridCols),
                contentPadding = PaddingValues(
                    start = PAGE_GUTTER,
                    end = PAGE_GUTTER,
                    top = contentPadding.calculateTopPadding() + 6.dp,
                    bottom = contentPadding.calculateBottomPadding() + 8.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(gridSpacing.horizontal),
                verticalArrangement = Arrangement.spacedBy(gridSpacing.vertical),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LogoThenHeader(showLogo, contentPadding.calculateTopPadding() + 6.dp, horizontalInset = PAGE_GUTTER) {
                    SectionHeader(
                        icon = Icons.Rounded.Person,
                        title = pluralStringResource(R.plurals.artist_count, artists.size, artists.size),
                        modifier = Modifier.padding(horizontal = 0.dp, vertical = 6.dp),
                        actions = {
                            ArtistViewAndSortControls(
                                sortOrder = artistSort,
                                onSortOrderChange = onArtistSortChange,
                                viewType = viewType,
                                onViewTypeToggle = onViewTypeToggle,
                            )
                        },
                    )
                    }
                }
                items(artists, key = { it.key }) { entry ->
                    ArtistGridCard(
                        modifier = Modifier.animateItem(),
                        name = entry.name,
                        songCount = entry.songs.size,
                        thumbnailUrl = entry.thumbnailUrl,
                        allowRemoteArtwork = allowRemoteArtwork,
                        onClick = { onArtistClick(entry.name, entry.songs) },
                        onLongPress = onArtistLongPress?.let { { it(entry.name, entry.songs) } },
                    )
                }
            }
            }
            FastScroller(
                gridState = gridState,
                itemCount = artists.size,
                sectionNameForIndex = { index -> SectionIndexer.getSectionName(artists[index].name) },
                contentPadding = PaddingValues(
                    top = contentPadding.calculateTopPadding() + 6.dp,
                    bottom = contentPadding.calculateBottomPadding() + 8.dp,
                ),
            )
        }
    } else {
        val listState = rememberLazyListState()
        val sectionPositions = remember(artists, artistSort) {
            if (artistSort == ArtistSort.MOST_SONGS) null else sectionPositions(artists.map { it.name })
        }
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
            ) {
                item {
                    LogoThenHeader(showLogo, contentPadding.calculateTopPadding()) {
                    SectionHeader(
                        icon = Icons.Rounded.Person,
                        title = pluralStringResource(R.plurals.artist_count, artists.size, artists.size),
                        actions = {
                            ArtistViewAndSortControls(
                                sortOrder = artistSort,
                                onSortOrderChange = onArtistSortChange,
                                viewType = viewType,
                                onViewTypeToggle = onViewTypeToggle,
                            )
                        },
                    )
                    }
                }
                itemsIndexed(artists, key = { _, entry -> entry.key }) { index, entry ->
                    // Sorted by name: one rounded group per initial letter.
                    // By song count there are no letters to group by, so plain rows.
                    val pos = sectionPositions?.getOrNull(index)
                    ArtistRow(
                        name = entry.name,
                        songCount = entry.songs.size,
                        thumbnailUrl = entry.thumbnailUrl,
                        allowRemoteArtwork = allowRemoteArtwork,
                        onClick = { onArtistClick(entry.name, entry.songs) },
                        onLongPress = onArtistLongPress?.let { { it(entry.name, entry.songs) } },
                        group = pos,
                    )
                }
            }
            FastScroller(
                listState = listState,
                itemCount = artists.size,
                sectionNameForIndex = { index -> SectionIndexer.getSectionName(artists[index].name) },
                contentPadding = contentPadding,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArtistRow(
    name: String,
    songCount: Int,
    thumbnailUrl: String? = null,
    allowRemoteArtwork: Boolean = true,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    /** Position in a letter group, or null for a plain row. */
    group: SectionPos? = null,
) {
    Column {
    if (group?.title != null) SectionLetter(group.title)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .groupedRow(group)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = if (group != null) 16.dp else PAGE_GUTTER, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar circle
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp),
            )
            val imageModel = remember(name, thumbnailUrl, allowRemoteArtwork) {
                ArtistImage(
                    name = name,
                    fallbackUrl = thumbnailUrl?.artworkAt(ROW_ART_PX),
                    isLarge = false,
                    allowOnlineLookup = allowRemoteArtwork,
                )
            }
            AsyncImage(
                model = imageModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .thumbnailBorder(CircleShape),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = pluralStringResource(R.plurals.song_count_plural, songCount, songCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArtistGridCard(
    name: String,
    songCount: Int,
    thumbnailUrl: String?,
    allowRemoteArtwork: Boolean = true,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val cardCompact = rememberCardCompact()
    Column(
        modifier = modifier
            .then(cardCompact.measure)
            .clip(RoundedCornerShape(com.music.bitchord.ui.components.LocalGridSpacing.current.corner))
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(com.music.bitchord.ui.components.LocalGridSpacing.current.cardPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .aspectRatio(1f)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(44.dp),
            )
            val imageModel = remember(name, thumbnailUrl, allowRemoteArtwork) {
                ArtistImage(
                    name = name,
                    fallbackUrl = thumbnailUrl?.artworkAt(CARD_ART_PX),
                    isLarge = false,
                    allowOnlineLookup = allowRemoteArtwork,
                )
            }
            AsyncImage(
                model = imageModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .thumbnailBorder(CircleShape),
            )
        }
        Spacer(Modifier.height(if (cardCompact.compact) 4.dp else 8.dp))
        Text(
            text = name,
            style = if (cardCompact.compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (!cardCompact.compact) Text(
            text = pluralStringResource(R.plurals.song_count_plural, songCount, songCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

// ── Albums tab ────────────────────────────────────────────────────────────────

/**
 * One row of the Albums tab, whichever of the two things it came from.
 *
 * The tab used to be a `Map.Entry<String, List<Song>>` straight off a `groupBy`,
 * which was exactly as much as a tag grouping can say. A downloaded release
 * knows three more things — its own cover, whether it is a playlist rather than
 * an album, and the order its tracks go in — and none of those has anywhere to
 * live in a map entry.
 */
private class AlbumEntry(
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    /** Billed as a playlist rather than by artist; see [AlbumRow]. */
    val playlist: Boolean,
    /** Kept in the order it was downloaded in, which is the release's own. */
    val songs: List<Song>,
    /** Whether this is a release the user asked for, or a grouping inferred. */
    val asked: Boolean,
    /**
     * What the list keys this row by — the release's own id where it has one.
     *
     * Not the title: an album and a playlist can be called the same thing (a
     * self-titled record and its "This is …" mix, say), and two rows sharing a
     * key is a crash out of `LazyColumn` rather than a cosmetic clash.
     */
    val key: String,
)

/**
 * The Albums tab's rows: the releases downloaded whole, then whatever else the
 * files' own album tags group up.
 *
 * The two are merged rather than shown as separate sections because they are the
 * same kind of thing to whoever is looking for one — a folder of songs with a
 * name they remember. What matters is only that the *named* ones win a collision:
 * an album downloaded whole also stamps its name onto each of its tracks (see
 * `withAlbum` in MainActivity), so without this every one of them would appear
 * twice, once with its cover and once without.
 *
 * Releases lead within their own alphabetical run rather than being sorted
 * together, because a tag grouping is a guess and a recorded release is not.
 */
private fun albumEntries(
    songs: List<Song>,
    collections: List<DownloadedCollection>,
): List<AlbumEntry> {
    val asked = collections.map { collection ->
        AlbumEntry(
            title = collection.title,
            artist = collection.subtitle.ifBlank {
                collection.songs.firstOrNull()?.artist.orEmpty()
            },
            thumbnailUrl = collection.thumbnailUrl,
            playlist = collection.playlist,
            songs = collection.songs,
            asked = true,
            key = "asked:${collection.id}",
        )
    }
    val claimed = asked.mapTo(HashSet()) { it.title.lowercase(Locale.ROOT) }
    val derived = songs
        .groupBy { it.albumName }
        .mapNotNull { (name, group) ->
            // Null is every track that never said what release it was off, and
            // there is no row to draw for "no album" — those are the Songs tab's
            // and nothing else's.
            if (name == null || name.lowercase(Locale.ROOT) in claimed) return@mapNotNull null
            AlbumEntry(
                title = name,
                artist = group.firstOrNull()?.artist.orEmpty(),
                thumbnailUrl = group.firstNotNullOfOrNull { it.thumbnailUrl },
                playlist = false,
                songs = group.sortedByAlbumTrackOrder(),
                asked = false,
                key = "tagged:$name",
            )
        }
    return (asked + derived).sortedWith(
        compareByDescending<AlbumEntry> { it.asked }.thenBy { it.title.lowercase(Locale.ROOT) },
    )
}

@Composable
private fun AlbumsTab(
    albums: List<AlbumEntry>,
    viewType: LibraryViewType,
    onViewTypeToggle: () -> Unit,
    selectedKeys: Set<String> = emptySet(),
    onAlbumClick: (AlbumEntry) -> Unit,
    onAlbumLongPress: ((AlbumEntry) -> Unit)?,
    contentPadding: PaddingValues,
    showLogo: Boolean = false,
) {
    if (viewType == LibraryViewType.GRID) {
        val gridState = rememberLazyGridState()
        Box(modifier = Modifier.fillMaxSize()) {
            val gridCols = rememberGridColumns(GridKind.ALBUMS)
            val gridSpacing = com.music.bitchord.ui.components.gridSpacingFor(gridCols)
            androidx.compose.runtime.CompositionLocalProvider(com.music.bitchord.ui.components.LocalGridSpacing provides gridSpacing) {
            LazyVerticalGrid(
                columns = ScaledGridCells(gridCols),
                state = gridState,
                modifier = Modifier.fillMaxSize().pinchToResizeGrid(GridKind.ALBUMS, gridCols),
                contentPadding = PaddingValues(
                    start = PAGE_GUTTER,
                    end = PAGE_GUTTER,
                    top = contentPadding.calculateTopPadding() + 6.dp,
                    bottom = contentPadding.calculateBottomPadding() + 8.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(gridSpacing.horizontal),
                verticalArrangement = Arrangement.spacedBy(gridSpacing.vertical),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LogoThenHeader(showLogo, contentPadding.calculateTopPadding() + 6.dp, horizontalInset = PAGE_GUTTER) {
                    SectionHeader(
                        icon = Icons.Rounded.Album,
                        title = pluralStringResource(R.plurals.album_count, albums.size, albums.size),
                        modifier = Modifier.padding(horizontal = 0.dp, vertical = 6.dp),
                        actions = {
                            LocalViewAndSortControls(
                                sortOrder = LocalMusicSort.TITLE_ASC,
                                onSortOrderChange = {},
                                viewType = viewType,
                                onViewTypeToggle = onViewTypeToggle,
                                showSort = false,
                            )
                        },
                    )
                    }
                }
                if (albums.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        MessageState(
                            message = stringResource(R.string.no_local_albums),
                        )
                    }
                }
                items(albums, key = { it.key }) { entry ->
                    AlbumGridCard(
                        modifier = Modifier.animateItem(),
                        entry = entry,
                        selected = entry.key in selectedKeys,
                        onClick = { onAlbumClick(entry) },
                        onLongPress = onAlbumLongPress?.let { { it(entry) } },
                    )
                }
            }
            }
            FastScroller(
                gridState = gridState,
                itemCount = albums.size,
                sectionNameForIndex = { index -> SectionIndexer.getSectionName(albums[index].title) },
                contentPadding = PaddingValues(
                    top = contentPadding.calculateTopPadding() + 6.dp,
                    bottom = contentPadding.calculateBottomPadding() + 8.dp,
                ),
            )
        }
    } else {
        val listState = rememberLazyListState()
        // One rounded group per initial letter of the album title.
        val albumSections = remember(albums) { sectionPositions(albums.map { it.title }) }
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
            ) {
                item {
                    LogoThenHeader(showLogo, contentPadding.calculateTopPadding()) {
                    SectionHeader(
                        icon = Icons.Rounded.Album,
                        title = pluralStringResource(R.plurals.album_count, albums.size, albums.size),
                        actions = {
                            LocalViewAndSortControls(
                                sortOrder = LocalMusicSort.TITLE_ASC,
                                onSortOrderChange = {},
                                viewType = viewType,
                                onViewTypeToggle = onViewTypeToggle,
                                showSort = false,
                            )
                        },
                    )
                    }
                }
                if (albums.isEmpty()) {
                    item {
                        MessageState(
                            message = stringResource(R.string.no_local_albums),
                        )
                    }
                }
                itemsIndexed(albums, key = { _, entry -> entry.key }) { index, entry ->
                    AlbumRow(
                        entry = entry,
                        selected = entry.key in selectedKeys,
                        onClick = { onAlbumClick(entry) },
                        onLongPress = onAlbumLongPress?.let { { it(entry) } },
                        group = albumSections.getOrNull(index),
                    )
                }
            }
            FastScroller(
                listState = listState,
                itemCount = albums.size,
                sectionNameForIndex = { index -> SectionIndexer.getSectionName(albums[index].title) },
                contentPadding = contentPadding,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumGridCard(
    entry: AlbumEntry,
    selected: Boolean = false,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val cardCompact = rememberCardCompact()
    Column(
        modifier = modifier
            .then(cardCompact.measure)
            .clip(RoundedCornerShape(com.music.bitchord.ui.components.LocalGridSpacing.current.corner))
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(com.music.bitchord.ui.components.LocalGridSpacing.current.cardPadding),
    ) {
        val selectionActive = com.music.bitchord.ui.components.LocalSongSelection.current?.active == true
        val artScale = com.music.bitchord.ui.components.selectionArtScale(selected)
        val shape = RoundedCornerShape(com.music.bitchord.ui.components.selectionArtCorner(selected, com.music.bitchord.ui.components.LocalGridSpacing.current.corner))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .graphicsLayer {
                    scaleX = artScale
                    scaleY = artScale
                }
                .clip(shape)
                .thumbnailBorder(shape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (entry.playlist) Icons.AutoMirrored.Rounded.QueueMusic else Icons.Rounded.Album,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(36.dp),
            )
            if (entry.thumbnailUrl != null) {
                AsyncImage(
                    model = entry.thumbnailUrl.artworkAt(CARD_ART_PX),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (selectionActive) {
                com.music.bitchord.ui.components.SelectionMark(
                    selected = selected,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                )
            }
        }
        Spacer(Modifier.height(if (cardCompact.compact) 4.dp else 8.dp))
        Text(
            text = entry.title,
            style = if (cardCompact.compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val songCount = pluralStringResource(
            R.plurals.song_count_plural,
            entry.songs.size,
            entry.songs.size,
        )
        val playlistLabel = stringResource(R.string.playlist)
        if (!cardCompact.compact) Text(
            text = buildString {
                if (entry.playlist) {
                    append(playlistLabel)
                    append(" · ")
                } else if (entry.artist.isNotBlank() && entry.artist != entry.title) {
                    append("${entry.artist} · ")
                }
                append(songCount)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumRow(
    entry: AlbumEntry,
    selected: Boolean = false,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    /** Position in a letter group, or null for a plain row. */
    group: SectionPos? = null,
) {
    Column {
    if (group?.title != null) SectionLetter(group.title)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .groupedRow(group)
            .background(
                animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    label = "albumRowSelected",
                ).value,
                RoundedCornerShape(16.dp),
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = if (group != null) 16.dp else PAGE_GUTTER, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CollectionArtwork(
            url = entry.thumbnailUrl,
            playlist = entry.playlist,
            size = 48.dp,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val songCount = pluralStringResource(
                R.plurals.song_count_plural,
                entry.songs.size,
                entry.songs.size,
            )
            val playlistLabel = stringResource(R.string.playlist)
            Text(
                text = buildString {
                    // A playlist's tracks are off forty different releases, so
                    // the first one's artist is not a credit for it — the kind
                    // of thing it is says more, and is true.
                    if (entry.playlist) {
                        append(playlistLabel)
                        append(" · ")
                    } else if (entry.artist.isNotBlank() && entry.artist != entry.title) {
                        append("${entry.artist} · ")
                    }
                    append(songCount)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (com.music.bitchord.ui.components.LocalSongSelection.current?.active == true) {
            com.music.bitchord.ui.components.SelectionMark(selected = selected)
        } else {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
    }
}

/**
 * A release's cover, with a glyph standing in when there isn't one.
 *
 * The placeholder is not a fallback so much as the common case for anything
 * grouped off tags: those files' artwork is whatever the media scanner extracted,
 * which for a `.m4a` this app wrote is frequently nothing at all. Drawn behind
 * the image rather than instead of it, so a cover that loads late replaces the
 * glyph without the row changing size under it.
 */
@Composable
private fun CollectionArtwork(url: String?, playlist: Boolean, size: Dp) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (playlist) Icons.AutoMirrored.Rounded.QueueMusic else Icons.Rounded.Album,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(size * 0.54f),
        )
        if (url != null) {
            AsyncImage(
                model = url.artworkAt(ROW_ART_PX),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(shape)
                    .thumbnailBorder(shape),
            )
        }
    }
}

// ── Shared helpers ────────────────────────────────────────────────────────────

/** Whether this track is a hit for a query typed into [LocalSearchField]. */
private fun Song.matchesSearch(query: String): Boolean =
    title.contains(query, ignoreCase = true) ||
        artist.contains(query, ignoreCase = true) ||
        albumName?.contains(query, ignoreCase = true) == true

/**
 * One drill-down page — an artist's albums, or an album / artist / folder's
 * tracks — as the key of the page transition. It holds what the page shows,
 * so an outgoing page keeps drawing itself while the next slides in.
 * [depth] is 1 for a page opened from a tab, 2 for an album opened from an
 * artist's album list.
 */
private data class DrillPage(
    val depth: Int,
    val label: String,
    val songs: List<Song>,
    val art: String?,
    val isArtist: Boolean,
    val showsAlbums: Boolean,
    val albumEntries: List<AlbumEntry>?,
    val fullScreenArtwork: Boolean,
)

private fun List<Song>.sortedForLibrary(order: LocalMusicSort): List<Song> = when (order) {
    LocalMusicSort.TITLE_ASC -> sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    LocalMusicSort.TITLE_DESC -> sortedWith(compareByDescending<Song> { it.title.lowercase(Locale.ROOT) })
    LocalMusicSort.ARTIST_ASC -> sortedWith(
        compareBy<Song, String>(String.CASE_INSENSITIVE_ORDER) { it.artist }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
    )
    LocalMusicSort.DATE_ADDED -> sortedWith(
        compareByDescending<Song> { it.localDateAddedSeconds ?: Long.MIN_VALUE }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
    )
    LocalMusicSort.DATE_MODIFIED -> sortedWith(
        compareByDescending<Song> { it.localDateModifiedSeconds ?: Long.MIN_VALUE }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
    )
    LocalMusicSort.DURATION_DESC -> sortedWith(
        compareByDescending<Song> { it.durationMillis() }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
    )
}

/**
 * The filter box above the tab row.
 *
 * Live rather than submit-on-enter: there is no network round trip behind it,
 * only a list already in memory, so narrowing it on every keystroke costs
 * nothing and a submit action would just be a tap this screen doesn't need.
 */
@Composable
private fun LocalViewAndSortControls(
    sortOrder: LocalMusicSort,
    onSortOrderChange: (LocalMusicSort) -> Unit,
    viewType: LibraryViewType,
    onViewTypeToggle: () -> Unit,
    showViewToggle: Boolean = true,
    showSort: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var sortMenuOpen by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (showViewToggle) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable {
                        haptics.play(Haptic.Select)
                        onViewTypeToggle()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (viewType == LibraryViewType.GRID) BitChordIcons.ListView else BitChordIcons.GridView,
                    contentDescription = stringResource(
                        if (viewType == LibraryViewType.GRID) R.string.switch_to_list_view else R.string.switch_to_grid_view,
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (showSort) {
            Box {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { sortMenuOpen = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Sort,
                        contentDescription = stringResource(R.string.sort_music),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                GlassDropdownMenu(
                    expanded = sortMenuOpen,
                    onDismissRequest = { sortMenuOpen = false },
                ) {
                    LocalMusicSort.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.localizedLabel()) },
                            trailingIcon = if (option == sortOrder) {
                                {
                                    Icon(
                                        Icons.Rounded.Check,
                                        contentDescription = stringResource(R.string.selected),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            } else {
                                null
                            },
                            onClick = {
                                onSortOrderChange(option)
                                sortMenuOpen = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalMusicSort.localizedLabel(): String = when (this) {
    LocalMusicSort.TITLE_ASC -> stringResource(R.string.sort_title_ascending)
    LocalMusicSort.TITLE_DESC -> stringResource(R.string.sort_title_descending)
    LocalMusicSort.DATE_ADDED -> stringResource(R.string.sort_date_added)
    LocalMusicSort.DATE_MODIFIED -> stringResource(R.string.sort_date_modified)
    LocalMusicSort.ARTIST_ASC -> "Artist"
    LocalMusicSort.DURATION_DESC -> "Duration"
}

@Composable
private fun LocalTab(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val haptics = rememberHaptics()
    Tab(
        selected = selected,
        onClick = {
            if (!selected) haptics.play(Haptic.Select)
            onClick()
        },
        selectedContentColor = MaterialTheme.colorScheme.primary,
        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun SectionHeader(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier.padding(horizontal = PAGE_GUTTER, vertical = 6.dp),
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        actions()
    }
}
