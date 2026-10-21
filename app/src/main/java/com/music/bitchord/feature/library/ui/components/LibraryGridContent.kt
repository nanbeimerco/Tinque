package com.music.bitchord.feature.library.ui.components

import com.music.bitchord.data.settings.GridKind
import com.music.bitchord.ui.components.ScaledGridCells
import com.music.bitchord.ui.components.pinchToResizeGrid
import com.music.bitchord.ui.components.rememberGridColumns
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.music.bitchord.feature.localmusic.domain.model.LocalPlaylist
import com.music.bitchord.data.model.Song

/**
 * Grid layout presentation of the Library featuring Favorites and Playlists.
 */
@Composable
fun LibraryGridContent(
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
    state: LazyGridState = rememberLazyGridState(),
    /** The page's large title and filters, scrolling with the grid. */
    header: @Composable () -> Unit = {},
) {
    val gridCols = rememberGridColumns(GridKind.LIBRARY)
    LazyVerticalGrid(
        columns = ScaledGridCells(gridCols),
        state = state,
        modifier = modifier.fillMaxSize().pinchToResizeGrid(GridKind.LIBRARY, gridCols),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 80.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "library_header", span = { GridItemSpan(maxLineSpan) }) {
            // Undo the grid's side padding so the header lines up with the list layout.
            androidx.compose.foundation.layout.Box(
                Modifier.layout { measurable, constraints ->
                    val extra = 32.dp.roundToPx()
                    val placeable = measurable.measure(
                        constraints.copy(
                            minWidth = constraints.minWidth + extra,
                            maxWidth = constraints.maxWidth + extra,
                        ),
                    )
                    layout(constraints.maxWidth, placeable.height) {
                        placeable.place(-16.dp.roundToPx(), 0)
                    }
                },
            ) { header() }
        }
        val showFavorites = filter == LibraryFilter.ALL || filter == LibraryFilter.FAVORITES
        val showPlaylists = filter == LibraryFilter.ALL || filter == LibraryFilter.PLAYLISTS

        if (showFavorites) {
            item(key = "library_favorites") {
                LibraryFavoritesCard(
                    modifier = Modifier.animateItem(),
                    songCount = favoriteSongCount,
                    onClick = onFavoritesClick,
                    onPlay = onFavoritesPlay,
                )
            }
        }

        if (showPlaylists) {
            items(playlists, key = { it.id }) { playlist ->
                val index = playlists.indexOfFirst { it.id == playlist.id }
                LibraryPlaylistCard(
                    modifier = Modifier.animateItem(),
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
                )
            }

            item(key = "library_create_playlist") {
                LibraryCreatePlaylistCard(
                    modifier = Modifier.animateItem(),
                    onClick = onCreatePlaylist,
                )
            }
        }
    }
}
