package com.music.bitchord.ui.screens.equalizer.components

import androidx.compose.runtime.Composable
import java.util.Locale

/**
 * The five graphic-EQ bands, one grouped row each. Call from inside an
 * [EqGroup] builder so the bands share a container with the switch and presets.
 */
fun EqGroupScope.equalizerBandRows(
    bandLevels: Map<Int, Short>,
    enabled: Boolean,
    onBandLevelChange: (band: Int, level: Short) -> Unit,
    centerFrequencies: List<Int> = listOf(60_000, 230_000, 910_000, 3_600_000, 14_000_000),
    minLevel: Short = -1500,
    maxLevel: Short = 1500,
) {
    centerFrequencies.forEachIndexed { band, freqMilliHz ->
        row {
            EqualizerBandRow(
                label = formatFrequency(freqMilliHz),
                level = bandLevels[band] ?: 0,
                enabled = enabled,
                minLevel = minLevel,
                maxLevel = maxLevel,
                onChange = { onBandLevelChange(band, it) },
            )
        }
    }
}

@Composable
private fun EqualizerBandRow(
    label: String,
    level: Short,
    enabled: Boolean,
    minLevel: Short,
    maxLevel: Short,
    onChange: (Short) -> Unit,
) {
    val dB = level.toFloat() / 100f
    EqSliderRow(
        label = label,
        valueText = String.format(Locale.US, if (dB > 0) "+%.1f dB" else "%.1f dB", dB),
        value = level.toFloat(),
        onValueChange = { newValue ->
            if (enabled) {
                // Snap to 0 dB if within ±30 mB
                val snapped = if (kotlin.math.abs(newValue) < 30f) 0f else newValue
                onChange(snapped.toInt().toShort())
            }
        },
        valueRange = minLevel.toFloat()..maxLevel.toFloat(),
        enabled = enabled,
    )
}

private fun formatFrequency(milliHz: Int): String {
    val hz = milliHz / 1000
    return if (hz >= 1000) {
        val kHz = hz / 1000f
        if (kHz % 1f == 0f) "${kHz.toInt()} kHz" else String.format(Locale.US, "%.1f kHz", kHz)
    } else {
        "$hz Hz"
    }
}
