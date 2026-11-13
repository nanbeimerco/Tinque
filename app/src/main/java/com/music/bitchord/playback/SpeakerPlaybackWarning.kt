package com.music.bitchord.playback

import android.media.AudioDeviceInfo
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

data class SpeakerWarningRequest(
    val id: Long = nextRequestId.incrementAndGet(),
    val isExternalPreview: Boolean = false,
) {
    private companion object {
        val nextRequestId = AtomicLong(0L)
    }
}

/**
 * Pure decision helper determining whether starting playback should be intercepted
 * by the built-in speaker confirmation warning.
 */
internal fun shouldWarnBeforeSpeakerPlaybackDecision(
    settingEnabled: Boolean,
    castActive: Boolean,
    isPlayingOrPlayWhenReady: Boolean,
    volumeLevel: Int,
    streamMuted: Boolean,
    activeOutputDeviceType: Int?,
    alreadyApprovedInSession: Boolean,
    isExternalPreview: Boolean,
): Boolean {
    if (!settingEnabled) return false
    if (!isExternalPreview && castActive) return false
    if (!isExternalPreview && isPlayingOrPlayWhenReady) return false
    if (alreadyApprovedInSession) return false
    if (volumeLevel <= 0 || streamMuted) return false
    return activeOutputDeviceType == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
}

/** Returns true when adding/removing [deviceType] represents an external audio output route change. */
internal fun isExternalWarningResetDeviceType(deviceType: Int): Boolean =
    deviceType != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER &&
        deviceType != AudioDeviceInfo.TYPE_BUILTIN_EARPIECE &&
        deviceType != AudioDeviceInfo.TYPE_TELEPHONY

/**
 * Coordinates speaker playback warning requests from [PlaybackService] to the active UI layer
 * (`SpeakerWarningHost`) without coupling service code to Compose.
 */
object SpeakerWarningBus {
    private val _pendingRequest = MutableStateFlow<SpeakerWarningRequest?>(null)
    val pendingRequest: StateFlow<SpeakerWarningRequest?> = _pendingRequest.asStateFlow()

    @Volatile
    private var sessionApproved = false

    @Volatile
    private var lastRequestUptimeMs = 0L

    @Volatile
    private var externalPreviewResumer: (() -> Unit)? = null

    private val foregroundHosts = AtomicInteger(0)

    fun onForegroundHostChanged(attached: Boolean) {
        if (attached) {
            foregroundHosts.incrementAndGet()
        } else {
            val updated = foregroundHosts.decrementAndGet()
            if (updated < 0) foregroundHosts.set(0)
        }
    }

    fun isForegroundUiActive(): Boolean = foregroundHosts.get() > 0

    fun isSessionApproved(): Boolean = sessionApproved

    fun resetSessionApproval() {
        sessionApproved = false
        _pendingRequest.value = null
    }

    fun registerExternalPreviewResumer(resumer: (() -> Unit)?) {
        externalPreviewResumer = resumer
    }

    fun requestWarning(
        request: SpeakerWarningRequest,
        cooldownMs: Long = 800L,
        nowUptimeMs: Long = SystemClock.uptimeMillis(),
    ): Boolean {
        val existing = _pendingRequest.value
        if (existing != null &&
            lastRequestUptimeMs > 0L &&
            nowUptimeMs - lastRequestUptimeMs < cooldownMs
        ) {
            return false
        }
        lastRequestUptimeMs = nowUptimeMs
        _pendingRequest.value = request
        return true
    }

    fun dismiss() {
        _pendingRequest.value = null
    }

    fun confirmAndPlay(onPlayMain: () -> Unit) {
        val current = _pendingRequest.value
        sessionApproved = true
        _pendingRequest.value = null
        if (current?.isExternalPreview == true) {
            externalPreviewResumer?.invoke() ?: onPlayMain()
        } else {
            onPlayMain()
        }
    }

    internal fun approveSessionForTest() {
        sessionApproved = true
        _pendingRequest.value = null
    }
}
