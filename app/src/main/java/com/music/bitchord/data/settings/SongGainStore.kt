package com.music.bitchord.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.Normalizer
import java.util.Locale

/**
 * What the listener has set by ear, kept per output device:
 *
 * - the per-song trim (a cut, in dB, on top of whatever levelling applies),
 *   held apart for "levelling on" and "levelling off" because the same number
 *   means a different thing over each;
 * - the attenuate-only levelling's reference loudness (LUFS at full system
 *   volume) and its lift (how far the whole of it has been eased back).
 *
 * Every write bumps [revision], which is what the playback service watches to
 * apply a change to the song that is playing.
 */
object SongGainStore {
    private const val PREFS = "song_gain"
    private var prefs: SharedPreferences? = null

    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    /** The most a song can be trimmed down. */
    const val TRIM_MIN_DB = -12f

    /** How far the levelling's overall level can go, either way. */
    const val LIFT_MAX_DB = 15f
    const val LIFT_MIN_DB = -15f

    fun init(context: Context) {
        if (prefs == null) prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    /**
     * A song's identity for its trim. A file on the device by its title, artist
     * and length, so it survives being moved or re-scanned; a streamed track by
     * its id.
     */
    fun songKey(song: Song): String {
        val local = song.localUri != null || song.localPath != null || "://" in song.videoId
        return if (local) {
            "f:${normal(song.title)}|${normal(song.artist)}|${seconds(song.durationText)}"
        } else {
            "v:${song.videoId}"
        }
    }

    fun trim(device: String, levelled: Boolean, songKey: String): Float =
        prefs?.getFloat(trimKey(device, levelled, songKey), 0f) ?: 0f

    fun setTrim(device: String, levelled: Boolean, songKey: String, db: Float) {
        val p = prefs ?: return
        val rounded = Math.round(db * 100f) / 100f
        p.edit().apply {
            if (rounded == 0f) remove(trimKey(device, levelled, songKey)) else putFloat(trimKey(device, levelled, songKey), rounded)
        }.apply()
        _revision.value++
    }

    /** The reference loudness for [device], or null when it has not been set. */
    fun reference(device: String): Float? =
        prefs?.takeIf { it.contains("ref|$device") }?.getFloat("ref|$device", 0f)

    fun setReference(device: String, lufs: Float?) {
        val p = prefs ?: return
        p.edit().apply { if (lufs == null) remove("ref|$device") else putFloat("ref|$device", lufs) }.apply()
        _revision.value++
    }

    fun lift(device: String): Float = prefs?.getFloat("lift|$device", 0f) ?: 0f

    fun setLift(device: String, db: Float) {
        val p = prefs ?: return
        p.edit().putFloat("lift|$device", (Math.round(db * 100f) / 100f).coerceIn(LIFT_MIN_DB, LIFT_MAX_DB)).apply()
        _revision.value++
    }

    private fun trimKey(device: String, levelled: Boolean, songKey: String) =
        "t|$device|${if (levelled) "n" else "r"}|$songKey"

    private fun normal(raw: String): String =
        Normalizer.normalize(raw, Normalizer.Form.NFKC).lowercase(Locale.ROOT).trim().replace(Regex("\\s+"), " ")

    private fun seconds(text: String?): Int {
        if (text.isNullOrBlank()) return 0
        return text.split(':').fold(0) { acc, part -> acc * 60 + (part.trim().toIntOrNull() ?: 0) }
    }
}
