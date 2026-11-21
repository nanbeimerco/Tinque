package com.music.bitchord.feature.lyricscatalog

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.LocalMediaRepository
import com.music.bitchord.data.lyrics.LyricsCatalog
import com.music.bitchord.data.lyrics.LyricsFormat
import com.music.bitchord.data.lyrics.LyricsOverrideStore
import com.music.bitchord.data.lyrics.align.LyricsAlignTaskManager
import com.music.bitchord.data.lyrics.align.OnDeviceLyricsAligner
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.feature.lyricseditor.data.LocalLyricsManager
import com.music.bitchord.feature.lyricseditor.domain.model.LyricsEditorSource
import com.music.bitchord.ui.components.GlassAlertDialog
import com.music.bitchord.ui.components.GlassContentTheme
import com.music.bitchord.ui.components.LocalLiquidGlassEnabled
import com.music.bitchord.ui.components.glassPalette
import com.music.bitchord.ui.components.isGlassSupported
import com.music.bitchord.ui.components.lightweightLiquidGlass
import com.music.bitchord.ui.components.liquidGlass
import com.music.bitchord.ui.isSameTrackForLyrics
import kotlinx.coroutines.launch

/** UI state shared by every [LyricsAlignFloatingHost] instance. */
object LyricsAlignPopupState {
    /** The completed task whose actions are expanded, or null for the compact pill. */
    val expandedTaskIdState = mutableStateOf<String?>(null)
}

/** A check in front of a button label once that action has been done. */
@Composable
private fun DoneMark(done: Boolean) {
    if (done) {
        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
    }
}

/**
 * Floating progress & completion popup for background AI lyrics alignment.
 * Automatically adapts between Liquid Glass and Material 3 Expressive surfaces,
 * expanding in-place when tapped after alignment finishes.
 */
