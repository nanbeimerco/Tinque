package com.music.bitchord.feature.library.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.bitchord.ui.components.heatRing
import androidx.compose.foundation.layout.fillMaxSize
import com.music.bitchord.R
import com.music.bitchord.data.model.Song
import com.music.bitchord.feature.library.data.LibraryEntry
import com.music.bitchord.feature.library.data.LibraryEntryType
import com.music.bitchord.ui.components.ExpressiveGroupDefaults
import com.music.bitchord.ui.components.SongRow
import com.music.bitchord.ui.components.expressiveItem

/** One card on the "Recently added" shelf. */
sealed interface RecentLibraryItem {
    val addedAt: Long
    data class OneSong(val song: Song, override val addedAt: Long) : RecentLibraryItem
    /** Several songs of one album added together: shown as the album, so a whole album added does not flood the shelf. */
    data class AlbumBatch(val title: String, val artist: String, val artworkUrl: String?, val songs: List<Song>, override val addedAt: Long) : RecentLibraryItem
    data class Collection(val entry: LibraryEntry, val artworkUrl: String?) : RecentLibraryItem {
        override val addedAt: Long get() = entry.addedAt
    }
}

private const val BATCH_WINDOW_MS = 10L * 60 * 1000
private const val BATCH_MIN = 3

/**
 * The "Recently added" shelf's cards, newest first: songs added together from
 * one album (three or more within ten minutes) fold into that album's card.
 */
fun recentLibraryItems(
    entries: List<LibraryEntry>,
    songsByEntry: Map<LibraryEntry, Song>,
    artFor: (LibraryEntry) -> String?,
): List<RecentLibraryItem> {
    val out = mutableListOf<RecentLibraryItem>()
    val sorted = entries.sortedByDescending { it.addedAt }
    var i = 0
    while (i < sorted.size) {
        val e = sorted[i]
        if (e.type != LibraryEntryType.SONG) {
            out += RecentLibraryItem.Collection(e, e.artworkUrl ?: artFor(e))
            i++
            continue
        }
        val song = songsByEntry[e]
        if (song == null) { i++; continue }
        val album = song.albumName?.trim().orEmpty()
        val batch = mutableListOf(song)
        var j = i + 1
        while (album.isNotEmpty() && j < sorted.size) {
            val next = sorted[j]
            val nextSong = songsByEntry[next] ?: break
            if (next.type != LibraryEntryType.SONG) break
            if (!nextSong.albumName?.trim().equals(album, ignoreCase = true)) break
            if (e.addedAt - next.addedAt > BATCH_WINDOW_MS) break
            batch += nextSong
            j++
        }
        if (batch.size >= BATCH_MIN) {
            out += RecentLibraryItem.AlbumBatch(
                title = album,
                artist = song.albumArtist?.takeIf { it.isNotBlank() } ?: song.artist,
                artworkUrl = batch.firstNotNullOfOrNull { it.thumbnailUrl },
                songs = batch,
                addedAt = e.addedAt,
            )
            i = j
        } else {
            out += RecentLibraryItem.OneSong(song, e.addedAt)
            i++
        }
    }
    return out
}

private val GUTTER = 16.dp

@Composable
fun LibrarySectionTitle(title: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = GUTTER + 8.dp, end = GUTTER, top = 20.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null) {
            TextButton(onClick = onAction) { Text(actionLabel, style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@Composable
private fun CardArt(url: String?, size: Dp, round: Boolean, fallback: androidx.compose.ui.graphics.vector.ImageVector, heat: Float?) {
    val shape = if (round) CircleShape else RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .size(size)
            .heatRing(heat, shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(fallback, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!url.isNullOrBlank()) {
            AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxSize().clip(shape),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        }
    }
}

@Composable
fun LibraryCard(
    title: String,
    subtitle: String,
    artworkUrl: String?,
    round: Boolean = false,
    size: Dp = 132.dp,
    heat: Float? = null,
    onClick: () -> Unit,
) {
    Column(
        // Only the artwork is rounded (CardArt): clipping the card shaved the
        // start of the second line off at the bottom corner.
        modifier = Modifier
            .width(size)
            .clickable(onClick = onClick),
        horizontalAlignment = if (round) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        CardArt(artworkUrl, size, round, if (round) Icons.Rounded.Person else Icons.Rounded.Album, heat)
        Spacer(Modifier.height(6.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle.isNotBlank()) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** An album or artist entry's ring level (null with heat off). */
@Composable
fun rememberEntryHeat(entry: LibraryEntry, songs: List<Song>): Float? {
    val members = androidx.compose.runtime.remember(entry.key, entry.type, songs) {
        val title = entry.title.trim()
        when (entry.type) {
            LibraryEntryType.ALBUM -> songs.filter { it.albumName?.trim().equals(title, ignoreCase = true) }
            LibraryEntryType.ARTIST -> songs.filter { it.artist.trim().equals(title, ignoreCase = true) }
            else -> emptyList()
        }
    }
    return com.music.bitchord.ui.components.rememberTileHeat(members)
}

/** A horizontal shelf of cards. */
@Composable
fun <T> LibraryShelf(items: List<T>, key: (T) -> Any, card: @Composable (T) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = GUTTER),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = key) { card(it) }
    }
}

fun LazyListScope.libraryEmptyNote(key: String) {
    item(key = key) {
        Text(
            text = stringResource(R.string.library_empty_section),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = GUTTER + 8.dp, vertical = 8.dp),
        )
    }
}

/** Songs as the Library's grouped rows (the same look as Favorites and Playlists). */
fun LazyListScope.librarySongRows(
    key: String,
    songs: List<Song>,
    allSongs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    selectedIds: Set<String>,
    onSongClick: (List<Song>, Int) -> Unit,
    onSongLongPress: (Song) -> Unit,
    onSongSwipe: (Song) -> Unit,
    onSongMore: ((Song, String?) -> Unit)?,
    songMenu: (@Composable (Song, String?) -> Unit)?,
) {
    itemsIndexed(songs, key = { _, s -> "${key}_${s.localUri ?: s.videoId}" }) { index, song ->
        val groupColor = ExpressiveGroupDefaults.containerColor()
        SongRow(
            song = song,
            modifier = Modifier
                .padding(horizontal = GUTTER)
                .padding(bottom = if (index < songs.lastIndex) ExpressiveGroupDefaults.Gap else 0.dp)
                .expressiveItem(index, songs.size, groupColor),
            rowBackground = groupColor,
            isCurrent = currentSong?.videoId == song.videoId,
            isPlaying = isPlaying,
            selected = song.videoId in selectedIds,
            onClick = { onSongClick(allSongs, allSongs.indexOf(song).coerceAtLeast(0)) },
            onLongPress = { onSongLongPress(song) },
            onMore = onSongMore?.let { more -> { more(song, key) } },
            onSwipeToQueue = { onSongSwipe(song) },
            dropdownMenu = songMenu?.let { menu -> { menu(song, key) } },
        )
    }
}
