package com.music.bitchord.playback

import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import com.music.bitchord.data.TrackLog

/**
 * Why playback started or stopped, one line each.
 *
 * Written to the app's own log (what "Copy log" hands over) and to logcat as
 * well, even in a release build: a pause nobody asked for has to be readable
 * over adb at the moment it happens, because the app's log does not survive
 * the reinstall that would be needed to read it any other way.
 */
internal object PlaybackLog {
    private const val TAG = "TinquePlay"

    /** Who sent the last play/pause; only believed for a moment after. */
    @Volatile private var lastCommandSource: String? = null
    @Volatile private var lastCommandAt = 0L

    fun noteCommandFrom(source: String) {
        lastCommandSource = source
        lastCommandAt = android.os.SystemClock.elapsedRealtime()
        note("command from $source")
    }

    fun lastCommandFrom(): String {
        val source = lastCommandSource
        return if (source != null && android.os.SystemClock.elapsedRealtime() - lastCommandAt < 1_000L) {
            " by $source"
        } else {
            " (from inside the app)"
        }
    }

    fun note(message: String, about: String? = null) {
        Log.i(TAG, message)
        if (about != null) TrackLog.i("Play", message, about) else TrackLog.i("Play", message)
    }

    fun playWhenReadyReason(reason: Int): String = when (reason) {
        Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST -> "command"
        Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS -> "audio focus lost (player)"
        Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY -> "output disconnected (becoming noisy)"
        Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE -> "remote"
        Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM -> "end of track"
        Player.PLAY_WHEN_READY_CHANGE_REASON_SUPPRESSED_TOO_LONG -> "suppressed too long"
        else -> "reason $reason"
    }

    fun stateName(state: Int): String = when (state) {
        Player.STATE_IDLE -> "idle"
        Player.STATE_BUFFERING -> "buffering"
        Player.STATE_READY -> "ready"
        Player.STATE_ENDED -> "ended"
        else -> "state $state"
    }

    fun focusChangeName(change: Int): String = when (change) {
        android.media.AudioManager.AUDIOFOCUS_GAIN -> "gain"
        android.media.AudioManager.AUDIOFOCUS_LOSS -> "loss"
        android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> "loss (transient)"
        android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> "loss (can duck)"
        else -> "change $change"
    }

    /** Who sent a command: the package, and whether it came in as a media button. */
    fun describe(controller: MediaSession.ControllerInfo): String =
        "${controller.packageName} (uid ${controller.uid})"

    fun describeMediaButton(intent: Intent): String {
        val key = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
        } ?: return "media button (no key)"
        val action = when (key.action) {
            KeyEvent.ACTION_DOWN -> "down"
            KeyEvent.ACTION_UP -> "up"
            else -> "action ${key.action}"
        }
        return "media button ${KeyEvent.keyCodeToString(key.keyCode)} $action device=${key.deviceId} source=${key.source}"
    }
}
