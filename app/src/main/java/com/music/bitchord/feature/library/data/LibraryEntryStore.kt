package com.music.bitchord.feature.library.data

import android.content.Context
import android.util.AtomicFile
import com.music.bitchord.data.model.Song
import com.music.bitchord.feature.localmusic.data.LocalFavoritesStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale

/** What a Library entry points at. */
@Serializable
enum class LibraryEntryType { SONG, ALBUM, ARTIST }

/**
 * Something the listener put in their Library by hand.
 *
 * The Library is the listener's own collection — what they chose — as against
 * Home, which shows what the app worked out. [title] and friends are a
 * snapshot so the entry can still be shown if the files behind it move.
 */
@Serializable
data class LibraryEntry(
    val type: LibraryEntryType,
    /** SONG: the track id playlists use (content URI or video id). ALBUM/ARTIST: see [albumKey]/[artistKey]. */
    val key: String,
    val addedAt: Long,
    val title: String = "",
    val subtitle: String = "",
    val artworkUrl: String? = null,
    /** SONG only: the file path, to find the track again after a media rescan. */
    val localPath: String? = null,
)

@Serializable
private data class LibraryEntryFile(
    val version: Int = 1,
    val favoritesImported: Boolean = false,
    val entries: List<LibraryEntry> = emptyList(),
)

fun Song.libraryKey(): String = localUri ?: videoId

fun albumKey(album: String, artist: String?): String =
    album.trim().lowercase(Locale.ROOT) + "\u0001" + artist.orEmpty().trim().lowercase(Locale.ROOT)

fun artistKey(artist: String): String = artist.trim().lowercase(Locale.ROOT)

object LibraryEntryStore {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var file: AtomicFile
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var favoritesImported = false

    private val _entries = MutableStateFlow<List<LibraryEntry>>(emptyList())
    /** Newest first. */
    val entries: StateFlow<List<LibraryEntry>> = _entries.asStateFlow()

    fun init(context: Context) {
        file = AtomicFile(File(context.filesDir, "library_entries.json"))
        val loaded = runCatching {
            file.openRead().bufferedReader().use { json.decodeFromString(LibraryEntryFile.serializer(), it.readText()) }
        }.getOrNull()
        favoritesImported = loaded?.favoritesImported == true
        _entries.value = loaded?.entries.orEmpty().sortedByDescending { it.addedAt }
        // A song starred is also a song in the Library (and stays there if the
        // star comes off later). Watched here so every place that can star a
        // song — the player, the notification, a menu — gets this for free.
        scope.launch {
            var previous: Set<String>? = null
            LocalFavoritesStore.favoriteIds.collect { ids ->
                val before = previous
                previous = ids
                val added = when {
                    before != null -> ids - before
                    !favoritesImported -> ids
                    else -> emptySet()
                }
                if (added.isNotEmpty()) addSongKeys(added)
                if (!favoritesImported && ids.isNotEmpty()) {
                    favoritesImported = true
                    save()
                }
            }
        }
    }

    private val ready get() = this::file.isInitialized

    fun contains(type: LibraryEntryType, key: String): Boolean =
        _entries.value.any { it.type == type && it.key == key }

    fun containsSong(song: Song): Boolean = contains(LibraryEntryType.SONG, song.libraryKey())

    fun addSongs(songs: List<Song>) {
        if (!ready || songs.isEmpty()) return
        val now = System.currentTimeMillis()
        // Consecutive adds share a time stamp order: the first of a batch is newest.
        add(
            songs.mapIndexed { i, s ->
                LibraryEntry(
                    type = LibraryEntryType.SONG,
                    key = s.libraryKey(),
                    addedAt = now - i,
                    title = s.title,
                    subtitle = s.artist,
                    artworkUrl = s.thumbnailUrl,
                    localPath = s.localPath,
                )
            },
        )
    }

    private fun addSongKeys(keys: Set<String>) {
        val now = System.currentTimeMillis()
        add(keys.mapIndexed { i, k -> LibraryEntry(type = LibraryEntryType.SONG, key = k, addedAt = now - i) })
    }

    fun addAlbum(album: String, artist: String?, artworkUrl: String?) {
        if (album.isBlank()) return
        add(
            listOf(
                LibraryEntry(
                    type = LibraryEntryType.ALBUM,
                    key = albumKey(album, artist),
                    addedAt = System.currentTimeMillis(),
                    title = album,
                    subtitle = artist.orEmpty(),
                    artworkUrl = artworkUrl,
                ),
            ),
        )
    }

    fun addArtist(artist: String, artworkUrl: String?) {
        if (artist.isBlank()) return
        add(
            listOf(
                LibraryEntry(
                    type = LibraryEntryType.ARTIST,
                    key = artistKey(artist),
                    addedAt = System.currentTimeMillis(),
                    title = artist,
                    artworkUrl = artworkUrl,
                ),
            ),
        )
    }

    private fun add(new: List<LibraryEntry>) {
        if (!ready || new.isEmpty()) return
        synchronized(lock) {
            val existing = _entries.value
            val fresh = new.filter { n -> existing.none { it.type == n.type && it.key == n.key } }
            if (fresh.isEmpty()) return
            _entries.value = (fresh + existing).sortedByDescending { it.addedAt }
        }
        save()
    }

    fun remove(type: LibraryEntryType, key: String) {
        if (!ready) return
        synchronized(lock) {
            _entries.value = _entries.value.filterNot { it.type == type && it.key == key }
        }
        save()
    }

    fun removeSongs(songs: List<Song>) {
        val keys = songs.map { it.libraryKey() }.toSet()
        synchronized(lock) {
            _entries.value = _entries.value.filterNot { it.type == LibraryEntryType.SONG && it.key in keys }
        }
        save()
    }

    private fun save() {
        if (!ready) return
        val snapshot = LibraryEntryFile(favoritesImported = favoritesImported, entries = _entries.value)
        scope.launch {
            synchronized(lock) {
                runCatching {
                    val out = file.startWrite()
                    try {
                        out.write(json.encodeToString(LibraryEntryFile.serializer(), snapshot).toByteArray())
                        file.finishWrite(out)
                    } catch (t: Throwable) {
                        file.failWrite(out)
                        throw t
                    }
                }
            }
        }
    }

    fun exportAll(): List<LibraryEntry> = _entries.value

    fun importAll(incoming: List<LibraryEntry>) {
        if (incoming.isEmpty()) return
        add(incoming)
    }

    /** The library songs a SONG entry stands for, matched by id and then by file path. */
    fun resolveSongs(entries: List<LibraryEntry>, library: List<Song>): List<Pair<LibraryEntry, Song>> {
        val byKey = HashMap<String, Song>(library.size * 2)
        val byPath = HashMap<String, Song>(library.size)
        library.forEach { s ->
            byKey[s.libraryKey()] = s
            byKey[s.videoId] = s
            s.localPath?.let { byPath[it] = s }
        }
        return entries.filter { it.type == LibraryEntryType.SONG }.mapNotNull { e ->
            (byKey[e.key] ?: e.localPath?.let(byPath::get))?.let { e to it }
        }
    }
}
