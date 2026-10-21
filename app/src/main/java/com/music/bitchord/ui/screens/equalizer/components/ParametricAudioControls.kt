package com.music.bitchord.ui.screens.equalizer.components

import com.music.bitchord.ui.components.GlassAlertDialog
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.ParametricEqBand
import com.music.bitchord.data.settings.ParametricEqChannelTarget
import com.music.bitchord.data.settings.ParametricEqConfig
import com.music.bitchord.data.settings.ParametricEqFilterType
import com.music.bitchord.data.settings.ParametricEqPreset
import com.music.bitchord.playback.equalizer.BiquadUtils
import com.music.bitchord.playback.equalizer.EelEqParser
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln

private val FILTER_TYPES = listOf(
    ParametricEqFilterType.PEAKING,
    ParametricEqFilterType.LOW_SHELF,
    ParametricEqFilterType.HIGH_SHELF,
    ParametricEqFilterType.LOW_PASS,
    ParametricEqFilterType.HIGH_PASS,
    ParametricEqFilterType.NOTCH,
)

/** Left-channel accent: a soft blue that holds on both light and dark surfaces. */
@Composable
internal fun eqLeftColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) Color(0xFF1565C0) else Color(0xFF8AB4F8)

/** Right-channel accent: a soft coral. */
@Composable
internal fun eqRightColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) Color(0xFFC62828) else Color(0xFFF28B82)

