package com.music.bitchord

import com.music.bitchord.data.model.Song
import com.music.bitchord.feature.homeai.LocalHomeAi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalHomeAiTest {

    private fun song(id: String, path: String = "/music/$id.flac", durationText: String = "3:30", startMs: Long = 0L) =
        Song(
            videoId = id,
            title = "Track $id",
            artist = "Artist",
            thumbnailUrl = "",
            durationText = durationText,
            localPath = path,
            localStartMs = startMs,
        )

    @Test
    fun enqueueLibrary_doesNotDuplicatePendingOrResetCompleted() {
        val queue = LocalHomeAi.AnalysisQueueState(LocalHomeAi::cacheKey)
        val a = song("a")
        val b = song("b")
        val c = song("c")
        val d = song("d")

        assertTrue(queue.enqueueLibrary(listOf(a, b, c), alreadyFailedCount = 0))
        val first = queue.pollNext()
        assertEquals(LocalHomeAi.cacheKey(a), first?.first)
        queue.markTrackDone(first!!.first, succeeded = true, wasLowMemory = false)
        assertEquals(1, queue.completed)
        assertEquals(3, queue.total)

        // Calling enqueueLibrary again with overlapping pending tracks merges cleanly
        assertTrue(queue.enqueueLibrary(listOf(b, c, d), alreadyFailedCount = 0))
        assertEquals(1, queue.completed)
        assertEquals(4, queue.total)

        // Duplicate call with no new tracks returns false and changes nothing
        assertFalse(queue.enqueueLibrary(listOf(b, c, d), alreadyFailedCount = 0))
        assertEquals(1, queue.completed)
        assertEquals(4, queue.total)
    }

    @Test
    fun prioritizeFront_reordersWithoutResettingCompleted() {
        val queue = LocalHomeAi.AnalysisQueueState(LocalHomeAi::cacheKey)
        val a = song("a")
        val b = song("b")
        val c = song("c")
        val d = song("d")

        queue.enqueueLibrary(listOf(a, b, c), alreadyFailedCount = 1)
        val polledA = queue.pollNext()!!
        queue.markTrackDone(polledA.first, succeeded = true, wasLowMemory = false)
        assertEquals(1, queue.completed)

        queue.prioritizeFront(listOf(c, d), alreadyFailedCount = 1)
        val snapshot = queue.snapshot(finished = false)
        assertEquals(1, snapshot.completed)
        assertEquals(4, snapshot.total)
        assertEquals(1, snapshot.failed)
        assertTrue(snapshot.hasMore)

        assertEquals(LocalHomeAi.cacheKey(c), queue.pollNext()?.first)
        assertEquals(LocalHomeAi.cacheKey(d), queue.pollNext()?.first)
        assertEquals(LocalHomeAi.cacheKey(b), queue.pollNext()?.first)
        assertNull(queue.pollNext())
    }

    @Test
    fun markTrackDone_tracksFailureAndLowMemorySnapshot() {
        val queue = LocalHomeAi.AnalysisQueueState(LocalHomeAi::cacheKey)
        val a = song("a")
        queue.enqueueLibrary(listOf(a), alreadyFailedCount = 2)
        val polled = queue.pollNext()!!
        queue.markTrackDone(polled.first, succeeded = false, wasLowMemory = true)

        val snap = queue.snapshot(finished = false)
        assertEquals(1, snap.completed)
        assertEquals(1, snap.total)
        assertEquals(3, snap.failed)
        assertTrue(snap.lowMemory)
        assertFalse(snap.hasMore)
    }

    @Test
    fun cacheKey_ignoresDurationTextDifferenceForSameSource() {
        val withText = song("id1", path = "/music/song.mp3", durationText = "3:45")
        val withoutText = song("id1", path = "/music/song.mp3", durationText = "")
        val splitCue = song("id1", path = "/music/song.mp3", durationText = "3:45", startMs = 120_000L)

        assertEquals(LocalHomeAi.cacheKey(withText), LocalHomeAi.cacheKey(withoutText))
        assertNotEquals(LocalHomeAi.cacheKey(withText), LocalHomeAi.cacheKey(splitCue))
    }

    @Test
    fun prioritizeLibraryForAnalysis_matchesLocalUriKeysAndDistributesUnplayedAcrossAlbums() {
        val downloadedFav = Song(
            videoId = "yt_dl_1",
            title = "Downloaded Fav",
            artist = "Artist A",
            thumbnailUrl = "",
            localUri = "content://media/external/audio/media/9001",
            localPath = "/music/dl/fav.m4a",
            localDateAddedSeconds = 100L,
        )
        val downloadedPlayed = Song(
            videoId = "yt_dl_2",
            title = "Downloaded Played",
            artist = "Artist B",
            thumbnailUrl = "",
            localUri = "content://media/external/audio/media/9002",
            localPath = "/music/dl/played.m4a",
            localDateAddedSeconds = 110L,
        )
        val oldAlbumSongs = (1..5).map { idx ->
            Song(
                videoId = "old_$idx",
                title = "Old $idx",
                artist = "OldArtist",
                albumName = "OldAlbum",
                thumbnailUrl = "",
                localPath = "/music/old/$idx.flac",
                localDateAddedSeconds = 100L + idx,
            )
        }
        val newAlbumSongs = (1..5).map { idx ->
            Song(
                videoId = "new_$idx",
                title = "New $idx",
                artist = "NewArtist",
                albumName = "NewAlbum",
                thumbnailUrl = "",
                localPath = "/music/new/$idx.flac",
                localDateAddedSeconds = 1_000_000L + idx,
            )
        }
        val midAlbumSongs = (1..5).map { idx ->
            Song(
                videoId = "mid_$idx",
                title = "Mid $idx",
                artist = "MidArtist",
                albumName = "MidAlbum",
                thumbnailUrl = "",
                localPath = "/music/mid/$idx.flac",
                localDateAddedSeconds = 500_000L + idx,
            )
        }

        val ordered = LocalHomeAi.prioritizeLibraryForAnalysis(
            songs = newAlbumSongs + oldAlbumSongs + midAlbumSongs + listOf(downloadedPlayed, downloadedFav),
            heatLevels = emptyMap(),
            favoriteIds = setOf("content://media/external/audio/media/9001"),
            playCounts = mapOf("content://media/external/audio/media/9002" to 3),
        )

        assertEquals(17, ordered.size)
        assertEquals(listOf("yt_dl_1", "yt_dl_2"), ordered.take(2).map { it.videoId })

        val firstRoundAlbums = ordered.drop(2).take(3).map { it.albumName }.toSet()
        assertEquals(setOf("OldAlbum", "NewAlbum", "MidAlbum"), firstRoundAlbums)
    }
}
