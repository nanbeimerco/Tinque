package com.music.bitchord.feature.home.data

import android.content.Context
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.stats.HeatInfo
import com.music.bitchord.data.stats.HeatStore
import com.music.bitchord.data.stats.ListeningStats
import com.music.bitchord.data.stats.PlaySource
import com.music.bitchord.data.stats.heatKey
import com.music.bitchord.feature.localmusic.data.resolvePlaylistSongs
import com.music.bitchord.feature.localmusic.domain.model.LocalPlaylist
import com.music.bitchord.feature.localsongactions.data.LocalPlayStatsStore
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import kotlin.random.Random

/** The quick mixes, in the order their pills are shown. */
enum class MixKind { HOT, REDISCOVER, NEW, DEEP_CUTS, TIME, UNPLAYED }

data class HomeMix(val kind: MixKind, val songs: List<Song>, val slot: Int = 0)

/** An album or artist on the Heat shelf. */
data class HomeGroup(
    val title: String,
    val subtitle: String,
    val artworkUrl: String?,
    val songs: List<Song>,
    val level: Float,
)

/** A playlist (or Favourites, [id] null) on the Heat shelf. */
data class HomePlaylist(
    val id: String?,
    val name: String,
    val artworkUrl: String?,
    val songs: List<Song>,
    val level: Float,
)

data class HomeAlbum(val title: String, val artist: String, val artworkUrl: String?, val songs: List<Song>)

data class DeepCut(val song: Song, val album: String)

/** "A year ago" or, with less history, [monthsBack] months ago. */
data class TimeCapsule(val monthsBack: Int, val month: YearMonth, val songs: List<Song>)

data class Coverage(val percent: Int, val stale: List<Song>)

data class HomeContent(
    val mixes: List<HomeMix> = emptyList(),
    val hero: Song? = null,
    val heatSongs: List<Song> = emptyList(),
    val heatAlbums: List<HomeGroup> = emptyList(),
    val heatArtists: List<HomeGroup> = emptyList(),
    val heatPlaylists: List<HomePlaylist> = emptyList(),
    val rising: List<Song> = emptyList(),
    val cooling: List<Song> = emptyList(),
    val recent: List<Song> = emptyList(),
    val unplayedAlbums: List<HomeAlbum> = emptyList(),
    val capsule: TimeCapsule? = null,
    val deepCuts: List<DeepCut> = emptyList(),
    val slot: Int = 0,
    val timeOfDay: List<Song> = emptyList(),
    val coverage: Coverage? = null,
    val streak: Int = 0,
    val week: List<Pair<LocalDate, Long>> = emptyList(),
)

/**
 * Works out everything Home shows, in one pass, off the main thread. Home's
 * screen only lays this out: no sorting or grouping happens while it draws
 * (2.5.1's lesson — Home sits under the glass and the player).
 */
object HomeModel {
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /**
     * Every song the Library holds, once: songs added (stars included — a
     * starred song is added for you), and the songs of added albums and artists.
     */
    private fun librarySongsOf(
        entries: List<com.music.bitchord.feature.library.data.LibraryEntry>,
        songs: List<Song>,
    ): List<Song> {
        if (entries.isEmpty()) return emptyList()
        val out = LinkedHashSet<Song>()
        com.music.bitchord.feature.library.data.LibraryEntryStore.resolveSongs(entries, songs).forEach { out += it.second }
        val albums = entries.filter { it.type == com.music.bitchord.feature.library.data.LibraryEntryType.ALBUM }
            .map { it.title.trim().lowercase(Locale.ROOT) }.toSet()
        val artists = entries.filter { it.type == com.music.bitchord.feature.library.data.LibraryEntryType.ARTIST }
            .map { it.title.trim().lowercase(Locale.ROOT) }.toSet()
        if (albums.isNotEmpty() || artists.isNotEmpty()) {
            songs.forEach { s ->
                if (s.albumName?.trim()?.lowercase(Locale.ROOT) in albums || s.artist.trim().lowercase(Locale.ROOT) in artists) out += s
            }
        }
        return out.toList()
    }

