package com.music.bitchord.playback

import android.media.AudioDeviceInfo
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

enum class SpeakerWarningTrigger {
    PLAY_START,
    VOLUME_RAISE_FROM_ZERO,
}

data class SpeakerWarningRequest(
    val id: Long = nextRequestId.incrementAndGet(),
    val isExternalPreview: Boolean = false,
    val trigger: SpeakerWarningTrigger = SpeakerWarningTrigger.PLAY_START,
    val pendingVolumeLevel: Int? = null,
    val volumeRaiseViaHardwareKey: Boolean = false,
    val pausedByService: Boolean = false,
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

/**
 * Pure decision helper determining whether raising media volume from 0/muted while
 * playback is already running should be intercepted by the built-in speaker warning.
 */
internal fun shouldWarnBeforeVolumeRaiseFromZeroDecision(
    settingEnabled: Boolean,
    castActive: Boolean,
    isPlayingOrPlayWhenReady: Boolean,
    previousVolumeLevel: Int,
    previousStreamMuted: Boolean,
    targetVolumeLevel: Int,
    targetStreamMuted: Boolean,
    activeOutputDeviceType: Int?,
    alreadyApprovedInSession: Boolean,
): Boolean {
    if (!settingEnabled) return false
    if (castActive) return false
    if (!isPlayingOrPlayWhenReady) return false
    if (alreadyApprovedInSession) return false
    val wasZeroOrMuted = previousVolumeLevel <= 0 || previousStreamMuted
    val becomesAudible = targetVolumeLevel > 0 && !targetStreamMuted
    if (!wasZeroOrMuted || !becomesAudible) return false
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

    @Volatile
    private var volumeRaiseGuard: ((targetVolume: Int, viaHardwareKey: Boolean) -> Boolean)? = null

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

    /** Re-arms the speaker warning whenever media volume drops to 0 or muted. */
    fun onMediaVolumeBecameZero() {
        sessionApproved = false
    }

    fun registerExternalPreviewResumer(resumer: (() -> Unit)?) {
        externalPreviewResumer = resumer
    }

    fun registerVolumeRaiseGuard(guard: ((targetVolume: Int, viaHardwareKey: Boolean) -> Boolean)?) {
        volumeRaiseGuard = guard
    }

    /**
     * Called by foreground UI controls (in-app volume slider and hardware volume-up key)
     * before raising STREAM_MUSIC above 0. Returns true if the raise was intercepted and
     * a warning dialog was requested so the caller should skip applying the volume change.
     */
    fun interceptVolumeRaise(targetVolume: Int, viaHardwareKey: Boolean): Boolean =
        volumeRaiseGuard?.invoke(targetVolume, viaHardwareKey) == true

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
        confirmAndPlay(onPlayMain = onPlayMain, onApplyVolume = null)
    }

    fun confirmAndPlay(
        onPlayMain: () -> Unit,
        onApplyVolume: ((targetVolume: Int?, viaHardwareKey: Boolean) -> Unit)?,
    ) {
        val current = _pendingRequest.value
        sessionApproved = true
        _pendingRequest.value = null
        when {
            current?.trigger == SpeakerWarningTrigger.VOLUME_RAISE_FROM_ZERO -> {
                if (current.pausedByService) {
                    if (current.isExternalPreview) {
                        externalPreviewResumer?.invoke() ?: onPlayMain()
                    } else {
                        onPlayMain()
                    }
                } else {
                    onApplyVolume?.invoke(current.pendingVolumeLevel, current.volumeRaiseViaHardwareKey)
                }
            }
            current?.isExternalPreview == true -> {
                externalPreviewResumer?.invoke() ?: onPlayMain()
            }
            else -> onPlayMain()
        }
    }

    internal fun approveSessionForTest() {
        sessionApproved = true
        _pendingRequest.value = null
    }
}
