package com.music.bitchord.ui.screens.equalizer.components

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.LocalMediaRepository
import com.music.bitchord.data.settings.EqualizerSettings
import com.music.bitchord.data.settings.SongGainStore
import com.music.bitchord.playback.TrackGain
import com.music.bitchord.ui.components.GlassAlertDialog
import com.music.bitchord.ui.player.ExpressiveGainSlider
import com.music.bitchord.ui.player.formatDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Where the reference setup starts: low enough that full system volume is
 * quiet on anything, since on some devices -20 dB at full volume is still loud.
 */
private const val CALIBRATION_START_DB = -40f

/** Skips to the next song from a screen that has no player of its own (the reference setup). */
val LocalSkipToNext = staticCompositionLocalOf<(() -> Unit)?> { null }

/** A song the reference was set by: its loudness and where the listener settled it. */
private data class Sample(val key: String, val title: String, val lufs: Double, val offset: Float)

/** One song's loudness, for the levelling's counts and list. */
private data class SongLoudness(val title: String, val artist: String, val lufs: Double)

/**
 * The attenuate-only levelling's own rows: the reference for the device in use,
 * the lift, and how many songs the lift has left at 0 dB.
 */
fun EqGroupScope.attenuateLevelingRows(enabled: Boolean) {
    row {
        val device by EqualizerSettings.activeDeviceKeyFlow.collectAsStateWithLifecycle()
        val label by EqualizerSettings.activeDeviceLabel.collectAsStateWithLifecycle()
        val revision by SongGainStore.revision.collectAsStateWithLifecycle()
        val reference = remember(device, revision) { SongGainStore.reference(device) }
        val referenceVolume = remember(device, revision) { SongGainStore.referenceVolume(device) }
        var calibrating by remember { mutableStateOf(false) }
        EqActionRow(
            title = stringResource(R.string.leveling_set_reference),
            subtitle = if (reference != null) {
                stringResource(R.string.leveling_reference_set, label, "%.2f".format(reference)) +
                    (referenceVolume?.let { (step, max) -> "\n" + stringResource(R.string.leveling_reference_volume, step, max) } ?: "")
            } else {
                stringResource(R.string.leveling_reference_unset, label)
            },
            onClick = { if (enabled) calibrating = true },
            icon = Icons.Rounded.Tune,
        )
        if (calibrating) ReferenceCalibrationDialog(onDismiss = { calibrating = false })
    }
    row { LiftRow(enabled) }
}

@Composable
private fun LiftRow(enabled: Boolean) {
    val context = LocalContext.current
    val device by EqualizerSettings.activeDeviceKeyFlow.collectAsStateWithLifecycle()
    val revision by SongGainStore.revision.collectAsStateWithLifecycle()
    val reference = remember(device, revision) { SongGainStore.reference(device) }
    var lift by remember(device) { mutableStateOf(SongGainStore.lift(device)) }
    var showList by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    val library by produceState<Pair<List<SongLoudness>, Int>?>(initialValue = null, reference) {
        if (reference == null) return@produceState
        value = withContext(Dispatchers.IO) { loadLoudness(context) { progress = it } }
    }

    EqRowContent(enabled = enabled && reference != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.leveling_lift),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                formatDb(lift),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            stringResource(R.string.leveling_lift_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (reference == null) return@EqRowContent
        val songs = library?.first
        // Past here every song is back at 0 dB: the same as no levelling.
        val allOut = songs?.maxOfOrNull { it.lufs - reference }?.toFloat()
            ?.takeIf { it in SongGainStore.LIFT_MIN_DB..SongGainStore.LIFT_MAX_DB }
        ExpressiveGainSlider(
            valueDb = lift,
            minDb = SongGainStore.LIFT_MIN_DB,
            maxDb = SongGainStore.LIFT_MAX_DB,
            onValueChange = { lift = it; SongGainStore.setLift(device, it) },
            activeColor = MaterialTheme.colorScheme.primary,
            inactiveColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            handleColor = MaterialTheme.colorScheme.primary,
            showCutIcon = false,
            bubble = ::formatDb,
            tickDb = allOut,
            fineLabel = stringResource(R.string.song_trim_fine),
        )
        if (allOut != null) {
            Text(
                stringResource(R.string.leveling_all_out_mark, formatDb(allOut).removeSuffix(" dB")),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (songs == null) {
            Text(
                stringResource(R.string.leveling_counting, progress),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val target = reference + lift
            val outside = songs.filter { target - it.lufs > 0.005 }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.leveling_outside_count, formatDb(lift).removeSuffix(" dB"), outside.size, songs.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    val unknown = library?.second ?: 0
                    if (unknown > 0) {
                        Text(
                            stringResource(R.string.leveling_unmeasured_count, unknown),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (outside.isNotEmpty()) {
                    TextButton(onClick = { showList = true }) { Text(stringResource(R.string.leveling_details)) }
                }
            }
            if (showList) {
                OutsideListDialog(
                    songs = outside.sortedByDescending { target - it.lufs }.map { it to (target - it.lufs).toFloat() },
                    onDismiss = { showList = false },
                )
            }
        }
    }
}

/** Every local song's loudness that is known; and how many are not. */
private suspend fun loadLoudness(context: Context, onProgress: (Int) -> Unit): Pair<List<SongLoudness>, Int> {
    val songs = LocalMediaRepository.getLocalMusic(context)
    val known = mutableListOf<SongLoudness>()
    var unknown = 0
    songs.forEachIndexed { index, song ->
        val lufs = TrackGain.lufsFor(context, song)
        if (lufs != null) known += SongLoudness(song.title, song.artist, lufs) else unknown++
        if (index % 25 == 0) onProgress(index * 100 / songs.size.coerceAtLeast(1))
    }
    return known to unknown
}

@Composable
private fun OutsideListDialog(songs: List<Pair<SongLoudness, Float>>, onDismiss: () -> Unit) {
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.leveling_outside_title)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(songs) { (song, short) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                song.artist,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.leveling_short_by, "%.2f".format(short)),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) } },
    )
}

