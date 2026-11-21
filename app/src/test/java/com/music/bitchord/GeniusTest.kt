package com.music.bitchord

import com.music.bitchord.data.lyrics.LrcParser
import com.music.bitchord.data.lyrics.providers.GeniusLyricsClient
import com.music.bitchord.data.lyrics.providers.WebScrapeHtmlUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeniusTest {

    private val sampleGeniusHtml = """
        <!DOCTYPE html>
        <html>
        <head><title>Queen - Bohemian Rhapsody Lyrics | Genius Lyrics</title></head>
        <body>
          <div data-lyrics-container="true" class="Lyrics__Container">
            <div data-exclude-from-selection="true" class="LyricsHeader__Container">
              <button>523 Contributors</button>
              <div class="SongBioPreview__Container">Song Bio</div>
            </div>
            [Intro]<br>
            Is this the real life?<br>
            Is this just fantasy?<br>
            <br>
            [Verse 1]<br>
            Mama, just killed a man<br>
            Put a gun against his head, pulled my trigger, now he's dead<br>
          </div>
          <div data-lyrics-container="true" class="Lyrics__Container">
            [Chorus]<br>
            Mama, life had just begun<br>
            But now I've gone and thrown it all away<br>
          </div>
        </body>
        </html>
    """.trimIndent()

    @Test
    fun `detects section headers correctly`() {
        assertTrue(LrcParser.isSectionHeader("[Verse 1]"))
        assertTrue(LrcParser.isSectionHeader("[Chorus]"))
        assertTrue(LrcParser.isSectionHeader("[Guitar Solo]"))
        assertTrue(LrcParser.isSectionHeader("[Bridge: Freddie Mercury]"))
        assertFalse(LrcParser.isSectionHeader("Is this the real life?"))
        assertFalse(LrcParser.isSectionHeader("[short"))
        assertFalse(LrcParser.isSectionHeader("]short["))
    }

    @Test
    fun `parses genius html and extracts clean lyrics and sections`() {
        val extracted = GeniusLyricsClient.extractGeniusContainers(sampleGeniusHtml)
        assertNotNull(extracted)
        val lines = extracted!!.lines()

        // Headers excluded
        assertFalse(lines.any { it.contains("Contributors") })
        assertFalse(lines.any { it.contains("Song Bio") })

        // Sections and lyrics present across multiple containers
        assertTrue(lines.contains("[Intro]"))
        assertTrue(lines.contains("Is this the real life?"))
        assertTrue(lines.contains("Is this just fantasy?"))
        assertTrue(lines.contains("[Verse 1]"))
        assertTrue(lines.contains("Mama, just killed a man"))
        assertTrue(lines.contains("[Chorus]"))
        assertTrue(lines.contains("Mama, life had just begun"))
        assertTrue(lines.contains("But now I've gone and thrown it all away"))
    }

    @Test
    fun `cleans html breaks scripts and entities to plain lyrics`() {
        val raw = """
            <style>.x{color:red}</style>
            <script>alert(1)</script>
            First&nbsp;line &amp; more<br/>
            <p>Second &#39;line&#39;</p>
        """.trimIndent()
        val cleaned = WebScrapeHtmlUtils.htmlToCleanLyrics(raw)

        assertFalse(cleaned.contains("color:red"))
        assertFalse(cleaned.contains("alert(1)"))
        assertEquals("First line & more\nSecond 'line'", cleaned)
    }
}
