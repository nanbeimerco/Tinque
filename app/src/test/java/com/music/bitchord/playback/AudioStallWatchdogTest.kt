package com.music.bitchord.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class AudioStallWatchdogTest {

    private class FakePlayerState {
        var mediaId: String = "track-1"
        var index: Int = 0
        var positionMs: Long = 136_997L
        var durationMs: Long = 245_960L
        var playWhenReady: Boolean = true
        var suppressionReason: Int = Player.PLAYBACK_SUPPRESSION_REASON_NONE
        val events = mutableListOf<String>()

        fun asPlayer(): Player {
            val mediaItem = MediaItem.Builder().setMediaId(mediaId).build()
            return Proxy.newProxyInstance(
                Player::class.java.classLoader,
                arrayOf(Player::class.java),
            ) { _, method, args ->
                when (method.name) {
                    "getCurrentMediaItem" -> mediaItem
                    "getCurrentMediaItemIndex" -> index
                    "getCurrentPosition" -> positionMs
                    "getDuration" -> durationMs
                    "getPlayWhenReady" -> playWhenReady
                    "getPlaybackSuppressionReason" -> suppressionReason
                    "seekTo" -> {
                        if (args != null && args.size == 2) {
                            val targetPos = args[1] as Long
                            events += "seekTo(${args[0]},$targetPos)"
                            positionMs = targetPos
                        }
                        null
                    }
                    "stop" -> {
                        events += "stop"
                        null
                    }
                    "prepare" -> {
                        events += "prepare"
                        null
                    }
                    else -> defaultValue(method.returnType)
                }
            } as Player
        }

        private fun defaultValue(type: Class<*>): Any? = when (type) {
            java.lang.Boolean.TYPE -> false
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0f
            java.lang.Double.TYPE -> 0.0
            else -> null
        }
    }

    @Test
    fun `first stall offsets seek target by 1ms so Media3 does not ignore same-ms seek`() {
        var nowMs = 1_000L
        val logs = mutableListOf<String>()
        val watchdog = AudioStallWatchdog(clock = { nowMs }) { logs += it }
        val state = FakePlayerState().apply { positionMs = 136_997L }
        val player = state.asPlayer()

        repeat(4) {
            watchdog.onPlaybackStateChanged(player, Player.STATE_READY, busy = false)
            nowMs += 110L
            watchdog.onPlaybackStateChanged(player, Player.STATE_BUFFERING, busy = false)
        }

        assertEquals(listOf("seekTo(0,136996)"), state.events)
        assertEquals(1, logs.size)
        assertTrue(logs[0].contains("136996ms"))
    }

    @Test
    fun `first stall at position zero seeks to 1ms instead of 0ms`() {
        var nowMs = 1_000L
        val watchdog = AudioStallWatchdog(clock = { nowMs }) {}
        val state = FakePlayerState().apply { positionMs = 0L }
        val player = state.asPlayer()

        repeat(4) {
            watchdog.onPlaybackStateChanged(player, Player.STATE_READY, busy = false)
            nowMs += 110L
            watchdog.onPlaybackStateChanged(player, Player.STATE_BUFFERING, busy = false)
        }

        assertEquals(listOf("seekTo(0,1)"), state.events)
    }

    @Test
    fun `backward seek across flips does not trigger recovery`() {
        var nowMs = 1_000L
        val logs = mutableListOf<String>()
        val watchdog = AudioStallWatchdog(clock = { nowMs }) { logs += it }
        val state = FakePlayerState().apply { positionMs = 136_997L }
        val player = state.asPlayer()

        repeat(3) {
            watchdog.onPlaybackStateChanged(player, Player.STATE_READY, busy = false)
            nowMs += 110L
            watchdog.onPlaybackStateChanged(player, Player.STATE_BUFFERING, busy = false)
        }

        // User seeks backward by 21 seconds before the 4th flip.
        state.positionMs = 115_671L
        watchdog.onPlaybackStateChanged(player, Player.STATE_READY, busy = false)
        nowMs += 110L
        watchdog.onPlaybackStateChanged(player, Player.STATE_BUFFERING, busy = false)

        assertTrue(state.events.isEmpty())
        assertTrue(logs.isEmpty())
    }

    @Test
    fun `second stall escalates to stop seek prepare to reopen the source`() {
        var nowMs = 1_000L
        val watchdog = AudioStallWatchdog(clock = { nowMs }) {}
        val state = FakePlayerState().apply { positionMs = 136_997L }
        val player = state.asPlayer()

        // First stall triggers seek-flush.
        repeat(4) {
            watchdog.onPlaybackStateChanged(player, Player.STATE_READY, busy = false)
            nowMs += 110L
            watchdog.onPlaybackStateChanged(player, Player.STATE_BUFFERING, busy = false)
        }
        assertEquals(listOf("seekTo(0,136996)"), state.events)
        state.events.clear()

        // Advance past 10s cooldown and stall again.
        nowMs += 11_000L
        repeat(4) {
            watchdog.onPlaybackStateChanged(player, Player.STATE_READY, busy = false)
            nowMs += 110L
            watchdog.onPlaybackStateChanged(player, Player.STATE_BUFFERING, busy = false)
        }

        assertEquals(listOf("stop", "seekTo(0,136996)", "prepare"), state.events)
    }
}
