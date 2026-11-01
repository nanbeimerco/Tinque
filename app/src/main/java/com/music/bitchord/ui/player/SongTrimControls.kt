package com.music.bitchord.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.SongGainStore
import com.music.bitchord.playback.TrackGain
import com.music.bitchord.ui.components.GlassAlertDialog
import com.music.bitchord.ui.components.LocalGlassMenu
import com.music.bitchord.ui.components.LocalGlassMenuDismiss
import com.music.bitchord.ui.components.trackBoundsInRoot
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * The player's volume row, with the per-song trim behind it.
 *
 * Normally the system volume bar, with a long press opening the trim in a glass
 * popup above it (and the finger can go straight on to drag it). With
 * [AppSettings.songTrimFirst] the two swap: the trim is the bar, the system
 * volume is behind the long press.
 */
@Composable
internal fun PlayerVolumeArea(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    onVolumeChangeFinished: () -> Unit,
) {
    val trimEnabled by AppSettings.songTrimEnabled.collectAsStateWithLifecycle()
    val trimFirst by AppSettings.songTrimFirst.collectAsStateWithLifecycle()
    val breakdown by TrackGain.current.collectAsStateWithLifecycle()
    val menus = LocalGlassMenu.current
    val haptics = LocalHapticFeedback.current
    var anchor by remember { mutableStateOf(Rect.Zero) }
    var popupToken by remember { mutableStateOf<Long?>(null) }
    // While a finger is held down from the long press, the popup stays open.
    var holding by remember { mutableStateOf(false) }
    var lastTouch by remember { mutableLongStateOf(0L) }
    var editing by remember { mutableStateOf(false) }
    val b = breakdown

    // Live value while it is being dragged, so the bar follows the finger at
    // once rather than after the round trip through the store and the service.
    var liveTrim by remember { mutableStateOf<Float?>(null) }
    val trimNow = liveTrim ?: b?.trimDb ?: 0f

    fun setTrim(db: Float) {
        val current = b ?: return
        val v = db.coerceIn(current.trimMinDb, current.trimMaxDb)
        liveTrim = v
        lastTouch = System.currentTimeMillis()
        SongGainStore.setTrim(current.deviceKey, current.levelled, current.songKey, v)
    }

    fun closePopup() {
        popupToken?.let { menus?.dismiss(it) }
        popupToken = null
    }

    fun openPopup() {
        val m = menus ?: return
        lastTouch = System.currentTimeMillis()
        popupToken = m.show(anchor, onDismiss = { popupToken = null; liveTrim = null }, wide = true) {
            if (trimFirst) {
                PopupVolume(volume = volume, onVolumeChange = onVolumeChange, onFinished = onVolumeChangeFinished, onTouch = { lastTouch = System.currentTimeMillis() })
            } else {
                val now = TrackGain.current.collectAsStateWithLifecycle().value
                PopupTrim(
                    breakdown = now,
                    trim = liveTrim ?: now?.trimDb ?: 0f,
                    onTrim = ::setTrim,
                    onEdit = { editing = true },
                )
            }
        }
    }

    // Closes by itself a few seconds after the last touch.
    LaunchedEffect(popupToken, lastTouch, holding) {
        if (popupToken == null || holding) return@LaunchedEffect
        delay(4_000)
        closePopup()
    }
    // A song or device change while it is open: the live value belongs to the old one.
    LaunchedEffect(b?.songKey, b?.deviceKey, b?.levelled) { liveTrim = null }

    // What a drag after the long press adds to, held here rather than read back
    // from state, so quick moves between frames all count.
    val held = remember { floatArrayOf(0f) }
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val longPress: (() -> Unit)? = if (trimEnabled && menus != null && (trimFirst || b != null)) {
        {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            holding = true
            held[0] = if (trimFirst) volume else trimNow
            openPopup()
        }
    } else {
        null
    }
    val longPressEnd = { holding = false; lastTouch = System.currentTimeMillis() }

    Box(Modifier.fillMaxWidth().trackBoundsInRoot { anchor = it }) {
        if (trimEnabled && trimFirst && b != null) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.song_trim_this_song, b.deviceLabel),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    ValueChip(text = formatDb(if (b.attenuateOnly) b.levelDb + trimNow else trimNow), onClick = { editing = true })
                }
                ExpressiveGainSlider(
                    valueDb = trimNow,
                    minDb = b.trimMinDb,
                    maxDb = b.trimMaxDb,
                    onValueChange = ::setTrim,
                    onValueChangeFinished = { liveTrim = null },
                    trackHeight = 16.dp,
                    handleHeight = 30.dp,
                    tickDb = if (b.attenuateOnly) 0f else null,
                    fineLabel = stringResource(R.string.song_trim_fine),
                    onLongPress = longPress,
                    onLongPressDrag = { dx, _ ->
                        held[0] = (held[0] + dx).coerceIn(0f, 1f)
                        lastTouch = System.currentTimeMillis()
                        onVolumeChange(held[0])
                    },
                    onLongPressEnd = longPressEnd,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.VolumeDown,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                ThinSlider(
                    value = volume,
                    onValueChange = onVolumeChange,
                    onValueChangeFinished = onVolumeChangeFinished,
                    idleHeight = 6.dp,
                    activeHeight = 10.dp,
                    relative = true,
                    onLongPress = longPress,
                    onLongPressDrag = { dx, fine ->
                        val current = b
                        if (current != null) {
                            val factor = fineFactor(fine, density)
                            held[0] = (held[0] + dx * (current.trimMaxDb - current.trimMinDb) * factor)
                                .coerceIn(current.trimMinDb, current.trimMaxDb)
                            val step = if (factor < 1f) 100f else 10f
                            setTrim((held[0] * step).roundToInt() / step)
                        }
                    },
                    onLongPressEnd = longPressEnd,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                Icon(
                    Icons.AutoMirrored.Rounded.VolumeUp,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp),
                )
            }
            // A trimmed song says so, by the bar: tap to open the trim, long press to clear it.
            if (trimEnabled && b != null && b.trimDb != 0f) {
                Text(
                    text = formatDb(b.trimDb),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFFFEEDC),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-30).dp, y = (-16).dp)
                        .background(GainSliderActive.copy(alpha = 0.22f), RoundedCornerShape(50))
                        .combinedClickable(
                            onClick = { openPopup() },
                            onLongClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                setTrim(0f)
                                liveTrim = null
                            },
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }

    if (editing && b != null) {
        TrimInputDialog(
            initial = trimNow,
            min = b.trimMinDb,
            max = b.trimMaxDb,
            onDismiss = { editing = false },
            onConfirm = { setTrim(it); liveTrim = null; editing = false },
        )
    }
}

