package com.music.bitchord.data.stats

import com.music.bitchord.data.model.Song

/**
 * How playback of a track was initiated, used by [HeatStore.finishListen]
 * to apply a mild active-selection multiplier on positive listening points.
 */
enum class PlayOrigin(val factor: Double) {
    /** Listener explicitly tapped this track to start a queue (first track only). */
    PICKED(1.2),

    /** Natural queue advance or repeat. */
    SEQUENTIAL(1.0),

    /** Shuffle auto-advance or shuffle button queue start. */
    SHUFFLE(1.0),

    /** Recommended via AutoPlay (`MediaItem.fromAutoplay`). */
    AUTOPLAY(1.0),

    /** Manual next/previous/seek transition inside an existing queue. */
    SKIPPED_TO(1.0),

    /** Restored session, widget, quick tile, crossfade adoption, or unknown source. */
    UNKNOWN(1.0),
}

/**
 * Passes one-shot UI track selection intent to [com.music.bitchord.playback.PlaybackService]
 * in the same process (matching [PlaySource]).
 */
object PlayIntent {
    private const val TTL_MS = 15_000L

    @Volatile
    private var pickedKey: String? = null

    @Volatile
    private var pickedAt: Long = 0L

    @Volatile
    private var nextIsShuffleStart: Boolean = false

    @Synchronized
    fun markShuffleStart() {
        nextIsShuffleStart = true
    }

    @Synchronized
    fun queueStarting(picked: Song?, now: Long = System.currentTimeMillis()) {
        if (nextIsShuffleStart) {
            nextIsShuffleStart = false
            pickedKey = null
            pickedAt = 0L
            return
        }
        val key = picked?.heatKey()?.takeIf { it.isNotBlank() }
        pickedKey = key
        pickedAt = if (key != null) now else 0L
    }

    @Synchronized
    fun consume(song: Song, now: Long = System.currentTimeMillis()): Boolean {
        val key = pickedKey ?: return false
        val at = pickedAt
        pickedKey = null
        pickedAt = 0L
        if (at <= 0L || now - at > TTL_MS) return false
        return song.heatKey() == key
    }

    @Synchronized
    internal fun clear() {
        pickedKey = null
        pickedAt = 0L
        nextIsShuffleStart = false
    }
}
