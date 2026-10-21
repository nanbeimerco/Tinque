package com.music.bitchord.ui.screens.equalizer.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.music.bitchord.R
import com.music.bitchord.playback.ReplayGainScanState

@Composable
fun ReplayGainControls(
    enabled: Boolean,
    useAlbumGain: Boolean,
    preampDb: Float,
    preventClipping: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onAlbumGainChange: (Boolean) -> Unit,
    onPreampChange: (Float) -> Unit,
    onPreventClippingChange: (Boolean) -> Unit,
    scanState: ReplayGainScanState,
    onScan: () -> Unit,
    onCancelScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EqGroup(modifier = modifier, title = stringResource(R.string.replaygain)) {
        row {
            EqSwitchRow(
                title = stringResource(R.string.replaygain),
                subtitle = stringResource(R.string.replaygain_description),
                checked = enabled,
                onCheckedChange = onEnabledChange,
                icon = Icons.Rounded.GraphicEq,
            )
        }
        row {
            EqRowContent(enabled = enabled) {
                val options = listOf(
                    stringResource(R.string.track_gain) to false,
                    stringResource(R.string.album_gain) to true,
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    options.forEachIndexed { index, (label, album) ->
                        SegmentedButton(
                            selected = useAlbumGain == album,
                            onClick = { onAlbumGainChange(album) },
                            enabled = enabled,
                            shape = SegmentedButtonDefaults.itemShape(index, options.size),
                            colors = eqSegmentedColors(),
                            label = { Text(label, maxLines = 1) },
                        )
                    }
                }
            }
        }
        row {
            EqSliderRow(
                label = stringResource(R.string.replaygain_preamp),
                valueText = "${"%.1f".format(preampDb)} dB",
                value = preampDb,
                onValueChange = onPreampChange,
                valueRange = -12f..12f,
                steps = 23,
                enabled = enabled,
            )
        }
        row {
            EqSwitchRow(
                title = stringResource(R.string.replaygain_prevent_clipping),
                checked = preventClipping,
                onCheckedChange = onPreventClippingChange,
                enabled = enabled,
            )
        }
        row {
            EqActionRow(
                title = stringResource(if (scanState.isRunning) R.string.replaygain_scan_cancel else R.string.replaygain_scan_action),
                subtitle = stringResource(R.string.replaygain_scan_description),
                onClick = if (scanState.isRunning) onCancelScan else onScan,
                icon = if (scanState.isRunning) Icons.Rounded.Close else Icons.Rounded.Search,
                showChevron = false,
            )
        }
        if (scanState.isRunning && scanState.total > 0 || scanState.isComplete ||
            scanState.errorMessage != null || scanState.wasCancelled
        ) {
            row {
                EqRowContent {
                    when {
                        scanState.isRunning && scanState.total > 0 -> {
                            Text(
                                stringResource(
                                    R.string.replaygain_scan_progress,
                                    scanState.processed,
                                    scanState.total,
                                    scanState.analyzed,
                                    scanState.alreadyTagged,
                                    scanState.skipped,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            LinearProgressIndicator(
                                progress = {
                                    (scanState.processed.toFloat() / scanState.total.toFloat()).coerceIn(0f, 1f)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        scanState.isComplete -> Text(
                            stringResource(
                                R.string.replaygain_scan_complete,
                                scanState.analyzed,
                                scanState.alreadyTagged,
                                scanState.skipped,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        scanState.errorMessage != null -> Text(
                            scanState.errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        else -> Text(
                            stringResource(R.string.replaygain_scan_cancelled),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun eqSegmentedColors() = SegmentedButtonDefaults.colors(
    activeContainerColor = MaterialTheme.colorScheme.primary,
    activeContentColor = MaterialTheme.colorScheme.onPrimary,
    activeBorderColor = MaterialTheme.colorScheme.primary,
    inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    inactiveContentColor = MaterialTheme.colorScheme.onSurface,
    inactiveBorderColor = MaterialTheme.colorScheme.surfaceContainerHighest,
)
