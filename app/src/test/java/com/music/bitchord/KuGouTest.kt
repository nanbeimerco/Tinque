package com.music.bitchord

import com.music.bitchord.data.lyrics.ProviderLyrics
import com.music.bitchord.data.lyrics.providers.KugouLyricsClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater

/**
 * Tests for [KugouLyricsClient]'s KRC decryption and conversion into enhanced LRC.
 */
class KuGouTest {

    private val krcKey = intArrayOf(
        64, 71, 97, 119, 94, 50, 116, 71, 81, 54, 49, 45, 206, 210, 110, 105,
    )

    private val sampleKrc = """
        [id:${'$'}00000000]
        [ti:Heartbreak Anniversary]
        [ar:Giveon]
        [al:Heartbreak Anniversary]
        [by:]
        [12360,2500]<0,500,0>Ooh
        [15900,3800]<0,1200,0>Balloons <1200,800,0>are <2000,1800,0>deflated
        [19730,1000]Plain line without word tags
    """.trimIndent()

    @Test
    fun `drops the unstamped header tags when converting krc`() {
        val converted = KugouLyricsClient.convertKrcToEnhancedLrc(sampleKrc)
        assertNotNull(converted)
        val out = converted!!
        assertFalse(out.contains("[id:"))
        assertFalse(out.contains("[ti:"))
        assertFalse(out.contains("[by:]"))
    }

    @Test
    fun `converts krc word and line timing into parseable enhanced lrc`() {
        val converted = KugouLyricsClient.convertKrcToEnhancedLrc(sampleKrc)!!
        assertTrue(converted.contains("[00:12.36]<00:12.36>Ooh<00:14.86>"))
        assertTrue(converted.contains("[00:15.90]<00:15.90>Balloons <00:17.10>are <00:17.90>deflated<00:19.70>"))
        assertTrue(converted.contains("[00:19.73]Plain line without word tags"))

        val parsed = ProviderLyrics.parse(converted)
        assertNotNull(parsed)
        val sung = parsed!!.filterNot { it.isGap }
        assertEquals(3, sung.size)
        assertTrue(sung[1].isWordSynced)
        assertEquals(15_900L, sung[1].timeMs)
    }

    @Test
    fun `empty or header-only krc yields null`() {
        assertNull(KugouLyricsClient.convertKrcToEnhancedLrc(""))
        assertNull(KugouLyricsClient.convertKrcToEnhancedLrc("[ti:Only Header]\n[ar:Artist]"))
    }

    @Test
    fun `decrypts krc1 xor-deflated payload and rejects invalid inputs`() {
        val plainBytes = sampleKrc.toByteArray(Charsets.UTF_8)
        val deflater = Deflater()
        deflater.setInput(plainBytes)
        deflater.finish()
        val deflatedOut = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (!deflater.finished()) {
            val count = deflater.deflate(buf)
            if (count > 0) deflatedOut.write(buf, 0, count)
        }
        deflater.end()

        val compressed = deflatedOut.toByteArray()
        val krcBytes = ByteArray(4 + compressed.size)
        krcBytes[0] = 'k'.code.toByte()
        krcBytes[1] = 'r'.code.toByte()
        krcBytes[2] = 'c'.code.toByte()
        krcBytes[3] = '1'.code.toByte()
        for (i in compressed.indices) {
            krcBytes[i + 4] = (compressed[i].toInt() xor krcKey[i % 16]).toByte()
        }

        assertEquals(sampleKrc, KugouLyricsClient.decryptKrcBytes(krcBytes))
        assertNull(KugouLyricsClient.decryptKrcBytes("bad".toByteArray()))
        assertNull(KugouLyricsClient.decryptKrcBytes("krc2payload".toByteArray()))
    }
}
