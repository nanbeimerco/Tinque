package com.music.bitchord

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.stats.HeatInfo
import com.music.bitchord.data.stats.HeatStore
import com.music.bitchord.data.stats.heatKey
import com.music.bitchord.feature.home.data.HomeModel
import com.music.bitchord.feature.home.data.MixKind
import com.music.bitchord.feature.homeai.LocalHomeAi
import com.music.bitchord.feature.localmusic.domain.model.LocalFolder
import com.music.bitchord.feature.localmusic.ui.components.buildSelectableRecommendationFolders
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

    @Test
    fun forYou_balancesRecentAdditionsAndCapsPerAlbumDiversity() {
        val nowMs = ZonedDateTime.of(2026, 11, 13, 12, 0, 0, 0, ZoneId.systemDefault()).toInstant().toEpochMilli()
        val oldSec = nowMs / 1000L - 90L * 24L * 3600L
        val recentSec = nowMs / 1000L - 2L * 24L * 3600L

        val favSeed = Song(videoId = "fav_1", title = "Fav", artist = "SeedArtist", thumbnailUrl = null, albumName = "SeedAlbum")
        val warmSeed = Song(videoId = "warm_1", title = "Warm", artist = "SeedArtist2", thumbnailUrl = null, albumName = "SeedAlbum2")
        val oldMatches = (1..6).map { idx ->
            Song(
                videoId = "old_$idx",
                title = "Old $idx",
                artist = "OldArtist$idx",
                thumbnailUrl = null,
                albumName = "OldAlbum$idx",
                localDateAddedSeconds = oldSec,
            )
        }
        val newAlbumA = (1..6).map { idx ->
            Song(
                videoId = "newA_$idx",
                title = "NewA $idx",
                artist = "NewArtistA",
                thumbnailUrl = null,
                albumName = "NewAlbumA",
                localDateAddedSeconds = recentSec,
            )
        }
        val newAlbumB = (1..4).map { idx ->
            Song(
                videoId = "newB_$idx",
                title = "NewB $idx",
                artist = "NewArtistB",
                thumbnailUrl = null,
                albumName = "NewAlbumB",
                localDateAddedSeconds = recentSec,
            )
        }

        val songs = listOf(favSeed, warmSeed) + newAlbumA + newAlbumB + oldMatches
        val heat = mapOf(
            warmSeed.heatKey() to heatInfo(level = 4.2f, plays = 10),
        )
        val features = buildMap {
            put(favSeed.videoId, LocalHomeAi.TrackMoodFeatures(0.75f, 0.70f, 0.75f, 0.20f))
            put(warmSeed.videoId, LocalHomeAi.TrackMoodFeatures(0.75f, 0.70f, 0.75f, 0.20f))
            oldMatches.forEach { put(it.videoId, LocalHomeAi.TrackMoodFeatures(0.73f, 0.68f, 0.72f, 0.22f)) }
            newAlbumA.forEach { put(it.videoId, LocalHomeAi.TrackMoodFeatures(0.75f, 0.70f, 0.75f, 0.20f)) }
            newAlbumB.forEach { put(it.videoId, LocalHomeAi.TrackMoodFeatures(0.74f, 0.69f, 0.73f, 0.21f)) }
        }

        val content = HomeModel.computeWithStats(
            songs = songs,
            heat = heat,
            playlists = emptyList(),
            favoriteIds = setOf(favSeed.videoId),
            favoritesName = "Favs",
            dailyMixBoost = false,
            today = LocalDate.of(2026, 11, 13),
            now = nowMs,
            moodFeatures = features,
        )

        val ids = content.forYouSongs.map { it.videoId }
        assertFalse("fav_1" in ids)
        assertFalse("warm_1" in ids)
        // Older matching songs rank ahead of newly added ones
        assertTrue(ids.take(6).all { it.startsWith("old_") })
        // After the older tracks, per-album diversity caps NewAlbumA at 2 before picking NewAlbumB
        val afterOld = content.forYouSongs.drop(6).take(4).map { it.albumName }.toSet()
        assertEquals(setOf("NewAlbumA", "NewAlbumB"), afterOld)
    }

    @Test
    fun calm_rescuesLowArousalBalladsAndDisplaysAtTwoTracks() {
        val up1 = aiSong("up_1", "UpArtist1")
        val up2 = aiSong("up_2", "UpArtist2")
        val ballad1 = aiSong("ballad_1", "BalladArtist1")
        val ballad2 = aiSong("ballad_2", "BalladArtist2")
        val songs = listOf(up1, up2, ballad1, ballad2)

        val features = mapOf(
            up1.videoId to LocalHomeAi.TrackMoodFeatures(0.82f, 0.80f, 0.78f, 0.15f),
            up2.videoId to LocalHomeAi.TrackMoodFeatures(0.78f, 0.76f, 0.72f, 0.18f),
            ballad1.videoId to LocalHomeAi.TrackMoodFeatures(0.25f, 0.32f, 0.28f, 0.39f),
            ballad2.videoId to LocalHomeAi.TrackMoodFeatures(0.28f, 0.34f, 0.26f, 0.40f),
        )

        val content = HomeModel.computeWithStats(
            songs = songs,
            heat = emptyMap(),
            playlists = emptyList(),
            favoriteIds = emptySet(),
            favoritesName = "Favs",
            dailyMixBoost = false,
            today = LocalDate.of(2026, 11, 13),
            moodFeatures = features,
        )

        assertEquals(setOf("ballad_1", "ballad_2"), content.calmSongs.map { it.videoId }.toSet())
        assertEquals(setOf("up_1", "up_2"), content.upliftingSongs.map { it.videoId }.toSet())
    }

    @Test
    fun uplifting_stage2FallbackSupplementsWhenUnderFourWithoutCalmOverlap() {
        val strictUp1 = aiSong("sup_1", "A1")
        val strictUp2 = aiSong("sup_2", "A2")
        val modUp1 = aiSong("mup_1", "A3")
        val modUp2 = aiSong("mup_2", "A4")
        val calm1 = aiSong("calm_1", "A5")
        val calm2 = aiSong("calm_2", "A6")
        val songs = listOf(strictUp1, strictUp2, modUp1, modUp2, calm1, calm2)

        val features = mapOf(
            strictUp1.videoId to LocalHomeAi.TrackMoodFeatures(0.80f, 0.80f, 0.70f, 0.20f),
            strictUp2.videoId to LocalHomeAi.TrackMoodFeatures(0.78f, 0.75f, 0.65f, 0.22f),
            modUp1.videoId to LocalHomeAi.TrackMoodFeatures(0.60f, 0.60f, 0.38f, 0.33f),
            modUp2.videoId to LocalHomeAi.TrackMoodFeatures(0.58f, 0.58f, 0.37f, 0.32f),
            calm1.videoId to LocalHomeAi.TrackMoodFeatures(0.30f, 0.30f, 0.20f, 0.75f),
            calm2.videoId to LocalHomeAi.TrackMoodFeatures(0.32f, 0.28f, 0.18f, 0.78f),
        )

        val content = HomeModel.computeWithStats(
            songs = songs,
            heat = emptyMap(),
            playlists = emptyList(),
            favoriteIds = emptySet(),
            favoritesName = "Favs",
            dailyMixBoost = false,
            today = LocalDate.of(2026, 11, 13),
            moodFeatures = features,
        )

        assertEquals(listOf("sup_1", "sup_2", "mup_1", "mup_2"), content.upliftingSongs.map { it.videoId })
        assertEquals(setOf("calm_1", "calm_2"), content.calmSongs.map { it.videoId }.toSet())
        assertTrue(content.upliftingSongs.map { it.videoId }.toSet().intersect(content.calmSongs.map { it.videoId }.toSet()).isEmpty())
    }

    @Test
    fun homeInstrumentalToggles_controlMixesAndAiShelvesIndependently() {
        val favSeed = aiSong("fav_seed", "FavArtist")
        val vocalsUp = (1..6).map { i -> aiSong("v_up_$i", "UpArtist$i").copy(title = "Sunny Day $i") }
        val vocalsCalm = (1..6).map { i -> aiSong("v_calm_$i", "CalmArtist$i").copy(title = "Night Ballad $i") }
        val instUp = aiSong("inst_up", "InstArtist1").copy(title = "Sunrise (Instrumental)")
        val instCalm = aiSong("inst_calm", "InstArtist2").copy(title = "Nocturne [Inst.]")
        val allSongs = listOf(favSeed, instUp, instCalm) + vocalsUp + vocalsCalm

        val features = buildMap {
            put(favSeed.videoId, LocalHomeAi.TrackMoodFeatures(0.75f, 0.72f, 0.75f, 0.20f))
            put(instUp.videoId, LocalHomeAi.TrackMoodFeatures(0.85f, 0.82f, 0.90f, 0.10f))
            put(instCalm.videoId, LocalHomeAi.TrackMoodFeatures(0.30f, 0.25f, 0.12f, 0.90f))
            vocalsUp.forEach { put(it.videoId, LocalHomeAi.TrackMoodFeatures(0.78f, 0.74f, 0.74f, 0.18f)) }
            vocalsCalm.forEach { put(it.videoId, LocalHomeAi.TrackMoodFeatures(0.32f, 0.28f, 0.15f, 0.72f)) }
        }
        val day = LocalDate.of(2026, 11, 13)

        val mixesOnlyExcluded = HomeModel.computeWithStats(
            songs = allSongs,
            heat = emptyMap(),
            playlists = emptyList(),
            favoriteIds = setOf(favSeed.videoId),
            favoritesName = "Favs",
            dailyMixBoost = false,
            today = day,
            moodFeatures = features,
            excludeInstrumentalsInMixes = true,
            excludeInstrumentalsInAiShelves = false,
        )
        val mixIdsA = mixesOnlyExcluded.mixes.flatMap { it.songs }.map { it.videoId }.toSet()
        assertFalse("inst_up" in mixIdsA)
        assertFalse("inst_calm" in mixIdsA)
        assertTrue("inst_up" in mixesOnlyExcluded.forYouSongs.map { it.videoId })
        assertTrue("inst_up" in mixesOnlyExcluded.upliftingSongs.map { it.videoId })
        assertTrue("inst_calm" in mixesOnlyExcluded.calmSongs.map { it.videoId })

        val shelvesOnlyExcluded = HomeModel.computeWithStats(
            songs = allSongs,
            heat = emptyMap(),
            playlists = emptyList(),
            favoriteIds = setOf(favSeed.videoId),
            favoritesName = "Favs",
            dailyMixBoost = false,
            today = day,
            moodFeatures = features,
            excludeInstrumentalsInMixes = false,
            excludeInstrumentalsInAiShelves = true,
        )
        assertFalse("inst_up" in shelvesOnlyExcluded.forYouSongs.map { it.videoId })
        assertFalse("inst_up" in shelvesOnlyExcluded.upliftingSongs.map { it.videoId })
        assertFalse("inst_calm" in shelvesOnlyExcluded.calmSongs.map { it.videoId })
        val mixIdsB = shelvesOnlyExcluded.mixes.flatMap { it.songs }.map { it.videoId }.toSet()
        assertTrue("inst_up" in mixIdsB)
        assertTrue("inst_calm" in mixIdsB)
    }

    @Test
    fun excludedFolders_removesFromHeatMixesAndAiWhileKeepingRecentlyPlayed() {
        val nowMs = 1_760_000_000_000L
        val toeicDirect = aiSong("toeic_1", "ETS").copy(
            title = "Test 1 Part 1",
            localPath = "/storage/emulated/0/Music/TOEIC/Test1.mp3",
            localDateAddedSeconds = nowMs / 1000L - 60,
        )
        val toeicSub = aiSong("toeic_2", "ETS").copy(
            title = "Part 3 Conversation",
            localPath = "/storage/emulated/0/Music/TOEIC/Part3/Lesson2.mp3",
            localDateAddedSeconds = nowMs / 1000L - 60,
        )
        val favMusic = aiSong("fav_music", "PopArtist").copy(
            localPath = "/storage/emulated/0/Music/Pop/Fav.flac",
            localDateAddedSeconds = nowMs / 1000L - 10_000,
        )
        val rockTracks = (1..8).map { i ->
            aiSong("rock_$i", "RockArtist$i").copy(
                localPath = "/storage/emulated/0/Music/Rock/Song$i.flac",
                localDateAddedSeconds = nowMs / 1000L - 50_000,
            )
        }
        val songs = listOf(toeicDirect, toeicSub, favMusic) + rockTracks
        val heat = buildMap {
            put(toeicDirect.heatKey(), heatInfo(level = 5.0f, plays = 30, shortScore = 4.0f).copy(lastPlayed = nowMs - 1_000L))
            put(toeicSub.heatKey(), heatInfo(level = 4.5f, plays = 20, shortScore = 3.5f).copy(lastPlayed = nowMs - 2_000L))
            rockTracks.take(4).forEachIndexed { idx, s ->
                put(s.heatKey(), heatInfo(level = 3.0f - idx * 0.2f, plays = 6, shortScore = 2.0f).copy(lastPlayed = nowMs - 10_000L))
            }
        }
        val features = buildMap {
            put(favMusic.videoId, LocalHomeAi.TrackMoodFeatures(0.75f, 0.72f, 0.76f, 0.20f))
            put(toeicDirect.videoId, LocalHomeAi.TrackMoodFeatures(0.85f, 0.85f, 0.90f, 0.10f))
            put(toeicSub.videoId, LocalHomeAi.TrackMoodFeatures(0.30f, 0.25f, 0.10f, 0.90f))
            rockTracks.take(4).forEach { put(it.videoId, LocalHomeAi.TrackMoodFeatures(0.74f, 0.70f, 0.74f, 0.20f)) }
            rockTracks.drop(4).forEach { put(it.videoId, LocalHomeAi.TrackMoodFeatures(0.32f, 0.28f, 0.18f, 0.75f)) }
        }

        val content = HomeModel.computeWithStats(
            songs = songs,
            heat = heat,
            playlists = emptyList(),
            favoriteIds = setOf(favMusic.videoId),
            favoritesName = "Favs",
            dailyMixBoost = false,
            today = LocalDate.of(2026, 11, 13),
            now = nowMs,
            moodFeatures = features,
            excludedFolders = setOf("/storage/emulated/0/Music/TOEIC"),
        )

        val excludedIds = setOf("toeic_1", "toeic_2")
        assertTrue(content.heatSongs.none { it.videoId in excludedIds })
        assertFalse(content.hero?.videoId in excludedIds)
        assertTrue(content.rising.none { it.videoId in excludedIds })
        assertTrue(content.mixes.flatMap { it.songs }.none { it.videoId in excludedIds })
        assertTrue(content.forYouSongs.none { it.videoId in excludedIds })
        assertTrue(content.upliftingSongs.none { it.videoId in excludedIds })
        assertTrue(content.calmSongs.none { it.videoId in excludedIds })

        val recentIds = content.recent.map { it.videoId }.toSet()
        assertTrue("toeic_1" in recentIds)
        assertTrue("toeic_2" in recentIds)
    }

    @Test
    fun buildSelectableRecommendationFolders_synthesizesNonGenericParentsOnly() {
        val discovered = listOf(
            LocalFolder("/storage/emulated/0/Music/TOEIC/Part1", "Part1", songCount = 12),
            LocalFolder("/storage/emulated/0/Music/TOEIC/Part2", "Part2", songCount = 18),
            LocalFolder("/storage/emulated/0/Music/Rock", "Rock", songCount = 50),
            LocalFolder("/storage/emulated/0/Music/Blacklisted", "Blacklisted", songCount = 5, isBlacklisted = true),
        )
        val built = buildSelectableRecommendationFolders(
            discovered = discovered,
            excludedFolders = setOf("/storage/emulated/0/Music/TOEIC"),
        )
        val byNorm = built.associateBy { it.normalizedPath }

        assertFalse("/storage/emulated/0" in byNorm)
        assertFalse("/storage/emulated/0/music" in byNorm)
        assertFalse("/storage/emulated/0/music/blacklisted" in byNorm)

        val parent = byNorm["/storage/emulated/0/music/toeic"]!!
        assertTrue(parent.isGroupFolder)
        assertTrue(parent.isDirectlyExcluded)
        assertFalse(parent.isExcludedByParent)
        assertEquals(30, parent.songCount)

        val child = byNorm["/storage/emulated/0/music/toeic/part1"]!!
        assertFalse(child.isDirectlyExcluded)
        assertTrue(child.isExcludedByParent)
    }
}
