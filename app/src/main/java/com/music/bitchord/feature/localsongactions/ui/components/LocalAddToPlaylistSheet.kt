package com.music.bitchord.feature.localsongactions.ui.components

import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.music.bitchord.ui.components.glassPalette
import com.music.bitchord.ui.components.expressiveItem
import com.music.bitchord.ui.components.LocalGlassSheets
import com.music.bitchord.ui.components.LocalGlassSheetDismiss
import com.music.bitchord.ui.components.GlassSheetKind
import com.music.bitchord.ui.components.GlassSheet
import com.music.bitchord.ui.components.GlassPalette
import com.music.bitchord.ui.components.ExpressiveGroupDefaults
import com.music.bitchord.feature.localmusic.domain.model.LocalPlaylist
import com.music.bitchord.R
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.model.Song
import com.music.bitchord.feature.localmusic.data.LocalPlaylistStore
import com.music.bitchord.feature.localmusic.domain.model.LocalPlaylistSongMetadata
import com.music.bitchord.feature.localmusic.ui.components.CreatePlaylistDialog

/**
 * Adds songs to an existing local playlist, or to a new one.
 *
 * A floating Liquid Glass sheet where the screen has a [LocalGlassSheets] host
 * (the content stays Material 3 Expressive: a grouped list), and Material's
 * bottom sheet anywhere else.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalAddToPlaylistSheet(
    songs: List<Song>,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val playlists by LocalPlaylistStore.playlists.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }

    val distinctSongs = remember(songs) {
        songs.distinctBy { it.localUri ?: it.videoId }
    }
    val songIds = remember(distinctSongs) { distinctSongs.map { it.localUri ?: it.videoId } }
    val songMetadata = remember(distinctSongs) {
        distinctSongs.associate { song ->
            (song.localUri ?: song.videoId) to LocalPlaylistSongMetadata(song.title, song.artist)
        }
    }
    val coverUrl = distinctSongs.firstNotNullOfOrNull { it.thumbnailUrl }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                val newPl = LocalPlaylistStore.createPlaylist(
                    name = name,
                    songIds = songIds,
                    songMetadata = songMetadata,
                    coverUrl = coverUrl,
                )
                Toast.makeText(
                    context,
                    context.getString(R.string.added_songs_to_playlist, songIds.size, newPl.name),
                    Toast.LENGTH_SHORT,
                ).show()
                showCreateDialog = false
                onDismissRequest()
            },
        )
    }

    val addTo: (LocalPlaylist) -> Unit = { playlist ->
        val addedCount = LocalPlaylistStore.addSongsToPlaylist(
            playlistId = playlist.id,
            songIds = songIds,
            songMetadata = songMetadata,
            artworkUrl = coverUrl,
        )
        Toast.makeText(
            context,
            context.getString(R.string.added_songs_to_playlist, addedCount, playlist.name),
            Toast.LENGTH_SHORT,
        ).show()
        onDismissRequest()
    }

    val sheets = com.music.bitchord.ui.components.glassSheetsHere()
    if (sheets != null) {
        GlassSheet(sheets, GlassSheetKind.Sheet, onDismiss = onDismissRequest) {
            AddToPlaylistContent(
                playlists = playlists,
                selectedCount = distinctSongs.size,
                palette = glassPalette(),
                onClose = LocalGlassSheetDismiss.current,
                onNewPlaylist = { showCreateDialog = true },
                onPick = addTo,
            )
        }
        return
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        AddToPlaylistContent(
            playlists = playlists,
            selectedCount = distinctSongs.size,
            palette = null,
            onClose = onDismissRequest,
            onNewPlaylist = { showCreateDialog = true },
            onPick = addTo,
        )
    }
}

/**
 * The sheet's contents: a title row, then two groups (M3 Expressive grouped
 * list) — "New playlist" on its own, and the playlists. On glass ([palette]
 * set) the colours are the glass's neutral ones; otherwise the theme's.
 */
@Composable
private fun AddToPlaylistContent(
    playlists: List<LocalPlaylist>,
    selectedCount: Int,
    palette: GlassPalette?,
    onClose: () -> Unit,
    onNewPlaylist: () -> Unit,
    onPick: (LocalPlaylist) -> Unit,
) {
    val content = palette?.content ?: MaterialTheme.colorScheme.onSurface
    val secondary = palette?.secondary ?: MaterialTheme.colorScheme.onSurfaceVariant
    val groupColor = palette?.content?.copy(alpha = 0.07f) ?: ExpressiveGroupDefaults.containerColor()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (palette == null) Modifier.navigationBarsPadding() else Modifier)
            .padding(bottom = 14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 10.dp, top = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.add_to_playlist),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = content,
                )
                Text(
                    text = pluralStringResource(R.plurals.song_count_plural, selectedCount, selectedCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondary,
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close), tint = content)
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(horizontal = 14.dp),
        ) {
            item(key = "new") {
                PlaylistRow(
                    icon = Icons.Rounded.Add,
                    title = stringResource(R.string.new_playlist),
                    subtitle = null,
                    titleColor = content,
                    subtitleColor = secondary,
                    iconContainer = content.copy(alpha = 0.12f),
                    modifier = Modifier
                        .padding(bottom = 14.dp)
                        .expressiveItem(0, 1, groupColor),
                    onClick = onNewPlaylist,
                )
            }
            itemsIndexed(playlists, key = { _, it -> it.id }) { index, playlist ->
                PlaylistRow(
                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                    cover = playlist.customCoverUrl ?: playlist.coverUrl,
                    title = playlist.name,
                    subtitle = pluralStringResource(R.plurals.song_count_plural, playlist.songIds.size, playlist.songIds.size),
                    titleColor = content,
                    subtitleColor = secondary,
                    iconContainer = content.copy(alpha = 0.08f),
                    modifier = Modifier
                        .padding(bottom = if (index < playlists.lastIndex) ExpressiveGroupDefaults.Gap else 0.dp)
                        .expressiveItem(index, playlists.size, groupColor),
                    onClick = { onPick(playlist) },
                )
            }
        }
    }
}

@Composable
private fun PlaylistRow(
    icon: ImageVector,
    title: String,
    /** The playlist's cover (chosen, else from its tracks); the icon stands in without one. */
    cover: String? = null,
    subtitle: String?,
    titleColor: Color,
    subtitleColor: Color,
    iconContainer: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = titleColor, modifier = Modifier.size(22.dp))
            if (!cover.isNullOrBlank()) {
                AsyncImage(
                    model = cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = subtitleColor,
                    maxLines = 1,
                )
            }
        }
    }
}
