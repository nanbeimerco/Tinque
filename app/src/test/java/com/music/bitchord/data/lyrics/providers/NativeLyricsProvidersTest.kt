package com.music.bitchord.data.lyrics.providers

import com.music.bitchord.data.lyrics.LyricsFurigana
import com.music.bitchord.data.lyrics.ProviderLyrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeLyricsProvidersTest {

    @Test
    fun allFifteenProvidersRegistered() {
        val expected = listOf(
            "apple_music", "lyricsplus", "better_lyrics", "bini_lrc_red",
            "netease", "qqmusic", "kugou", "petitlyrics", "musixmatch",
            "lrclib", "utanet", "utaten", "jlyric", "genius", "ctc_align",
        )
        assertEquals(15, expected.size)
        for (id in expected) {
            assertTrue("Expected $id to be native", NativeLyricsProviders.isNativeProvider(id))
        }
    }

    @Test
    fun kugouKrcConvertsToEnhancedLrc() {
        val sampleKrc = """
            [ti:Test]
            [12000,3000]<0,500,0>Hello <500,800,0>world<1300,1700,0>!
        """.trimIndent()

        val enhanced = KugouLyricsClient.convertKrcToEnhancedLrc(sampleKrc)
        assertNotNull(enhanced)
        val parsed = ProviderLyrics.parse(enhanced!!)
        assertNotNull(parsed)
        assertEquals(1, parsed!!.size)
        assertTrue(parsed[0].isWordSynced)
        assertEquals(12000L, parsed[0].timeMs)
    }

    @Test
    fun neteaseYrcAndTranslationMergesCorrectly() {
        val sampleYrc = """
            {"t":0,"c":[{"tx":"作曲 : Test"}]}
            [10000,2500](10000,800,0)君(10800,900,0)の名(11700,800,0)は
        """.trimIndent()
        val sampleTrans = "[00:10.00]Your Name"

        val mainLrc = NetEaseLyricsClient.convertYrcToEnhancedLrc(sampleYrc)
        assertNotNull(mainLrc)
        val merged = NativeLyricsHttp.mergeLrcWithTranslation(mainLrc!!, sampleTrans)
        val parsed = ProviderLyrics.parse(merged)
        assertNotNull(parsed)
        assertEquals(2, parsed!!.size)
        assertTrue(parsed[0].isWordSynced)
        assertEquals(parsed[0], parsed[1].timingSource)
        assertEquals("Your Name", parsed[1].text)
    }

    @Test
    fun utatenRubyConvertsToTinqueFurigana() {
        val html = """
            <div class="medium">
                <span class="ruby"><span class="rb">夜</span><span class="rt">よる</span></span>に<span class="ruby"><span class="rb">駆</span><span class="rt">か</span></span>ける<br />
                次の行
            </div>
        """.trimIndent()

        val block = UtaTenLyricsClient.extractUtatenMediumBlock(html)
        assertNotNull(block)
        val clean = WebScrapeHtmlUtils.convertUtatenRubyToFurigana(block!!)
        assertEquals("夜((よる))に駆((か))ける\n次の行", clean)

        val annotations = LyricsFurigana.annotations(clean.lines().first())
        assertEquals(2, annotations.size)
        assertEquals("夜", annotations[0].base)
        assertEquals("よる", annotations[0].reading)
        assertEquals("駆", annotations[1].base)
        assertEquals("か", annotations[1].reading)
    }

    @Test
    fun geniusContainerExcludesHeaderAndCleansBr() {
        val html = """
            <div data-lyrics-container="true" class="Lyrics__Container">
                <div data-exclude-from-selection="true">100 Contributors</div>
                First line<br/>Second line
            </div>
        """.trimIndent()
        val extracted = GeniusLyricsClient.extractGeniusContainers(html)
        assertEquals("First line\nSecond line", extracted)
    }

    @Test
    fun ctcGeneratorAlignsPlainLyrics() {
        val plain = "一行目の歌詞です\n二行目の歌詞です\n\n三行目のサビです"
        val lrc = CtcLrcGenerator.generateAutoSyncedLrc(plain, 180)
        val parsed = ProviderLyrics.parse(lrc)
        assertNotNull(parsed)
        assertEquals(3, parsed!!.size)
        assertTrue(parsed[0].timeMs > 0L)
        assertTrue(parsed[1].timeMs > parsed[0].timeMs)
        assertTrue(parsed[2].timeMs > parsed[1].timeMs)
    }
}