    fun compute(
        context: Context,
        songs: List<Song>,
        heat: Map<String, HeatInfo>,
        playlists: List<LocalPlaylist>,
        favoriteIds: Set<String>,
        favoritesName: String,
        libraryEntries: List<com.music.bitchord.feature.library.data.LibraryEntry> = emptyList(),
    ): HomeContent {
        if (songs.isEmpty()) return HomeContent(week = ListeningStats.recentDays(7))
        val now = System.currentTimeMillis()
        val today = LocalDate.now()
        val byKey = songs.associateBy { it.heatKey() }
        fun levelOf(song: Song): Float = heat[song.heatKey()]?.level ?: 0f
        val stats = songs.associateWith { LocalPlayStatsStore.getStats(context, it.localUri ?: it.videoId) }
        fun played(song: Song): Boolean = (stats[song]?.playedCount ?: 0) > 0 || (heat[song.heatKey()]?.plays ?: 0) > 0
        fun lastPlayed(song: Song): Long = maxOf(stats[song]?.lastPlayedTimestamp ?: 0L, heat[song.heatKey()]?.lastPlayed ?: 0L)

        // ── Heat ──
        val songHeat = heat.entries.mapNotNull { (k, info) -> byKey[k]?.let { it to info } }
        val heatSongs = songHeat.filter { it.second.level >= 2.5f }
            .sortedByDescending { it.second.level }.map { it.first }.take(20)
        val hero = (songHeat.filter { it.second.trend > 0 && it.second.level >= 1f }
            .ifEmpty { songHeat.filter { it.second.level >= 1f } })
            .maxByOrNull { it.second.level }?.first
        val rising = songHeat
            .filter { (s, i) -> i.momentum >= 1.3f && i.level >= 1.5f && s != hero }
            .sortedByDescending { it.second.momentum * it.second.level }
            .map { it.first }.take(20)
        val cooling = songHeat.filter { (_, info) ->
            (info.trend < 0 || now - info.lastPlayed > 14L * DAY_MS) && info.peak >= 2.5f && info.level < info.peak - 0.6f
        }.sortedByDescending { it.second.peak }.map { it.first }.take(20)

        val albums = songs.filter { !it.albumName.isNullOrBlank() }
            .groupBy { it.albumName!!.trim().lowercase(Locale.ROOT) }
        val albumLevels = albums.mapValues { (_, list) -> HeatStore.groupLevel(list.map(::levelOf)) }
        val heatAlbums = albums.entries
            .filter { (albumLevels[it.key] ?: 0f) >= 0.8f }
            .sortedByDescending { albumLevels[it.key] }
            .take(15)
            .map { (key, list) ->
                val first = list.first()
                HomeGroup(
                    title = first.albumName!!.trim(),
                    subtitle = first.albumArtist?.takeIf(String::isNotBlank) ?: first.artist,
                    artworkUrl = list.firstNotNullOfOrNull { it.thumbnailUrl?.takeIf(String::isNotBlank) },
                    songs = list,
                    level = albumLevels[key] ?: 0f,
                )
            }
        val artists = songs.filter { it.artist.isNotBlank() }.groupBy { it.artist.trim().lowercase(Locale.ROOT) }
        val heatArtists = artists.entries
            .map { (_, list) -> list to HeatStore.groupLevel(list.map(::levelOf)) }
            .filter { it.second >= 0.8f }
            .sortedByDescending { it.second }
            .take(15)
            .map { (list, level) ->
                HomeGroup(
                    title = list.first().artist.trim(),
                    subtitle = "",
                    artworkUrl = list.firstNotNullOfOrNull { it.thumbnailUrl?.takeIf(String::isNotBlank) },
                    songs = list,
                    level = level,
                )
            }
        val favoriteSongs = songs.filter { (it.localUri ?: it.videoId) in favoriteIds || it.videoId in favoriteIds }
        val heatPlaylists = buildList {
            if (favoriteSongs.isNotEmpty()) {
                add(HomePlaylist(null, favoritesName, null, favoriteSongs,
                    HeatStore.playlistLevel(PlaySource.FAVORITES_KEY, favoriteSongs.map(::levelOf))))
            }
            playlists.forEach { p ->
                val list = resolvePlaylistSongs(p, songs)
                if (list.isEmpty()) return@forEach
                add(HomePlaylist(p.id, p.name, p.customCoverUrl ?: p.coverUrl ?: list.firstNotNullOfOrNull { it.thumbnailUrl },
                    list, HeatStore.playlistLevel(PlaySource.playlistKey(p.id), list.map(::levelOf))))
            }
        }.filter { it.level >= 0.5f }.sortedByDescending { it.level }.take(15)

        // ── Played lately, and not at all ──
        val recent = songs.filter { lastPlayed(it) > 0 }.sortedByDescending(::lastPlayed).take(20)
        val unplayedAlbums = albums.values
            .filter { list -> list.none(::played) }
            .sortedByDescending { list -> list.maxOf { it.localDateAddedSeconds ?: 0L } }
            .take(12)
            .map { list ->
                val first = list.first()
                HomeAlbum(
                    title = first.albumName!!.trim(),
                    artist = first.albumArtist?.takeIf(String::isNotBlank) ?: first.artist,
                    artworkUrl = list.firstNotNullOfOrNull { it.thumbnailUrl?.takeIf(String::isNotBlank) },
                    songs = list,
                )
            }

        // ── A year ago (or as far back as there is) ──
        val months = ListeningStats.months().toSet()
        val thisMonth = YearMonth.from(today)
        val capsule = listOf(12, 6, 3).firstNotNullOfOrNull { back ->
            val month = thisMonth.minusMonths(back.toLong())
            if (month !in months) return@firstNotNullOfOrNull null
            val byVideoId = songs.associateBy { it.videoId }
            val list = ListeningStats.tracksOf(month)
                .filter { it.ms >= 60_000L }
                .mapNotNull { byVideoId[it.id] }
                .filter { levelOf(it) < 3.5f }
                .take(20)
            if (list.size >= 4) TimeCapsule(back, month, list) else null
        }

        // ── Deep cuts: the less-played tracks of albums that are warm ──
        val deepCuts = albums.entries
            .filter { it.value.size >= 3 && (albumLevels[it.key] ?: 0f) >= 2.5f }
            .sortedByDescending { albumLevels[it.key] }
            .flatMap { (_, list) ->
                val plays = list.associateWith { heat[it.heatKey()]?.plays ?: stats[it]?.playedCount ?: 0 }
                val average = plays.values.average()
                list.filter { (plays[it] ?: 0) == 0 || (plays[it] ?: 0) <= average / 3 }
                    .sortedBy { plays[it] ?: 0 }
                    .take(2)
                    .map { DeepCut(it, it.albumName!!.trim()) }
            }
            .take(20)

        // ── This time of day ──
        val slot = HeatStore.slotOf(now)
        val timeOfDay = songHeat
            .filter { (_, i) ->
                val total = i.slots.sum()
                total >= 3 && i.slots.size == 4 && i.slots[slot] * 10 >= total * 4
            }
            .sortedByDescending { it.second.slots[slot] }
            .map { it.first }
            .take(30)

        // ── Coverage: how much of the *Library* (what was added to it, not every
        // file on the device) the last three months reached ──
        val since = now - 90L * DAY_MS
        val librarySongs = librarySongsOf(libraryEntries, songs)
        val coverage = if (librarySongs.isEmpty()) null else {
            val stale = librarySongs.filter { lastPlayed(it) < since }
            Coverage(((librarySongs.size - stale.size) * 100 / librarySongs.size), stale)
        }

        // ── Mixes ──
        val seed = today.toEpochDay()
        fun pick(kind: MixKind, list: List<Song>, min: Int): HomeMix? {
            if (list.size < min) return null
            val rnd = Random(seed * 31 + kind.ordinal)
            return HomeMix(kind, if (list.size > 50) list.shuffled(rnd).take(50) else list, slot)
        }
        val newSongs = songs.filter { ((it.localDateAddedSeconds ?: 0L) * 1000L) >= now - 30L * DAY_MS }
        val rediscover = songHeat.filter { (_, i) -> i.plays >= 3 && now - i.lastPlayed > 30L * DAY_MS }.map { it.first }
        val mixes = listOfNotNull(
            pick(MixKind.HOT, songHeat.filter { it.second.level >= 3.5f }.sortedByDescending { it.second.level }.map { it.first }, 8),
            pick(MixKind.REDISCOVER, rediscover, 10),
            pick(MixKind.NEW, newSongs, 5),
            pick(MixKind.DEEP_CUTS, deepCuts.map { it.song }, 8),
            pick(MixKind.TIME, timeOfDay, 10),
            pick(MixKind.UNPLAYED, songs.filterNot(::played), 10),
        )

        return HomeContent(
            mixes = mixes,
            hero = hero,
            heatSongs = heatSongs,
            heatAlbums = heatAlbums,
            heatArtists = heatArtists,
            heatPlaylists = heatPlaylists,
            rising = rising,
            cooling = cooling,
            recent = recent,
            unplayedAlbums = unplayedAlbums,
            capsule = capsule,
            deepCuts = deepCuts,
            slot = slot,
            timeOfDay = timeOfDay,
            coverage = coverage,
            streak = ListeningStats.streak(),
            week = ListeningStats.recentDays(7),
        )
    }
}
