package com.music.bitchord.ui.screens.equalizer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.EqualizerSettings
import com.music.bitchord.data.settings.KnownOutputDevice
import com.music.bitchord.data.settings.ParametricEqPreset
import com.music.bitchord.playback.ReplayGainScanner
import com.music.bitchord.playback.equalizer.EqualizerPreset
import com.music.bitchord.ui.screens.equalizer.components.AudioEnhancements
import com.music.bitchord.ui.screens.equalizer.components.EqualizerBandSliders
import com.music.bitchord.ui.screens.equalizer.components.EqualizerPresetChips
import com.music.bitchord.ui.screens.equalizer.components.ReplayGainControls
import com.music.bitchord.ui.screens.equalizer.components.ParametricEqControls
import com.music.bitchord.ui.screens.equalizer.components.ReverbControls

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    val enabled by EqualizerSettings.enabled.collectAsStateWithLifecycle()
    val presetId by EqualizerSettings.presetId.collectAsStateWithLifecycle()
    val bandLevels by EqualizerSettings.bandLevels.collectAsStateWithLifecycle()
    val bassBoostStrength by EqualizerSettings.bassBoostStrength.collectAsStateWithLifecycle()
    val virtualizerStrength by EqualizerSettings.virtualizerStrength.collectAsStateWithLifecycle()
    val activeDeviceLabel by EqualizerSettings.activeDeviceLabel.collectAsStateWithLifecycle()
    val activeDeviceKey by EqualizerSettings.activeDeviceKeyFlow.collectAsStateWithLifecycle()
    val knownDevices by EqualizerSettings.knownDevices.collectAsStateWithLifecycle()
    val devicePresetAssignments by EqualizerSettings.devicePresetAssignments.collectAsStateWithLifecycle()
    val sessionId by AppSettings.audioSessionId.collectAsStateWithLifecycle()
    val replayGainEnabled by AppSettings.replayGainEnabled.collectAsStateWithLifecycle()
    val replayGainAlbumMode by AppSettings.replayGainAlbumMode.collectAsStateWithLifecycle()
    val replayGainPreampDb by AppSettings.replayGainPreampDb.collectAsStateWithLifecycle()
    val replayGainPreventClipping by AppSettings.replayGainPreventClipping.collectAsStateWithLifecycle()
    val parametricEqConfig by AppSettings.parametricEqConfig.collectAsStateWithLifecycle()
    val parametricEqPresets by AppSettings.parametricEqPresets.collectAsStateWithLifecycle()
    val reverbEnabled by AppSettings.reverbEnabled.collectAsStateWithLifecycle()
    val reverbAmount by AppSettings.reverbAmount.collectAsStateWithLifecycle()
    val replayGainScanState by ReplayGainScanner.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        EqualizerSettings.refreshConnectedDevices(context)
    }

    val scrollState = rememberScrollState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .navigationBarsPadding(),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = stringResource(R.string.equalizer),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(
                        onClick = {
                            EqualizerSettings.reset()
                            AppSettings.resetParametricEqConfig()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RestartAlt,
                            contentDescription = "Reset Equalizer",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.equalizer_output_profile, activeDeviceLabel),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )

            // Per-device automatic EQ preset assignment
            DeviceAutoPresetSection(
                activeDeviceKey = activeDeviceKey,
                activeDeviceLabel = activeDeviceLabel,
                knownDevices = knownDevices,
                assignments = devicePresetAssignments,
                parametricPresets = parametricEqPresets,
                onAssignPreset = { deviceKey, spec ->
                    EqualizerSettings.setDevicePresetAssignment(deviceKey, spec)
                },
            )

            // High-precision In-App Parametric EQ, L/R Independent EQ, Channel Swap & EEL Preset Manager
            ParametricEqControls(
                config = parametricEqConfig,
                presets = parametricEqPresets,
                onConfigChange = AppSettings::setParametricEqConfig,
            )

            // Standard 5-band Graphic Equalizer Header & Switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "5バンド グラフィックEQ / システムエフェクト",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = { EqualizerSettings.setEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        checkedBorderColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }

            // Presets
            EqualizerPresetChips(
                selectedPresetId = presetId,
                enabled = enabled,
                onSelectPreset = { EqualizerSettings.selectPreset(it) },
            )

            // Frequency band sliders
            EqualizerBandSliders(
                bandLevels = bandLevels,
                enabled = enabled,
                onBandLevelChange = { band, level ->
                    EqualizerSettings.setBandLevel(band, level)
                },
            )

            ReverbControls(
                enabled = reverbEnabled,
                amount = reverbAmount,
                onEnabledChange = AppSettings::setReverbEnabled,
                onAmountChange = AppSettings::setReverbAmount,
            )

            // Bass Boost, Virtualizer & System Equalizer action
            AudioEnhancements(
                bassBoostStrength = bassBoostStrength,
                virtualizerStrength = virtualizerStrength,
                enabled = enabled,
                audioSessionId = sessionId,
                onBassBoostChange = { EqualizerSettings.setBassBoostStrength(it) },
                onVirtualizerChange = { EqualizerSettings.setVirtualizerStrength(it) },
            )

            ReplayGainControls(
                enabled = replayGainEnabled,
                useAlbumGain = replayGainAlbumMode,
                preampDb = replayGainPreampDb,
                preventClipping = replayGainPreventClipping,
                onEnabledChange = AppSettings::setReplayGainEnabled,
                onAlbumGainChange = AppSettings::setReplayGainAlbumMode,
                onPreampChange = AppSettings::setReplayGainPreampDb,
                onPreventClippingChange = AppSettings::setReplayGainPreventClipping,
                scanState = replayGainScanState,
                onScan = { ReplayGainScanner.start(context) },
                onCancelScan = ReplayGainScanner::cancel,
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DeviceAutoPresetSection(
    activeDeviceKey: String,
    activeDeviceLabel: String,
    knownDevices: List<KnownOutputDevice>,
    assignments: Map<String, String>,
    parametricPresets: List<ParametricEqPreset>,
    onAssignPreset: (String, String?) -> Unit,
) {
    val allDevices = remember(activeDeviceKey, activeDeviceLabel, knownDevices) {
        buildList {
            add(KnownOutputDevice(key = activeDeviceKey, label = activeDeviceLabel))
            for (d in knownDevices) {
                if (d.key != activeDeviceKey && none { it.key == d.key }) {
                    add(d)
                }
            }
        }
    }

    var editingDevice by remember { mutableStateOf<KnownOutputDevice?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(16.dp),
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Headphones,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.equalizer_device_auto_preset_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.equalizer_device_auto_preset_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        allDevices.forEach { device ->
            val isConnected = device.key == activeDeviceKey
            val assignedSpec = assignments[device.key].orEmpty()
            val assignedLabel = resolveAssignedPresetLabel(assignedSpec, parametricPresets)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isConnected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                        }
                    )
                    .clickable { editingDevice = device }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = device.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isConnected) FontWeight.SemiBold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (isConnected) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary,
                            ) {
                                Text(
                                    text = stringResource(R.string.equalizer_device_connected_badge),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    Text(
                        text = assignedLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (assignedSpec.isNotEmpty()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    val targetDevice = editingDevice
    if (targetDevice != null) {
        val currentSpec = assignments[targetDevice.key].orEmpty()
        val options = buildList<Pair<String, String>> {
            add(
                EqualizerSettings.AUTO_PRESET_NONE to
                    stringResource(R.string.equalizer_device_auto_preset_none)
            )
            add(
                EqualizerSettings.AUTO_PRESET_OFF to
                    stringResource(R.string.equalizer_device_auto_preset_off)
            )
            parametricPresets.forEach { peq ->
                add(
                    "${EqualizerSettings.AUTO_PRESET_PEQ_PREFIX}${peq.id}" to
                        stringResource(R.string.equalizer_device_auto_preset_peq_prefix, peq.name)
                )
            }
            EqualizerPreset.PRESETS.forEach { geq ->
                add(
                    "${EqualizerSettings.AUTO_PRESET_GEQ_PREFIX}${geq.id}" to
                        stringResource(R.string.equalizer_device_auto_preset_geq_prefix, geq.name)
                )
            }
        }

        AlertDialog(
            onDismissRequest = { editingDevice = null },
            title = {
                Text(
                    text = stringResource(R.string.equalizer_device_select_preset, targetDevice.label),
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    options.forEach { (spec, label) ->
                        val selected = currentSpec == spec
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    onAssignPreset(targetDevice.key, spec)
                                    editingDevice = null
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = {
                                    onAssignPreset(targetDevice.key, spec)
                                    editingDevice = null
                                },
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { editingDevice = null }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun resolveAssignedPresetLabel(
    spec: String,
    parametricPresets: List<ParametricEqPreset>,
): String {
    if (spec.isBlank()) {
        return stringResource(R.string.equalizer_device_auto_preset_none)
    }
    if (spec == EqualizerSettings.AUTO_PRESET_OFF) {
        return stringResource(R.string.equalizer_device_auto_preset_off)
    }
    if (spec.startsWith(EqualizerSettings.AUTO_PRESET_PEQ_PREFIX)) {
        val id = spec.removePrefix(EqualizerSettings.AUTO_PRESET_PEQ_PREFIX)
        val name = parametricPresets.firstOrNull { it.id == id }?.name ?: id
        return stringResource(R.string.equalizer_device_auto_preset_peq_prefix, name)
    }
    if (spec.startsWith(EqualizerSettings.AUTO_PRESET_GEQ_PREFIX)) {
        val id = spec.removePrefix(EqualizerSettings.AUTO_PRESET_GEQ_PREFIX).toIntOrNull()
        val name = id?.let { EqualizerPreset.getPresetById(it)?.name } ?: spec
        return stringResource(R.string.equalizer_device_auto_preset_geq_prefix, name)
    }
    return stringResource(R.string.equalizer_device_auto_preset_none)
}
