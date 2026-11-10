package com.music.bitchord.data.stats

import com.music.bitchord.data.model.Song

/**
 * Which playlist the queue was started from, so a playlist's heat can come
 * from listening to *it* rather than only from its tracks' own heat.
 *
 * A screen that starts a queue from a playlist (or Favourites) calls [fromPlaylist]
 * just before it hands the songs to the player; every queue start then calls
 * [queueStarted], which takes that up or, for any other queue, clears it. A
 * listen is credited only when its track is one of the playlist's, so songs
 * queued in from elsewhere on top of it do not count.
 */
object PlaySource {
    const val FAVORITES_KEY = "pl:favorites"

    fun playlistKey(id: String): String = "pl:$id"

    private class Source(val key: String, val members: Set<String>)

    @Volatile private var pending: Source? = null
    @Volatile private var current: Source? = null

    /** The next queue is [songs] of the playlist [key] ([playlistKey] or [FAVORITES_KEY]). */
    fun fromPlaylist(key: String, songs: List<Song>) {
        pending = Source(key, songs.mapTo(HashSet()) { it.heatKey() })
    }

    /** A new queue has been handed to the player. */
    fun queueStarted() {
        current = pending
        pending = null
    }

    /** The playlist key a finished listen of [song] belongs to, if any. */
    fun creditFor(song: Song): String? = current?.takeIf { song.heatKey() in it.members }?.key
}
