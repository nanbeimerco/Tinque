package com.music.bitchord.playback

import android.content.Context
import com.music.bitchord.data.innertube.StreamResolver
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.EqualizerSettings
import com.music.bitchord.data.settings.LevelingMode
import com.music.bitchord.data.settings.SongGainStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * The one gain the playback chain's last stage applies to a song: levelling
 * (standard ReplayGain, or the attenuate-only kind) plus the listener's own
 * per-song trim, for the output device in use.
 *
 * The playback service asks for it whenever the song, the device or any of the
 * settings behind it change, and publishes the playing song's figures in
 * [current] for the player's trim popup and the levelling settings to read.
 */
object TrackGain {

    /** How a song's gain was arrived at. All figures in dB except [lufs]. */
    data class Breakdown(
        val songKey: String,
        val title: String,
        val artist: String,
        val deviceKey: String,
        val deviceLabel: String,
        /** Whether levelling of either kind is on (and so which trim is in use). */
        val levelled: Boolean,
        /** Whether attenuate-only levelling is in effect for this song. */
        val attenuateOnly: Boolean,
        val levelDb: Float,
        val trimDb: Float,
        val trimMinDb: Float,
        val trimMaxDb: Float,
        val totalDb: Float,
        /** The song's own loudness, when known. */
        val lufs: Double?,
        val calibrating: Boolean,
        /**
         * The song's loudness is not known yet, so its levelling is a guess
         * that errs quiet (see [ASSUMED_LOUD_LUFS]). Worked out again once it is.
         */
        val provisional: Boolean = false,
    )

    /**
     * What a song of unknown loudness is taken to be under attenuate-only
     * levelling: about as loud as anything is mastered, so it is cut at least as
     * far as it needs and plays too quiet rather than too loud.
     */
    const val ASSUMED_LOUD_LUFS = -5.0

    /**
     * Each queued song's total gain, by media id, worked out ahead of its audio
     * so the processor can switch to it at the exact sample the song starts.
     */
    val gains = ConcurrentHashMap<String, Float>()

    /**
     * The loudness each song is being levelled by for the play in progress, by
     * media id, so nothing that happens mid-song — its analysis finishing, a
     * recompute for any other reason — can move it louder. Settings the listener
     * changes still apply at once (they are worked from the same loudness). It
     * may only move quieter mid-song. Cleared for a song when another starts.
     */
    val pins = ConcurrentHashMap<String, Double>()

    /** Asks the playback service for a song's gain that is not in [gains] yet. */
    @Volatile
    var onGainNeeded: ((String) -> Unit)? = null

    /**
     * What to start a song at whose gain never arrived: as if it were very loud
     * (about -5 LUFS) and untrimmed, so a wrong guess is too quiet, never too loud.
     */
    fun fallbackDb(): Float {
        calibrationOffsetDb.value?.let { return it }
        if (AppSettings.bitPerfectPlayback.value) return 0f
        val device = EqualizerSettings.activeDeviceKeyFlow.value
        val reference = SongGainStore.reference(device)
        return if (AppSettings.replayGainEnabled.value &&
            AppSettings.levelingMode.value == LevelingMode.ATTENUATE_ONLY && reference != null
        ) {
            minOf(0f, (reference + SongGainStore.lift(device) - ASSUMED_LOUD_LUFS).toFloat())
        } else if (AppSettings.replayGainEnabled.value || AppSettings.songTrimEnabled.value) {
            SongGainStore.TRIM_MIN_DB
        } else {
            0f
        }
    }

    /** The playing song's figures; null with nothing playing. */
    val current = MutableStateFlow<Breakdown?>(null)

    /**
     * While the reference is being set: the cut the listener is trying, applied
     * to whatever plays instead of everything else. Null otherwise.
     */
    val calibrationOffsetDb = MutableStateFlow<Float?>(null)

    /** Bumped when a song measured during reference setup has its figure, so it can be used at once. */
    val calibrationMeasured = MutableStateFlow(0)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val measuring = ConcurrentHashMap.newKeySet<String>()

    fun isLocal(song: Song): Boolean =
        song.localUri != null || song.localPath != null || "://" in song.videoId

    /** The song's integrated loudness in LUFS, when it is known. */
    suspend fun lufsFor(context: Context, song: Song): Double? =
        if (isLocal(song)) {
            ReplayGainReader.trackLufs(context, song)
        } else {
            // YouTube's figure is how far the track sits above its own -14 LUFS target.
            StreamResolver.loudnessDbFor(song.videoId)?.let { -14.0 + it }
        }

