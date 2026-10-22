package com.music.bitchord

import com.music.bitchord.data.lyrics.EnhancedLrc
import com.music.bitchord.data.lyrics.LrcParser
import com.music.bitchord.data.lyrics.LyricAlignment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class VoiceTagsTest {

    private val duet = """
        [00:05.00]v1:<00:05.00>I'll <00:05.30>show <00:05.60>you<00:06.00>
        [00:06.00]v2:<00:06.00>Show <00:06.30>me<00:06.80>
        [00:06.40]bg:<00:06.40>(show <00:06.60>me)<00:06.90>
        [00:07.00]v1:<00:07.00>Again<00:07.50>
    """.trimIndent()

    @Test
    fun `voice marks are not shown as words`() {
        for (lines in listOf(LrcParser.parseLrc(duet), EnhancedLrc.parse(duet))) {
            val sung = lines.filterNot { it.isGap }
            sung.forEach { assertFalse(it.text, it.text.contains("v1:") || it.text.contains("v2:") || it.text.contains("bg:")) }
            assertEquals(listOf("I'll show you", "Show me", "Again"), sung.map { it.text })
        }
    }

    @Test
    fun `second voice sits on the other side and backing goes under its line`() {
        for (lines in listOf(LrcParser.parseLrc(duet), EnhancedLrc.parse(duet))) {
            val sung = lines.filterNot { it.isGap }
            assertEquals(
                listOf(LyricAlignment.Start, LyricAlignment.End, LyricAlignment.Start),
                sung.map { it.alignment },
            )
            assertNotNull(sung[1].background)
            assertEquals("(show me)", sung[1].background!!.text)
        }
    }

    @Test
    fun `one voice only reads as ordinary lyrics`() {
        val lines = LrcParser.parseLrc("[03:05.32]v1:<03:05.32>I'll <03:05.63>show <03:06.10>you<03:06.90>")
            .filterNot { it.isGap }
        assertEquals("I'll show you", lines.single().text)
        assertEquals(LyricAlignment.Start, lines.single().alignment)
    }
}
