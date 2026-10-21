package com.music.bitchord.ui.screens.equalizer

import com.music.bitchord.ui.components.GlassAlertDialog
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.EqualizerSettings
import com.music.bitchord.data.settings.KnownOutputDevice
import com.music.bitchord.data.settings.ParametricEqPreset
import com.music.bitchord.playback.ReplayGainScanner
import com.music.bitchord.playback.equalizer.EqualizerPreset
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import com.music.bitchord.ui.components.LocalAppBackdrop
import com.music.bitchord.ui.components.LocalLiquidGlassEnabled
import com.music.bitchord.ui.components.LocalTopBarGlassBackdrop
import com.music.bitchord.ui.components.TopBarContentHeight
import com.music.bitchord.ui.components.TopBarGlassButton
import com.music.bitchord.ui.components.TopBarTitleCapsule
import com.music.bitchord.ui.components.backdrop.backdrops.layerBackdrop
import com.music.bitchord.ui.components.backdrop.backdrops.rememberLayerBackdrop
import com.music.bitchord.ui.components.isGlassSupported
import com.music.bitchord.ui.components.topSafeInset
import com.music.bitchord.ui.screens.equalizer.components.AudioEnhancements
import com.music.bitchord.ui.screens.equalizer.components.EqGroup
import com.music.bitchord.ui.screens.equalizer.components.EqLeadingIcon
import com.music.bitchord.ui.screens.equalizer.components.EqRowContent
import com.music.bitchord.ui.screens.equalizer.components.EqSwitchRow
import com.music.bitchord.ui.screens.equalizer.components.EqTag
import com.music.bitchord.ui.screens.equalizer.components.EqualizerPresetChips
import com.music.bitchord.ui.screens.equalizer.components.ParametricEqControls
import com.music.bitchord.ui.screens.equalizer.components.ReplayGainControls
import com.music.bitchord.ui.screens.equalizer.components.ReverbControls
import com.music.bitchord.ui.screens.equalizer.components.equalizerBandRows

/**
 * The equalizer, as a full-screen page.
 *
 * It used to be a bottom sheet, which never quite reached the top of the
 * screen and left the page underneath peeking out around it. It is now a
 * full-window dialog that slides up over everything, laid out edge to edge in
 * Material 3 Expressive grouped sections.
 */
@Composable
fun EqualizerSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
    val close: () -> Unit = { visibleState.targetState = false }
    LaunchedEffect(visibleState.currentState, visibleState.isIdle) {
        if (visibleState.isIdle && !visibleState.currentState && !visibleState.targetState) onDismiss()
    }

    Dialog(
        onDismissRequest = close,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false,
        ),
    ) {
        com.music.bitchord.ui.components.NoDialogDim()
        AnimatedVisibility(
            visibleState = visibleState,
            enter = slideInVertically(tween(320)) { it / 6 } + fadeIn(tween(220)),
            exit = slideOutVertically(tween(240)) { it / 6 } + fadeOut(tween(200)),
        ) {
            Surface(
                modifier = modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface,
            ) {
                BackHandler(onBack = close)
                com.music.bitchord.ui.components.GlassWindowScope {
                    // Recording the page for the top buttons' glass is the heaviest
                    // thing on screen; it waits until the slide in has finished.
                    EqualizerPage(
                        onClose = close,
                        recordGlass = visibleState.isIdle && visibleState.currentState,
                    )
                }
            }
        }
    }
}

