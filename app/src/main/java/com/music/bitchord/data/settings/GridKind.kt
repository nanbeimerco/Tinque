package com.music.bitchord.data.settings

/**
 * The grid pages whose size the user can change (pinch, or Settings).
 * Each keeps its own column count; [defaultColumns] matches the old fixed look.
 */
enum class GridKind(val key: String, val defaultColumns: Int) {
    SONGS("songs", 2),
    ALBUMS("albums", 2),
    ARTISTS("artists", 3),
    LIBRARY("library", 2),
    COLLECTION_SONGS("collection_songs", 2);

    companion object {
        const val MIN_COLUMNS = 2
        const val MAX_COLUMNS = 5
    }
}
