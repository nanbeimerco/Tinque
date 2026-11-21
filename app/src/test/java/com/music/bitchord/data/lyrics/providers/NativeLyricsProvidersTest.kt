package com.music.bitchord.data.lyrics.providers

import com.music.bitchord.data.lyrics.LyricsFurigana
import com.music.bitchord.data.lyrics.ProviderLyrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeLyricsProvidersTest {

    @Test
    fun allSixteenProvidersRegistered() {
        val expected = listOf(
            "bini_lrc_red", "lrcapi", "netease", "lrclib",
            "lyricsplus", "kugou", "utaten", "jlyric",
            "petitlyrics", "apple_music", "better_lyrics", "qqmusic",
            "musixmatch", "utanet", "genius", "ctc_align",
        )
        assertEquals(16, expected.size)
        for (id in expected) {
            assertTrue("Expected $id to be native", NativeLyricsProviders.isNativeProvider(id))
        }
    }

    @Test
    fun biniAndLrcRedParseLiveSchemas() {
        val biniJson = """
            {
              "results": [
                {
                  "id": "b1",
                  "track_name": "夜に駆ける",
                  "artist_name": "YOASOBI",
                  "album_name": "THE BOOK",
                  "duration": 261,
                  "timing_type": "word",
                  "lyricsUrl": "https://bini.example/ttml/1.ttml"
                }
              ]
            }
        """.trimIndent()
        val biniHits = BiniAndLrcRedClient.parseBiniHits(biniJson, "夜に駆ける", "YOASOBI", 261)
        assertEquals(1, biniHits.size)
        assertEquals("https://bini.example/ttml/1.ttml", biniHits[0].lyricsUrl)
        assertTrue(biniHits[0].isWord)
        assertEquals(261, biniHits[0].durationSec)

        val lrcRedJson = """
            {
              "hits": [
                {
                  "isrc": "JPU901903771",
                  "title": "夜に駆ける",
                  "artist": "YOASOBI",
                  "duration": 261
                }
              ]
            }
        """.trimIndent()
        val lrcRedHits = BiniAndLrcRedClient.parseLrcRedHits(lrcRedJson, "夜に駆ける", "YOASOBI", 261)
        assertEquals(1, lrcRedHits.size)
        assertEquals("JPU901903771", lrcRedHits[0].isrc)
        assertEquals("https://lrc.red/s/JPU901903771.ttml", lrcRedHits[0].lyricsUrl)
    }

    @Test
    fun lrcApiParsesTtmlAndKugouLrc() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:itunes="http://music.apple.com/lyric-ttml-internal" itunes:timing="Word">
              <body><div>
                <p begin="10.0s" end="12.0s"><span begin="10.0s" end="11.0s">Hello</span> <span begin="11.0s" end="12.0s">world</span></p>
              </div></body>
            </tt>
        """.trimIndent()
        val json = org.json.JSONArray().apply {
            put(
                org.json.JSONObject().apply {
                    put("id", "kugou_1")
                    put("title", "Hello")
                    put("artist", "Artist")
                    put("duration", 180)
                    put("lrc", "[00:10.00]Hello world")
                },
            )
            put(
                org.json.JSONObject().apply {
                    put("id", "apple_1")
                    put("title", "Hello")
                    put("artist", "Artist")
                    put("duration", 180)
                    put("lrc_ttml", ttml)
                },
            )
        }.toString()

        val items = LrcApiClient.parseJsonApiItems(json, "Hello", "Artist", 180)
        assertEquals(2, items.size)
        assertEquals("apple_1", items[0].id)
        assertNotNull(items[0].ttml)
        assertNotNull(items[1].lrc)

        val parsedWord = ProviderLyrics.parse(items[0].ttml!!)
        assertNotNull(parsedWord)
        assertTrue(parsedWord!!.any { it.isWordSynced })
    }

    @Test
    fun neteaseCleansJsonCreditsAndMergesOffsetTranslation() {
        val raw = """
            {"t":0,"c":[{"tx":"作词 : Ayase"}]}
            [00:10.00]沈むように溶けてゆくように
            [00:15.00]二人だけの空が広がる夜に
        """.trimIndent()
        val cleaned = NetEaseLyricsClient.cleanNetEaseLrc(raw)
        assertNotNull(cleaned)
        assertFalse(cleaned!!.contains("\"tx\""))
        assertTrue(cleaned.contains("[00:10.00]沈むように溶けてゆくように"))

        val offsetTrans = "[00:10.35]Like sinking, like melting away\n[00:15.20]In a night where a sky for just the two of us spreads"
        val merged = NativeLyricsHttp.mergeLrcWithTranslation(cleaned, offsetTrans)
        val parsed = ProviderLyrics.parse(merged)
        assertNotNull(parsed)
        val withTrans = parsed!!.filter { it.timingSource != null }
        assertEquals(2, withTrans.size)
        assertEquals("Like sinking, like melting away", withTrans[0].text)
    }

    @Test
    fun jlyricExtractsRelativeLPaths() {
        val html = """
            <div id="mnb">
              <div class="bdy">
                <p class="mid"><a href="/artist/a055041/l04c817.html">夜に駆ける</a></p>
                <p class="sml"><a href="/artist/a055041/">YOASOBI</a></p>
                <p class="mid"><a href="/artist/a055041/l04c818.html">群青</a></p>
              </div>
            </div>
        """.trimIndent()
        val urls = JLyricClient.extractSongUrlsFromSearchHtml(html)
        assertEquals(
            listOf(
                "https://j-lyric.net/artist/a055041/l04c817.html",
                "https://j-lyric.net/artist/a055041/l04c818.html",
            ),
            urls,
        )
    }

    @Test
    fun lrclibRanksSyncedAndDurationBest() {
        val json = org.json.JSONArray().apply {
            put(
                org.json.JSONObject().apply {
                    put("id", 1)
                    put("trackName", "Night")
                    put("artistName", "Artist")
                    put("duration", 95)
                    put("plainLyrics", "Unsynced wrong duration")
                },
            )
            put(
                org.json.JSONObject().apply {
                    put("id", 2)
                    put("trackName", "Night")
                    put("artistName", "Artist")
                    put("duration", 200)
                    put("syncedLyrics", "[00:10.00]Matched duration synced")
                },
            )
        }.toString()

        val scored = LrcLibClient.parseSearchArray(json, "Night", "Artist", 200)
        assertEquals(2, scored.size)
        val best = scored.maxByOrNull { it.second }!!.first
        assertEquals("lrclib_2", best.id)
        assertNotNull(best.syncedLyrics)
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
        val sung = parsed!!.filterNot { it.isGap }
        assertEquals(1, sung.size)
        assertTrue(sung[0].isWordSynced)
        assertEquals(12000L, sung[0].timeMs)
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
        val sung = parsed!!.filterNot { it.isGap }
        assertEquals(2, sung.size)
        assertTrue(sung[0].isWordSynced)
        assertEquals(sung[0], sung[1].timingSource)
        assertEquals("Your Name", sung[1].text)
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
        val sung = parsed!!.filterNot { it.isGap }
        assertEquals(3, sung.size)
        assertTrue(sung[0].timeMs > 0L)
        assertTrue(sung[1].timeMs > sung[0].timeMs)
        assertTrue(sung[2].timeMs > sung[1].timeMs)
    }

    @Test
    fun petitLyricsConvertWsyAndParsePetitXmlHandlesWordSyncAndFullPlain() {
        val wsyXml = """
            <wsy>
              <line>
                <linestring>銀の鍵</linestring>
                <word><starttime>1000</starttime><endtime>1250</endtime><wordstring>銀</wordstring></word>
                <word><starttime>1250</starttime><endtime>1500</endtime><wordstring>の</wordstring></word>
                <word><starttime>1500</starttime><endtime>2000</endtime><wordstring>鍵</wordstring></word>
              </line>
              <line>
                <linestring>I&apos;ll try &amp; go</linestring>
                <word><starttime>2500</starttime><endtime>2700</endtime><wordstring>I&apos;l</wordstring></word>
                <word><starttime>2700</starttime><endtime>2900</endtime><wordstring>l </wordstring></word>
                <word><starttime>2900</starttime><endtime>3200</endtime><wordstring>try</wordstring></word>
                <word><starttime>3200</starttime><endtime>3300</endtime><wordstring> </wordstring></word>
                <word><starttime>3300</starttime><endtime>3600</endtime><wordstring>&amp;</wordstring></word>
                <word><starttime>3600</starttime><endtime>4000</endtime><wordstring> go</wordstring></word>
              </line>
            </wsy>
        """.trimIndent()

        val enhancedLrc = PetitLyricsClient.convertWsyToEnhancedLrc(wsyXml)
        assertNotNull(enhancedLrc)
        val parsed = ProviderLyrics.parse(enhancedLrc!!)
        assertNotNull(parsed)
        val sung = parsed!!.filterNot { it.isGap }
        assertEquals(2, sung.size)
        assertEquals("銀の鍵", sung[0].text)
        assertTrue(sung[0].words.isNotEmpty())
        assertEquals(1000L, sung[0].timeMs)
        assertTrue(sung[1].text.contains("I'll"))
        assertTrue(sung[1].text.contains("&"))
        assertTrue(sung[1].timeMs > sung[0].timeMs)
        assertTrue(sung[1].words.size >= 3)

        val encoder = java.util.Base64.getEncoder()
        val wsyB64 = encoder.encodeToString(wsyXml.toByteArray(Charsets.UTF_8))
        val plainFull = (1..40).joinToString("\n") { "Full lyric line $it" }
        val plainB64 = encoder.encodeToString(plainFull.toByteArray(Charsets.UTF_8))
        val xmlResponse = """
            <response>
              <songs>
                <song>
                  <lyricsId>101</lyricsId>
                  <title>Open Invitation</title>
                  <artist>CINDY</artist>
                  <album>Angel Touch</album>
                  <duration>214752</duration>
                  <lyricsType>3</lyricsType>
                  <availableLyricsType>3</availableLyricsType>
                  <lyricsData>$wsyB64</lyricsData>
                </song>
                <song>
                  <lyricsId>102</lyricsId>
                  <title>Open Invitation</title>
                  <artist>CINDY</artist>
                  <duration>214752</duration>
                  <lyricsType>1</lyricsType>
                  <availableLyricsType>1</availableLyricsType>
                  <lyricsData>$plainB64</lyricsData>
                </song>
              </songs>
            </response>
        """.trimIndent()

        val candidates = PetitLyricsClient.parsePetitXml(xmlResponse, "Fallback", "Artist")
        assertEquals(2, candidates.size)
        assertEquals("petitlyrics_101", candidates[0].id)
        assertEquals(214, candidates[0].durationSeconds)
        assertNotNull(candidates[0].syncedLyrics)
        assertEquals("petitlyrics_102", candidates[1].id)
        assertEquals(null, candidates[1].syncedLyrics)
        assertEquals(40, candidates[1].plainLyrics!!.lines().size)
    }
}
