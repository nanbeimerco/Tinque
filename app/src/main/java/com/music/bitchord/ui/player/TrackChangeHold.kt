package com.music.bitchord.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * How long after a track change the player's slower-moving inputs are held:
 * as long as the skip arrows take to roll.
 */
internal const val TRACK_CHANGE_HOLD_MS = 400L

/**
 * [value], but held as it stood at the moment [trackKey] changed until
 * [holdMs] has passed, then let through.
 *
 * A skip makes the lyrics and the queue arrive in a string of updates over the
 * next few hundred milliseconds, and each one rebuilt the whole player while
 * the skip animation ran. Held, they land in one update once it has finished.
 * The first track the player opens on is not held.
 */
@Composable
internal fun <T> rememberHeldThroughTrackChange(value: T, trackKey: Any?, holdMs: Long = TRACK_CHANGE_HOLD_MS): T {
    val firstKey = remember { trackKey }
    val atChange = remember(trackKey) { value }
    var released by remember(trackKey) { mutableStateOf(trackKey == firstKey) }
    LaunchedEffect(trackKey) {
        if (!released) {
            delay(holdMs)
            released = true
        }
    }
    return if (released) value else atChange
}

/**
 * [value] only if it was last set while [trackKey] was the track playing:
 * otherwise it is left over from the previous track (the lookup for this one
 * has not even reset it yet) and [stale] is returned instead.
 */
@Composable
internal fun <T> freshForTrack(value: T, trackKey: Any?, stale: T): T {
    val setFor = remember(value) { trackKey }
    return if (setFor == trackKey) value else stale
}
