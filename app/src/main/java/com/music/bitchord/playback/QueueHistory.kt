package com.music.bitchord.playback

import com.music.bitchord.data.model.Song

/** Number of completed queue entries retained behind the current song. */
internal const val MAX_QUEUE_HISTORY = 25

/** How many entries at the head of the live queue are older than its history window. */
internal fun queueHistoryTrimCount(currentIndex: Int): Int =
    (currentIndex - MAX_QUEUE_HISTORY).coerceAtLeast(0)

/**
 * Queue list position to open at, keeping the current song visible with a
 * small amount of recent playback history above it. AutoPlay's section label
 * occupies an extra LazyColumn row only after its boundary.
 */
internal fun queueInitialVisibleIndex(currentIndex: Int, autoplayStart: Int): Int {
    val firstVisible = (currentIndex - 2).coerceAtLeast(0)
    return firstVisible + if (firstVisible >= autoplayStart) 1 else 0
}

/**
 * Entries bypassed when a listener chooses a later row directly from the queue.
 *
 * The current song is deliberately not included: it has actually been current,
 * while the rows between it and [targetIndex] have never played. Backward jumps
 * remove nothing, so revisiting history leaves the songs after it in the queue.
 */
internal fun skippedByQueueJump(currentIndex: Int, targetIndex: Int): IntRange? =
    if (targetIndex > currentIndex + 1) (currentIndex + 1) until targetIndex else null

/**
 * Starts a new queue at the row the listener selected.
 *
 * Rows before it have not played in this session, so putting them behind the
 * current item would make the player present them as history. They can still be
 * reached by starting a new queue from one of those rows later.
 */
internal fun <T> queueStartingAt(items: List<T>, startIndex: Int): List<T> {
    if (items.isEmpty()) return emptyList()
    return items.drop(startIndex.coerceIn(items.indices))
}

/**
 * The rows before the one the listener tapped, for repeat-all.
 *
 * [queueStartingAt] leaves them out so they never pose as history. But with
 * repeat-all on, the end of a playlist should lead back to its first track, not
 * to the tapped one. So they are kept here and go on the *end* of the queue
 * only while repeat-all is on: after the last row, the loop carries on from
 * the top of the playlist in its own order, and with repeat off the queue
 * still stops at the playlist's end.
 *
 * Set by `playSongs` on the UI side and read by the playback service, which
 * run in the same process.
 */
internal object QueueWrap {
    /** Songs before the tapped row, in playlist order. */
    @Volatile
    var head: List<Song> = emptyList()
        private set

    /** Media id of the tapped row, so a queue replaced some other way is not wrapped. */
    @Volatile
    var anchor: String? = null
        private set

    /** Whether [head] is currently on the end of the live queue. */
    @Volatile
    var appended: Boolean = false

    fun set(head: List<Song>, anchor: String?, appended: Boolean) {
        this.head = head
        this.anchor = anchor
        this.appended = appended
    }

    fun clear() = set(emptyList(), null, false)
}
