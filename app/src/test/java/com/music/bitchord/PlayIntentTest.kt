package com.music.bitchord

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.stats.PlayIntent
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayIntentTest {

    @After
    fun tearDown() {
        PlayIntent.clear()
    }

    private fun song(id: String, localPath: String? = null) = Song(
        videoId = id,
        title = "Title $id",
        artist = "Artist",
        thumbnailUrl = null,
        localPath = localPath,
    )

    @Test
    fun consume_matchesOnceWithinTtl_evenAfterUriChange_usingHeatKey() {
        val s1 = song("vid1", "/music/a.flac")
        val t0 = 1_000_000L
        PlayIntent.queueStarting(s1, now = t0)
        assertTrue(PlayIntent.consume(s1.copy(localUri = "content://media/99"), now = t0 + 3_000L))
        // Second call must be consumed already
        assertFalse(PlayIntent.consume(s1, now = t0 + 3_100L))
    }

    @Test
    fun markShuffleStart_suppressesPickedIntent() {
        val s1 = song("vid1")
        PlayIntent.markShuffleStart()
        PlayIntent.queueStarting(s1, now = 1_000L)
        assertFalse(PlayIntent.consume(s1, now = 1_500L))
    }

    @Test
    fun expiredIntent_returnsFalse() {
        val s1 = song("vid1")
        PlayIntent.queueStarting(s1, now = 1_000L)
        assertFalse(PlayIntent.consume(s1, now = 17_000L))
    }
}
