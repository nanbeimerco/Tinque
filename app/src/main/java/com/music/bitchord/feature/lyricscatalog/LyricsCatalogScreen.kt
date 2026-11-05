package com.music.bitchord.feature.lyricscatalog

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.durationMillis
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.feature.lyrics.manager.LyricsExtensionManager
import com.music.bitchord.feature.lyricseditor.data.LocalLyricsManager
import com.music.bitchord.feature.lyricseditor.domain.model.LyricsEditorSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Asks for the catalog to open on a song, from wherever (the player's lyrics
 * footer, its ⋮ menu); the activity shows it while this holds a song.
 */
object LyricsCatalogRequests {
    val song = MutableStateFlow<Song?>(null)
    fun open(song: Song) { this.song.value = song }
    fun close() { song.value = null }
}

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
fun LyricsCatalogScreen(song: Song, onDismiss: () -> Unit, onApplied: (Song) -> Unit) {
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
    var tolerance by remember { mutableIntStateOf(5) }
    var formatFilter by remember { mutableStateOf<LyricsFormat?>(null) }

    val candidates = remember { mutableStateListOf<LyricsCandidate>() }
    val statuses = remember { mutableStateMapOf<String, SourceStatus>() }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var preview by remember { mutableStateOf<LyricsCandidate?>(null) }
    val overrideKey = remember(song) { LyricsOverrideStore.keyFor(song.videoId, song.localUri) }
    var hasOverride by remember { mutableStateOf(LyricsOverrideStore.get(overrideKey) != null) }

    fun search() {
        searchJob?.cancel()
        candidates.clear()
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
                    is CatalogEvent.Results -> {
                        candidates += event.candidates
                        candidates.sortByDescending { it.match }
                    }
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
            onUse = {
                // Stored as shown, translation kept or dropped as the switch says
                // (many sources add a Chinese one even to Japanese songs).
                val stored = LyricsCatalog.serialize(
                    candidate,
                    wordSync = true,
                    keepTranslation = AppSettings.lyricsKeepTranslation.value,
                    keepFurigana = true,
                )
                LyricsOverrideStore.set(overrideKey, LyricsOverrideStore.Override(candidate.source.id, candidate.source.label, stored))
                hasOverride = true
                preview = null
                Toast.makeText(context, context.getString(R.string.lyrics_catalog_used), Toast.LENGTH_SHORT).show()
                onApplied(song)
            },
            onSaved = {
                preview = null
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
private fun CandidatePreview(song: Song, candidate: LyricsCandidate, onDismiss: () -> Unit, onUse: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val wordSyncSetting by AppSettings.lyricsSaveWordSync.collectAsStateWithLifecycle()
    val keepTranslation by AppSettings.lyricsKeepTranslation.collectAsStateWithLifecycle()
    val keepFurigana by AppSettings.lyricsKeepFurigana.collectAsStateWithLifecycle()
    val hasFile = !song.localUri.isNullOrBlank() || !song.localPath.isNullOrBlank()
    var pendingEmbed by remember { mutableStateOf<String?>(null) }

    fun text() = LyricsCatalog.serialize(candidate, wordSyncSetting, keepTranslation, keepFurigana)

    fun save(target: LyricsEditorSource, text: String) {
        scope.launch {
            val ok = LocalLyricsManager.saveLyrics(context, song, target, text)
            Toast.makeText(
                context,
                context.getString(if (ok) R.string.l10n_lyrics_saved else R.string.l10n_lyrics_save_failed),
                Toast.LENGTH_SHORT,
            ).show()
            if (ok) onSaved()
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(candidate.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${candidate.source.label} · ${stringResource(R.string.lyrics_match, (candidate.match * 100).toInt())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Badges(candidate)
                Column(
                    Modifier
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    candidate.lines.forEach { line ->
                        if (line.text.isNotBlank()) {
                            Text(
                                line.text,
                                style = if (line.timingSource != null) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                                color = if (line.timingSource != null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                if (candidate.format == LyricsFormat.WORD) {
                    SwitchLine(stringResource(R.string.lyrics_keep_word_sync), wordSyncSetting, AppSettings::setLyricsSaveWordSync)
                }
                if (candidate.hasTranslation) {
                    SwitchLine(stringResource(R.string.lyrics_keep_translation), keepTranslation, AppSettings::setLyricsKeepTranslation)
                }
                if (candidate.hasFurigana) {
                    SwitchLine(stringResource(R.string.lyrics_keep_furigana), keepFurigana, AppSettings::setLyricsKeepFurigana)
                }
                if ((candidate.hasTranslation && keepTranslation) || (candidate.hasFurigana && keepFurigana)) {
                    Text(
                        stringResource(R.string.lyrics_keep_extras_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onClick = onUse, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.lyrics_use_for_song)) }
                if (hasFile) {
                    FilledTonalButton(onClick = { save(LyricsEditorSource.File, text()) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.lyrics_save_lrc))
                    }
                    FilledTonalButton(onClick = ::embed, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.lyrics_embed))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.l10n_close)) }
        },
    )
}

@Composable
private fun SwitchLine(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
