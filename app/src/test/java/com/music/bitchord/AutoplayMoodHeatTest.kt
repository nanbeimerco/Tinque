package com.music.bitchord

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.stats.HeatInfo
import com.music.bitchord.data.stats.heatKey
import com.music.bitchord.feature.homeai.LocalHomeAi.TrackMoodFeatures
import com.music.bitchord.playback.AutoplayScoringInput
import com.music.bitchord.playback.isMetadataOrCachedInstrumental
import com.music.bitchord.playback.rankAutoplayCandidates
import com.music.bitchord.playback.rankOnlineRadioCandidates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoplayMoodHeatTest {

    private fun song(
        id: String,
        title: String = "Track $id",
        artist: String = "Artist $id",
        localPath: String? = null,
    ) = Song(
        videoId = id,
        title = title,
        artist = artist,
        thumbnailUrl = null,
        localPath = localPath,
    )

    private fun heat(
        level: Float = 1f,
        plays: Int = 4,
        fullListens: Int = 3,
        skips: Int = 0,
        lastPlayed: Long = 1000L,
        peak: Float = level,
        recordedPlays: Int = plays,
        isRevival: Boolean = false,
    ) = HeatInfo(
        level = level,
        trend = 0,
        plays = plays,
        fullListens = fullListens,
        skips = skips,
        listensThisWeek = 0,
        firstPlayed = 1000L,
        lastPlayed = lastPlayed,
        peak = peak,
        recordedPlays = recordedPlays,
        isRevival = isRevival,
    )

    @Test
    fun instrumentalTier1CatchesMarkersWithoutFalsePositives() {
        assertTrue(isMetadataOrCachedInstrumental(song("1", "Nightfall (Instrumental)")))
        assertTrue(isMetadataOrCachedInstrumental(song("2", "Starlight [Inst.]")))
        assertTrue(isMetadataOrCachedInstrumental(song("3", "Blue Sky - Off Vocal")))
        assertTrue(isMetadataOrCachedInstrumental(song("4", "光（オフボーカル）")))
        assertTrue(isMetadataOrCachedInstrumental(song("5", "夢 [カラオケ]")))
        assertTrue(isMetadataOrCachedInstrumental(song("6", "Evening", localPath = "/music/song_Backing_Track.flac")))
        assertTrue(isMetadataOrCachedInstrumental(song("7", "Dawn", localPath = "/music/純音楽_ver.mp3")))
        assertTrue(isMetadataOrCachedInstrumental(song("8", "Song"), genre = "Instrumental"))
        assertTrue(
            isMetadataOrCachedInstrumental(
                song("9", "Mystery"),
                cachedLyrics = "[00:05.00] (Instrumental)\n[00:40.00] 演奏のみ",
            ),
        )

        // Never false-positive on substrings of real words or songs simply lacking lyrics.
        assertFalse(isMetadataOrCachedInstrumental(song("10", "Instead of Goodbye"), cachedLyrics = null))
        assertFalse(isMetadataOrCachedInstrumental(song("11", "Against The Wind"), cachedLyrics = null))
        assertFalse(isMetadataOrCachedInstrumental(song("12", "インストラクターの恋")))
        assertFalse(
            isMetadataOrCachedInstrumental(
                song("13", "Morning Light"),
                cachedLyrics = "[00:05.00] (Instrumental)\n[00:18.00] Walking in the sun",
            ),
        )
    }

    @Test
    fun instrumentalFilterExcludesMarkedAndFallsBackWhenSeedInstrumentalOrEmpty() {
        val vocalSeed = song("seed", "Main Vocal")
        val instSeed = song("instSeed", "Main Theme (Instrumental)")
        val vocalTrack = song("v1", "Singing Heart")
        val taggedInst = song("i1", "Singing Heart (Off Vocal)")
        val untaggedAudioInst = song("i2", "Moonlight Sonata")

        val filtered = rankAutoplayCandidates(
            AutoplayScoringInput(
                seed = vocalSeed,
                candidates = listOf(taggedInst, untaggedAudioInst, vocalTrack),
                excludeInstrumentals = true,
                seedIsInstrumental = false,
                instrumentalKeys = setOf("i2"),
                jitterFor = { 0.0 },
            ),
        )
        assertEquals(listOf("v1"), filtered.map { it.videoId })

        val keptForInstrumentalSeed = rankAutoplayCandidates(
            AutoplayScoringInput(
                seed = instSeed,
                candidates = listOf(taggedInst, untaggedAudioInst, vocalTrack),
                excludeInstrumentals = true,
                seedIsInstrumental = true,
                instrumentalKeys = setOf("i2"),
                jitterFor = { 0.0 },
            ),
        )
        assertEquals(3, keptForInstrumentalSeed.size)

        val fallbackWhenEmpty = rankAutoplayCandidates(
            AutoplayScoringInput(
                seed = vocalSeed,
                candidates = listOf(taggedInst, untaggedAudioInst),
                excludeInstrumentals = true,
                seedIsInstrumental = false,
                instrumentalKeys = setOf("i2"),
                jitterFor = { 0.0 },
            ),
        )
        assertEquals(listOf("i1", "i2"), fallbackWhenEmpty.map { it.videoId }.sorted())
    }

    @Test
    fun moodGaussianContinuityAndMertSimilarityOutrankMoodClash() {
        val seed = song("seed", artist = "SeedArtist")
        val sameArtistMoodClash = song("clash", artist = "SeedArtist")
        val closeMoodAndSonic = song("close", artist = "OtherArtist")
        val distantMood = song("far", artist = "FarArtist")

        val features = mapOf(
            "seed" to TrackMoodFeatures(valence = 0.75f, arousal = 0.70f, uplifting = 0.7f, calm = 0.2f),
            "clash" to TrackMoodFeatures(valence = 0.15f, arousal = 0.10f, uplifting = 0.1f, calm = 0.9f),
            "close" to TrackMoodFeatures(valence = 0.72f, arousal = 0.68f, uplifting = 0.65f, calm = 0.25f),
            "far" to TrackMoodFeatures(valence = 0.30f, arousal = 0.50f, uplifting = 0.3f, calm = 0.4f),
        )
        val seedVec = floatArrayOf(1f, 0f, 0f)
        val embeddings = mapOf(
            "clash" to floatArrayOf(0f, 1f, 0f),
            "close" to floatArrayOf(0.95f, 0.3f, 0f),
            "far" to floatArrayOf(0.2f, 0.9f, 0f),
        )

        val ranked = rankAutoplayCandidates(
            AutoplayScoringInput(
                seed = seed,
                candidates = listOf(sameArtistMoodClash, distantMood, closeMoodAndSonic),
                featuresByKey = features,
                seedEmbedding = seedVec,
                candidateEmbeddings = embeddings,
                jitterFor = { 0.0 },
            ),
        )
        assertEquals("close", ranked.first().videoId)
        assertEquals("clash", ranked.last().videoId)
    }

    @Test
    fun skippedArtistAndMoodAreDownrankedWhileRevivalIsInjectedIntoSlotWindow() {
        val now = 1_700_000_000_000L
        val seed = song("seed", artist = "Seed")
        val candidates = (1..8).map { i -> song("c$i", artist = "Artist$i") } +
            listOf(
                song("skipTarget", artist = "SkippedArtist"),
                song("sameDay", artist = "ArtistSameDay"),
                song("revival", artist = "ForgottenArtist"),
            )

        val heatMap = buildMap {
            candidates.forEach { s ->
                put(s.heatKey(), heat(level = 2f, lastPlayed = now - 5 * 86_400_000L))
            }
            put(
                "sameDay",
                heat(level = 2f, lastPlayed = now - 30 * 60_000L), // played 30m ago
            )
            put(
                "revival",
                heat(
                    level = 0.8f,
                    plays = 15,
                    fullListens = 14,
                    lastPlayed = now - 30 * 86_400_000L,
                    peak = 4.2f,
                    isRevival = true,
                ),
            )
        }

        val ranked = rankAutoplayCandidates(
            AutoplayScoringInput(
                seed = seed,
                candidates = candidates,
                skippedArtists = setOf("skippedartist"),
                heatByKey = heatMap,
                nowMs = now,
                limit = 10,
                jitterFor = { 0.0 },
            ),
        )

        val revivalIndex = ranked.indexOfFirst { it.videoId == "revival" }
        assertTrue("Revival track should be woven into the batch", revivalIndex in 1..4)
        assertEquals("skipTarget", ranked.last().videoId)
        assertTrue(ranked.indexOfFirst { it.videoId == "sameDay" } > revivalIndex)
    }

    @Test
    fun onlineRadioRerankingRespectsSkipsAndInstrumentalFilter() {
        val r1 = song("r1", "Track 1", artist = "Skipped")
        val r2 = song("r2", "Track 2 (Karaoke)", artist = "Other")
        val r3 = song("r3", "Track 3", artist = "GoodArtist")

        val ranked = rankOnlineRadioCandidates(
            related = listOf(r1, r2, r3),
            skippedArtists = setOf("skipped"),
            excludeInstrumentals = true,
        )
        assertEquals(listOf("r3", "r1"), ranked.map { it.videoId })
    }
}