/** The trim, in the long-press popup. */
@Composable
private fun PopupTrim(
    breakdown: TrackGain.Breakdown?,
    trim: Float,
    onTrim: (Float) -> Unit,
    onEdit: () -> Unit,
) {
    val b = breakdown ?: return
    val dismiss = LocalGlassMenuDismiss.current
    val light = MaterialTheme.colorScheme.onSurface.luminance() < 0.5f
    Column(Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.song_trim_this_song, b.deviceLabel),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (b.attenuateOnly) {
                    Text(
                        text = stringResource(R.string.song_trim_breakdown, formatDb(b.levelDb), formatDb(trim)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    )
                }
            }
            ValueChip(
                text = formatDb(if (b.attenuateOnly) b.levelDb + trim else trim),
                onClick = { dismiss(); onEdit() },
                light = light,
            )
        }
        ExpressiveGainSlider(
            valueDb = trim,
            minDb = b.trimMinDb,
            maxDb = b.trimMaxDb,
            onValueChange = onTrim,
            tickDb = if (b.attenuateOnly) 0f else null,
            fineLabel = stringResource(R.string.song_trim_fine),
            // On light glass, the player's white-on-dark would vanish.
            activeColor = if (light) Color(0xFFD98A55) else GainSliderActive,
            inactiveColor = if (light) Color.Black.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.24f),
            handleColor = if (light) MaterialTheme.colorScheme.onSurface else Color.White,
        )
    }
}

/** The system volume, in the long-press popup when the trim is the main bar. */
@Composable
private fun PopupVolume(volume: Float, onVolumeChange: (Float) -> Unit, onFinished: () -> Unit, onTouch: () -> Unit) {
    var v by remember { mutableStateOf(volume) }
    Column(Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
        Text(
            text = stringResource(R.string.song_trim_system_volume),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        ThinSlider(
            value = v,
            onValueChange = { v = it; onTouch(); onVolumeChange(it) },
            onValueChangeFinished = onFinished,
            idleHeight = 6.dp,
            activeHeight = 10.dp,
            relative = true,
            activeColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
            inactiveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
        )
    }
}

@Composable
private fun ValueChip(text: String, onClick: () -> Unit, light: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = if (light) MaterialTheme.colorScheme.onSurface else Color.White,
        modifier = Modifier
            .background(
                (if (light) Color.Black else Color.White).copy(alpha = 0.10f),
                RoundedCornerShape(50),
            )
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/** Typing the trim in, to the hundredth of a dB. */
@Composable
private fun TrimInputDialog(initial: Float, min: Float, max: Float, onDismiss: () -> Unit, onConfirm: (Float) -> Unit) {
    var text by remember { mutableStateOf("%.2f".format(initial)) }
    val parsed = text.replace('−', '-').replace(',', '.').trim().toFloatOrNull()
    val valid = parsed != null && parsed in min..max
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.song_trim_input_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    suffix = { Text("dB") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = !valid,
                )
                Text(
                    text = stringResource(R.string.song_trim_input_range, formatDb(min), formatDb(max)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let { onConfirm((it * 100f).roundToInt() / 100f) } }, enabled = valid) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}
