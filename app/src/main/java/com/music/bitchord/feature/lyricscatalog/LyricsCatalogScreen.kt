package com.music.bitchord.feature.lyricscatalog

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.lyrics.CatalogEvent
import com.music.bitchord.data.lyrics.LyricsCandidate
import com.music.bitchord.data.lyrics.LyricsCatalog
import com.music.bitchord.data.lyrics.LyricsFormat
import com.music.bitchord.data.lyrics.LyricsOverrideStore
import com.music.bitchord.data.lyrics.LyricsQuery
import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.data.lyrics.LyricsStrictness
import com.music.bitchord.data.lyrics.SourceStatus
import com.music.bitchord.data.lyrics.align.LyricsAlignTaskManager
import com.music.bitchord.data.lyrics.align.OnDeviceLyricsAligner
import com.music.bitchord.data.lyrics.align.OnDeviceLyricsAlignerModel
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.durationMillis
import com.music.bitchord.data.LocalMediaRepository
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.feature.lyrics.manager.LyricsExtensionManager
import com.music.bitchord.feature.lyricseditor.data.LocalLyricsManager
import com.music.bitchord.feature.lyricseditor.domain.model.LyricsEditorSource
import com.music.bitchord.ui.components.GlassAlertDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Asks for the catalog to open on a song, from wherever (the player's lyrics
 * footer, its ⋮ menu); the activity shows it while this holds a song.
 */
object LyricsCatalogRequests {
    val song = MutableStateFlow<Song?>(null)
    fun open(song: Song) { this.song.value = song }
    fun close() { song.value = null }
}

/**
 * Process-scoped session memory keeping AI-aligned [LyricsCandidate] results
 * across dialog close/reopen and catalog reopen within the same app run.
 */
internal object LyricsCatalogAiSessionCache {
    private const val MAX_ENTRIES = 32
    private val map = object : LinkedHashMap<Pair<String, String>, LyricsCandidate>(MAX_ENTRIES + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<String, String>, LyricsCandidate>?): Boolean =
            size > MAX_ENTRIES
    }

    @Synchronized
    fun get(songKey: String, candidateId: String): LyricsCandidate? = map[songKey to candidateId]

    @Synchronized
    fun put(songKey: String, candidateId: String, candidate: LyricsCandidate) {
        map[songKey to candidateId] = candidate
    }

    @Synchronized
    fun remove(songKey: String, candidateId: String) {
        map.remove(songKey to candidateId)
    }

    @Synchronized
    fun allForSong(songKey: String): List<LyricsCandidate> =
        map.entries.filter { it.key.first == songKey }.map { it.value }

    @Synchronized
    fun clear() {
        map.clear()
    }
}

private const val LOCAL_EMBEDDED_CANDIDATE_ID = "local:embedded"
private const val LOCAL_LRC_CANDIDATE_ID = "local:lrc"

internal fun isLocalCatalogCandidateId(id: String): Boolean =
    id == LOCAL_EMBEDDED_CANDIDATE_ID || id == LOCAL_LRC_CANDIDATE_ID

private fun LyricsFormat.label(): Int = when (this) {
    LyricsFormat.PLAIN -> R.string.lyrics_format_plain
    LyricsFormat.LINE -> R.string.lyrics_format_line
    LyricsFormat.WORD -> R.string.lyrics_format_word
}