@Composable
private fun EqualizerPage(onClose: () -> Unit, recordGlass: Boolean = true) {
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

    // This page is its own window, so the app's glass backdrop records nothing
    // here. It records its own scrolling content instead, and the top buttons
    // float over that as Liquid Glass — no band behind them.
    val pageColor = MaterialTheme.colorScheme.surface
    val eqBackdrop = rememberLayerBackdrop(
        onDraw = {
            drawRect(pageColor)
            drawContent()
        },
    )
    val glassOn = LocalLiquidGlassEnabled.current && isGlassSupported()
    val scrollState = rememberScrollState()
    val titleAwayPx = with(androidx.compose.ui.platform.LocalDensity.current) { 56.dp.toPx() }
    val titleAway by remember(titleAwayPx) {
        androidx.compose.runtime.derivedStateOf { scrollState.value > titleAwayPx }
    }
    val topInset = topSafeInset()
    val equalizerTitle = stringResource(R.string.equalizer)

    CompositionLocalProvider(
        LocalAppBackdrop provides eqBackdrop,
        LocalTopBarGlassBackdrop provides glassOn,
    ) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (glassOn && recordGlass) Modifier.layerBackdrop(eqBackdrop) else Modifier)
                .background(pageColor)
                .verticalScroll(scrollState)
                .padding(top = topInset + TopBarContentHeight),
        ) {
            // Large expressive headline.
            Column(
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.equalizer),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.equalizer_output_profile, activeDeviceLabel),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

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

            ParametricEqControls(
                config = parametricEqConfig,
                presets = parametricEqPresets,
                onConfigChange = AppSettings::setParametricEqConfig,
            )

            EqGroup(title = stringResource(R.string.eq_graphic)) {
                row {
                    EqSwitchRow(
                        title = stringResource(R.string.eq_graphic),
                        subtitle = stringResource(R.string.eq_graphic_subtitle),
                        checked = enabled,
                        onCheckedChange = { EqualizerSettings.setEnabled(it) },
                        icon = Icons.Rounded.Equalizer,
                    )
                }
                row {
                    EqualizerPresetChips(
                        selectedPresetId = presetId,
                        enabled = enabled,
                        onSelectPreset = { EqualizerSettings.selectPreset(it) },
                    )
                }
                equalizerBandRows(
                    bandLevels = bandLevels,
                    enabled = enabled,
                    onBandLevelChange = { band, level -> EqualizerSettings.setBandLevel(band, level) },
                )
            }

            ReverbControls(
                enabled = reverbEnabled,
                amount = reverbAmount,
                onEnabledChange = AppSettings::setReverbEnabled,
                onAmountChange = AppSettings::setReverbAmount,
            )

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

            Spacer(Modifier.height(24.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }

        // Status bar fade only — just enough for the clock to stay readable.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(topInset + 12.dp)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(pageColor.copy(alpha = 0.85f), pageColor.copy(alpha = 0f)),
                    ),
                ),
        )

        // Floating glass controls over the scrolling page.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = topInset)
                .height(TopBarContentHeight),
        ) {
            TopBarGlassButton(
                onClick = onClose,
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp),
            )
            androidx.compose.animation.AnimatedVisibility(
                visible = titleAway,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(160)),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 72.dp),
            ) {
                TopBarTitleCapsule(equalizerTitle)
            }
            TopBarGlassButton(
                onClick = {
                    EqualizerSettings.reset()
                    AppSettings.resetParametricEqConfig()
                },
                icon = Icons.Rounded.RestartAlt,
                contentDescription = "Reset Equalizer",
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp),
            )
        }
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

    EqGroup(title = stringResource(R.string.equalizer_device_auto_preset_title)) {
        row {
            EqRowContent {
                Text(
                    text = stringResource(R.string.equalizer_device_auto_preset_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        allDevices.forEach { device ->
            row {
                val isConnected = device.key == activeDeviceKey
                val assignedSpec = assignments[device.key].orEmpty()
                val assignedLabel = resolveAssignedPresetLabel(assignedSpec, parametricPresets)
                EqRowContent(onClick = { editingDevice = device }) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        EqLeadingIcon(Icons.Rounded.Headphones)
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = device.label,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (isConnected) {
                                EqTag(
                                    text = stringResource(R.string.equalizer_device_connected_badge),
                                    container = MaterialTheme.colorScheme.primary,
                                    content = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                            Text(
                                text = assignedLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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

        GlassAlertDialog(
            onDismissRequest = { editingDevice = null },
            shape = RoundedCornerShape(28.dp),
            title = {
                Text(
                    text = stringResource(R.string.equalizer_device_select_preset, targetDevice.label),
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    options.forEachIndexed { index, (spec, label) ->
                        if (index > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onAssignPreset(targetDevice.key, spec)
                                    editingDevice = null
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = currentSpec == spec,
                                onClick = {
                                    onAssignPreset(targetDevice.key, spec)
                                    editingDevice = null
                                },
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
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