    /** Measures a local song in the background, once, so its next play can be levelled. */
    fun measureLater(context: Context, song: Song, onMeasured: (() -> Unit)? = null) {
        if (!isLocal(song)) return
        val key = SongGainStore.songKey(song)
        if (!measuring.add(key)) return
        val appContext = context.applicationContext
        scope.launch {
            try {
                if (ReplayGainScanner.measureOne(appContext, song)) onMeasured?.invoke()
            } finally {
                measuring.remove(key)
            }
        }
    }

    /** A token that changes whenever anything [compute] reads (other than the song) does. */
    fun settingsToken(): String = listOf(
        EqualizerSettings.activeDeviceKeyFlow.value,
        SongGainStore.revision.value,
        calibrationOffsetDb.value,
        calibrationMeasured.value,
        AppSettings.replayGainEnabled.value,
        AppSettings.replayGainAlbumMode.value,
        AppSettings.replayGainPreampDb.value,
        AppSettings.replayGainPreventClipping.value,
        AppSettings.levelingMode.value,
        AppSettings.songTrimEnabled.value,
        AppSettings.bitPerfectPlayback.value,
    ).joinToString("|")

    suspend fun compute(context: Context, song: Song, mediaId: String? = null, pin: Boolean = false): Breakdown {
        val device = EqualizerSettings.activeDeviceKeyFlow.value
        val deviceLabel = EqualizerSettings.activeDeviceLabel.value
        val key = SongGainStore.songKey(song)
        val local = isLocal(song)
        // Only read when something uses it: reading a file's tags on every song
        // change is wasted work with levelling off.
        val needLufs = calibrationOffsetDb.value != null ||
            (AppSettings.replayGainEnabled.value && AppSettings.levelingMode.value == LevelingMode.ATTENUATE_ONLY)
        val lufs = if (needLufs) runCatching { lufsFor(context, song) }.getOrNull() else null
        val pinned = mediaId?.let { pins[it] }
        // What this play is levelled by: the pinned figure, unless the real one
        // is now known and asks for more cut (only ever quieter mid-song).
        val levelBy = when {
            pinned == null -> lufs
            lufs != null && lufs > pinned -> lufs
            else -> pinned
        }

        var provisional = false
        fun result(levelled: Boolean, attenuate: Boolean, level: Float, trim: Float, min: Float, max: Float, calibrating: Boolean = false, total: Float = level + trim) =
            Breakdown(key, song.title, song.artist, device, deviceLabel, levelled, attenuate, level, trim, min, max, total, lufs, calibrating, provisional)

        if (AppSettings.bitPerfectPlayback.value) return result(false, false, 0f, 0f, 0f, 0f)
        calibrationOffsetDb.value?.let { offset ->
            if (lufs == null) measureLater(context, song) { calibrationMeasured.value++ }
            return result(false, false, 0f, 0f, 0f, 0f, calibrating = true, total = offset)
        }

        val levelled = AppSettings.replayGainEnabled.value
        var level = 0f
        var attenuate = false
        if (levelled) {
            when (AppSettings.levelingMode.value) {
                LevelingMode.STANDARD -> if (local) {
                    level = ReplayGainReader.readGainDb(
                        context = context,
                        song = song,
                        useAlbumGain = AppSettings.replayGainAlbumMode.value,
                        preampDb = AppSettings.replayGainPreampDb.value,
                        preventClipping = AppSettings.replayGainPreventClipping.value,
                    )
                }
                LevelingMode.ATTENUATE_ONLY -> {
                    val reference = SongGainStore.reference(device)
                    if (reference != null) {
                        attenuate = true
                        val loudness = levelBy ?: run {
                            // Not known yet: played as if very loud (too quiet at
                            // worst, never too loud), measured meanwhile, and
                            // levelled properly from its next play.
                            measureLater(context, song)
                            ASSUMED_LOUD_LUFS
                        }
                        provisional = lufs == null
                        if (pin && mediaId != null) pins[mediaId] = loudness
                        level = minOf(0f, (reference + SongGainStore.lift(device) - loudness).toFloat())
                    }
                }
            }
        }

        if (!AppSettings.songTrimEnabled.value) return result(levelled, attenuate, level, 0f, 0f, 0f)
        val min = SongGainStore.TRIM_MIN_DB
        // Over attenuate-only levelling the trim may give back what levelling took,
        // and no more: the total never goes above 0 dB.
        val max = if (attenuate) -level else 0f
        val trim = SongGainStore.trim(device, levelled, key).coerceIn(min, max)
        val total = if (attenuate) minOf(0f, level + trim) else level + trim
        return result(levelled, attenuate, level, trim, min, max, total = total)
    }
}