/**
 * The lyrics catalog for [song]: every source's lyrics side by side, each
 * with how it is timed and how well it matches, to use for the song, save
 * as an .lrc or embed in its file. [onApplied] runs after a pick or a save,
 * for the player to load them.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LyricsCatalogScreen(
    song: Song,
    onDismiss: () -> Unit,
    onApplied: (Song) -> Unit,
    onPlayAndOpenLyrics: ((Song) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sources by LyricsExtensionManager.dynamicSources.collectAsStateWithLifecycle()
    val autoStrictness = remember { LyricsCatalog.autoStrictness() }

    var title by remember { mutableStateOf(song.title) }
    var artist by remember { mutableStateOf(song.artist) }
    var album by remember { mutableStateOf(song.albumName.orEmpty()) }
    var strictness by remember { mutableStateOf(autoStrictness) }
    var useArtist by remember { mutableStateOf(autoStrictness.useArtist) }
    var useAlbum by remember { mutableStateOf(autoStrictness.useAlbum) }
    var useDuration by remember { mutableStateOf(autoStrictness.useDuration) }
    var tolerance by remember { mutableIntStateOf(10) }
    var formatFilter by remember { mutableStateOf<LyricsFormat?>(null) }

    val overrideKey = remember(song) { LyricsOverrideStore.keyFor(song.videoId, song.localUri) }
    val candidates = remember { mutableStateListOf<LyricsCandidate>() }
    val localCandidates = remember(overrideKey) { mutableStateListOf<LyricsCandidate>() }
    var localReloadTick by remember(overrideKey) { mutableIntStateOf(0) }
    val statuses = remember { mutableStateMapOf<String, SourceStatus>() }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var preview by remember { mutableStateOf<LyricsCandidate?>(null) }
    var hasOverride by remember { mutableStateOf(LyricsOverrideStore.get(overrideKey) != null) }
    val alignSnapshot by LyricsAlignTaskManager.state.collectAsStateWithLifecycle()
    val aiModelInstalledState by OnDeviceLyricsAlignerModel.modelInstalled.collectAsStateWithLifecycle()
    var aiModelInstalled by remember { mutableStateOf(aiModelInstalledState) }
    LaunchedEffect(aiModelInstalledState) {
        aiModelInstalled = withContext(Dispatchers.IO) {
            OnDeviceLyricsAlignerModel.isModelInstalled(context)
        }
    }
    val canAlignAudio by produceState(false, song.videoId, song.localUri, song.localPath) {
        value = withContext(Dispatchers.IO) { OnDeviceLyricsAligner.canAlignAudio(context, song) }
    }

    LaunchedEffect(song.videoId, song.localUri, song.localPath, overrideKey, localReloadTick) {
        val loaded = withContext(Dispatchers.IO) {
            val found = mutableListOf<LyricsCandidate>()
            val durationSec = (song.durationMillis() / 1000L).toInt().coerceAtLeast(0)
            val embeddedRaw = runCatching {
                LocalLyricsManager.readLyrics(context, song, LyricsEditorSource.Embedded)
            }.getOrDefault("").trim()
            if (embeddedRaw.isNotEmpty()) {
                val id = LOCAL_EMBEDDED_CANDIDATE_ID
                val resolved = LyricsCatalogAiSessionCache.get(overrideKey, id)
                    ?: LyricsCatalog.localCandidate(
                        id = id,
                        source = LyricsSource(
                            id = "local_embedded",
                            label = context.getString(R.string.lyrics_local_embedded_source),
                            wordSynced = false,
                        ),
                        title = song.title,
                        artist = song.artist,
                        album = song.albumName,
                        durationSec = durationSec,
                        raw = embeddedRaw,
                    )
                if (resolved != null) found += resolved
            }
            val fileRaw = runCatching {
                LocalLyricsManager.readLyrics(context, song, LyricsEditorSource.File)
            }.getOrDefault("").trim()
            if (fileRaw.isNotEmpty() && fileRaw != embeddedRaw) {
                val id = LOCAL_LRC_CANDIDATE_ID
                val resolved = LyricsCatalogAiSessionCache.get(overrideKey, id)
                    ?: LyricsCatalog.localCandidate(
                        id = id,
                        source = LyricsSource(
                            id = "local_lrc",
                            label = context.getString(R.string.lyrics_local_lrc_source),
                            wordSynced = false,
                        ),
                        title = song.title,
                        artist = song.artist,
                        album = song.albumName,
                        durationSec = durationSec,
                        raw = fileRaw,
                    )
                if (resolved != null) found += resolved
            }
            found
        }
        localCandidates.clear()
        localCandidates.addAll(loaded)
    }

    LaunchedEffect(alignSnapshot.completed, overrideKey) {
        val doneForSong = alignSnapshot.completed
            .filter { it.songKey == overrideKey }
            .mapNotNull { it.resultCandidate }
        if (doneForSong.isNotEmpty()) {
            doneForSong.forEach { updated ->
                val localIdx = localCandidates.indexOfFirst { it.id == updated.id }
                if (localIdx >= 0) {
                    localCandidates[localIdx] = updated
                }
                val idx = candidates.indexOfFirst { it.id == updated.id }
                if (idx >= 0) {
                    candidates[idx] = updated
                }
                if (preview?.id == updated.id) {
                    preview = updated
                }
            }
        }
    }

    fun mergeIntoCandidates(incoming: List<LyricsCandidate>) {
        incoming.forEach { rawCandidate ->
            val resolved = LyricsCatalogAiSessionCache.get(overrideKey, rawCandidate.id) ?: rawCandidate
            val existingIdx = candidates.indexOfFirst { it.id == resolved.id }
            if (existingIdx >= 0) {
                candidates[existingIdx] = resolved
            } else {
                candidates += resolved
            }
        }
        candidates.sortByDescending { it.match }
    }

    fun search() {
        searchJob?.cancel()
        candidates.clear()
        val cachedForSong = LyricsCatalogAiSessionCache.allForSong(overrideKey)
            .filterNot { isLocalCatalogCandidateId(it.id) }
        if (cachedForSong.isNotEmpty()) {
            candidates.addAll(cachedForSong.sortedByDescending { it.match })
        }
        statuses.clear()
        val query = LyricsQuery(
            title = title.trim(),
            artist = artist.trim(),
            album = album.trim().ifEmpty { null },
            durationMs = song.durationMillis(),
            useArtist = useArtist,
            useAlbum = useAlbum,
            useDuration = useDuration,
            toleranceSec = tolerance,
        )
        searchJob = scope.launch {
            LyricsCatalog.search(query, sources).collect { event ->
                when (event) {
                    is CatalogEvent.Status -> statuses[event.source.id] = event.status
                    is CatalogEvent.Results -> mergeIntoCandidates(event.candidates)
                }
            }
        }
    }

    LaunchedEffect(sources) { if (sources.isNotEmpty()) search() }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.l10n_back))
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.lyrics_catalog_title), style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${song.title} — ${song.artist}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (hasOverride) {
                    TextButton(onClick = {
                        LyricsOverrideStore.clear(overrideKey)
                        hasOverride = false
                        onApplied(song)
                        Toast.makeText(context, context.getString(R.string.lyrics_catalog_override_cleared), Toast.LENGTH_SHORT).show()
                    }) { Text(stringResource(R.string.lyrics_catalog_clear_override)) }
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.l10n_song_title)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(artist, { artist = it }, label = { Text(stringResource(R.string.l10n_artist)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(album, { album = it }, label = { Text(stringResource(R.string.l10n_album)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Text(stringResource(R.string.lyrics_catalog_strictness), style = MaterialTheme.typography.labelLarge)
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            val levels = LyricsStrictness.entries
                            levels.forEachIndexed { index, level ->
                                SegmentedButton(
                                    selected = strictness == level && useArtist == level.useArtist && useAlbum == level.useAlbum && useDuration == level.useDuration,
                                    onClick = {
                                        strictness = level
                                        useArtist = level.useArtist
                                        useAlbum = level.useAlbum
                                        useDuration = level.useDuration
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(index, levels.size),
                                    label = { Text(stringResource(level.label()), maxLines = 1) },
                                )
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.Center) {
                            CheckRow(stringResource(R.string.l10n_artist), useArtist) { useArtist = it }
                            CheckRow(stringResource(R.string.l10n_album), useAlbum) { useAlbum = it }
                            CheckRow(stringResource(R.string.lyrics_catalog_use_length), useDuration) { useDuration = it }
                        }
                        if (useDuration) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(3, 5, 10, 20).forEach { seconds ->
                                    FilterChip(
                                        selected = tolerance == seconds,
                                        onClick = { tolerance = seconds },
                                        label = { Text(stringResource(R.string.lyrics_catalog_tolerance, seconds)) },
                                    )
                                }
                            }
                        }
                        Button(onClick = ::search, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.lyrics_catalog_search))
                        }
                    }
                }
                if (localCandidates.isNotEmpty()) {
                    items(localCandidates, key = { it.id }) { localCandidate ->
                        val activeTask = alignSnapshot.active?.takeIf {
                            it.songKey == overrideKey && it.candidateId == localCandidate.id
                        }
                        val queuedTask = alignSnapshot.queued.firstOrNull {
                            it.songKey == overrideKey && it.candidateId == localCandidate.id
                        }
                        val queuedAhead = queuedTask?.let { qt ->
                            alignSnapshot.queued.indexOfFirst { it.taskId == qt.taskId } +
                                if (alignSnapshot.active != null) 1 else 0
                        } ?: 0
                        LocalLyricsAiCard(
                            candidate = localCandidate,
                            canAlignAudio = canAlignAudio,
                            aiModelInstalled = aiModelInstalled,
                            activeTask = activeTask,
                            queuedTask = queuedTask,
                            queuedAhead = queuedAhead,
                            onPreviewClick = { preview = localCandidate },
                            onAiSyncClick = {
                                val plainForAi = localCandidate.lines
                                    .filter { it.timingSource == null && it.text.isNotBlank() }
                                    .joinToString("\n") { it.text }
                                if (plainForAi.isNotBlank()) {
                                    LyricsAlignTaskManager.enqueue(
                                        context = context,
                                        song = song,
                                        candidate = localCandidate,
                                        plainText = plainForAi,
                                        forceReanalyze = localCandidate.source.id == "on_device_ai",
                                    )
                                }
                            },
                            onCancelAiSync = {
                                LyricsAlignTaskManager.cancelForCandidate(overrideKey, localCandidate.id)
                            },
                        )
                    }
                }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        sources.forEach { source ->
                            val status = statuses[source.id]
                            val text = when (status) {
                                SourceStatus.Searching -> stringResource(R.string.lyrics_status_searching, source.label)
                                is SourceStatus.Found -> stringResource(R.string.lyrics_status_found, source.label, status.count)
                                SourceStatus.None -> stringResource(R.string.lyrics_status_none, source.label)
                                SourceStatus.Failed -> stringResource(R.string.lyrics_status_failed, source.label)
                                null -> source.label
                            }
                            Text(
                                text,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(end = 2.dp),
                            )
                        }
                    }
                    if (statuses.values.any { it == SourceStatus.Searching }) {
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
                    }
                }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(formatFilter == null, { formatFilter = null }, label = { Text(stringResource(R.string.lyrics_filter_all)) })
                        LyricsFormat.entries.reversed().forEach { format ->
                            FilterChip(formatFilter == format, { formatFilter = format }, label = { Text(stringResource(format.label())) })
                        }
                    }
                }
                val shown = candidates.filter { formatFilter == null || it.format == formatFilter }
                if (shown.isEmpty() && statuses.isNotEmpty() && statuses.values.none { it == SourceStatus.Searching }) {
                    item {
                        Text(
                            stringResource(R.string.l10n_no_lyrics_found),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                }
                items(shown, key = { it.id }) { candidate ->
                    CandidateCard(candidate, onClick = { preview = candidate })
                }
            }
        }
    }

    preview?.let { candidate ->
        CandidatePreview(
            song = song,
            candidate = candidate,
            onDismiss = { preview = null },
            onCandidateUpdated = { updated ->
                LyricsCatalogAiSessionCache.put(overrideKey, updated.id, updated)
                if (isLocalCatalogCandidateId(updated.id)) {
                    val localIdx = localCandidates.indexOfFirst { it.id == updated.id }
                    if (localIdx >= 0) {
                        localCandidates[localIdx] = updated
                    } else {
                        localCandidates.add(0, updated)
                    }
                } else {
                    val idx = candidates.indexOfFirst { it.id == updated.id }
                    if (idx >= 0) {
                        candidates[idx] = updated
                    } else {
                        candidates.add(0, updated)
                    }
                }
                preview = updated
            },
            onUse = { chosen ->
                val stored = LyricsCatalog.serialize(
                    chosen,
                    wordSync = true,
                    keepTranslation = AppSettings.lyricsKeepTranslation.value,
                    keepFurigana = true,
                )
                LyricsOverrideStore.set(overrideKey, LyricsOverrideStore.Override(chosen.source.id, chosen.source.label, stored))
                LyricsAlignTaskManager.taskFor(overrideKey, chosen.id)?.let { t ->
                    LyricsAlignTaskManager.dismissCompleted(t.taskId)
                }
                hasOverride = true
                preview = null
                Toast.makeText(context, context.getString(R.string.lyrics_catalog_used), Toast.LENGTH_SHORT).show()
                onApplied(song)
            },
            onPlayAndTry = if (onPlayAndOpenLyrics != null) {
                { chosen ->
                    val stored = LyricsCatalog.serialize(
                        chosen,
                        wordSync = true,
                        keepTranslation = AppSettings.lyricsKeepTranslation.value,
                        keepFurigana = true,
                    )
                    LyricsOverrideStore.set(overrideKey, LyricsOverrideStore.Override(chosen.source.id, chosen.source.label, stored))
                    LyricsAlignTaskManager.taskFor(overrideKey, chosen.id)?.let { t ->
                        LyricsAlignTaskManager.dismissCompleted(t.taskId)
                    }
                    hasOverride = true
                    preview = null
                    onApplied(song)
                    onPlayAndOpenLyrics(song)
                }
            } else {
                null
            },
            onSaved = {
                LyricsCatalogAiSessionCache.remove(overrideKey, candidate.id)
                preview = null
                localReloadTick++
                onApplied(song)
            },
        )
    }
}

private fun LyricsStrictness.label(): Int = when (this) {
    LyricsStrictness.STRICT -> R.string.lyrics_strict_strict
    LyricsStrictness.STANDARD -> R.string.lyrics_strict_standard
    LyricsStrictness.LOOSE -> R.string.lyrics_strict_loose
    LyricsStrictness.TITLE_ONLY -> R.string.lyrics_strict_title_only
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onChange(!checked) }) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 8.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Badges(candidate: LyricsCandidate) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Badge(stringResource(candidate.format.label()), primary = true)
        if (candidate.hasTranslation) Badge(stringResource(R.string.lyrics_badge_translation))
        if (candidate.hasFurigana) Badge(stringResource(R.string.lyrics_badge_furigana))
        if (candidate.hasBackgroundVocals) Badge(stringResource(R.string.lyrics_badge_chorus))
    }
}

@Composable
private fun Badge(text: String, primary: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (primary) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = if (primary) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun LocalLyricsAiCard(
    candidate: LyricsCandidate,
    canAlignAudio: Boolean,
    aiModelInstalled: Boolean,
    activeTask: LyricsAlignTaskManager.AlignTask?,
    queuedTask: LyricsAlignTaskManager.AlignTask?,
    queuedAhead: Int,
    onPreviewClick: () -> Unit,
    onAiSyncClick: () -> Unit,
    onCancelAiSync: () -> Unit,
) {
    val isPending = activeTask != null || queuedTask != null
    val isAiSynced = candidate.source.id == "on_device_ai"
    val isEmbedded = candidate.id == LOCAL_EMBEDDED_CANDIDATE_ID
    val canUpgrade = canAlignAudio && !isAiSynced && (
        candidate.format == LyricsFormat.PLAIN || candidate.format == LyricsFormat.LINE
    )
    val headline = when {
        isAiSynced -> stringResource(R.string.lyrics_local_ai_ready_headline)
        isEmbedded && candidate.format == LyricsFormat.PLAIN ->
            stringResource(
                if (aiModelInstalled) R.string.lyrics_local_embedded_plain_ctc_hint
                else R.string.lyrics_local_embedded_plain_vad_hint,
            )
        isEmbedded && candidate.format == LyricsFormat.LINE ->
            stringResource(
                if (aiModelInstalled) R.string.lyrics_local_embedded_line_ctc_hint
                else R.string.lyrics_local_embedded_line_download_hint,
            )
        !isEmbedded && candidate.format == LyricsFormat.PLAIN ->
            stringResource(
                if (aiModelInstalled) R.string.lyrics_local_lrc_plain_ctc_hint
                else R.string.lyrics_local_lrc_plain_vad_hint,
            )
        !isEmbedded && candidate.format == LyricsFormat.LINE ->
            stringResource(
                if (aiModelInstalled) R.string.lyrics_local_lrc_line_ctc_hint
                else R.string.lyrics_local_lrc_line_download_hint,
            )
        else -> stringResource(
            if (isEmbedded) R.string.lyrics_local_embedded_word_ready else R.string.lyrics_local_lrc_word_ready,
        )
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (canUpgrade || isAiSynced) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        border = if (canUpgrade || isAiSynced) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
        } else {
            null
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPreviewClick),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = candidate.source.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Badge(stringResource(candidate.format.label()), primary = true)
            }

            Text(
                text = headline,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            val firstLines = candidate.lines.filter { it.text.isNotBlank() && it.timingSource == null }.take(2)
            if (firstLines.isNotEmpty()) {
                Text(
                    text = firstLines.joinToString("\n") { it.text },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            when {
                isPending -> {
                    val ap = activeTask?.progress
                    val stageLabel = when {
                        queuedTask != null -> stringResource(R.string.lyrics_ai_bg_queued, queuedAhead.coerceAtLeast(1))
                        ap?.stage == OnDeviceLyricsAligner.AlignStage.DECODING_VOCALS ->
                            stringResource(R.string.lyrics_ai_stage_decoding)
                        ap?.stage == OnDeviceLyricsAligner.AlignStage.CTC_INFERENCE ->
                            stringResource(R.string.lyrics_ai_stage_ctc)
                        else -> stringResource(R.string.lyrics_ai_stage_aligning)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = stageLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                onClick = onCancelAiSync,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            ) {
                                Text(stringResource(R.string.lyrics_ai_cancel))
                            }
                        }
                        LinearProgressIndicator(
                            progress = { (ap?.fraction ?: 0f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                canUpgrade -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val canDirectWordOrLine = candidate.format == LyricsFormat.PLAIN || aiModelInstalled
                        FilledTonalButton(
                            onClick = if (canDirectWordOrLine) onAiSyncClick else onPreviewClick,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(
                                    when {
                                        candidate.format == LyricsFormat.LINE && aiModelInstalled ->
                                            R.string.lyrics_ai_upgrade_word_button
                                        candidate.format == LyricsFormat.LINE && !aiModelInstalled ->
                                            R.string.lyrics_ai_download_model_short
                                        else -> R.string.lyrics_ai_generate_button
                                    },
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TextButton(onClick = onPreviewClick) {
                            Text(stringResource(R.string.lyrics_local_preview_button))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CandidateCard(candidate: LyricsCandidate, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    candidate.source.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.lyrics_match, (candidate.match * 100).toInt()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(candidate.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val details = listOfNotNull(
                candidate.artist.takeIf { it.isNotBlank() },
                candidate.album?.takeIf { it.isNotBlank() },
                candidate.durationSec.takeIf { it > 0 }?.let { "%d:%02d".format(it / 60, it % 60) },
            ).joinToString(" · ")
            if (details.isNotEmpty()) {
                Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Badges(candidate)
            val firstLines = candidate.lines.filter { it.text.isNotBlank() && it.timingSource == null }.take(3)
            Text(
                firstLines.joinToString("\n") { it.text },
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * One candidate in full, with what can be done with it: use it for this song
 * (Tinque only), save it as an .lrc beside the file, or embed it in the file —
 * with or without its word timing, translation and furigana.
 */
