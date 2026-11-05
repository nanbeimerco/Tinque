package com.music.bitchord.data.lyrics

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Lyrics the listener picked for a song from the catalog ("use for this song"):
 * kept in Tinque only, the file untouched, and used ahead of anything else —
 * the file's own lyrics and every online source — until they are cleared.
 */
object LyricsOverrideStore {
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("lyrics_overrides", Context.MODE_PRIVATE)
    }

    /** The key a song's override is kept under: its file when it has one, else its id. */
    fun keyFor(videoId: String, localUri: String?): String = localUri?.takeIf { it.isNotBlank() } ?: videoId

    data class Override(val sourceId: String, val sourceLabel: String, val raw: String)

    fun get(key: String): Override? {
        val json = prefs?.getString(key, null) ?: return null
        return runCatching {
            val obj = JSONObject(json)
            Override(obj.getString("id"), obj.getString("label"), obj.getString("raw"))
        }.getOrNull()
    }

    fun set(key: String, override: Override) {
        val obj = JSONObject().put("id", override.sourceId).put("label", override.sourceLabel).put("raw", override.raw)
        prefs?.edit()?.putString(key, obj.toString())?.apply()
    }

    fun clear(key: String) {
        prefs?.edit()?.remove(key)?.apply()
    }

    /** The override's lines, parsed the same way as anything a source returns. */
    fun lines(key: String): Pair<Override, List<LyricLine>>? {
        val override = get(key) ?: return null
        val lines = ProviderLyrics.parse(override.raw)?.map { it.withDecodedEntities() }?.withBackgroundVocals()
            ?: return null
        return override to lines
    }
}
