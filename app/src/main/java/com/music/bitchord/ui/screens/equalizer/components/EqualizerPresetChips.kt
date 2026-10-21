package com.music.bitchord.ui.screens.equalizer.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.music.bitchord.playback.equalizer.EqualizerPreset

/** Graphic-EQ presets as a single scrolling line of pill chips. */
@Composable
fun EqualizerPresetChips(
    selectedPresetId: Int,
    enabled: Boolean,
    onSelectPreset: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (selectedPresetId == EqualizerPreset.CUSTOM_PRESET_ID) {
            PresetChip(label = "Custom", selected = true, enabled = enabled, onClick = {})
        }
        EqualizerPreset.PRESETS.forEach { preset ->
            PresetChip(
                label = preset.name,
                selected = selectedPresetId == preset.id,
                enabled = enabled,
                onClick = { onSelectPreset(preset.id) },
            )
        }
    }
}

@Composable
private fun PresetChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label, maxLines = 1) },
        shape = RoundedCornerShape(50),
        border = null,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            labelColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}