@Composable
fun ParametricEqControls(
    config: ParametricEqConfig,
    presets: List<ParametricEqPreset>,
    onConfigChange: (ParametricEqConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0: 一覧, 1: 共通, 2: L, 3: R
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var showRawEelDialog by remember { mutableStateOf(false) }
    var editingBandTarget by remember { mutableStateOf<Pair<ParametricEqChannelTarget, Int>?>(null) }
    var editingPreampDialog by remember { mutableStateOf(false) }
    var expandedBandIds by remember { mutableStateOf(setOf<String>()) }

    val report = remember(config) { EelEqParser.analyze(config) }

    val importEelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            val displayName = queryDisplayName(context, uri) ?: "imported_eq.eel"
            val text = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: error(context.getString(R.string.peq_error_read_file))
            val parsed = AppSettings.importEelPreset(text, displayName)
            selectedTab = 0
            Toast.makeText(context, context.getString(R.string.peq_preset_imported, parsed.preset.name), Toast.LENGTH_SHORT).show()
        }.onFailure { err ->
            Toast.makeText(context, context.getString(R.string.peq_import_error, err.message.orEmpty()), Toast.LENGTH_LONG).show()
        }
    }

    val exportEelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            val eelText = BiquadUtils.toEelString(config)
            context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(eelText) }
            Toast.makeText(context, context.getString(R.string.peq_exported), Toast.LENGTH_SHORT).show()
        }.onFailure { err ->
            Toast.makeText(context, context.getString(R.string.peq_export_error, err.message.orEmpty()), Toast.LENGTH_SHORT).show()
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // 1. Master switch, response graph, live summary
        EqGroup(title = stringResource(R.string.peq_title)) {
            row {
                EqSwitchRow(
                    title = stringResource(R.string.peq_title),
                    subtitle = stringResource(R.string.peq_subtitle),
                    checked = config.enabled,
                    onCheckedChange = { AppSettings.setParametricEqEnabled(it) },
                    icon = Icons.Rounded.GraphicEq,
                )
            }
            row { ParametricEqResponseGraph(config = config) }
            row { ActiveDspSummary(config = config, report = report) }
        }

        // 2. Saved presets
        EqGroup(title = stringResource(R.string.peq_presets_count, presets.size)) {
            if (presets.isEmpty()) {
                row {
                    EqRowContent {
                        Text(
                            text = stringResource(R.string.peq_presets_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            presets.forEach { preset ->
                row {
                    PresetRow(
                        preset = preset,
                        selected = preset.id == config.activePresetId,
                        onSelect = {
                            AppSettings.selectParametricEqPreset(preset.id)
                            Toast.makeText(context, context.getString(R.string.peq_preset_applied, preset.name), Toast.LENGTH_SHORT).show()
                        },
                        onDelete = {
                            AppSettings.deleteParametricEqPreset(preset.id)
                            Toast.makeText(context, context.getString(R.string.peq_preset_deleted, preset.name), Toast.LENGTH_SHORT).show()
                        },
                    )
                }
            }
        }

        // 3. File actions
        EqGroup(title = stringResource(R.string.peq_file)) {
            row {
                EqActionRow(
                    title = stringResource(R.string.peq_load_file),
                    subtitle = ".eel / EqualizerAPO .txt",
                    icon = Icons.Rounded.FileOpen,
                    onClick = { importEelLauncher.launch(arrayOf("*/*")) },
                )
            }
            row {
                EqActionRow(
                    title = stringResource(R.string.peq_save_as_preset),
                    icon = Icons.Rounded.BookmarkAdd,
                    onClick = { showSavePresetDialog = true },
                )
            }
            row {
                EqActionRow(
                    title = stringResource(R.string.peq_export_eel),
                    icon = Icons.Rounded.FileUpload,
                    onClick = {
                        val suggested = (config.activePresetName ?: "custom_peq")
                            .replace(Regex("""[^\w\-. ]"""), "_") + ".eel"
                        exportEelLauncher.launch(suggested)
                    },
                )
            }
            row {
                EqActionRow(
                    title = stringResource(R.string.peq_show_eel),
                    icon = Icons.Rounded.Code,
                    onClick = { showRawEelDialog = true },
                )
            }
            row {
                EqActionRow(
                    title = stringResource(R.string.peq_reset_flat),
                    icon = Icons.Rounded.RestartAlt,
                    titleColor = MaterialTheme.colorScheme.error,
                    showChevron = false,
                    onClick = {
                        AppSettings.resetParametricEqConfig()
                        Toast.makeText(context, context.getString(R.string.peq_reset_done), Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }

        // 4. Channel swap & preamp
        EqGroup(title = stringResource(R.string.peq_channel_preamp)) {
            row {
                EqSwitchRow(
                    title = stringResource(R.string.peq_swap_channels),
                    subtitle = if (config.swapChannels) {
                        if (config.swapBeforeEq) stringResource(R.string.peq_swap_before_desc) else stringResource(R.string.peq_swap_after_desc)
                    } else {
                        stringResource(R.string.peq_swap_off_desc)
                    },
                    checked = config.swapChannels,
                    onCheckedChange = { AppSettings.setParametricEqSwapChannels(it, config.swapBeforeEq) },
                    icon = Icons.Rounded.SwapHoriz,
                )
            }
            if (config.swapChannels) {
                row {
                    EqRowContent {
                        Text(
                            stringResource(R.string.peq_swap_position),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        EqSegmented(
                            options = listOf(stringResource(R.string.peq_before_eq), stringResource(R.string.peq_after_eq)),
                            selectedIndex = if (config.swapBeforeEq) 0 else 1,
                            onSelect = { AppSettings.setParametricEqSwapChannels(true, it == 0) },
                        )
                    }
                }
            }
            row {
                val linGain = Math.pow(10.0, config.totalPreampDb.toDouble() / 20.0)
                EqActionRow(
                    title = stringResource(R.string.peq_preamp_total, EelEqParser.fmtSignedDb(config.totalPreampDb)),
                    subtitle = stringResource(R.string.peq_preamp_linear_tap, String.format(Locale.US, "%.4f", linGain)),
                    icon = Icons.Rounded.Edit,
                    onClick = { editingPreampDialog = true },
                )
            }
            row {
                EqSliderRow(
                    label = stringResource(R.string.peq_global_preamp),
                    valueText = EelEqParser.fmtSignedDb(config.globalPreampDb),
                    value = config.globalPreampDb,
                    valueRange = -20f..12f,
                    onValueChange = {
                        val snapped = if (abs(it) < 0.08f) 0f else (kotlin.math.round(it * 10f) / 10f)
                        AppSettings.setParametricEqPreamps(globalDb = snapped)
                    },
                )
            }
            row {
                EqSliderRow(
                    label = stringResource(R.string.peq_lr_preamp),
                    valueText = EelEqParser.fmtSignedDb(config.lrPreampDb),
                    value = config.lrPreampDb,
                    valueRange = -20f..12f,
                    onValueChange = {
                        val snapped = if (abs(it) < 0.08f) 0f else (kotlin.math.round(it * 10f) / 10f)
                        AppSettings.setParametricEqPreamps(lrDb = snapped)
                    },
                )
            }
        }

        // 5. Filters
        EqGroup(title = stringResource(R.string.peq_filters)) {
            row {
                EqRowContent {
                    EqSegmented(
                        options = listOf(
                            stringResource(R.string.peq_tab_overview, report.globalBands.size + report.leftBands.size + report.rightBands.size),
                            stringResource(R.string.peq_tab_shared, config.globalBands.size),
                            "L ${config.leftBands.size}",
                            "R ${config.rightBands.size}",
                        ),
                        selectedIndex = selectedTab,
                        onSelect = { selectedTab = it },
                    )
                }
            }
            val toggleExpand: (String) -> Unit = { id ->
                expandedBandIds = if (id in expandedBandIds) expandedBandIds - id else expandedBandIds + id
            }
            when (selectedTab) {
                0 -> filterBreakdownRows(
                    config = config,
                    report = report,
                    onJumpToEdit = { target, index -> editingBandTarget = target to index },
                )
                else -> {
                    val target = when (selectedTab) {
                        1 -> ParametricEqChannelTarget.GLOBAL
                        2 -> ParametricEqChannelTarget.LEFT
                        else -> ParametricEqChannelTarget.RIGHT
                    }
                    val bands = when (target) {
                        ParametricEqChannelTarget.GLOBAL -> config.globalBands
                        ParametricEqChannelTarget.LEFT -> config.leftBands
                        ParametricEqChannelTarget.RIGHT -> config.rightBands
                    }
                    val opposite = when (target) {
                        ParametricEqChannelTarget.LEFT -> config.rightBands
                        ParametricEqChannelTarget.RIGHT -> config.leftBands
                        ParametricEqChannelTarget.GLOBAL -> emptyList()
                    }
                    channelBandRows(
                        target = target,
                        bands = bands,
                        expandedBandIds = expandedBandIds,
                        onToggleExpand = toggleExpand,
                        onBandsChange = { AppSettings.setParametricEqChannelBands(target, it) },
                        onOpenNumericEditor = { idx -> editingBandTarget = target to idx },
                        onCopyFromOpposite = if (opposite.isNotEmpty()) {
                            {
                                AppSettings.setParametricEqChannelBands(
                                    target,
                                    opposite.map { it.copy(id = java.util.UUID.randomUUID().toString()) },
                                )
                            }
                        } else null,
                        onInvertFromOpposite = if (opposite.isNotEmpty()) {
                            {
                                AppSettings.setParametricEqChannelBands(
                                    target,
                                    opposite.map { it.copy(gainDb = -it.gainDb, id = java.util.UUID.randomUUID().toString()) },
                                )
                            }
                        } else null,
                    )
                }
            }
        }
    }

    // Dialogs
    if (showSavePresetDialog) {
        SavePresetDialog(
            initialName = config.activePresetName ?: "My PEQ Preset",
            onDismiss = { showSavePresetDialog = false },
            onSave = { name ->
                val saved = AppSettings.saveCurrentAsParametricEqPreset(name)
                showSavePresetDialog = false
                Toast.makeText(context, context.getString(R.string.peq_preset_saved, saved.name), Toast.LENGTH_SHORT).show()
            },
        )
    }

    if (showRawEelDialog) {
        RawEelPreviewDialog(
            eelCode = BiquadUtils.toEelString(config),
            onDismiss = { showRawEelDialog = false },
        )
    }

    if (editingPreampDialog) {
        PreampNumericEditDialog(
            config = config,
            onDismiss = { editingPreampDialog = false },
            onConfirm = { gDb, lrDb, lDb, rDb ->
                AppSettings.setParametricEqPreamps(globalDb = gDb, lrDb = lrDb, leftDb = lDb, rightDb = rDb)
                editingPreampDialog = false
            },
        )
    }

    editingBandTarget?.let { (target, index) ->
        val list = when (target) {
            ParametricEqChannelTarget.GLOBAL -> config.globalBands
            ParametricEqChannelTarget.LEFT -> config.leftBands
            ParametricEqChannelTarget.RIGHT -> config.rightBands
        }
        val band = list.getOrNull(index)
        if (band != null) {
            BandNumericEditDialog(
                target = target,
                bandIndex = index,
                band = band,
                onDismiss = { editingBandTarget = null },
                onConfirm = { updatedBand ->
                    val mutable = list.toMutableList()
                    if (index in mutable.indices) {
                        mutable[index] = updatedBand.sanitized(band)
                        AppSettings.setParametricEqChannelBands(target, mutable)
                    }
                    editingBandTarget = null
                },
            )
        } else {
            editingBandTarget = null
        }
    }
}

/** A connected row of options; the selected one is filled. No check glyph, so short labels fit on a phone. */
@Composable
internal fun EqSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    enabled: Boolean = true,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, label ->
            SegmentedButton(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                colors = eqSegmentedColors(),
                icon = {},
                label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresetRow(
    preset: ParametricEqPreset,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    EqRowContent(
        modifier = if (selected) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier,
        onClick = onSelect,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            EqLeadingIcon(if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.Tune)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = preset.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val tagBg = MaterialTheme.colorScheme.surfaceContainerHighest
                    val tagFg = MaterialTheme.colorScheme.onSurfaceVariant
                    if (selected) EqTag(stringResource(R.string.peq_active), MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
                    if (preset.swapChannels) EqTag("L↔R", tagBg, tagFg)
                    if (abs(preset.totalPreampDb) > 0.001f) {
                        EqTag("Pre ${EelEqParser.fmtSignedDb(preset.totalPreampDb)}", tagBg, tagFg)
                    }
                    EqTag(
                        stringResource(R.string.peq_preset_band_counts, preset.globalBands.size, preset.leftBands.size, preset.rightBands.size),
                        tagBg,
                        tagFg,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = stringResource(R.string.peq_delete_preset),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * The combined L/R frequency response, drawn on the group's own surface in the
 * theme's colours rather than as a separate dark panel, with axis labels placed
 * at their true log-frequency positions.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ParametricEqResponseGraph(config: ParametricEqConfig) {
    val response = remember(
        config.globalBands,
        config.leftBands,
        config.rightBands,
        config.globalPreampDb,
        config.lrPreampDb,
        config.leftGainDb,
        config.rightGainDb,
    ) {
        BiquadUtils.computeChannelResponses(config, numPoints = 200)
    }

    val minCurve = minOf(
        response.leftGainsDb.minOrNull() ?: 0.0,
        response.rightGainsDb.minOrNull() ?: 0.0,
        response.globalGainsDb.minOrNull() ?: 0.0,
    )
    val maxCurve = maxOf(
        response.leftGainsDb.maxOrNull() ?: 0.0,
        response.rightGainsDb.maxOrNull() ?: 0.0,
        response.globalGainsDb.maxOrNull() ?: 0.0,
    )
    val minDb = floor(minOf(minCurve - 2.0, -12.0)).coerceAtLeast(-36.0)
    val maxDb = ceil(maxOf(maxCurve + 2.0, 12.0)).coerceAtMost(36.0)

    val leftColor = eqLeftColor()
    val rightColor = eqRightColor()
    val globalColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    val zeroLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val plotBg = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = labelColor)

    EqRowContent {
        Text(
            text = stringResource(R.string.peq_frequency_response),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(plotBg, RoundedCornerShape(16.dp)),
        ) {
            val labelBand = 18.dp.toPx()
            val padX = 8.dp.toPx()
            val padTop = 10.dp.toPx()
            val w = size.width - padX * 2
            val h = size.height - labelBand - padTop
            val logMin = ln(20.0)
            val logMax = ln(20_000.0)

            fun xOfFreq(f: Double): Float =
                padX + (((ln(f.coerceIn(20.0, 20_000.0)) - logMin) / (logMax - logMin)) * w).toFloat()

            fun yOfDb(db: Double): Float {
                val norm = ((db - minDb) / (maxDb - minDb)).coerceIn(0.0, 1.0)
                return padTop + ((1.0 - norm) * h).toFloat()
            }

            // dB grid every 6 dB, labelled at the left edge
            var dbLine = (ceil(minDb / 6.0) * 6.0).toInt()
            while (dbLine <= maxDb.toInt()) {
                val y = yOfDb(dbLine.toDouble())
                drawLine(
                    color = if (dbLine == 0) zeroLineColor else gridColor,
                    start = Offset(padX, y),
                    end = Offset(padX + w, y),
                    strokeWidth = if (dbLine == 0) 1.5.dp.toPx() else 1.dp.toPx(),
                )
                val label = if (dbLine > 0) "+$dbLine" else "$dbLine"
                val measured = textMeasurer.measure(label, labelStyle)
                drawText(measured, topLeft = Offset(padX + 4.dp.toPx(), y - measured.size.height - 1f))
                dbLine += 6
            }

            // Frequency grid, labelled underneath at the true positions
            val freqGrid = listOf(20.0 to "20", 100.0 to "100", 1000.0 to "1k", 10_000.0 to "10k", 20_000.0 to "20k")
            for (f in doubleArrayOf(50.0, 200.0, 500.0, 2000.0, 5000.0)) {
                val x = xOfFreq(f)
                drawLine(gridColor, Offset(x, padTop), Offset(x, padTop + h), strokeWidth = 1.dp.toPx())
            }
            for ((f, label) in freqGrid) {
                val x = xOfFreq(f)
                drawLine(gridColor, Offset(x, padTop), Offset(x, padTop + h), strokeWidth = 1.dp.toPx())
                val measured = textMeasurer.measure(label, labelStyle)
                val tx = (x - measured.size.width / 2f).coerceIn(0f, size.width - measured.size.width)
                drawText(measured, topLeft = Offset(tx, padTop + h + 3.dp.toPx()))
            }

            val freqs = response.frequencies
            if (freqs.isNotEmpty()) {
                val globalPath = Path()
                val leftPath = Path()
                val rightPath = Path()
                val leftFillPath = Path()
                for (i in freqs.indices) {
                    val x = xOfFreq(freqs[i])
                    val yg = yOfDb(response.globalGainsDb[i])
                    val yl = yOfDb(response.leftGainsDb[i])
                    val yr = yOfDb(response.rightGainsDb[i])
                    if (i == 0) {
                        globalPath.moveTo(x, yg); leftPath.moveTo(x, yl); rightPath.moveTo(x, yr); leftFillPath.moveTo(x, yl)
                    } else {
                        globalPath.lineTo(x, yg); leftPath.lineTo(x, yl); rightPath.lineTo(x, yr); leftFillPath.lineTo(x, yl)
                    }
                }
                val zeroY = yOfDb(0.0)
                leftFillPath.lineTo(padX + w, zeroY)
                leftFillPath.lineTo(padX, zeroY)
                leftFillPath.close()
                drawPath(
                    path = leftFillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(leftColor.copy(alpha = 0.22f), leftColor.copy(alpha = 0.02f)),
                        startY = padTop,
                        endY = padTop + h,
                    ),
                )
                drawPath(
                    path = globalPath,
                    color = globalColor,
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f),
                    ),
                )
                drawPath(rightPath, rightColor, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(leftPath, leftColor, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            LegendDot(color = leftColor, label = "L ch")
            LegendDot(color = rightColor, label = "R ch")
            LegendDot(color = globalColor, label = stringResource(R.string.peq_legend_shared))
        }

        val lPeak = response.leftGainsDb.maxOrNull() ?: 0.0
        val lMin = response.leftGainsDb.minOrNull() ?: 0.0
        val rPeak = response.rightGainsDb.maxOrNull() ?: 0.0
        val rMin = response.rightGainsDb.minOrNull() ?: 0.0
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.peq_peak_min, "L", String.format(Locale.US, "%+.1f", lPeak), String.format(Locale.US, "%+.1f", lMin)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.peq_peak_min, "R", String.format(Locale.US, "%+.1f", rPeak), String.format(Locale.US, "%+.1f", rMin)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ActiveDspSummary(
    config: ParametricEqConfig,
    report: EelEqParser.EelAnalysisReport,
) {
    EqRowContent {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.peq_current_config),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = config.activePresetName ?: stringResource(R.string.peq_custom),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            EqTag(
                text = if (config.enabled) stringResource(R.string.peq_running) else stringResource(R.string.off),
                container = if (config.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                content = if (config.enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!config.sourceFileName.isNullOrBlank()) {
            Text(
                text = stringResource(R.string.peq_source, config.sourceFileName.orEmpty()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!config.notes.isNullOrBlank()) {
            Text(
                text = stringResource(R.string.peq_notes, config.notes.orEmpty()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        peqSummaryLines(report).forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Small label row that heads a run of filter rows inside the group. */
@Composable
private fun SubheaderRow(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

@Composable
private fun EmptyRow(text: String) {
    EqRowContent {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The read-only overview of every filter, one grouped row per band. */
private fun EqGroupScope.filterBreakdownRows(
    config: ParametricEqConfig,
    report: EelEqParser.EelAnalysisReport,
    onJumpToEdit: (ParametricEqChannelTarget, Int) -> Unit,
) {
    row { SubheaderRow(stringResource(R.string.peq_sub_shared, config.globalBands.size)) }
    if (config.globalBands.isEmpty()) {
        row { EmptyRow(stringResource(R.string.peq_empty_shared)) }
    } else {
        config.globalBands.forEachIndexed { idx, band ->
            row { BandSummaryRow(index = idx + 1, band = band, onClick = { onJumpToEdit(ParametricEqChannelTarget.GLOBAL, idx) }) }
        }
    }

    row { SubheaderRow(stringResource(R.string.peq_sub_lr, config.leftBands.size, config.rightBands.size)) }
    val pairedView = report.lrPairs.isNotEmpty() &&
        report.lrPairs.size == config.leftBands.size &&
        config.leftBands.size == config.rightBands.size
    when {
        config.leftBands.isEmpty() && config.rightBands.isEmpty() ->
            row { EmptyRow(stringResource(R.string.peq_empty_lr)) }
        pairedView -> report.lrPairs.forEachIndexed { idx, pair ->
            row {
                LrPairRow(
                    pair = pair,
                    onEditLeft = { onJumpToEdit(ParametricEqChannelTarget.LEFT, idx) },
                    onEditRight = { onJumpToEdit(ParametricEqChannelTarget.RIGHT, idx) },
                )
            }
        }
        else -> {
            config.leftBands.forEachIndexed { idx, band ->
                row { BandSummaryRow(idx + 1, band, channel = "L", onClick = { onJumpToEdit(ParametricEqChannelTarget.LEFT, idx) }) }
            }
            config.rightBands.forEachIndexed { idx, band ->
                row { BandSummaryRow(idx + 1, band, channel = "R", onClick = { onJumpToEdit(ParametricEqChannelTarget.RIGHT, idx) }) }
            }
        }
    }
}

@Composable
private fun IndexBadge(text: String, color: Color = MaterialTheme.colorScheme.surfaceContainerHighest) {
    Box(
        modifier = Modifier.size(36.dp).background(color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun gainColor(band: ParametricEqBand): Color = when {
    !band.enabled -> MaterialTheme.colorScheme.outline
    else -> MaterialTheme.colorScheme.onSurface
}

@Composable
private fun BandSummaryRow(
    index: Int,
    band: ParametricEqBand,
    onClick: () -> Unit,
    channel: String? = null,
) {
    EqRowContent(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IndexBadge(
                text = if (channel != null) "$channel$index" else "$index",
                color = when (channel) {
                    "L" -> eqLeftColor().copy(alpha = 0.25f)
                    "R" -> eqRightColor().copy(alpha = 0.25f)
                    else -> MaterialTheme.colorScheme.surfaceContainerHighest
                },
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterTypeTag(band.filterType)
                    Text(
                        text = EelEqParser.fmtHz(band.frequencyHz),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (band.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                    )
                }
                Text(
                    text = String.format(Locale.US, "Q %.3f", band.q),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = EelEqParser.fmtSignedDb(band.gainDb),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = gainColor(band),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun LrPairRow(
    pair: EelEqParser.FilterPairDiff,
    onEditLeft: () -> Unit,
    onEditRight: () -> Unit,
) {
    EqRowContent {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IndexBadge("${pair.index}")
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterTypeTag(pair.filterType)
                    Text(
                        text = EelEqParser.fmtHz(pair.frequencyHz),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
                Text(
                    text = String.format(Locale.US, "Q %.3f", pair.q),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ChannelGainChip("L", pair.leftGainDb, eqLeftColor(), onEditLeft)
                ChannelGainChip("R", pair.rightGainDb, eqRightColor(), onEditRight)
            }
        }
    }
}

@Composable
private fun ChannelGainChip(label: String, gainDb: Float, color: Color, onClick: () -> Unit) {
    Text(
        text = "$label ${EelEqParser.fmtSignedDb(gainDb)}",
        style = MaterialTheme.typography.labelLarge,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        modifier = Modifier
            .background(color.copy(alpha = 0.22f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** The editable band list for one channel. */
@OptIn(ExperimentalLayoutApi::class)
private fun EqGroupScope.channelBandRows(
    target: ParametricEqChannelTarget,
    bands: List<ParametricEqBand>,
    expandedBandIds: Set<String>,
    onToggleExpand: (String) -> Unit,
    onBandsChange: (List<ParametricEqBand>) -> Unit,
    onOpenNumericEditor: (Int) -> Unit,
    onCopyFromOpposite: (() -> Unit)?,
    onInvertFromOpposite: (() -> Unit)?,
) {
    row {
        EqRowContent {
            Text(
                text = stringResource(R.string.peq_target_bands, stringResource(target.labelRes), bands.size),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = {
                        val nextFreq = when (bands.size) {
                            0 -> 60f
                            1 -> 250f
                            2 -> 1000f
                            3 -> 4000f
                            else -> 8000f
                        }
                        onBandsChange(
                            bands + ParametricEqBand(
                                frequencyHz = nextFreq,
                                q = 1.414f,
                                gainDb = 0f,
                                filterType = ParametricEqFilterType.PEAKING,
                            ),
                        )
                    },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.peq_add_band))
                }
                if (bands.isNotEmpty()) {
                    TextButton(onClick = { onBandsChange(emptyList()) }) {
                        Text(stringResource(R.string.peq_clear_all), color = MaterialTheme.colorScheme.error)
                    }
                }
                val otherCh = if (target == ParametricEqChannelTarget.LEFT) "R" else "L"
                onCopyFromOpposite?.let { copyFn ->
                    AssistChip(
                        onClick = copyFn,
                        label = { Text(stringResource(R.string.peq_copy_from, otherCh)) },
                        shape = RoundedCornerShape(50),
                        border = null,
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ),
                    )
                }
                onInvertFromOpposite?.let { invFn ->
                    AssistChip(
                        onClick = invFn,
                        label = { Text(stringResource(R.string.peq_copy_inverted_from, otherCh)) },
                        shape = RoundedCornerShape(50),
                        border = null,
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ),
                    )
                }
            }
        }
    }

    if (bands.isEmpty()) {
        row { EmptyRow(stringResource(R.string.peq_empty_channel)) }
    } else {
        bands.forEachIndexed { index, band ->
            row {
                BandEditorRow(
                    index = index,
                    band = band,
                    expanded = band.id in expandedBandIds || bands.size <= 3,
                    onToggleExpand = { onToggleExpand(band.id) },
                    onBandChange = { updatedBand ->
                        val updated = bands.toMutableList()
                        updated[index] = updatedBand
                        onBandsChange(updated)
                    },
                    onDelete = { onBandsChange(bands.toMutableList().apply { removeAt(index) }) },
                    onOpenNumericEditor = { onOpenNumericEditor(index) },
                )
            }
        }
    }
}

@Composable
private fun BandEditorRow(
    index: Int,
    band: ParametricEqBand,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onBandChange: (ParametricEqBand) -> Unit,
    onDelete: () -> Unit,
    onOpenNumericEditor: () -> Unit,
) {
    EqRowContent {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onToggleExpand),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                IndexBadge("${index + 1}")
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterTypeTag(band.filterType)
                        Text(
                            text = EelEqParser.fmtHz(band.frequencyHz),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                        )
                    }
                    Text(
                        text = "${EelEqParser.fmtSignedDb(band.gainDb)}  ・  Q ${String.format(Locale.US, "%.3f", band.q)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = band.enabled,
                onCheckedChange = { onBandChange(band.copy(enabled = it)) },
                colors = eqSwitchColors(),
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FILTER_TYPES.forEach { ft ->
                        FilterChip(
                            selected = band.filterType == ft,
                            onClick = { onBandChange(band.copy(filterType = ft)) },
                            label = { Text(ft.displayLabel, maxLines = 1) },
                            shape = RoundedCornerShape(50),
                            border = null,
                            colors = eqFilterChipColors(),
                        )
                    }
                }
                EqSliderBody(
                    label = stringResource(R.string.parametric_eq_frequency),
                    valueText = EelEqParser.fmtHz(band.frequencyHz),
                    value = frequencyToPosition(band.frequencyHz),
                    onValueChange = { onBandChange(band.copy(frequencyHz = positionToFrequency(it))) },
                )
                EqSliderBody(
                    label = stringResource(R.string.parametric_eq_q),
                    valueText = String.format(Locale.US, "%.3f", band.q),
                    value = band.q.coerceIn(0.1f, 10f),
                    valueRange = 0.1f..10f,
                    onValueChange = { onBandChange(band.copy(q = it)) },
                )
                EqSliderBody(
                    label = stringResource(R.string.parametric_eq_gain),
                    valueText = EelEqParser.fmtSignedDb(band.gainDb),
                    value = band.gainDb.coerceIn(-20f, 20f),
                    valueRange = -20f..20f,
                    onValueChange = { onBandChange(band.copy(gainDb = it)) },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(onClick = onOpenNumericEditor) {
                        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.peq_enter_value))
                    }
                    TextButton(onClick = onDelete) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun eqFilterChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    labelColor = MaterialTheme.colorScheme.onSurface,
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
)

@Composable
private fun FilterTypeTag(type: ParametricEqFilterType) {
    EqTag(
        text = type.displayLabel,
        container = MaterialTheme.colorScheme.surfaceContainerHighest,
        content = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun SavePresetDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text(stringResource(R.string.peq_save_as_preset)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.peq_save_preset_desc),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.peq_preset_name)) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun RawEelPreviewDialog(
    eelCode: String,
    onDismiss: () -> Unit,
) {
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text(stringResource(R.string.peq_eel_code)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(16.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
            ) {
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(
                        text = eelCode,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 11.sp,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun PreampNumericEditDialog(
    config: ParametricEqConfig,
    onDismiss: () -> Unit,
    onConfirm: (Float, Float, Float, Float) -> Unit,
) {
    var globalText by remember { mutableStateOf(config.globalPreampDb.toString()) }
    var lrText by remember { mutableStateOf(config.lrPreampDb.toString()) }
    var leftText by remember { mutableStateOf(config.leftGainDb.toString()) }
    var rightText by remember { mutableStateOf(config.rightGainDb.toString()) }

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text(stringResource(R.string.peq_preamp_db)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DecimalField(globalText, { globalText = it }, stringResource(R.string.peq_global_preamp))
                DecimalField(lrText, { lrText = it }, stringResource(R.string.peq_lr_preamp))
                DecimalField(leftText, { leftText = it }, stringResource(R.string.peq_left_extra_gain))
                DecimalField(rightText, { rightText = it }, stringResource(R.string.peq_right_extra_gain))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        globalText.toFloatOrNull() ?: config.globalPreampDb,
                        lrText.toFloatOrNull() ?: config.lrPreampDb,
                        leftText.toFloatOrNull() ?: config.leftGainDb,
                        rightText.toFloatOrNull() ?: config.rightGainDb,
                    )
                },
            ) { Text(stringResource(R.string.peq_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun DecimalField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BandNumericEditDialog(
    target: ParametricEqChannelTarget,
    bandIndex: Int,
    band: ParametricEqBand,
    onDismiss: () -> Unit,
    onConfirm: (ParametricEqBand) -> Unit,
) {
    var selectedType by remember { mutableStateOf(band.filterType) }
    var freqText by remember { mutableStateOf(band.frequencyHz.toString()) }
    var gainText by remember { mutableStateOf(band.gainDb.toString()) }
    var qText by remember { mutableStateOf(band.q.toString()) }

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text(stringResource(R.string.peq_band_title, target.shortLabel, bandIndex + 1)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.peq_filter_type), style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FILTER_TYPES.forEach { ft ->
                        FilterChip(
                            selected = selectedType == ft,
                            onClick = { selectedType = ft },
                            label = { Text(ft.displayLabel) },
                            shape = RoundedCornerShape(50),
                            border = null,
                            colors = eqFilterChipColors(),
                        )
                    }
                }
                DecimalField(freqText, { freqText = it }, stringResource(R.string.peq_frequency_field))
                DecimalField(gainText, { gainText = it }, stringResource(R.string.peq_gain_field))
                DecimalField(qText, { qText = it }, "Q 0.05–30")
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        band.copy(
                            filterType = selectedType,
                            frequencyHz = freqText.toFloatOrNull() ?: band.frequencyHz,
                            gainDb = gainText.toFloatOrNull() ?: band.gainDb,
                            q = qText.toFloatOrNull() ?: band.q,
                        ),
                    )
                },
            ) { Text(stringResource(R.string.peq_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun ReverbControls(
    enabled: Boolean,
    amount: Int,
    onEnabledChange: (Boolean) -> Unit,
    onAmountChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    EqGroup(modifier = modifier, title = stringResource(R.string.reverb)) {
        row {
            EqSwitchRow(
                title = stringResource(R.string.reverb),
                subtitle = stringResource(R.string.reverb_subtitle),
                checked = enabled,
                onCheckedChange = onEnabledChange,
                icon = Icons.Rounded.Waves,
            )
        }
        row {
            EqSliderRow(
                label = stringResource(R.string.reverb_amount),
                valueText = "$amount%",
                value = amount.toFloat(),
                onValueChange = { onAmountChange(it.toInt()) },
                valueRange = 0f..100f,
                enabled = enabled,
            )
        }
    }
}

private fun frequencyToPosition(frequencyHz: Float): Float {
    val minLog = ln(20f)
    val maxLog = ln(20_000f)
    return ((ln(frequencyHz.coerceIn(20f, 20_000f)) - minLog) / (maxLog - minLog)).coerceIn(0f, 1f)
}

private fun positionToFrequency(position: Float): Float {
    val minLog = ln(20f)
    val maxLog = ln(20_000f)
    return exp(minLog + (maxLog - minLog) * position.coerceIn(0f, 1f))
}

private fun queryDisplayName(context: Context, uri: Uri): String? {
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx) else null
            } else null
        }
    }.getOrNull() ?: uri.lastPathSegment
}

/** The current setup in a few readable lines, in the user's language. */
@Composable
private fun peqSummaryLines(report: EelEqParser.EelAnalysisReport): List<String> {
    val res = LocalContext.current.resources
    return remember(report, res) {
        buildList {
            if (report.swapChannels) {
                val timing = res.getString(if (report.swapBeforeEq) R.string.peq_sum_before else R.string.peq_sum_after)
                add(res.getString(R.string.peq_sum_swap_on, timing))
            } else {
                add(res.getString(R.string.peq_sum_swap_off))
            }

            if (abs(report.globalPreampDb) > 0.001f || abs(report.lrPreampDb) > 0.001f ||
                abs(report.leftGainDb) > 0.001f || abs(report.rightGainDb) > 0.001f
            ) {
                val parts = mutableListOf<String>()
                if (abs(report.globalPreampDb) > 0.001f) parts.add("Global ${EelEqParser.fmtSignedDb(report.globalPreampDb)}")
                if (abs(report.lrPreampDb) > 0.001f) parts.add("L/R ${EelEqParser.fmtSignedDb(report.lrPreampDb)}")
                if (abs(report.leftGainDb) > 0.001f || abs(report.rightGainDb) > 0.001f) {
                    parts.add(
                        res.getString(
                            R.string.peq_sum_ch_trim,
                            EelEqParser.fmtSignedDb(report.leftGainDb),
                            EelEqParser.fmtSignedDb(report.rightGainDb),
                        ),
                    )
                }
                val left = String.format(Locale.US, "%.4f", report.linearGainLeft)
                val right = String.format(Locale.US, "%.4f", report.linearGainRight)
                val total = if (abs(report.totalLeftPreampDb - report.totalRightPreampDb) < 0.005f) {
                    res.getString(R.string.peq_sum_total_linear, EelEqParser.fmtSignedDb(report.totalLeftPreampDb), left)
                } else {
                    "L: ${EelEqParser.fmtSignedDb(report.totalLeftPreampDb)} (×$left) / " +
                        "R: ${EelEqParser.fmtSignedDb(report.totalRightPreampDb)} (×$right)"
                }
                add(res.getString(R.string.peq_sum_preamp, parts.joinToString(" + "), total))
            } else {
                add(res.getString(R.string.peq_sum_preamp_zero))
            }

            val global = report.globalBands
            if (global.isNotEmpty()) {
                val typeCounts = global.groupingBy { it.filterType.displayLabel }.eachCount()
                    .entries.joinToString(", ") { "${it.key}×${it.value}" }
                add(
                    res.getString(
                        R.string.peq_sum_global,
                        global.size,
                        typeCounts,
                        EelEqParser.fmtHz(global.minOf { it.frequencyHz }),
                        EelEqParser.fmtHz(global.maxOf { it.frequencyHz }),
                    ),
                )
            } else {
                add(res.getString(R.string.peq_sum_global_none))
            }

            val left = report.leftBands
            val right = report.rightBands
            if (left.isNotEmpty() || right.isNotEmpty()) {
                val complementary = report.lrPairs.count { it.isComplementary }
                val note = when {
                    complementary > 0 && complementary == left.size && left.size == right.size ->
                        res.getString(R.string.peq_sum_all_pairs, complementary)
                    complementary > 0 -> res.getString(R.string.peq_sum_some_pairs, complementary)
                    else -> ""
                }
                add(res.getString(R.string.peq_sum_lr, left.size, right.size, note))
            } else {
                add(res.getString(R.string.peq_sum_lr_none))
            }
        }
    }
}
