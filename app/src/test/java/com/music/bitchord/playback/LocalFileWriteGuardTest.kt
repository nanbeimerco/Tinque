package com.music.bitchord.playback

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LocalFileWriteGuardTest {

    @Test
    fun `matches by normalized MediaStore content uri across primary and external volumes`() {
        assertTrue(
            LocalFileWriteGuard.matches(
                target = LocalFileWriteGuard.Target(
                    mediaId = null,
                    uri = "content://media/external_primary/audio/media/4139",
                    path = null,
                ),
                itemMediaId = "other-id",
                itemUri = "content://media/external/audio/media/4139",
                itemExtraLocalUri = null,
                itemExtraLocalPath = null,
            ),
        )
    }

    @Test
    fun `matches by file uri against raw local path`() {
        val temp = File.createTempFile("tinque-guard", ".flac").apply { deleteOnExit() }
        assertTrue(
            LocalFileWriteGuard.matches(
                target = LocalFileWriteGuard.Target(path = temp.absolutePath),
                itemMediaId = "local-1",
                itemUri = temp.toURI().toString(),
                itemExtraLocalUri = null,
                itemExtraLocalPath = null,
            ),
        )
        assertFalse(
            LocalFileWriteGuard.matches(
                target = LocalFileWriteGuard.Target(path = temp.absolutePath + ".other"),
                itemMediaId = "local-1",
                itemUri = temp.toURI().toString(),
                itemExtraLocalUri = null,
                itemExtraLocalPath = null,
            ),
        )
    }

    @Test
    fun `aroundWrite calls before and afterWrite even on failure and serializes concurrent writes`() = runBlocking {
        val events = mutableListOf<String>()
        val previous = LocalFileWriteGuard.handler
        try {
            LocalFileWriteGuard.handler = object : LocalFileWriteGuard.Handler {
                override suspend fun beforeWrite(target: LocalFileWriteGuard.Target): Any {
                    events += "before:${target.mediaId}"
                    return "token-${target.mediaId}"
                }

                override suspend fun afterWrite(target: LocalFileWriteGuard.Target, token: Any?) {
                    events += "after:${target.mediaId}:$token"
                }
            }

            // Failure path still runs afterWrite with token.
            runCatching {
                LocalFileWriteGuard.aroundWrite(LocalFileWriteGuard.Target(mediaId = "err")) {
                    events += "write:err"
                    error("boom")
                }
            }
            assertEquals(
                listOf("before:err", "write:err", "after:err:token-err"),
                events,
            )

            // Concurrent writes do not overlap.
            events.clear()
            listOf("a", "b").map { id ->
                async {
                    LocalFileWriteGuard.aroundWrite(LocalFileWriteGuard.Target(mediaId = id)) {
                        events += "write:$id"
                        delay(20)
                        id
                    }
                }
            }.awaitAll()

            assertEquals(6, events.size)
            assertEquals("before:", events[0].substring(0, 7))
            val firstId = events[0].substringAfter("before:")
            assertEquals("write:$firstId", events[1])
            assertEquals("after:$firstId:token-$firstId", events[2])
        } finally {
            LocalFileWriteGuard.handler = previous
        }
    }
}
