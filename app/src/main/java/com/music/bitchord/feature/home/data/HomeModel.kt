package com.music.bitchord.feature.home.data

import android.content.Context
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.stats.HeatInfo
import com.music.bitchord.data.stats.HeatScorer
import com.music.bitchord.data.stats.HeatStore
import com.music.bitchord.data.stats.ListeningStats
import com.music.bitchord.data.stats.PlaySource
import com.music.bitchord.data.stats.heatKey
import com.music.bitchord.feature.homeai.LocalHomeAi
import com.music.bitchord.feature.localmusic.data.resolvePlaylistSongs
import com.music.bitchord.feature.localmusic.domain.model.LocalPlaylist
import com.music.bitchord.feature.localsongactions.data.LocalPlayStatsStore
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/** The quick mixes, in the order their pills are shown. */
enum class MixKind { HOT, REDISCOVER, NEW, DEEP_CUTS, TIME, FOR_YOU, UPLIFTING, CALM, UNPLAYED }

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
    val forYouSongs: List<Song> = emptyList(),
    val upliftingSongs: List<Song> = emptyList(),
    val calmSongs: List<Song> = emptyList(),
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

    internal fun dailyJitter(epochDay: Long, key: String, magnitude: Float): Float {
        if (magnitude <= 0f) return 0f
        val rnd = Random(epochDay * 1_000_003L + key.hashCode().toLong() * 37L)
        return (rnd.nextFloat() * 2f - 1f) * magnitude
    }

    private fun <T> weightedShuffle(
        items: List<T>,
        rnd: Random,
        weightOf: (T) -> Double,
    ): List<T> {
        if (items.size <= 1) return items
        return items
            .map { item ->
                val w = weightOf(item).coerceAtLeast(1e-4)
                val u = rnd.nextDouble().coerceIn(1e-12, 1.0 - 1e-12)
                item to (-ln(u) / w)
            }
            .sortedBy { it.second }
            .map { it.first }
    }

    fun compute(
        context: Context,
        songs: List<Song>,
        heat: Map<String, HeatInfo>,
        playlists: List<LocalPlaylist>,
        favoriteIds: Set<String>,
        favoritesName: String,
        libraryEntries: List<com.music.bitchord.feature.library.data.LibraryEntry> = emptyList(),
        dailyMixBoost: Boolean = AppSettings.homeHeatDailyMixBoost.value,
        today: LocalDate = LocalDate.now(),
        now: Long = System.currentTimeMillis(),
    ): HomeContent {
        val aiReady = LocalHomeAi.isModelInstalled(context)
        val moodFeatures = if (aiReady) LocalHomeAi.cachedFeaturesByVideoId(context, songs) else emptyMap()
        val embeddingsByKey = if (aiReady && moodFeatures.size >= 4) {
            songs.asSequence()
                .filter { moodFeatures.containsKey(it.videoId) }
                .sortedByDescending { s ->
                    val h = heat[s.heatKey()]
                    val isFav = (s.localUri ?: s.videoId) in favoriteIds || s.videoId in favoriteIds
                    (if (isFav) 100f else 0f) + (h?.level ?: 0f) * 10f + if ((h?.plays ?: 0) == 0) 5f else 0f
                }
                .take(150)
                .mapNotNull { song -> LocalHomeAi.loadEmbedding(context, song)?.let { song.videoId to it } }
                .toMap()
        } else {
            emptyMap()
        }
        return computeWithStats(
            songs = songs,
            heat = heat,
            playlists = playlists,
            favoriteIds = favoriteIds,
            favoritesName = favoritesName,
            libraryEntries = libraryEntries,
            dailyMixBoost = dailyMixBoost,
            today = today,
            now = now,
            moodFeatures = moodFeatures,
            embeddingsByKey = embeddingsByKey,
            statsProvider = { song -> LocalPlayStatsStore.getStats(context, song.localUri ?: song.videoId) },
        )
    }

    internal fun computeWithStats(
        songs: List<Song>,
        heat: Map<String, HeatInfo>,
        playlists: List<LocalPlaylist>,
        favoriteIds: Set<String>,
        favoritesName: String,
        libraryEntries: List<com.music.bitchord.feature.library.data.LibraryEntry> = emptyList(),
        dailyMixBoost: Boolean = true,
        today: LocalDate = LocalDate.now(),
        now: Long = System.currentTimeMillis(),
        moodFeatures: Map<String, LocalHomeAi.TrackMoodFeatures> = emptyMap(),
        embeddingsByKey: Map<String, FloatArray> = emptyMap(),
        statsProvider: (Song) -> com.music.bitchord.feature.localsongactions.domain.model.LocalPlayStats = {
            com.music.bitchord.feature.localsongactions.domain.model.LocalPlayStats.Empty
        },
    ): HomeContent {
        if (songs.isEmpty()) return HomeContent(week = ListeningStats.recentDays(7))
        val seed = today.toEpochDay()
        val byKey = songs.associateBy { it.heatKey() }
        fun levelOf(song: Song): Float = heat[song.heatKey()]?.level ?: 0f
        val stats = songs.associateWith(statsProvider)
        fun played(song: Song): Boolean = (stats[song]?.playedCount ?: 0) > 0 || (heat[song.heatKey()]?.plays ?: 0) > 0
        fun lastPlayed(song: Song): Long = maxOf(stats[song]?.lastPlayedTimestamp ?: 0L, heat[song.heatKey()]?.lastPlayed ?: 0L)

        // ── Heat ──
        val songHeat = heat.entries.mapNotNull { (k, info) -> byKey[k]?.let { it to info } }
        val heatSongs = songHeat.filter { it.second.level >= 2.5f }
            .sortedByDescending { (song, info) ->
                if (dailyMixBoost) {
                    val unbounded = if (info.longScore > 0f) {
                        HeatScorer.unboundedHeat(info.longScore.toDouble()).toFloat()
                    } else {
                        info.level
                    }
                    unbounded + 0.35f * info.shortScore + dailyJitter(seed, song.heatKey(), 0.30f)
                } else {
                    info.level
                }
            }
            .map { it.first }
            .take(20)
        val hero = (songHeat.filter { it.second.shortScore >= 1.8f && it.second.level >= 1f }
            .ifEmpty { songHeat.filter { it.second.level >= 1f } })
            .maxByOrNull { (_, i) -> i.level * (1f + i.momentum.coerceIn(0f, 3f) * 0.4f) }
            ?.first
        val rising = songHeat
            .filter { (s, i) -> i.shortScore >= 1.8f && i.momentum >= 0.7f && s != hero }
            .sortedByDescending { it.second.momentum }
            .map { it.first }.take(20)
        val cooling = songHeat.filter { (_, info) ->
            (info.trend < 0 || now - info.lastPlayed > 14L * DAY_MS) && info.peak >= 2.5f && info.level < info.peak - 0.6f
        }.sortedByDescending { it.second.peak }.map { it.first }.take(20)

        val albums = songs.filter { !it.albumName.isNullOrBlank() }
            .groupBy { it.albumName!!.trim().lowercase(Locale.ROOT) }
        val albumLevels = albums.mapValues { (_, list) -> HeatStore.groupLevel(list.map(::levelOf)) }
        val heatAlbums = albums.entries
            .filter { (albumLevels[it.key] ?: 0f) >= 0.8f }
            .sortedByDescending { (key, _) ->
                val baseLevel = albumLevels[key] ?: 0f
                if (dailyMixBoost) baseLevel + dailyJitter(seed, "album:$key", 0.25f) else baseLevel
            }
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
            .map { (key, list) -> Triple(key, list, HeatStore.groupLevel(list.map(::levelOf))) }
            .filter { it.third >= 0.8f }
            .sortedByDescending { (key, _, level) ->
                if (dailyMixBoost) level + dailyJitter(seed, "artist:$key", 0.25f) else level
            }
            .take(15)
            .map { (_, list, level) ->
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
        }.filter { it.level >= 0.5f }
            .sortedByDescending { pl ->
                if (dailyMixBoost) {
                    pl.level + dailyJitter(seed, "pl:${pl.id ?: PlaySource.FAVORITES_KEY}", 0.25f)
                } else {
                    pl.level
                }
            }
            .take(15)

        // ── Played lately, and not at all ──
        val recent = songs.filter { lastPlayed(it) > 0 }.sortedByDescending(::lastPlayed).take(20)
        val unplayedCandidates = albums.values
            .filter { list -> list.none(::played) }
            .sortedByDescending { list -> list.maxOf { it.localDateAddedSeconds ?: 0L } }
        val freshCount = when {
            unplayedCandidates.size <= 4 -> 0
            else -> 3
        }
        val selectedUnplayed = if (unplayedCandidates.size <= 12) {
            unplayedCandidates.take(freshCount) +
                unplayedCandidates.drop(freshCount).shuffled(Random(seed * 37 + 1))
        } else {
            val fresh = unplayedCandidates.take(freshCount)
            val rotated = unplayedCandidates.drop(freshCount).shuffled(Random(seed * 37 + 1))
                .take(12 - freshCount)
                .shuffled(Random(seed * 37 + 2))
            fresh + rotated
        }
        val unplayedAlbums = selectedUnplayed
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
        val warmAlbums = weightedShuffle(
            albums.entries.filter { it.value.size >= 3 && (albumLevels[it.key] ?: 0f) >= 2.5f },
            Random(seed * 41 + 3),
        ) { (key, _) ->
            ((albumLevels[key] ?: 2.5f) - 2.0f).coerceAtLeast(0.3f).toDouble().pow(1.4)
        }
        val deepCuts = warmAlbums
            .flatMap { (key, list) ->
                val plays = list.associateWith { heat[it.heatKey()]?.plays ?: stats[it]?.playedCount ?: 0 }
                val average = plays.values.average()
                val low = list.filter { (plays[it] ?: 0) == 0 || (plays[it] ?: 0) <= average / 3 }
                val (unplayedTracks, playedLowTracks) = low.partition { (plays[it] ?: 0) == 0 }
                val trackRnd = Random(seed * 43 + key.hashCode().toLong())
                val picked = unplayedTracks.shuffled(trackRnd) +
                    playedLowTracks.shuffled(trackRnd).sortedBy { plays[it] ?: 0 }
                picked.take(2).map { DeepCut(it, it.albumName!!.trim()) }
            }
            .take(20)

        // ── This time of day ──
        val slot = HeatStore.slotOf(now)
        val slotHabit = songHeat
            .filter { (_, i) ->
                val total = i.slots.sum()
                total >= 3 && i.slots.size == 4 && i.slots[slot] * 10 >= total * 4
            }
            .sortedByDescending { it.second.slots[slot] }
            .map { it.first }
        val slotMoodCentroid = computeSlotMoodCentroid(songs, heat, moodFeatures, slot)
        val timeOfDay = if (slotMoodCentroid != null && moodFeatures.size >= 4) {
            val habitIds = slotHabit.mapTo(HashSet()) { it.videoId }
            val aiTimePicks = songs.asSequence()
                .filter { it.videoId !in habitIds }
                .mapNotNull { song ->
                    val feat = moodFeatures[song.videoId] ?: return@mapNotNull null
                    val info = heat[song.heatKey()]
                    if ((info?.skips ?: 0) >= 3 && (info?.completionRate ?: 1f) < 0.35f) return@mapNotNull null
                    val dv = (feat.valence - slotMoodCentroid.first).toDouble()
                    val da = (feat.arousal - slotMoodCentroid.second).toDouble()
                    val vaFit = exp(-(dv * dv + da * da) / (2.0 * 0.28 * 0.28))
                    if (vaFit < 0.45) return@mapNotNull null
                    val slotPlays = info?.slots?.getOrNull(slot) ?: 0
                    val totalPlays = info?.slots?.sum() ?: 0
                    val share = if (totalPlays > 0) slotPlays.toDouble() / totalPlays else 0.0
                    val crBonus = ((info?.completionRate ?: 0.55f) - 0.5f) * 0.20
                    val score = share * 0.45 + vaFit * 0.45 + crBonus +
                        if (dailyMixBoost) dailyJitter(seed, "slot:${song.videoId}", 0.08f) else 0f
                    song to score
                }
                .sortedByDescending { it.second }
                .map { it.first }
                .toList()
            (slotHabit + aiTimePicks).take(30)
        } else {
            slotHabit.take(30)
        }

        // ── Music2EMO Mood Shelves & Mixes (hidden until >= 4 analyzed tracks) ──
        val (forYouSongs, upliftingSongs, calmSongs) = computeAiRecommendations(
            songs = songs,
            heat = heat,
            stats = stats,
            favoriteIds = favoriteIds,
            moodFeatures = moodFeatures,
            embeddingsByKey = embeddingsByKey,
            seed = seed,
            dailyMixBoost = dailyMixBoost,
            warmIds = heatSongs.take(8).mapTo(HashSet()) { it.videoId },
        )

        // ── Coverage: how much of the *Library* (what was added to it, not every
        // file on the device) the last three months reached ──
        val since = now - 90L * DAY_MS
        val librarySongs = librarySongsOf(libraryEntries, songs)
        val coverage = if (librarySongs.isEmpty()) null else {
            val stale = librarySongs.filter { lastPlayed(it) < since }
            Coverage(((librarySongs.size - stale.size) * 100 / librarySongs.size), stale)
        }

        // ── Mixes ──
        fun pick(kind: MixKind, list: List<Song>, min: Int): HomeMix? {
            if (list.size < min) return null
            val rnd = Random(seed * 31 + kind.ordinal)
            val ordered = if (kind == MixKind.HOT) {
                weightedShuffle(list, rnd) { song ->
                    val info = heat[song.heatKey()]
                    val scoreWeight = if (info != null && info.longScore > 0f) {
                        HeatScorer.unboundedHeat(info.longScore.toDouble())
                    } else {
                        (info?.level ?: 3.5f).toDouble()
                    }
                    scoreWeight.coerceAtLeast(1.0).pow(1.4)
                }
            } else {
                list.shuffled(rnd)
            }
            return HomeMix(kind, ordered.take(50), slot)
        }
        val newSongs = songs.filter { ((it.localDateAddedSeconds ?: 0L) * 1000L) >= now - 30L * DAY_MS }
        val rediscover = songHeat.filter { (_, i) -> i.plays >= 3 && now - i.lastPlayed > 30L * DAY_MS }.map { it.first }
        val mixes = listOfNotNull(
            pick(MixKind.HOT, songHeat.filter { it.second.level >= 3.5f }.sortedByDescending { it.second.level }.map { it.first }, 8),
            pick(MixKind.REDISCOVER, rediscover, 10),
            pick(MixKind.NEW, newSongs, 5),
            pick(MixKind.DEEP_CUTS, deepCuts.map { it.song }, 8),
            pick(MixKind.TIME, timeOfDay, 10),
            pick(MixKind.FOR_YOU, forYouSongs, 10),
            pick(MixKind.UPLIFTING, upliftingSongs, 10),
            pick(MixKind.CALM, calmSongs, 10),
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
            forYouSongs = forYouSongs,
            upliftingSongs = upliftingSongs,
            calmSongs = calmSongs,
            coverage = coverage,
            streak = ListeningStats.streak(),
            week = ListeningStats.recentDays(7),
        )
    }

    private fun computeSlotMoodCentroid(
        songs: List<Song>,
        heat: Map<String, HeatInfo>,
        moodFeatures: Map<String, LocalHomeAi.TrackMoodFeatures>,
        slot: Int,
    ): Pair<Float, Float>? {
        if (moodFeatures.isEmpty()) return null
        var vSum = 0.0
        var aSum = 0.0
        var wSum = 0.0
        var contributors = 0
        for (song in songs) {
            val feat = moodFeatures[song.videoId] ?: continue
            val info = heat[song.heatKey()] ?: continue
            if (info.slots.size != 4) continue
            val count = info.slots[slot]
            if (count <= 0) continue
            val cr = (info.completionRate ?: 0.6f).coerceIn(0.25f, 1f).toDouble()
            val w = count * cr * (1.0 + info.level * 0.25)
            vSum += feat.valence * w
            aSum += feat.arousal * w
            wSum += w
            contributors++
        }
        if (contributors < 2 || wSum <= 0.0) return null
        return (vSum / wSum).toFloat() to (aSum / wSum).toFloat()
    }

    private data class AiShelves(
        val forYou: List<Song>,
        val uplifting: List<Song>,
        val calm: List<Song>,
    )

    private fun computeAiRecommendations(
        songs: List<Song>,
        heat: Map<String, HeatInfo>,
        stats: Map<Song, com.music.bitchord.feature.localsongactions.domain.model.LocalPlayStats>,
        favoriteIds: Set<String>,
        moodFeatures: Map<String, LocalHomeAi.TrackMoodFeatures>,
        embeddingsByKey: Map<String, FloatArray>,
        seed: Long,
        dailyMixBoost: Boolean,
        warmIds: Set<String>,
    ): AiShelves {
        if (moodFeatures.size < 4) return AiShelves(emptyList(), emptyList(), emptyList())

        var prefVSum = 0.0
        var prefASum = 0.0
        var prefWSum = 0.0
        var embSum: DoubleArray? = null
        var embWeight = 0.0

        for (song in songs) {
            val feat = moodFeatures[song.videoId] ?: continue
            val info = heat[song.heatKey()]
            val st = stats[song]
            val isFav = (song.localUri ?: song.videoId) in favoriteIds || song.videoId in favoriteIds
            val plays = info?.plays ?: st?.playedCount ?: 0
            val level = info?.level ?: 0f
            val cr = info?.completionRate ?: 0.65f
            if (!(isFav || level >= 1.2f || plays >= 2)) continue
            if (!isFav && cr < 0.40f) continue
            val w = (level.toDouble() + (if (isFav) 2.5 else 0.0) + minOf(plays, 10) * 0.20) *
                cr.toDouble().coerceIn(0.30, 1.0)
            if (w <= 0.0) continue
            prefVSum += feat.valence * w
            prefASum += feat.arousal * w
            prefWSum += w
            embeddingsByKey[song.videoId]?.let { vec ->
                val acc = embSum ?: DoubleArray(vec.size).also { embSum = it }
                if (acc.size == vec.size) {
                    for (i in vec.indices) acc[i] += vec[i] * w
                    embWeight += w
                }
            }
        }

        val listenerCentroid = if (prefWSum > 0.0) {
            (prefVSum / prefWSum).toFloat() to (prefASum / prefWSum).toFloat()
        } else null
        val listenerMert = embSum?.takeIf { embWeight > 0.0 }?.let { acc ->
            FloatArray(acc.size) { (acc[it] / embWeight).toFloat() }
        }

        fun isDiscoveryCandidate(song: Song, maxPlays: Int, maxLevel: Float): Boolean {
            val info = heat[song.heatKey()]
            val st = stats[song]
            val isFav = (song.localUri ?: song.videoId) in favoriteIds || song.videoId in favoriteIds
            val plays = info?.plays ?: st?.playedCount ?: 0
            val level = info?.level ?: 0f
            return !isFav && plays <= maxPlays && level < maxLevel && (info?.skips ?: 0) < 2 &&
                song.videoId !in warmIds
        }

        val forYou = if (listenerCentroid != null) {
            val strict = songs.filter { moodFeatures.containsKey(it.videoId) && isDiscoveryCandidate(it, maxPlays = 2, maxLevel = 2.2f) }
            val pool = if (strict.size >= 4) strict else {
                songs.filter { moodFeatures.containsKey(it.videoId) && isDiscoveryCandidate(it, maxPlays = 5, maxLevel = 3.0f) }
            }
            val scored = pool.mapNotNull { song ->
                val feat = moodFeatures[song.videoId] ?: return@mapNotNull null
                val dv = (feat.valence - listenerCentroid.first).toDouble()
                val da = (feat.arousal - listenerCentroid.second).toDouble()
                val vaScore = exp(-(dv * dv + da * da) / (2.0 * 0.30 * 0.30))
                val mertVec = embeddingsByKey[song.videoId]
                val baseScore = if (listenerMert != null && mertVec != null) {
                    vaScore * 0.58 + cosineSimilarityPure(listenerMert, mertVec).coerceIn(0f, 1f) * 0.42
                } else {
                    vaScore
                }
                val jitter = if (dailyMixBoost) dailyJitter(seed, "foryou:${song.videoId}", 0.10f) else 0f
                song to (baseScore + jitter)
            }.sortedByDescending { it.second }.map { it.first }.take(24)
            if (scored.size >= 4) scored else emptyList()
        } else emptyList()

        val upliftingRaw = songs.mapNotNull { song ->
            val feat = moodFeatures[song.videoId] ?: return@mapNotNull null
            val margin = feat.uplifting - feat.calm
            if (feat.uplifting < 0.45f || margin < 0.10f) return@mapNotNull null
            val info = heat[song.heatKey()]
            val crBoost = ((info?.completionRate ?: 0.55f) - 0.5f) * 0.18
            val heatBoost = (info?.level ?: 0f).coerceAtMost(5f) / 5f * 0.16
            val jitter = if (dailyMixBoost) dailyJitter(seed, "up:${song.videoId}", 0.08f) else 0f
            val score = feat.uplifting * 0.65 + margin * 0.25 + heatBoost + crBoost + jitter
            song to score
        }.sortedByDescending { it.second }.map { it.first }.take(24)

        val calmRaw = songs.mapNotNull { song ->
            val feat = moodFeatures[song.videoId] ?: return@mapNotNull null
            val margin = feat.calm - feat.uplifting
            if (feat.calm < 0.45f || margin < 0.10f) return@mapNotNull null
            val info = heat[song.heatKey()]
            val crBoost = ((info?.completionRate ?: 0.55f) - 0.5f) * 0.18
            val heatBoost = (info?.level ?: 0f).coerceAtMost(5f) / 5f * 0.16
            val jitter = if (dailyMixBoost) dailyJitter(seed, "calm:${song.videoId}", 0.08f) else 0f
            val score = feat.calm * 0.65 + margin * 0.25 + heatBoost + crBoost + jitter
            song to score
        }.sortedByDescending { it.second }.map { it.first }.take(24)

        return AiShelves(
            forYou = forYou,
            uplifting = if (upliftingRaw.size >= 4) upliftingRaw else emptyList(),
            calm = if (calmRaw.size >= 4) calmRaw else emptyList(),
        )
    }

    private fun cosineSimilarityPure(a: FloatArray, b: FloatArray): Float {
        if (a.isEmpty() || a.size != b.size) return 0f
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            val av = a[i].toDouble()
            val bv = b[i].toDouble()
            dot += av * bv
            normA += av * av
            normB += bv * bv
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (!denom.isFinite() || denom <= 1e-8) 0f else (dot / denom).toFloat().coerceIn(-1f, 1f)
    }
}
