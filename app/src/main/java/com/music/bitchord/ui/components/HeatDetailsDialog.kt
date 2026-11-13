package com.music.bitchord.ui.components

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.stats.HeatInfo
import com.music.bitchord.data.stats.HeatStore
import com.music.bitchord.data.stats.heatKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A song with no heat yet, for the details to show their dashes. */
val EmptyHeatInfo = HeatInfo(
    level = 0f, trend = 0, plays = 0, fullListens = 0, skips = 0,
    listensThisWeek = 0, firstPlayed = 0L, lastPlayed = 0L,
    peak = 0f, recordedPlays = 0, momentum = 0f,
    shortScore = 0f, longScore = 0f, shortLevel = 0f, isRevival = false,
)

/**
 * "Heat details" from a song's ⋮ menu (the player's or a list's): the
 * numbers that used to sit behind a long press on the player's meter.
 *
 * A request remembers the glass layer it was opened from ([owner], the sheet
 * host there), so the dialog shows on that layer: opened from the player, it
 * shows over the player, not behind it.
 */
object HeatDetailsRequests {
    class Request(val song: Song, val owner: Any?)

    private val _request = MutableStateFlow<Request?>(null)
    val request = _request.asStateFlow()

    /** Layers that have a host of their own. */
    internal val hosted = mutableStateListOf<Any>()

    fun open(song: Song, owner: Any? = null) { _request.value = Request(song, owner) }
    fun close() { _request.value = null }
}

/** Opens the details on the glass layer this is called from. */
@Composable
fun rememberOpenHeatDetails(): (Song) -> Unit {
    val owner = LocalGlassSheets.current
    return { song -> HeatDetailsRequests.open(song, owner) }
}

/**
 * Shows [HeatDetailsRequests]. One near the root ([root] = true), and one in
 * each glass window that can open the details on its own layer (the player).
 */
@Composable
fun HeatDetailsHost(root: Boolean = true) {
    val here = LocalGlassSheets.current
    if (!root && here != null) {
        DisposableEffect(here) {
            HeatDetailsRequests.hosted.add(here)
            onDispose { HeatDetailsRequests.hosted.remove(here) }
        }
    }
    val request by HeatDetailsRequests.request.collectAsStateWithLifecycle()
    val target = request ?: return
    val mine = if (root) {
        target.owner == null || target.owner == here || target.owner !in HeatDetailsRequests.hosted
    } else {
        target.owner != null && target.owner == here
    }
    if (!mine) return
    val info by HeatStore.info.collectAsStateWithLifecycle()
    val hue = rememberHeatHue()
    GlassAlertDialog(
        onDismissRequest = HeatDetailsRequests::close,
        title = { Text(target.song.title, maxLines = 2) },
        text = { HeatDetails(info = info[target.song.heatKey()] ?: EmptyHeatInfo, hue = hue, song = target.song) },
        confirmButton = {
            TextButton(onClick = HeatDetailsRequests::close) { Text(stringResource(R.string.done)) }
        },
    )
}