@Composable
fun LyricsAlignFloatingHost(
    visible: Boolean,
    glassBackdrop: Boolean,
    currentPlayingSong: Song?,
    onApplied: (Song) -> Unit,
    onPlayAndOpenLyrics: (Song) -> Unit,
    onOpenCatalog: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snapshot by LyricsAlignTaskManager.state.collectAsStateWithLifecycle()
    val wordSyncSetting by AppSettings.lyricsSaveWordSync.collectAsStateWithLifecycle()
    val keepTranslation by AppSettings.lyricsKeepTranslation.collectAsStateWithLifecycle()
    val keepFurigana by AppSettings.lyricsKeepFurigana.collectAsStateWithLifecycle()

    // Shared by every host (over the page and inside the player), so a popup left
    // open in the player is still open above the mini player once it closes.
    var expandedTaskId by LyricsAlignPopupState.expandedTaskIdState
    val expandedTask = expandedTaskId?.let { id ->
        snapshot.completed.firstOrNull { it.taskId == id }
    }
    if (expandedTaskId != null && expandedTask == null) {
        expandedTaskId = null
    }

    val hasWork = snapshot.active != null ||
        snapshot.queued.isNotEmpty() ||
        snapshot.completed.isNotEmpty() ||
        snapshot.failed.isNotEmpty()

    var pendingEmbedTask by remember { mutableStateOf<Pair<LyricsAlignTaskManager.AlignTask, String>?>(null) }
    var pendingOverwriteLrc by remember { mutableStateOf<Triple<LyricsAlignTaskManager.AlignTask, String, String>?>(null) }
    var pendingSafLrc by remember { mutableStateOf<Triple<LyricsAlignTaskManager.AlignTask, LocalLyricsManager.LrcSaveResult.NeedsDirectoryAccess, String>?>(null) }

    fun saveTaskLyrics(
        task: LyricsAlignTaskManager.AlignTask,
        target: LyricsEditorSource,
        text: String,
    ) {
        scope.launch {
            when (val outcome = LocalLyricsManager.saveLyricsDetailed(context, task.song, target, text)) {
                is LocalLyricsManager.LrcSaveResult.Success -> {
                    Toast.makeText(context, context.getString(R.string.l10n_lyrics_saved), Toast.LENGTH_SHORT).show()
                    onApplied(task.song)
                    // Stay open: the user may still want the other target (LRC and embed).
                    LyricsAlignTaskManager.markActionDone(
                        task.taskId,
                        if (target == LyricsEditorSource.File) {
                            LyricsAlignTaskManager.ResultAction.SAVED_LRC
                        } else {
                            LyricsAlignTaskManager.ResultAction.EMBEDDED
                        },
                    )
                }
                is LocalLyricsManager.LrcSaveResult.NeedsDirectoryAccess -> {
                    if (target == LyricsEditorSource.File) {
                        pendingSafLrc = Triple(task, outcome, text)
                    } else {
                        Toast.makeText(context, context.getString(R.string.l10n_lyrics_save_failed), Toast.LENGTH_SHORT).show()
                    }
                }
                is LocalLyricsManager.LrcSaveResult.Failed -> {
                    Toast.makeText(context, context.getString(R.string.l10n_lyrics_save_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val safTreeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri ->
        val pending = pendingSafLrc
        pendingSafLrc = null
        if (treeUri != null && pending != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            saveTaskLyrics(pending.first, LyricsEditorSource.File, pending.third)
        } else if (pending != null) {
            Toast.makeText(context, context.getString(R.string.l10n_lyrics_save_failed), Toast.LENGTH_SHORT).show()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val pending = pendingEmbedTask
        pendingEmbedTask = null
        if (result.resultCode == Activity.RESULT_OK && pending != null) {
            saveTaskLyrics(pending.first, LyricsEditorSource.Embedded, pending.second)
        } else {
            Toast.makeText(context, context.getString(R.string.l10n_no_edit_permission), Toast.LENGTH_SHORT).show()
        }
    }

    fun applyInApp(task: LyricsAlignTaskManager.AlignTask, collapse: Boolean) {
        val candidate = task.resultCandidate ?: return
        val stored = LyricsCatalog.serialize(
            candidate,
            wordSync = true,
            keepTranslation = keepTranslation,
            keepFurigana = true,
        )
        LyricsOverrideStore.set(
            task.songKey,
            LyricsOverrideStore.Override(candidate.source.id, candidate.source.label, stored),
        )
        onApplied(task.song)
        Toast.makeText(
            context,
            context.getString(R.string.lyrics_ai_applied_toast, task.song.title),
            Toast.LENGTH_SHORT,
        ).show()
        LyricsAlignTaskManager.markActionDone(task.taskId, LyricsAlignTaskManager.ResultAction.APPLIED)
        if (collapse) expandedTaskId = null
    }

    fun embedTask(task: LyricsAlignTaskManager.AlignTask, text: String) {
        val uri = task.song.localUri?.takeIf { it.startsWith("content://") }?.let(Uri::parse)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && uri != null) {
            val granted = context.checkUriPermission(
                uri,
                Process.myPid(),
                Process.myUid(),
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                val request = runCatching {
                    MediaStore.createWriteRequest(context.contentResolver, listOf(uri))
                }.getOrNull()
                if (request != null) {
                    pendingEmbedTask = task to text
                    permissionLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
                    return
                }
            }
        }
        saveTaskLyrics(task, LyricsEditorSource.Embedded, text)
    }

    AnimatedVisibility(
        visible = visible && hasWork,
        modifier = modifier,
        enter = fadeIn() +
            scaleIn(spring(0.75f, 700f), initialScale = 0.9f) +
            slideInVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) { it / 2 },
        exit = fadeOut(tween(140)) + scaleOut(tween(150), targetScale = 0.92f),
    ) {
        val palette = glassPalette()
        val glassEnabled = LocalLiquidGlassEnabled.current && isGlassSupported()
        val shape = RoundedCornerShape(if (expandedTask != null) 24.dp else 26.dp)
        val containerModifier = when {
            glassEnabled && glassBackdrop ->
                Modifier.liquidGlass(shape = shape, surfaceAlpha = 0.62f)
            glassEnabled ->
                Modifier.lightweightLiquidGlass(shape = shape, fallbackColor = palette.fallbackFill)
            else ->
                Modifier
                    .shadow(elevation = 10.dp, shape = shape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, shape)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), shape)
        }

        val inner: @Composable () -> Unit = {
            Column(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = 0.82f,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    ),
            ) {
                val completedItem = expandedTask
                val firstCompleted = snapshot.completed.firstOrNull()
                val active = snapshot.active
                val firstFailed = snapshot.failed.firstOrNull()

                when {
                    completedItem != null && completedItem.resultCandidate != null -> {
                        val cand = completedItem.resultCandidate
                        val hasFile = !completedItem.song.localUri.isNullOrBlank() ||
                            !completedItem.song.localPath.isNullOrBlank()
                        val isCurrentlyPlaying = currentPlayingSong != null &&
                            isSameTrackForLyrics(currentPlayingSong, completedItem.song)

                        fun serializedText() = LyricsCatalog.serialize(
                            cand,
                            wordSyncSetting,
                            keepTranslation,
                            keepFurigana,
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        completedItem.song.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "${cand.source.label} · ${stringResource(R.string.lyrics_ai_bg_done_title)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                IconButton(
                                    onClick = { expandedTaskId = null },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.ExpandLess,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        expandedTaskId = null
                                        LyricsAlignTaskManager.dismissCompleted(completedItem.taskId)
                                    },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.close),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                val previewText = remember(cand.id, cand.lines) {
                                    cand.lines.filter { it.text.isNotBlank() && it.timingSource == null }
                                        .take(4)
                                        .joinToString("\n") { it.text }
                                }
                                Text(
                                    text = previewText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                )
                            }

                            if (cand.format == LyricsFormat.WORD) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        stringResource(R.string.lyrics_keep_word_sync),
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Switch(
                                        checked = wordSyncSetting,
                                        onCheckedChange = AppSettings::setLyricsSaveWordSync,
                                    )
                                }
                            }

                            val done = completedItem.doneActions
                            val applied = LyricsAlignTaskManager.ResultAction.APPLIED in done
                            val savedLrc = LyricsAlignTaskManager.ResultAction.SAVED_LRC in done
                            val embedded = LyricsAlignTaskManager.ResultAction.EMBEDDED in done

                            Button(
                                onClick = { applyInApp(completedItem, collapse = false) },
                                enabled = !applied,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                DoneMark(applied)
                                Text(
                                    stringResource(R.string.lyrics_use_for_song),
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }

                            if (hasFile) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    FilledTonalButton(
                                        onClick = {
                                            val lyricsText = serializedText()
                                            val existing = LocalLyricsManager.findExistingLrcFile(context, completedItem.song)
                                            if (existing != null) {
                                                pendingOverwriteLrc = Triple(completedItem, existing.name, lyricsText)
                                            } else {
                                                saveTaskLyrics(completedItem, LyricsEditorSource.File, lyricsText)
                                            }
                                        },
                                        enabled = !savedLrc,
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        DoneMark(savedLrc)
                                        Text(
                                            stringResource(R.string.lyrics_save_lrc),
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                    FilledTonalButton(
                                        onClick = { embedTask(completedItem, serializedText()) },
                                        enabled = !embedded,
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        DoneMark(embedded)
                                        Text(
                                            stringResource(R.string.lyrics_embed),
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                            }

                            FilledTonalButton(
                                onClick = {
                                    // Leaves the task in place (collapsed to its pill) so
                                    // saving or embedding is still one tap away afterwards.
                                    if (!applied) {
                                        applyInApp(completedItem, collapse = true)
                                    } else {
                                        expandedTaskId = null
                                    }
                                    onPlayAndOpenLyrics(completedItem.song)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    stringResource(
                                        if (isCurrentlyPlaying) R.string.lyrics_ai_open_lyrics_now else R.string.lyrics_ai_try_play_song,
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(
                                    onClick = {
                                        expandedTaskId = null
                                        onOpenCatalog(completedItem.song)
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    Text(stringResource(R.string.lyrics_ai_open_catalog))
                                }
                                val remainingCount = (snapshot.completed.size - 1).coerceAtLeast(0) +
                                    snapshot.queued.size + if (active != null) 1 else 0
                                if (remainingCount > 0) {
                                    Text(
                                        stringResource(R.string.lyrics_ai_bg_queued, remainingCount),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    firstCompleted != null -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shape)
                                .clickable { expandedTaskId = firstCompleted.taskId }
                                .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    firstCompleted.song.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val extraQueue = snapshot.queued.size + if (active != null) 1 else 0
                                val extraDone = (snapshot.completed.size - 1).coerceAtLeast(0)
                                val suffix = buildString {
                                    if (extraDone > 0) append(" (+$extraDone)")
                                    if (extraQueue > 0) append(" · ⏳$extraQueue")
                                }
                                Text(
                                    stringResource(R.string.lyrics_ai_bg_done_pill) + suffix,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(
                                onClick = { LyricsAlignTaskManager.dismissCompleted(firstCompleted.taskId) },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.close),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }

                    active != null -> {
                        val p = active.progress
                        val stageLabel = when (p?.stage) {
                            OnDeviceLyricsAligner.AlignStage.DECODING_VOCALS -> stringResource(R.string.lyrics_ai_stage_decoding)
                            OnDeviceLyricsAligner.AlignStage.CTC_INFERENCE -> stringResource(R.string.lyrics_ai_stage_ctc)
                            OnDeviceLyricsAligner.AlignStage.ALIGNING, null -> stringResource(R.string.lyrics_ai_stage_aligning)
                        }
                        val pct = ((p?.fraction ?: 0f).coerceIn(0f, 1f) * 100).toInt()
                        val queueSuffix = if (snapshot.queued.isNotEmpty()) " · +${snapshot.queued.size}" else ""
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shape)
                                .clickable { onOpenCatalog(active.song) }
                                .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        active.song.title,
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "$stageLabel $pct%$queueSuffix",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                IconButton(
                                    onClick = { LyricsAlignTaskManager.cancel(active.taskId) },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.lyrics_ai_cancel),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            LinearProgressIndicator(
                                progress = { (p?.fraction ?: 0f).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = 8.dp)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                            )
                        }
                    }

                    firstFailed != null -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shape)
                                .clickable { onOpenCatalog(firstFailed.song) }
                                .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    firstFailed.song.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    stringResource(R.string.lyrics_ai_bg_failed_pill),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(
                                onClick = { LyricsAlignTaskManager.dismissFailed(firstFailed.taskId) },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.close),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .then(containerModifier)
                .clip(shape),
        ) {
            if (glassEnabled) {
                GlassContentTheme(content = inner)
            } else {
                inner()
            }
        }
    }

    pendingOverwriteLrc?.let { (task, fileName, lyricsText) ->
        GlassAlertDialog(
            onDismissRequest = { pendingOverwriteLrc = null },
            title = { Text(stringResource(R.string.lrc_overwrite_title)) },
            text = { Text(stringResource(R.string.lrc_overwrite_message, fileName)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingOverwriteLrc = null
                        saveTaskLyrics(task, LyricsEditorSource.File, lyricsText)
                    },
                ) {
                    Text(stringResource(R.string.lrc_overwrite_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingOverwriteLrc = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    pendingSafLrc?.let { (task, access, _) ->
        GlassAlertDialog(
            onDismissRequest = { pendingSafLrc = null },
            title = { Text(stringResource(R.string.lrc_saf_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.lrc_saf_message, access.fileName))
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        TextButton(
                            onClick = {
                                pendingSafLrc = null
                                LocalMediaRepository.requestAllFilesAccess(context)
                            },
                        ) {
                            Text(stringResource(R.string.lrc_saf_all_files))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        safTreeLauncher.launch(LocalLyricsManager.treeUriInitialHint(access.parentDir))
                    },
                ) {
                    Text(stringResource(R.string.lrc_saf_choose_folder))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingSafLrc = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
