package com.music.bitchord.ui.player

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerTopStripLayoutTest {

    @Test
    fun dockedAlwaysUsesDockedTopPad() {
        assertEquals(
            12.dp,
            resolvePlayerTopStripHeight(
                docked = true,
                showDragHandle = true,
                hasRadioName = false,
                hasStatusBarPadding = true,
            ),
        )
        assertEquals(
            12.dp,
            resolvePlayerTopStripHeight(
                docked = true,
                showDragHandle = false,
                hasRadioName = true,
                hasStatusBarPadding = false,
            ),
        )
    }

    @Test
    fun visibleDragHandleKeepsStandardDismissStripHeight() {
        assertEquals(
            32.dp,
            resolvePlayerTopStripHeight(
                docked = false,
                showDragHandle = true,
                hasRadioName = false,
                hasStatusBarPadding = true,
            ),
        )
    }

    @Test
    fun hiddenDragHandlePreservesDismissStripHeightToAvoidPunchHoleOverlap() {
        assertEquals(
            32.dp,
            resolvePlayerTopStripHeight(
                docked = false,
                showDragHandle = false,
                hasRadioName = false,
                hasStatusBarPadding = true,
            ),
        )
        assertEquals(
            32.dp,
            resolvePlayerTopStripHeight(
                docked = false,
                showDragHandle = false,
                hasRadioName = false,
                hasStatusBarPadding = false,
            ),
        )
        assertEquals(
            32.dp,
            resolvePlayerTopStripHeight(
                docked = false,
                showDragHandle = false,
                hasRadioName = true,
                hasStatusBarPadding = true,
            ),
        )
    }

    @Test
    fun landscapeSplitHiddenDragHandlePreservesArtworkTopInset() {
        assertEquals(
            20.dp to 0.dp,
            resolveLandscapeSplitTopInsets(showDragHandle = true, hasRadioName = false),
        )
        assertEquals(
            0.dp to 20.dp,
            resolveLandscapeSplitTopInsets(showDragHandle = false, hasRadioName = false),
        )
        assertEquals(
            20.dp to 0.dp,
            resolveLandscapeSplitTopInsets(showDragHandle = false, hasRadioName = true),
        )
    }
}
