package com.music.bitchord

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.stats.HeatInfo
import com.music.bitchord.data.stats.HeatStore
import com.music.bitchord.data.stats.heatKey
import com.music.bitchord.feature.home.data.HomeModel
import com.music.bitchord.feature.home.data.MixKind
import com.music.bitchord.feature.homeai.LocalHomeAi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class HomeRotationTest {

    private fun makeSong(
        idx: Int,
        album: String,
        addedSec: Long,
    ) = Song(
        videoId = "id_$idx",
        title = "Track $idx",
        artist = "Artist ${idx % 4}",
        thumbnailUrl = "https://example.com/$idx.jpg",
        albumName = album,
        localPath = "/music/$album/$idx.flac",
        localDateAddedSeconds = addedSec,
    )

    private fun heatInfo(level: Float, plays: Int = 5, shortScore: Float = 2f, longScore: Float = level * 4f) =
        HeatInfo(
            level = level,
            trend = 0,
            plays = plays,
            fullListens = plays,
            skips = 0,
            listensThisWeek = 1,
            firstPlayed = 1000L,
            lastPlayed = 2000L,
            peak = level,
            recordedPlays = plays,
            momentum = 0f,
            shortScore = shortScore,
            longScore = longScore,
            shortLevel = level,
            isRevival = false,
        )

    @Test
    fun unplayedAlbums_keepsFreshTop3AndRotatesRemainingDaily() {
        val songs = (1..20).flatMap { albIdx ->
            listOf(makeSong(albIdx * 10, "Album_$albIdx", addedSec = albIdx * 100L))
        }
        val day1 = LocalDate.of(2026, 11, 13)
        val day2 = day1.plusDays(1)
        val day3 = day1.plusDays(2)

        val c1 = HomeModel.computeWithStats(songs, emptyMap(), emptyList(), emptySet(), "Favs", today = day1)
        val c1Repeat = HomeModel.computeWithStats(songs, emptyMap(), emptyList(), emptySet(), "Favs", today = day1)
        val c2 = HomeModel.computeWithStats(songs, emptyMap(), emptyList(), emptySet(), "Favs", today = day2)
        val c3 = HomeModel.computeWithStats(songs, emptyMap(), emptyList(), emptySet(), "Favs", today = day3)

        assertEquals(12, c1.unplayedAlbums.size)
        // Newest 3 albums stay pinned at front
        assertEquals(listOf("Album_20", "Album_19", "Album_18"), c1.unplayedAlbums.take(3).map { it.title })
        assertEquals(listOf("Album_20", "Album_19", "Album_18"), c2.unplayedAlbums.take(3).map { it.title })
        // Same day deterministic
        assertEquals(c1.unplayedAlbums.map { it.title }, c1Repeat.unplayedAlbums.map { it.title })
        // Remaining 9 change across days
        val tail1 = c1.unplayedAlbums.drop(3).map { it.title }
        val tail2 = c2.unplayedAlbums.drop(3).map { it.title }
        val tail3 = c3.unplayedAlbums.drop(3).map { it.title }
        assertTrue(tail1 != tail2 || tail2 != tail3)
    }

    @Test
    fun mixes_under50_rotateOrderAcrossDays() {
        val songs = (1..20).map { makeSong(it, "HotAlbum_${it % 5}", addedSec = it * 10L) }
        val heat = songs.associate { it.heatKey() to heatInfo(level = 3.8f + (it.videoId.hashCode() and 3) * 0.2f) }
        val d1 = HomeModel.computeWithStats(songs, heat, emptyList(), emptySet(), "Favs", today = LocalDate.of(2026, 11, 13))
        val d2 = HomeModel.computeWithStats(songs, heat, emptyList(), emptySet(), "Favs", today = LocalDate.of(2026, 11, 14))

        val hot1 = d1.mixes.first { it.kind == MixKind.HOT }.songs.map { it.videoId }
        val hot2 = d2.mixes.first { it.kind == MixKind.HOT }.songs.map { it.videoId }
        assertEquals(20, hot1.size)
        assertNotEquals(hot1, hot2)
    }

    @Test
    fun dailyMixBoostFalse_sortsHeatSongsStrictlyByLevel() {
        val sLow = makeSong(1, "A", 10L)
        val sHigh = makeSong(2, "B", 20L)
        val heat = mapOf(
            sLow.heatKey() to heatInfo(level = 2.8f, shortScore = 10f, longScore = 8f),
            sHigh.heatKey() to heatInfo(level = 4.6f, shortScore = 0.2f, longScore = 25f),
        )
        val strict = HomeModel.computeWithStats(
            listOf(sLow, sHigh),
            heat,
            emptyList(),
            emptySet(),
            "Favs",
            dailyMixBoost = false,
            today = LocalDate.of(2026, 11, 13),
        )
        assertEquals(listOf("id_2", "id_1"), strict.heatSongs.map { it.videoId })
    }

    private fun aiSong(id: String, artist: String = "Artist") = Song(
        videoId = id,
        title = id,
        artist = artist,
        thumbnailUrl = null,
    )

    @Test
    fun aiRecommendationsRequireFourAnalyzedTracks() {
        val songs = (1..6).map { idx -> aiSong("s_$idx", "Artist $idx") }
        val onlyThree = songs.take(3).associate { s ->
            s.videoId to LocalHomeAi.TrackMoodFeatures(0.8f, 0.8f, 0.85f, 0.1f)
        }
        val content = HomeModel.computeWithStats(
            songs = songs,
            heat = emptyMap(),
            playlists = emptyList(),
            favoriteIds = setOf("s_1", "s_2"),
            favoritesName = "Favs",
            moodFeatures = onlyThree,
            today = LocalDate.of(2026, 11, 13),
        )
        assertTrue(content.forYouSongs.isEmpty())
        assertTrue(content.upliftingSongs.isEmpty())
        assertTrue(content.calmSongs.isEmpty())
        assertFalse(content.mixes.any { it.kind in setOf(MixKind.FOR_YOU, MixKind.UPLIFTING, MixKind.CALM) })
    }

    @Test
    fun aiRecommendationsSeparateUpliftingCalmAndMatchForYouToUserProfile() {
        fun basisVec(index: Int): FloatArray = FloatArray(16).apply { this[index] = 1f }
        val warm = (1..4).map { aiSong("warm_$it", "WarmArtist") }
        val matchUnplayed = (1..10).map { aiSong("match_$it", "DiscoveryArtist$it") }
        val calmUnplayed = (1..10).map { aiSong("calm_$it", "AmbientArtist$it") }
        val songs = warm + matchUnplayed + calmUnplayed

        val heat = warm.associate { song ->
            song.heatKey() to HeatInfo(
                level = 4.2f,
                trend = 1,
                plays = 15,
                fullListens = 14,
                skips = 0,
                listensThisWeek = 4,
                firstPlayed = 1_000L,
                lastPlayed = 5_000L,
                peak = 4.5f,
                recordedPlays = 15,
                longScore = 10f,
                shortScore = 5f,
            )
        }
        val features = buildMap {
            warm.forEach { put(it.videoId, LocalHomeAi.TrackMoodFeatures(0.86f, 0.84f, 0.82f, 0.10f)) }
            matchUnplayed.forEach { put(it.videoId, LocalHomeAi.TrackMoodFeatures(0.85f, 0.80f, 0.78f, 0.12f)) }
            calmUnplayed.forEach { put(it.videoId, LocalHomeAi.TrackMoodFeatures(0.24f, 0.20f, 0.10f, 0.86f)) }
        }
        val embeddings = buildMap {
            warm.forEach { put(it.videoId, basisVec(0)) }
            matchUnplayed.forEach { put(it.videoId, basisVec(0)) }
            calmUnplayed.forEach { put(it.videoId, basisVec(1)) }
        }

        val content = HomeModel.computeWithStats(
            songs = songs,
            heat = heat,
            playlists = emptyList(),
            favoriteIds = emptySet(),
            favoritesName = "Favs",
            moodFeatures = features,
            embeddingsByKey = embeddings,
            dailyMixBoost = false,
            today = LocalDate.of(2026, 11, 13),
        )

        assertTrue(content.upliftingSongs.size >= 10)
        assertTrue(content.calmSongs.size >= 10)
        assertTrue(content.forYouSongs.size >= 10)
        val upliftingIds = content.upliftingSongs.map { it.videoId }.toSet()
        val calmIds = content.calmSongs.map { it.videoId }.toSet()
        assertTrue(upliftingIds.intersect(calmIds).isEmpty())
        assertTrue(calmIds.all { it.startsWith("calm_") })
        assertTrue(content.forYouSongs.take(10).all { it.videoId.startsWith("match_") })
        val mixKinds = content.mixes.map { it.kind }.toSet()
        assertTrue(MixKind.FOR_YOU in mixKinds)
        assertTrue(MixKind.UPLIFTING in mixKinds)
        assertTrue(MixKind.CALM in mixKinds)
    }

    @Test
    fun timeOfDayIncorporatesSlotMoodCentroid() {
        val now = ZonedDateTime.of(2026, 11, 13, 23, 0, 0, 0, ZoneId.systemDefault()).toInstant().toEpochMilli()
        val currentSlot = HeatStore.slotOf(now)
        val slotCounts = List(4) { idx -> if (idx == currentSlot) 12 else 0 }

        val seedA = aiSong("seed_a", "Artist A")
        val seedB = aiSong("seed_b", "Artist B")
        val calmPicks = (1..4).map { aiSong("calm_pick_$it", "Artist Calm$it") }
        val loudPicks = (1..4).map { aiSong("loud_pick_$it", "Artist Loud$it") }
        val songs = listOf(seedA, seedB) + calmPicks + loudPicks

        val heat = listOf(seedA, seedB).associate { song ->
            song.heatKey() to HeatInfo(
                level = 4.0f,
                trend = 0,
                plays = 12,
                fullListens = 11,
                skips = 0,
                listensThisWeek = 3,
                firstPlayed = 1_000L,
                lastPlayed = now - 60_000L,
                peak = 4.0f,
                recordedPlays = 12,
                longScore = 8f,
                shortScore = 4f,
                slots = slotCounts,
            )
        }
        val features = buildMap {
            put(seedA.videoId, LocalHomeAi.TrackMoodFeatures(0.22f, 0.20f, 0.12f, 0.88f))
            put(seedB.videoId, LocalHomeAi.TrackMoodFeatures(0.26f, 0.24f, 0.14f, 0.84f))
            calmPicks.forEach { put(it.videoId, LocalHomeAi.TrackMoodFeatures(0.24f, 0.22f, 0.15f, 0.85f)) }
            loudPicks.forEach { put(it.videoId, LocalHomeAi.TrackMoodFeatures(0.92f, 0.94f, 0.90f, 0.05f)) }
        }

        val content = HomeModel.computeWithStats(
            songs = songs,
            heat = heat,
            playlists = emptyList(),
            favoriteIds = emptySet(),
            favoritesName = "Favs",
            dailyMixBoost = false,
            today = LocalDate.of(2026, 11, 13),
            now = now,
            moodFeatures = features,
        )

        val ids = content.timeOfDay.map { it.videoId }
        assertEquals(6, ids.size)
        val aiExpandedIds = ids.filterNot { it == "seed_a" || it == "seed_b" }
        assertEquals(4, aiExpandedIds.size)
        assertTrue(aiExpandedIds.all { it.startsWith("calm_pick_") })
        assertTrue(ids.none { it.startsWith("loud_pick_") })
    }
}