/**
 * Setting the reference: with the system volume at full, cut each of a few
 * songs to where it sounds right; the average of where they settle is what the
 * device's songs are levelled to.
 */
@Composable
private fun ReferenceCalibrationDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVol = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    var volumeStep by remember { mutableStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    val atMax = volumeStep >= maxVol
    val device by EqualizerSettings.activeDeviceKeyFlow.collectAsStateWithLifecycle()
    val label by EqualizerSettings.activeDeviceLabel.collectAsStateWithLifecycle()
    val playing by TrackGain.current.collectAsStateWithLifecycle()
    val offset by TrackGain.calibrationOffsetDb.collectAsStateWithLifecycle()
    val samples = remember { mutableStateListOf<Sample>() }
    val skipNext = LocalSkipToNext.current

    // Starts low, so going to full volume is never a jump to loud.
    DisposableEffect(Unit) {
        TrackGain.calibrationOffsetDb.value = CALIBRATION_START_DB
        onDispose { TrackGain.calibrationOffsetDb.value = null }
    }
    LaunchedEffect(Unit) {
        while (true) {
            volumeStep = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            delay(500)
        }
    }

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.leveling_set_reference_title, label)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Any system volume will do: the levelling between songs does not
                // depend on it, only what the reference means does. Full volume is
                // offered (safely) for a reference that is easy to come back to.
                Text(
                    stringResource(R.string.leveling_current_volume, volumeStep, maxVol),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (!atMax) {
                    Text(
                        stringResource(R.string.leveling_step_max_volume),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(onClick = {
                        TrackGain.calibrationOffsetDb.value = CALIBRATION_START_DB
                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, maxVol, 0)
                        volumeStep = maxVol
                    }) {
                        Icon(Icons.Rounded.VolumeUp, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.leveling_set_max_volume))
                    }
                }
                Text(
                    stringResource(R.string.leveling_step_adjust),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val p = playing
                Text(
                    text = p?.let { "${it.title} · ${it.artist}" } ?: stringResource(R.string.nothing_playing),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ExpressiveGainSlider(
                    valueDb = offset ?: CALIBRATION_START_DB,
                    minDb = -60f,
                    maxDb = 0f,
                    onValueChange = { TrackGain.calibrationOffsetDb.value = it },
                    activeColor = MaterialTheme.colorScheme.primary,
                    inactiveColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    handleColor = MaterialTheme.colorScheme.primary,
                    fineLabel = stringResource(R.string.song_trim_fine),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = p?.lufs != null,
                        onClick = {
                            val lufs = p?.lufs ?: return@Button
                            samples.removeAll { it.key == p.songKey }
                            samples += Sample(p.songKey, p.title, lufs, offset ?: CALIBRATION_START_DB)
                        },
                    ) { Text(stringResource(R.string.leveling_use_this_song)) }
                    if (skipNext != null) {
                        TextButton(onClick = skipNext) { Text(stringResource(R.string.leveling_next_song)) }
                    }
                }
                if (p != null && p.lufs == null) {
                    Text(
                        stringResource(R.string.leveling_measuring),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (samples.isNotEmpty()) {
                    val reference = samples.map { it.lufs + it.offset }.average()
                    Text(
                        stringResource(R.string.leveling_samples, samples.size, "%.2f".format(reference)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    samples.toList().forEach { s ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                s.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            Text(formatDb(s.offset), style = MaterialTheme.typography.bodySmall)
                            IconButton(onClick = { samples.remove(s) }) {
                                Icon(Icons.Rounded.Close, contentDescription = null)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = samples.isNotEmpty(),
                onClick = {
                    SongGainStore.setReference(device, samples.map { it.lufs + it.offset }.average().toFloat())
                    SongGainStore.setReferenceVolume(device, volumeStep, maxVol)
                    onDismiss()
                },
            ) { Text(stringResource(R.string.leveling_done)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}
