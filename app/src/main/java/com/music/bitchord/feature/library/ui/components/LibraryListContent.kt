package com.music.bitchord.feature.library.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.data.model.Song
import com.music.bitchord.feature.localmusic.domain.model.LocalPlaylist
import com.music.bitchord.data.settings.LibrarySection
import com.music.bitchord.feature.library.data.LibraryEntry
import com.music.bitchord.feature.library.data.LibraryEntryType
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import com.music.bitchord.feature.localmusic.ui.components.LocalPlaylistItem
import com.music.bitchord.ui.components.ExpressiveGroupDefaults
import com.music.bitchord.ui.components.SongRow
import com.music.bitchord.ui.components.expressiveItem
import com.music.bitchord.ui.components.expressiveItemShape

/** How many tracks the Recently / Most played sections preview on "All". */
private const val HISTORY_PREVIEW_COUNT = 5

private val SECTION_GUTTER = 16.dp

/**
 * List layout of the Library.
 *
 * On "All" the page is four Material 3 Expressive groups — Favorites,
 * Playlists, Recently played, Most played — each one rounded container with
 * thin gaps between its rows, matching the Folders tab. The other filters show
 * just their own group.
 */
@Composable
fun LibraryListContent(
    filter: LibraryFilter,
    favoriteSongCount: Int,
    playlists: List<LocalPlaylist>,
    onFavoritesClick: () -> Unit,
    onFavoritesPlay: () -> Unit,
    onPlaylistClick: (LocalPlaylist) -> Unit,
    onPlaylistPlay: (LocalPlaylist) -> Unit,
    onRenamePlaylist: (LocalPlaylist) -> Unit,
    onDeletePlaylist: (LocalPlaylist) -> Unit,
    onChangePlaylistCover: (LocalPlaylist) -> Unit = {},
    onResetPlaylistCover: (LocalPlaylist) -> Unit = {},
    onMovePlaylist: (LocalPlaylist, Int) -> Unit,
    onCreatePlaylist: () -> Unit,
    librarySongs: List<Song> = emptyList(),
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    /** The page's large title and filters, scrolling with the list. */
    header: @Composable () -> Unit = {},
    recentlyPlayed: List<Song> = emptyList(),
    mostPlayed: List<Song> = emptyList(),
    playCountOf: (Song) -> Int = { 0 },
    onShowAllRecent: () -> Unit = {},
    onShowAllMost: () -> Unit = {},
    currentSong: Song? = null,
    isPlaying: Boolean = false,
    onSongClick: (List<Song>, Int) -> Unit = { _, _ -> },
    onSongLongPress: (Song) -> Unit = {},
    /** A row's ⋮: opens [songMenu] for that song. */
    onSongMore: ((Song, String?) -> Unit)? = null,
    /** The ⋮ menu, drawn inside the row's button (the Songs tab's menu). */
    songMenu: (@Composable (Song, String?) -> Unit)? = null,
    /** Picked tracks while selecting, for the rows' selected look. */
    selectedIds: Set<String> = emptySet(),
    onSongSwipe: (Song) -> Unit = {},
    /** The All page's groups, in the listener's order, and the ones they hid. */
    sectionOrder: List<LibrarySection> = LibrarySection.entries,
    hiddenSections: Set<LibrarySection> = emptySet(),
    /** Songs, albums and artists added to the Library, newest first. */
    libraryEntries: List<LibraryEntry> = emptyList(),
    /** Each SONG entry's song, where it is still on the device. */
    entrySongs: Map<LibraryEntry, Song> = emptyMap(),
    artFor: (LibraryEntry) -> String? = { null },
    onOpenAlbum: (String, String?) -> Unit = { _, _ -> },
    onOpenArtist: (String) -> Unit = {},
    onShowAll: (LibraryFilter) -> Unit = {},
) {
    val showFavorites = filter == LibraryFilter.FAVORITES
    val showPlaylists = filter == LibraryFilter.PLAYLISTS
    val showHistory = false
    val librarySongList = libraryEntries.mapNotNull { entrySongs[it] }.distinctBy { it.videoId }
    val albumEntries = libraryEntries.filter { it.type == LibraryEntryType.ALBUM }
    val artistEntries = libraryEntries.filter { it.type == LibraryEntryType.ARTIST }
    val recent = recentLibraryItems(libraryEntries, entrySongs, artFor).take(16)
    val visibleSections = if (filter == LibraryFilter.ALL) sectionOrder.filter { it !in hiddenSections } else emptyList()
    var editLayout by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    LazyColumn(
        state = state,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 80.dp,
        ),
    ) {
        item(key = "library_header") { header() }

        // "All": every group, in the listener's order.
        visibleSections.forEach { section ->
            when (section) {
                LibrarySection.RECENTLY_ADDED -> if (recent.isNotEmpty()) {
                    item(key = "library_recent_title") { LibrarySectionTitle(stringResource(R.string.recently_added_to_library)) }
                    item(key = "library_recent_shelf") {
                        LibraryShelf(recent, key = { "${it.addedAt}_${it.hashCode()}" }) { item ->
                            when (item) {
                                is RecentLibraryItem.OneSong -> LibraryCard(
                                    title = item.song.title,
                                    subtitle = item.song.artist,
                                    artworkUrl = item.song.thumbnailUrl,
                                    heat = com.music.bitchord.ui.components.rememberSongTileHeat(item.song),
                                    onClick = { onSongClick(listOf(item.song), 0) },
                                )
                                is RecentLibraryItem.AlbumBatch -> LibraryCard(
                                    title = item.title,
                                    subtitle = item.artist,
                                    artworkUrl = item.artworkUrl,
                                    heat = com.music.bitchord.ui.components.rememberTileHeat(item.songs),
                                    onClick = { onOpenAlbum(item.title, item.artworkUrl) },
                                )
                                is RecentLibraryItem.Collection -> LibraryCard(
                                    title = item.entry.title,
                                    subtitle = item.entry.subtitle,
                                    artworkUrl = item.artworkUrl,
                                    round = item.entry.type == LibraryEntryType.ARTIST,
                                    heat = rememberEntryHeat(item.entry, librarySongs),
                                    onClick = {
                                        if (item.entry.type == LibraryEntryType.ARTIST) onOpenArtist(item.entry.title)
                                        else onOpenAlbum(item.entry.title, item.artworkUrl)
                                    },
                                )
                            }
                        }
                    }
                }
                LibrarySection.FAVORITES -> favoritesGroup(favoriteSongCount, onFavoritesClick, onFavoritesPlay, librarySongs)
                LibrarySection.PLAYLISTS -> playlistsGroup(
                    playlists, onPlaylistClick, onPlaylistPlay, onRenamePlaylist, onDeletePlaylist,
                    onChangePlaylistCover, onResetPlaylistCover, onMovePlaylist, onCreatePlaylist, librarySongs,
                )
                LibrarySection.SONGS -> if (librarySongList.isNotEmpty()) {
                    val preview = librarySongList.take(5)
                    item(key = "library_songs_title") {
                        LibrarySectionTitle(
                            stringResource(R.string.songs),
                            actionLabel = if (librarySongList.size > preview.size) stringResource(R.string.show_all) else null,
                            onAction = { onShowAll(LibraryFilter.SONGS) },
                        )
                    }
                    librarySongRows("library_songs", preview, librarySongList, currentSong, isPlaying, selectedIds,
                        onSongClick, onSongLongPress, onSongSwipe, onSongMore, songMenu)
                }
                LibrarySection.ALBUMS -> if (albumEntries.isNotEmpty()) {
                    item(key = "library_albums_title") {
                        LibrarySectionTitle(stringResource(R.string.albums), stringResource(R.string.show_all)) { onShowAll(LibraryFilter.ALBUMS) }
                    }
                    item(key = "library_albums_shelf") {
                        LibraryShelf(albumEntries, key = { it.key }) { e ->
                            LibraryCard(e.title, e.subtitle, e.artworkUrl ?: artFor(e), heat = rememberEntryHeat(e, librarySongs)) { onOpenAlbum(e.title, e.artworkUrl ?: artFor(e)) }
                        }
                    }
                }
                LibrarySection.ARTISTS -> if (artistEntries.isNotEmpty()) {
                    item(key = "library_artists_title") {
                        LibrarySectionTitle(stringResource(R.string.artists), stringResource(R.string.show_all)) { onShowAll(LibraryFilter.ARTISTS) }
                    }
                    item(key = "library_artists_shelf") {
                        LibraryShelf(artistEntries, key = { it.key }) { e ->
                            LibraryCard(e.title, "", e.artworkUrl ?: artFor(e), round = true, size = 112.dp, heat = rememberEntryHeat(e, librarySongs)) { onOpenArtist(e.title) }
                        }
                    }
                }
            }
        }

        // One group on its own, from its filter chip.
        when (filter) {
            LibraryFilter.SONGS -> if (librarySongList.isEmpty()) libraryEmptyNote("library_songs_empty") else
                librarySongRows("library_songs_all", librarySongList, librarySongList, currentSong, isPlaying, selectedIds,
                    onSongClick, onSongLongPress, onSongSwipe, onSongMore, songMenu)
            LibraryFilter.ALBUMS -> if (albumEntries.isEmpty()) libraryEmptyNote("library_albums_empty") else
                collectionRows("library_albums_all", albumEntries, round = false, artFor = artFor) { e -> onOpenAlbum(e.title, e.artworkUrl ?: artFor(e)) }
            LibraryFilter.ARTISTS -> if (artistEntries.isEmpty()) libraryEmptyNote("library_artists_empty") else
                collectionRows("library_artists_all", artistEntries, round = true, artFor = artFor) { e -> onOpenArtist(e.title) }
            else -> Unit
        }

        if (showFavorites) favoritesGroup(favoriteSongCount, onFavoritesClick, onFavoritesPlay, librarySongs)
        if (showPlaylists) playlistsGroup(
            playlists, onPlaylistClick, onPlaylistPlay, onRenamePlaylist, onDeletePlaylist,
            onChangePlaylistCover, onResetPlaylistCover, onMovePlaylist, onCreatePlaylist, librarySongs,
        )
        if (showHistory) {
            historySection(
                key = "recent",
                titleRes = R.string.recently_played,
                songs = recentlyPlayed,
                onShowAll = onShowAllRecent,
                subtitleExtra = null,
                currentSong = currentSong,
                isPlaying = isPlaying,
                onSongClick = onSongClick,
                onSongLongPress = onSongLongPress,
                onSongMore = onSongMore,
                songMenu = songMenu,
                selectedIds = selectedIds,
                onSongSwipe = onSongSwipe,
            )
            historySection(
                key = "most",
                titleRes = R.string.most_played,
                songs = mostPlayed,
                onShowAll = onShowAllMost,
                subtitleExtra = playCountOf,
                currentSong = currentSong,
                isPlaying = isPlaying,
                onSongClick = onSongClick,
                onSongLongPress = onSongLongPress,
                onSongMore = onSongMore,
                songMenu = songMenu,
                selectedIds = selectedIds,
                onSongSwipe = onSongSwipe,
            )
        }

        // "All"'s groups: order and visibility, edited here rather than in Settings.
        if (filter == LibraryFilter.ALL) {
            item(key = "library_edit_layout") {
                com.music.bitchord.ui.components.EditLayoutButton(onClick = { editLayout = true })
            }
        }
    }
    if (editLayout) {
        com.music.bitchord.ui.components.LibraryLayoutSheet(onDismiss = { editLayout = false })
    }
}

