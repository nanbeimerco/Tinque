package com.music.bitchord.playback.smart

import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Coordinates background CPU/GC-heavy audio analysis ([LocalHomeAi]) with live
 * playback transitions and interactive vocal probes so track startup, buffering
 * recovery, and UI responsiveness are not hit by concurrent codec/ONNX storms.
 */
object PlaybackAnalysisGate {

    private const val STARTUP_QUIET_WINDOW_MS = 2_000L
    private const val POLL_INTERVAL_MS = 250L

    private val lastDisruptionMs = AtomicLong(Long.MIN_VALUE / 4)
    private val bufferingActive = AtomicBoolean(false)
    private val activeInteractiveProbes = AtomicInteger(0)

    private fun nowMs(): Long = System.nanoTime() / 1_000_000L

    /** Marks a track change or seek so background analysis pauses during startup buffering. */
    fun notePlaybackDisruption() {
        lastDisruptionMs.set(nowMs())
    }

    /** Tracks whether the active player is currently in `STATE_BUFFERING`. */
    fun setBuffering(isBuffering: Boolean) {
        val wasBuffering = bufferingActive.getAndSet(isBuffering)
        if (isBuffering && !wasBuffering) {
            lastDisruptionMs.set(nowMs())
        }
    }

    /** Marks an interactive Autoplay vocal probe active so background backlog analysis yields. */
    fun beginInteractiveProbe() {
        activeInteractiveProbes.incrementAndGet()
    }

    fun endInteractiveProbe() {
        while (true) {
            val current = activeInteractiveProbes.get()
            if (current <= 0) return
            if (activeInteractiveProbes.compareAndSet(current, current - 1)) return
        }
    }

    /** True when background analysis should pause to protect live playback or interactive probes. */
    fun shouldPauseBackgroundAnalysis(): Boolean {
        if (bufferingActive.get() || activeInteractiveProbes.get() > 0) return true
        val elapsed = nowMs() - lastDisruptionMs.get()
        return elapsed in 0L until STARTUP_QUIET_WINDOW_MS
    }

    /** Suspends while playback is buffering, settling right after a track change, or probing. */
    suspend fun awaitQuietWindow() {
        while (shouldPauseBackgroundAnalysis()) {
            val remainingStartupMs = if (!bufferingActive.get() && activeInteractiveProbes.get() <= 0) {
                val elapsed = nowMs() - lastDisruptionMs.get()
                if (elapsed in 0L until STARTUP_QUIET_WINDOW_MS) {
                    (STARTUP_QUIET_WINDOW_MS - elapsed).coerceAtLeast(50L)
                } else {
                    break
                }
            } else {
                POLL_INTERVAL_MS
            }
            delay(remainingStartupMs.coerceAtMost(POLL_INTERVAL_MS))
        }
    }
}
