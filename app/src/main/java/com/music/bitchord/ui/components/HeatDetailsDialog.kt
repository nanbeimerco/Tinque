package com.music.bitchord.ui.components

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
    peak = 0f, recordedPlays = 0,
)

/**
 * "Heat details" from a song's ⋮ menu (the player's or a list's): the
 * numbers that used to sit behind a long press on the player's meter.
 */
object HeatDetailsRequests {
    private val _song = MutableStateFlow<Song?>(null)
    val song = _song.asStateFlow()
    fun open(song: Song) { _song.value = song }
    fun close() { _song.value = null }
}

/** Shows [HeatDetailsRequests]. Once, near the root. */
@Composable
fun HeatDetailsHost() {
    val song by HeatDetailsRequests.song.collectAsStateWithLifecycle()
    val target = song ?: return
    val info by HeatStore.info.collectAsStateWithLifecycle()
    val hue = rememberHeatHue()
    GlassAlertDialog(
        onDismissRequest = HeatDetailsRequests::close,
        title = { Text(target.title, maxLines = 2) },
        text = { HeatDetails(info = info[target.heatKey()] ?: EmptyHeatInfo, hue = hue, song = target) },
        confirmButton = {
            TextButton(onClick = HeatDetailsRequests::close) { Text(stringResource(R.string.done)) }
        },
    )
}
