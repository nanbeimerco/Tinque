package com.music.bitchord

import android.media.AudioDeviceInfo
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.settings.LandscapePlayerLayout
import com.music.bitchord.data.settings.PlayerControl
import com.music.bitchord.data.settings.PortraitPlayerLayout
import com.music.bitchord.playback.SpeakerWarningBus
import com.music.bitchord.playback.SpeakerWarningRequest
import com.music.bitchord.playback.isExternalWarningResetDeviceType
import com.music.bitchord.playback.shouldWarnBeforeSpeakerPlaybackDecision
import com.music.bitchord.ui.components.GlassMenuPlacement
import com.music.bitchord.ui.player.PlayerLayout
import com.music.bitchord.ui.player.needsLandscapeSplitClosePill
import com.music.bitchord.ui.player.playerLayoutFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassMenuPlacementTest {

    @Test
    fun upperAnchorOpensDownwardWithZeroPivot() {
        val result = GlassMenuPlacement.verticalPositionPx(
            wide = false,
            anchorTop = 100,
            anchorBottom = 148,
            anchorCenterY = 124,
            menuHeight = 360,
            containerHeight = 2000,
            topBound = 80,
            bottomBound = 1920,
            gap = 12,
            acrossLift = 60,
        )
        assertEquals(0f, result.pivot, 1e-4f)
        // First row aligns on anchor: 124 - 60 = 64 -> clamped to topBound 80
        assertEquals(80, result.y)
    }

    @Test
    fun middleAnchorBalancesVerticallyAroundButton() {
        val result = GlassMenuPlacement.verticalPositionPx(
            wide = false,
            anchorTop = 900,
            anchorBottom = 948,
            anchorCenterY = 924,
            menuHeight = 400,
            containerHeight = 2000,
            topBound = 80,
            bottomBound = 1920,
            gap = 12,
            acrossLift = 60,
        )
        // anchorFrac = (924 - 80) / 1840 ~= 0.4587 -> smooth(0.14..0.76) ~= 0.522
        assertTrue("Expected middle pivot near 0.5, got ${result.pivot}", result.pivot in 0.4f..0.65f)
        val center = result.y + 200
        // Balanced opening keeps menu centre reasonably close to anchor centre instead of hanging far below.
        assertTrue("Expected balanced menu centre near 924, got $center", kotlin.math.abs(center - 924) < 80)
    }

    @Test
    fun lowerAnchorOpensUpwardAndRespectsSafeBottomBound() {
        val result = GlassMenuPlacement.verticalPositionPx(
            wide = false,
            anchorTop = 1740,
            anchorBottom = 1788,
            anchorCenterY = 1764,
            menuHeight = 360,
            containerHeight = 2000,
            topBound = 80,
            bottomBound = 1880,
            gap = 12,
            acrossLift = 60,
        )
        assertEquals(1f, result.pivot, 1e-4f)
        // 1764 + 60 - 360 = 1464 -> well above bottomBound
        assertEquals(1464, result.y)
        assertTrue(result.y + 360 <= 1880)
    }

    @Test
    fun wideStripPlacesAboveWhenRoomOrFallsBackBelow() {
        val fitsAbove = GlassMenuPlacement.verticalPositionPx(
            wide = true,
            anchorTop = 900,
            anchorBottom = 960,
            anchorCenterY = 930,
            menuHeight = 200,
            containerHeight = 2000,
            topBound = 80,
            bottomBound = 1920,
            gap = 16,
            acrossLift = 60,
        )
        assertEquals(684, fitsAbove.y)
        assertEquals(1f, fitsAbove.pivot, 1e-4f)

        val tooCloseToTop = GlassMenuPlacement.verticalPositionPx(
            wide = true,
            anchorTop = 150,
            anchorBottom = 210,
            anchorCenterY = 180,
            menuHeight = 200,
            containerHeight = 2000,
            topBound = 80,
            bottomBound = 1920,
            gap = 16,
            acrossLift = 60,
        )
        assertEquals(226, tooCloseToTop.y)
        assertEquals(0f, tooCloseToTop.pivot, 1e-4f)
    }

    @Test
    fun phoneLandscapeSplitLayoutSelectedBeforeDockedFallbackOnCompactPhones() {
        val phoneSplit = playerLayoutFor(
            windowWidth = 800.dp,
            windowHeight = 390.dp,
            landscapeLayout = LandscapePlayerLayout.SIDE_BY_SIDE,
            portraitLayout = PortraitPlayerLayout.PHONE,
            phoneLandscapeSplit = true,
        )
        assertEquals(PlayerLayout.LANDSCAPE_SPLIT, phoneSplit)

        val tabletLandscape = playerLayoutFor(
            windowWidth = 960.dp,
            windowHeight = 620.dp,
            landscapeLayout = LandscapePlayerLayout.TWO_COLUMN,
            portraitLayout = PortraitPlayerLayout.PHONE,
            phoneLandscapeSplit = true,
        )
        assertEquals(PlayerLayout.TWO_COLUMN, tabletLandscape)
    }

    @Test
    fun landscapeSplitClosePillAppearsOnlyWhenOpenPanelToggleMissing() {
        assertFalse(
            needsLandscapeSplitClosePill(
                landscapeSplit = true,
                lyricsOpen = true,
                queueOpen = false,
                visibleControls = setOf(PlayerControl.LYRICS, PlayerControl.QUEUE),
            ),
        )
        assertTrue(
            needsLandscapeSplitClosePill(
                landscapeSplit = true,
                lyricsOpen = true,
                queueOpen = false,
                visibleControls = setOf(PlayerControl.QUEUE),
            ),
        )
        assertFalse(
            needsLandscapeSplitClosePill(
                landscapeSplit = true,
                lyricsOpen = false,
                queueOpen = true,
                visibleControls = setOf(PlayerControl.LYRICS, PlayerControl.QUEUE),
            ),
        )
        assertTrue(
            needsLandscapeSplitClosePill(
                landscapeSplit = true,
                lyricsOpen = false,
                queueOpen = true,
                visibleControls = setOf(PlayerControl.LYRICS),
            ),
        )
    }

    @Test
    fun speakerWarningGuardsOnlyUnapprovedAudibleSpeakerStarts() {
        SpeakerWarningBus.resetSessionApproval()
        assertFalse(
            shouldWarnBeforeSpeakerPlaybackDecision(
                settingEnabled = false,
                castActive = false,
                isPlayingOrPlayWhenReady = false,
                volumeLevel = 5,
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
        assertFalse(
            shouldWarnBeforeSpeakerPlaybackDecision(
                settingEnabled = true,
                castActive = false,
                isPlayingOrPlayWhenReady = false,
                volumeLevel = 5,
                streamMuted = false,
                activeOutputDeviceType = AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                alreadyApprovedInSession = false,
                isExternalPreview = false,
            ),
        )
        assertFalse(
            shouldWarnBeforeSpeakerPlaybackDecision(
                settingEnabled = true,
                castActive = false,
                isPlayingOrPlayWhenReady = true,
                volumeLevel = 5,
                streamMuted = false,
                activeOutputDeviceType = AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                alreadyApprovedInSession = false,
                isExternalPreview = false,
            ),
        )
        assertTrue(
            shouldWarnBeforeSpeakerPlaybackDecision(
                settingEnabled = true,
                castActive = true,
                isPlayingOrPlayWhenReady = true,
                volumeLevel = 5,
                streamMuted = false,
                activeOutputDeviceType = AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                alreadyApprovedInSession = false,
                isExternalPreview = true,
            ),
        )

        var resumed = false
        SpeakerWarningBus.requestWarning(SpeakerWarningRequest(), cooldownMs = 0L, nowUptimeMs = 100L)
        SpeakerWarningBus.confirmAndPlay { resumed = true }
        assertTrue(resumed)
        assertTrue(SpeakerWarningBus.isSessionApproved())

        assertTrue(isExternalWarningResetDeviceType(AudioDeviceInfo.TYPE_WIRED_HEADPHONES))
        assertFalse(isExternalWarningResetDeviceType(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER))

        SpeakerWarningBus.resetSessionApproval()
        assertFalse(SpeakerWarningBus.isSessionApproved())
    }
}
