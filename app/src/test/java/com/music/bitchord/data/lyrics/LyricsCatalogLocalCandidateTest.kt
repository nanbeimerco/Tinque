package com.music.bitchord.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LyricsCatalogLocalCandidateTest {

    private val embeddedSource = LyricsSource(
        id = "local_embedded",
        label = "Embedded lyrics",
        wordSynced = false,
    )

    @Test
    fun localCandidateParsesPlainEmbeddedLyricsEvenWhenContainingErrorWord() {
        val raw = """
            System error in the midnight rain
            Running through the neon glow
        """.trimIndent()

        val candidate = LyricsCatalog.localCandidate(
            id = "local:embedded",
            source = embeddedSource,
            title = "Midnight",
            artist = "Artist",
            album = null,
            durationSec = 210,
            raw = raw,
        )

        assertNotNull(candidate)
        assertEquals(LyricsFormat.PLAIN, candidate!!.format)
        assertEquals(2, candidate.lines.size)
        assertEquals(1f, candidate.match, 0.0001f)
    }

    @Test
    fun localCandidateDetectsLineSyncedEmbeddedLrc() {
        val raw = """
            [ti:Sample]
            [00:12.34]First line of the song
            [00:18.90]Second line of the song
        """.trimIndent()

        val candidate = LyricsCatalog.localCandidate(
            id = "local:embedded",
            source = embeddedSource,
            title = "Sample",
            artist = "Artist",
            album = "Album",
            durationSec = 180,
            raw = raw,
        )

        assertNotNull(candidate)
        assertEquals(LyricsFormat.LINE, candidate!!.format)
        assertEquals(2, candidate.lines.count { it.text.isNotBlank() })
    }

    @Test
    fun localCandidateDetectsWordSyncedEnhancedLrc() {
        val raw = "[00:10.00]<00:10.00>Hel<00:10.40>lo <00:10.80>world"

        val candidate = LyricsCatalog.localCandidate(
            id = "local:embedded",
            source = embeddedSource,
            title = "Hello",
            artist = "Artist",
            album = null,
            durationSec = 120,
            raw = raw,
        )

        assertNotNull(candidate)
        assertEquals(LyricsFormat.WORD, candidate!!.format)
    }

    @Test
    fun localCandidateReturnsNullForBlankInput() {
        assertNull(
            LyricsCatalog.localCandidate(
                id = "local:embedded",
                source = embeddedSource,
                title = "Empty",
                artist = "Artist",
                album = null,
                durationSec = 120,
                raw = "   \n  ",
            ),
        )
    }
}