/** A Recently / Most played group: the top few tracks and a "Show all" link. Hidden when empty. */
private fun LazyListScope.historySection(
    key: String,
    titleRes: Int,
    songs: List<Song>,
    onShowAll: () -> Unit,
    subtitleExtra: ((Song) -> Int)?,
    currentSong: Song?,
    isPlaying: Boolean,
    onSongClick: (List<Song>, Int) -> Unit,
    onSongLongPress: (Song) -> Unit,
    onSongSwipe: (Song) -> Unit,
    onSongMore: ((Song, String?) -> Unit)? = null,
    songMenu: (@Composable (Song, String?) -> Unit)? = null,
    selectedIds: Set<String> = emptySet(),
) {
    if (songs.isEmpty()) return
    val preview = songs.take(HISTORY_PREVIEW_COUNT)
    item(key = "library_${key}_title") {
        SectionHeader(
            title = stringResource(titleRes),
            actionLabel = if (songs.size > preview.size) stringResource(R.string.show_all) else null,
            onAction = onShowAll,
        )
    }
    itemsIndexed(preview, key = { _, song -> "library_${key}_${song.localUri ?: song.videoId}" }) { index, song ->
        val groupColor = ExpressiveGroupDefaults.containerColor()
        // Only the displayed copy carries the play count; every action gets the real song.
        val shown = subtitleExtra?.let { countOf ->
            val count = countOf(song)
            if (count > 0) {
                val countText = stringResource(R.string.library_play_count, count)
                song.copy(artist = if (song.artist.isBlank()) countText else "${song.artist} · $countText")
            } else song
        } ?: song
        SongRow(
            song = shown,
            modifier = Modifier
                .padding(horizontal = SECTION_GUTTER)
                .padding(bottom = if (index < preview.lastIndex) ExpressiveGroupDefaults.Gap else 0.dp)
                .expressiveItem(index, preview.size, groupColor),
            rowBackground = groupColor,
            isCurrent = currentSong?.videoId == song.videoId,
            isPlaying = isPlaying,
            selected = song.videoId in selectedIds,
            onClick = { onSongClick(songs, index) },
            onLongPress = { onSongLongPress(song) },
            onMore = onSongMore?.let { more -> { more(song, key) } },
            onSwipeToQueue = { onSongSwipe(song) },
            dropdownMenu = songMenu?.let { menu -> { menu(song, key) } },
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    actionLabel: String? = null,
    actionIsAdd: Boolean = false,
    onAction: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = SECTION_GUTTER + 8.dp, end = SECTION_GUTTER, top = 20.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null) {
            TextButton(onClick = onAction) {
                if (actionIsAdd) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(actionLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun CreatePlaylistRow(onCreatePlaylist: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = SECTION_GUTTER)
            .fillMaxWidth()
            .expressiveItem(0, 1)
            .clickable(onClick = onCreatePlaylist)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .expressiveItem(0, 1, MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
        }
        Text(
            text = stringResource(R.string.new_playlist),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun LazyListScope.favoritesGroup(
    favoriteSongCount: Int,
    onFavoritesClick: () -> Unit,
    onFavoritesPlay: () -> Unit,
    librarySongs: List<Song>,
) {
    item(key = "library_favorites_title") {
        SectionHeader(stringResource(R.string.favorites))
    }
    item(key = "library_list_favorites") {
        LibraryFavoritesItem(
            songCount = favoriteSongCount,
            onClick = onFavoritesClick,
            onPlay = onFavoritesPlay,
            modifier = Modifier.padding(horizontal = SECTION_GUTTER),
            shape = expressiveItemShape(0, 1),
            containerColor = ExpressiveGroupDefaults.containerColor(),
            heat = com.music.bitchord.ui.components.rememberFavoritesTileHeat(librarySongs),
        )
    }
}

private fun LazyListScope.playlistsGroup(
    playlists: List<LocalPlaylist>,
    onPlaylistClick: (LocalPlaylist) -> Unit,
    onPlaylistPlay: (LocalPlaylist) -> Unit,
    onRenamePlaylist: (LocalPlaylist) -> Unit,
    onDeletePlaylist: (LocalPlaylist) -> Unit,
    onChangePlaylistCover: (LocalPlaylist) -> Unit,
    onResetPlaylistCover: (LocalPlaylist) -> Unit,
    onMovePlaylist: (LocalPlaylist, Int) -> Unit,
    onCreatePlaylist: () -> Unit,
    librarySongs: List<Song>,
) {
    item(key = "library_playlists_title") {
        SectionHeader(
            title = stringResource(R.string.playlists),
            actionLabel = stringResource(R.string.new_playlist),
            actionIsAdd = true,
            onAction = onCreatePlaylist,
        )
    }
    if (playlists.isEmpty()) {
        item(key = "library_playlists_empty") {
            CreatePlaylistRow(onCreatePlaylist)
        }
    }
    itemsIndexed(playlists, key = { _, playlist -> playlist.id }) { index, playlist ->
        LocalPlaylistItem(
            playlist = playlist,
            onClick = { onPlaylistClick(playlist) },
            onPlay = { onPlaylistPlay(playlist) },
            onRename = { onRenamePlaylist(playlist) },
            onDelete = { onDeletePlaylist(playlist) },
            onChangeCover = { onChangePlaylistCover(playlist) },
            onResetCover = { onResetPlaylistCover(playlist) },
            canMoveUp = index > 0,
            canMoveDown = index < playlists.lastIndex,
            onMoveUp = { onMovePlaylist(playlist, -1) },
            onMoveDown = { onMovePlaylist(playlist, 1) },
            librarySongs = librarySongs,
            modifier = Modifier
                .padding(horizontal = SECTION_GUTTER)
                .padding(bottom = if (index < playlists.lastIndex) ExpressiveGroupDefaults.Gap else 0.dp),
            shape = expressiveItemShape(index, playlists.size),
            containerColor = ExpressiveGroupDefaults.containerColor(),
        )
    }
}

/** Albums or artists added to the Library, as rows (their filter's full list). */
private fun LazyListScope.collectionRows(
    key: String,
    entries: List<LibraryEntry>,
    round: Boolean,
    artFor: (LibraryEntry) -> String?,
    onClick: (LibraryEntry) -> Unit,
) {
    itemsIndexed(entries, key = { _, e -> "${key}_${e.key}" }) { index, e ->
        val groupColor = ExpressiveGroupDefaults.containerColor()
        Row(
            modifier = Modifier
                .padding(horizontal = SECTION_GUTTER)
                .padding(bottom = if (index < entries.lastIndex) ExpressiveGroupDefaults.Gap else 0.dp)
                .fillMaxWidth()
                .expressiveItem(index, entries.size, groupColor)
                .clickable { onClick(e) }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val shape = if (round) androidx.compose.foundation.shape.CircleShape else androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                coil3.compose.AsyncImage(
                    model = e.artworkUrl ?: artFor(e),
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.size(52.dp).clip(shape),
                )
            }
            Spacer(Modifier.width(14.dp))
            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                Text(e.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                if (e.subtitle.isNotBlank()) {
                    Text(e.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
        }
    }
}