@Composable
private fun CandidatePreview(
    song: Song,
    candidate: LyricsCandidate,
    onDismiss: () -> Unit,
    onCandidateUpdated: (LyricsCandidate) -> Unit,
    onUse: (LyricsCandidate) -> Unit,
    onPlayAndTry: ((LyricsCandidate) -> Unit)? = null,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val wordSyncSetting by AppSettings.lyricsSaveWordSync.collectAsStateWithLifecycle()
    val keepTranslation by AppSettings.lyricsKeepTranslation.collectAsStateWithLifecycle()
    val keepFurigana by AppSettings.lyricsKeepFurigana.collectAsStateWithLifecycle()
    val offlineMode by AppSettings.offlineMode.collectAsStateWithLifecycle()
    val aiModelInstalledState by OnDeviceLyricsAlignerModel.modelInstalled.collectAsStateWithLifecycle()
    val aiDownloadProgress by OnDeviceLyricsAlignerModel.downloadProgress.collectAsStateWithLifecycle()
    val downloadSupported = remember(context) { OnDeviceLyricsAlignerModel.isDownloadSupported(context) }

    val songKey = remember(song) { LyricsAlignTaskManager.songKeyFor(song) }
    val alignSnapshot by LyricsAlignTaskManager.state.collectAsStateWithLifecycle()
    val activeTask = alignSnapshot.active?.takeIf { it.songKey == songKey && it.candidateId == candidate.id }
    val queuedTask = alignSnapshot.queued.firstOrNull { it.songKey == songKey && it.candidateId == candidate.id }
    val completedTask = alignSnapshot.completed.firstOrNull { it.songKey == songKey && it.candidateId == candidate.id }
    val isTaskPending = activeTask != null || queuedTask != null

    var activeCandidate by remember(candidate.id) { mutableStateOf(candidate) }
    var installedChecked by remember { mutableStateOf(aiModelInstalledState) }
    LaunchedEffect(aiModelInstalledState) {
        installedChecked = withContext(Dispatchers.IO) {
            OnDeviceLyricsAlignerModel.isModelInstalled(context)
        }
    }
    val canAlign by produceState(false, song.videoId, song.localUri, song.localPath) {
        value = withContext(Dispatchers.IO) { OnDeviceLyricsAligner.canAlignAudio(context, song) }
    }

    var showAiDownloadConfirm by remember { mutableStateOf(false) }
    var aiDownloadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(candidate) {
        if (!isTaskPending) {
            activeCandidate = candidate
        }
    }
    LaunchedEffect(completedTask?.taskId) {
        val updated = completedTask?.resultCandidate ?: return@LaunchedEffect
        activeCandidate = updated
        onCandidateUpdated(updated)
    }

    fun runAiAlign(plainForAi: String, forceReanalyze: Boolean = false) {
        if (plainForAi.isBlank()) return
        LyricsAlignTaskManager.enqueue(
            context = context,
            song = song,
            candidate = activeCandidate,
            plainText = plainForAi,
            forceReanalyze = forceReanalyze,
        )
    }

    val hasFile = !song.localUri.isNullOrBlank() || !song.localPath.isNullOrBlank()
    var pendingEmbed by remember { mutableStateOf<String?>(null) }

    fun text() = LyricsCatalog.serialize(activeCandidate, wordSyncSetting, keepTranslation, keepFurigana)

    var pendingOverwriteLrc by remember { mutableStateOf<Pair<String, String>?>(null) }
    var pendingSafLrc by remember { mutableStateOf<Pair<LocalLyricsManager.LrcSaveResult.NeedsDirectoryAccess, String>?>(null) }

    fun save(target: LyricsEditorSource, text: String) {
        scope.launch {
            when (val outcome = LocalLyricsManager.saveLyricsDetailed(context, song, target, text)) {
                is LocalLyricsManager.LrcSaveResult.Success -> {
                    completedTask?.let { LyricsAlignTaskManager.dismissCompleted(it.taskId) }
                    Toast.makeText(
                        context,
                        context.getString(R.string.l10n_lyrics_saved),
                        Toast.LENGTH_SHORT,
                    ).show()
                    onSaved()
                }
                is LocalLyricsManager.LrcSaveResult.NeedsDirectoryAccess -> {
                    if (target == LyricsEditorSource.File) {
                        pendingSafLrc = outcome to text
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.l10n_lyrics_save_failed),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
                is LocalLyricsManager.LrcSaveResult.Failed -> {
                    Toast.makeText(
                        context,
                        context.getString(R.string.l10n_lyrics_save_failed),
                        Toast.LENGTH_SHORT,
                    ).show()
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
            save(LyricsEditorSource.File, pending.second)
        } else if (pending != null) {
            Toast.makeText(
                context,
                context.getString(R.string.l10n_lyrics_save_failed),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val text = pendingEmbed
        pendingEmbed = null
        if (result.resultCode == Activity.RESULT_OK && text != null) {
            save(LyricsEditorSource.Embedded, text)
        } else {
            Toast.makeText(context, context.getString(R.string.l10n_no_edit_permission), Toast.LENGTH_SHORT).show()
        }
    }

    fun embed() {
        val text = text()
        val uri = song.localUri?.takeIf { it.startsWith("content://") }?.let(android.net.Uri::parse)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && uri != null) {
            val granted = context.checkUriPermission(
                uri, android.os.Process.myPid(), android.os.Process.myUid(), Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                val request = runCatching { android.provider.MediaStore.createWriteRequest(context.contentResolver, listOf(uri)) }.getOrNull()
                if (request != null) {
                    pendingEmbed = text
                    permission.launch(IntentSenderRequest.Builder(request.intentSender).build())
                    return
                }
            }
        }
        save(LyricsEditorSource.Embedded, text)
    }

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(activeCandidate.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${activeCandidate.source.label} · ${stringResource(R.string.lyrics_match, (activeCandidate.match * 100).toInt())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Badges(activeCandidate)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 176.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        activeCandidate.lines.forEach { line ->
                            if (line.text.isNotBlank()) {
                                Text(
                                    line.text,
                                    style = if (line.timingSource != null) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                                    color = if (line.timingSource != null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
                if (canAlign && activeCandidate.lines.isNotEmpty()) {
                    HorizontalDivider()
                    val dp = aiDownloadProgress
                    val ap = activeTask?.progress
                    val plainForAi = activeCandidate.lines
                        .filter { it.timingSource == null && it.text.isNotBlank() }
                        .joinToString("\n") { it.text }
                    when {
                        dp != null -> {
                            val totalMb = dp.totalBytes.coerceAtLeast(1L) / (1024f * 1024f)
                            val doneMb = dp.bytes / (1024f * 1024f)
                            val frac = (dp.bytes.toFloat() / dp.totalBytes.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    stringResource(R.string.lyrics_ai_download_progress, doneMb, totalMb),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth())
                            }
                        }
                        isTaskPending -> {
                            val stageLabel = when {
                                queuedTask != null -> {
                                    val ahead = alignSnapshot.queued.indexOfFirst { it.taskId == queuedTask.taskId } +
                                        if (alignSnapshot.active != null) 1 else 0
                                    stringResource(R.string.lyrics_ai_bg_queued, ahead.coerceAtLeast(1))
                                }
                                ap?.stage == OnDeviceLyricsAligner.AlignStage.DECODING_VOCALS ->
                                    stringResource(R.string.lyrics_ai_stage_decoding)
                                ap?.stage == OnDeviceLyricsAligner.AlignStage.CTC_INFERENCE ->
                                    stringResource(R.string.lyrics_ai_stage_ctc)
                                else -> stringResource(R.string.lyrics_ai_stage_aligning)
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        stageLabel,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(
                                        onClick = {
                                            LyricsAlignTaskManager.cancelForCandidate(songKey, candidate.id)
                                        },
                                    ) {
                                        Text(stringResource(R.string.lyrics_ai_cancel))
                                    }
                                }
                                LinearProgressIndicator(
                                    progress = { (ap?.fraction ?: 0f).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text(
                                    stringResource(R.string.lyrics_ai_bg_running_hint),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        activeCandidate.source.id == "on_device_ai" -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(
                                        Icons.Rounded.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        stringResource(R.string.lyrics_ai_synced_ready),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                TextButton(
                                    onClick = { runAiAlign(plainForAi, forceReanalyze = true) },
                                    enabled = plainForAi.isNotBlank(),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                ) {
                                    Text(stringResource(R.string.lyrics_ai_reanalyze))
                                }
                            }
                        }
                        else -> {
                            FilledTonalButton(
                                onClick = { runAiAlign(plainForAi, forceReanalyze = false) },
                                enabled = plainForAi.isNotBlank(),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.lyrics_ai_generate_button))
                            }
                            Text(
                                stringResource(
                                    if (installedChecked) R.string.lyrics_ai_mode_ctc_desc else R.string.lyrics_ai_mode_vad_desc,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (!installedChecked && downloadSupported && !offlineMode) {
                                TextButton(
                                    onClick = { showAiDownloadConfirm = true },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                ) {
                                    Text(stringResource(R.string.lyrics_ai_download_model_cta))
                                }
                            }
                        }
                    }
                }
                if (activeCandidate.format == LyricsFormat.WORD) {
                    SwitchLine(stringResource(R.string.lyrics_keep_word_sync), wordSyncSetting, AppSettings::setLyricsSaveWordSync)
                }
                if (activeCandidate.hasTranslation) {
                    SwitchLine(stringResource(R.string.lyrics_keep_translation), keepTranslation, AppSettings::setLyricsKeepTranslation)
                }
                if (activeCandidate.hasFurigana) {
                    SwitchLine(stringResource(R.string.lyrics_keep_furigana), keepFurigana, AppSettings::setLyricsKeepFurigana)
                }
                if ((activeCandidate.hasTranslation && keepTranslation) || (activeCandidate.hasFurigana && keepFurigana)) {
                    Text(
                        stringResource(R.string.lyrics_keep_extras_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = { onUse(activeCandidate) },
                    enabled = !isTaskPending,
                    modifier = Modifier.fillMaxWidth(),
                ) {
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
                                val lyricsText = text()
                                val existing = LocalLyricsManager.findExistingLrcFile(context, song)
                                if (existing != null) {
                                    pendingOverwriteLrc = existing.name to lyricsText
                                } else {
                                    save(LyricsEditorSource.File, lyricsText)
                                }
                            },
                            enabled = !isTaskPending,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                            modifier = Modifier.weight(1f),
                        ) {
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
                            onClick = ::embed,
                            enabled = !isTaskPending,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                            modifier = Modifier.weight(1f),
                        ) {
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
                if (onPlayAndTry != null && activeCandidate.format != LyricsFormat.PLAIN && !isTaskPending) {
                    FilledTonalButton(
                        onClick = { onPlayAndTry(activeCandidate) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.lyrics_ai_try_play_song),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(if (isTaskPending) R.string.lyrics_ai_continue_bg else R.string.l10n_close))
            }
        },
    )

    if (showAiDownloadConfirm) {
        GlassAlertDialog(
            onDismissRequest = { showAiDownloadConfirm = false },
            title = { Text(stringResource(R.string.lyrics_ai_download_confirm_title)) },
            text = { Text(stringResource(R.string.lyrics_ai_download_confirm_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        showAiDownloadConfirm = false
                        scope.launch {
                            val res = OnDeviceLyricsAlignerModel.downloadModel(context)
                            res.onFailure { err ->
                                aiDownloadError = err.localizedMessage ?: "Download failed"
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.lyrics_ai_download_confirm_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAiDownloadConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    aiDownloadError?.let { err ->
        GlassAlertDialog(
            onDismissRequest = { aiDownloadError = null },
            title = { Text(stringResource(R.string.lyrics_ai_download_failed)) },
            text = { Text(err) },
            confirmButton = {
                TextButton(onClick = { aiDownloadError = null }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
    }

    pendingOverwriteLrc?.let { (fileName, lyricsText) ->
        GlassAlertDialog(
            onDismissRequest = { pendingOverwriteLrc = null },
            title = { Text(stringResource(R.string.lrc_overwrite_title)) },
            text = { Text(stringResource(R.string.lrc_overwrite_message, fileName)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingOverwriteLrc = null
                        save(LyricsEditorSource.File, lyricsText)
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

    pendingSafLrc?.let { (access, _) ->
        GlassAlertDialog(
            onDismissRequest = { pendingSafLrc = null },
            title = { Text(stringResource(R.string.lrc_saf_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.lrc_saf_message, access.fileName))
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
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

@Composable
private fun SwitchLine(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
