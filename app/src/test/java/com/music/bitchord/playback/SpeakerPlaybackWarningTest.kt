package com.music.bitchord.playback

import android.media.AudioDeviceInfo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SpeakerPlaybackWarningTest {

    @Before
    fun setUp() {
        SpeakerWarningBus.resetSessionApproval()
        SpeakerWarningBus.registerVolumeRaiseGuard(null)
        SpeakerWarningBus.registerExternalPreviewResumer(null)
    }

    @After
    fun tearDown() {
        SpeakerWarningBus.resetSessionApproval()
        SpeakerWarningBus.registerVolumeRaiseGuard(null)
        SpeakerWarningBus.registerExternalPreviewResumer(null)
    }

    @Test
    fun playStartDecisionSkipsAtZeroVolumeAndWarnsWhenAudibleOnSpeaker() {
        assertFalse(
            shouldWarnBeforeSpeakerPlaybackDecision(
                settingEnabled = true,
                castActive = false,
                isPlayingOrPlayWhenReady = false,
                volumeLevel = 0,
                streamMuted = false,
                activeOutputDeviceType = AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                alreadyApprovedInSession = false,
                isExternalPreview = false,
            ),
        )
        assertTrue(
            shouldWarnBeforeSpeakerPlaybackDecision(
                settingEnabled = true,
                castActive = false,
                isPlayingOrPlayWhenReady = false,
                volumeLevel = 5,
                streamMuted = false,
                activeOutputDeviceType = AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                alreadyApprovedInSession = false,
                isExternalPreview = false,
            ),
        )
    }

    @Test
    fun volumeRaiseFromZeroWarnsWhilePlayingOnSpeaker() {
        assertTrue(
            shouldWarnBeforeVolumeRaiseFromZeroDecision(
                settingEnabled = true,
                castActive = false,
                isPlayingOrPlayWhenReady = true,
                previousVolumeLevel = 0,
                previousStreamMuted = false,
                targetVolumeLevel = 1,
                targetStreamMuted = false,
                activeOutputDeviceType = AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                alreadyApprovedInSession = false,
            ),
        )
        assertTrue(
            shouldWarnBeforeVolumeRaiseFromZeroDecision(
                settingEnabled = true,
                castActive = false,
                isPlayingOrPlayWhenReady = true,
                previousVolumeLevel = 4,
                previousStreamMuted = true,
                targetVolumeLevel = 4,
                targetStreamMuted = false,
                activeOutputDeviceType = AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                alreadyApprovedInSession = false,
            ),
        )
    }

    @Test
    fun volumeRaiseFromZeroDoesNotWarnWhenNotApplicable() {
        // Setting disabled
        assertFalse(
            shouldWarnBeforeVolumeRaiseFromZeroDecision(
                settingEnabled = false,
                castActive = false,
                isPlayingOrPlayWhenReady = true,
                previousVolumeLevel = 0,
                previousStreamMuted = false,
                targetVolumeLevel = 3,
                targetStreamMuted = false,
                activeOutputDeviceType = AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                alreadyApprovedInSession = false,
            ),
        )
        // Not currently playing
        assertFalse(
            shouldWarnBeforeVolumeRaiseFromZeroDecision(
                settingEnabled = true,
                castActive = false,
                isPlayingOrPlayWhenReady = false,
                previousVolumeLevel = 0,
                previousStreamMuted = false,
                targetVolumeLevel = 3,
                targetStreamMuted = false,
                activeOutputDeviceType = AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                alreadyApprovedInSession = false,
            ),
        )
        // Already audible before raising
        assertFalse(
            shouldWarnBeforeVolumeRaiseFromZeroDecision(
                settingEnabled = true,
                castActive = false,
                isPlayingOrPlayWhenReady = true,
                previousVolumeLevel = 2,
                previousStreamMuted = false,
                targetVolumeLevel = 3,
                targetStreamMuted = false,
                activeOutputDeviceType = AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                alreadyApprovedInSession = false,
            ),
        )
        // External Bluetooth headphones connected
        assertFalse(
            shouldWarnBeforeVolumeRaiseFromZeroDecision(
                settingEnabled = true,
                castActive = false,
                isPlayingOrPlayWhenReady = true,
                previousVolumeLevel = 0,
                previousStreamMuted = false,
                targetVolumeLevel = 3,
                targetStreamMuted = false,
                activeOutputDeviceType = AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                alreadyApprovedInSession = false,
            ),
        )
        // Cast active
        assertFalse(
            shouldWarnBeforeVolumeRaiseFromZeroDecision(
                settingEnabled = true,
                castActive = true,
                isPlayingOrPlayWhenReady = true,
                previousVolumeLevel = 0,
                previousStreamMuted = false,
                targetVolumeLevel = 3,
                targetStreamMuted = false,
                activeOutputDeviceType = AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                alreadyApprovedInSession = false,
            ),
        )
        // Already approved in session
        assertFalse(
            shouldWarnBeforeVolumeRaiseFromZeroDecision(
                settingEnabled = true,
                castActive = false,
                isPlayingOrPlayWhenReady = true,
                previousVolumeLevel = 0,
                previousStreamMuted = false,
                targetVolumeLevel = 3,
                targetStreamMuted = false,
                activeOutputDeviceType = AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                alreadyApprovedInSession = true,
            ),
        )
    }

    @Test
    fun busConfirmAppliesPendingVolumeAndRearmsWhenVolumeDropsToZero() {
        val req = SpeakerWarningRequest(
            trigger = SpeakerWarningTrigger.VOLUME_RAISE_FROM_ZERO,
            pendingVolumeLevel = 7,
            volumeRaiseViaHardwareKey = false,
            pausedByService = false,
        )
        assertTrue(SpeakerWarningBus.requestWarning(req, nowUptimeMs = 1_000L))
        var played = false
        var appliedVolume: Int? = null
        var appliedViaKey: Boolean? = null

        SpeakerWarningBus.confirmAndPlay(
            onPlayMain = { played = true },
            onApplyVolume = { level, viaKey ->
                appliedVolume = level
                appliedViaKey = viaKey
            },
        )

        assertFalse(played)
        assertEquals(7, appliedVolume)
        assertEquals(false, appliedViaKey)
        assertTrue(SpeakerWarningBus.isSessionApproved())
        assertNull(SpeakerWarningBus.pendingRequest.value)

        // Dropping media volume back to 0 re-arms the warning.
        SpeakerWarningBus.onMediaVolumeBecameZero()
        assertFalse(SpeakerWarningBus.isSessionApproved())
    }

    @Test
    fun busConfirmResumesPlaybackWhenServicePausedBackgroundVolumeRaise() {
        val req = SpeakerWarningRequest(
            trigger = SpeakerWarningTrigger.VOLUME_RAISE_FROM_ZERO,
            pausedByService = true,
        )
        assertTrue(SpeakerWarningBus.requestWarning(req, nowUptimeMs = 2_000L))
        var played = false
        var volumeApplied = false

        SpeakerWarningBus.confirmAndPlay(
            onPlayMain = { played = true },
            onApplyVolume = { _, _ -> volumeApplied = true },
        )

        assertTrue(played)
        assertFalse(volumeApplied)
        assertTrue(SpeakerWarningBus.isSessionApproved())
    }
}
