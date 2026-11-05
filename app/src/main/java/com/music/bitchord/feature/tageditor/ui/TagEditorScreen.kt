package com.music.bitchord.feature.tageditor.ui

import com.music.bitchord.R
import android.app.SearchManager
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FabPosition
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.music.bitchord.data.model.Song
import com.music.bitchord.feature.tageditor.ui.components.TagEditorArtworkSection
import com.music.bitchord.feature.tageditor.ui.components.TagEditorFolderSection
import com.music.bitchord.feature.tageditor.ui.components.TagEditorFormFields
import com.music.bitchord.feature.tageditor.ui.components.TagEditorGenreDialog
import com.music.bitchord.feature.tageditor.ui.components.TagEditorImageOptionsDialog
import com.music.bitchord.feature.tageditor.ui.components.TagEditorSaveFab
import com.music.bitchord.feature.tageditor.ui.components.TagEditorTopBar
import com.music.bitchord.feature.tageditor.ui.viewmodel.TagEditorViewModel
import androidx.compose.ui.graphics.luminance
import com.music.bitchord.ui.theme.SystemBarIcons

@Composable
fun TagEditorScreen(
    song: Song,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TagEditorViewModel = viewModel(),
    onTagsSaved: (() -> Unit)? = null,
    /**
     * Songs to edit together (a folder's own); [song] is then only the first of
     * them. [batchSongsWithSubfolders] is the same folder with its subfolders.
     */
    batchSongs: List<Song>? = null,
    batchSongsWithSubfolders: List<Song>? = null,
) {
    // Its own window: give the sheets and menus opened in it their glass.
    com.music.bitchord.ui.components.GlassWindowScope {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    SystemBarIcons(dark = !isDarkTheme)

    val context = LocalContext.current
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val batch by viewModel.batch.collectAsStateWithLifecycle()
    val folder by viewModel.folder.collectAsStateWithLifecycle()
    val mixed by viewModel.mixed.collectAsStateWithLifecycle()
    val applyToFolder by viewModel.applyToFolder.collectAsStateWithLifecycle()
    val includeSubfolders by viewModel.includeSubfolders.collectAsStateWithLifecycle()
    val toggledFields by viewModel.toggledFields.collectAsStateWithLifecycle()
    var confirmTargets by remember { mutableStateOf<List<Song>?>(null) }

    var showImageOptions by remember { mutableStateOf(false) }
    var showGenreDialog by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            viewModel.updateArtworkFromUri(uri)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.save(song)
        } else {
            Toast.makeText(context, context.getString(R.string.l10n_no_edit_permission), Toast.LENGTH_SHORT).show()
        }
    }

    // Asks once for every file the save will write that Tinque may not write
    // yet: one system prompt for a whole folder, not one per song.
    fun writeWithPermission(targets: List<Song>) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val needed = targets.mapNotNull { target ->
                val uriStr = target.localUri ?: target.videoId
                val uri = if (uriStr.startsWith("content://")) android.net.Uri.parse(uriStr) else null
                uri?.takeIf {
                    context.checkUriPermission(
                        it,
                        android.os.Process.myPid(),
                        android.os.Process.myUid(),
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                }
            }
            if (needed.isNotEmpty()) {
                val pendingIntent = runCatching {
                    android.provider.MediaStore.createWriteRequest(context.contentResolver, needed)
                }.getOrNull()
                if (pendingIntent != null) {
                    permissionLauncher.launch(
                        androidx.activity.result.IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                    )
                    return
                }
            }
        }
        viewModel.save(song)
    }

    fun handleSave() {
        val targets = viewModel.allTargets()
        if (targets.size > 1) confirmTargets = targets else writeWithPermission(targets)
    }

    LaunchedEffect(song, batchSongs) {
        if (batchSongs != null) {
            viewModel.loadBatch(batchSongs, batchSongsWithSubfolders ?: batchSongs)
        } else {
            viewModel.loadTags(song)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.saveEvent.collect { result ->
            val message = when {
                result.success && result.total > 1 -> context.getString(R.string.tag_result_all, result.written)
                result.success -> context.getString(R.string.tag_saved)
                result.total > 1 -> context.getString(
                    R.string.tag_result_some,
                    result.written,
                    result.total,
                    result.failedTitles.joinToString("、"),
                )
                else -> context.getString(R.string.tag_save_failed)
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            if (result.written > 0) onTagsSaved?.invoke()
            if (result.success) onNavigateBack()
        }
    }

    confirmTargets?.let { targets ->
        val fields = viewModel.fieldsForOthers()
        val fieldNames = fields.map { context.getString(it.label) }.joinToString("、")
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmTargets = null },
            title = { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(R.string.tag_confirm_title, targets.size)) },
            text = {
                androidx.compose.material3.Text(
                    if (batch == null) {
                        androidx.compose.ui.res.stringResource(R.string.tag_confirm_body_single, fieldNames.ifEmpty { "—" })
                    } else {
                        androidx.compose.ui.res.stringResource(R.string.tag_confirm_body, fieldNames.ifEmpty { "—" })
                    },
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirmTargets = null
                    writeWithPermission(targets)
                }) { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(R.string.tag_write)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmTargets = null }) {
                    androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(R.string.l10n_cancel))
                }
            },
        )
    }

    fun openWebSearch(query: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra(SearchManager.QUERY, query)
            }
            context.startActivity(intent)
        }
    }

    Scaffold(
        topBar = {
            TagEditorTopBar(
                onBackClick = onNavigateBack,
                onSearchOnlineImage = {
                    openWebSearch("${tags.artist} ${tags.album.ifEmpty { tags.title }} album cover")
                },
                onResetTags = { viewModel.resetTags() },
            )
        },
        floatingActionButton = {
            TagEditorSaveFab(
                isSaving = isSaving,
                onSaveClick = { handleSave() },
            )
        },
        floatingActionButtonPosition = FabPosition.End,
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize(),
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                state = rememberLazyListState(),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 20.dp),
            ) {
                item {
                    TagEditorArtworkSection(
                        artworkBitmap = tags.artworkBitmap,
                        thumbnailUrl = song.thumbnailUrl,
                        onClick = { showImageOptions = true },
                    )
                }

                item {
                    TagEditorFormFields(
                        tags = tags,
                        onTagsChange = { viewModel.updateTags(it) },
                        onOpenGenrePicker = { showGenreDialog = true },
                        mixed = mixed,
                    )
                }

                item {
                    val changed = remember(tags) { viewModel.changedFields() }
                    val scope = batch ?: folder
                    TagEditorFolderSection(
                        batchCount = batch?.let { if (includeSubfolders) it.withSubfolders.size else it.direct.size },
                        folderCount = if (batch == null) folder?.direct?.size?.takeIf { it > 0 } ?: folder?.let { 0 } else null,
                        subfolderCount = scope?.takeIf { it.withSubfolders.size > it.direct.size }?.withSubfolders?.size,
                        applyToFolder = applyToFolder,
                        onApplyToFolderChange = { viewModel.applyToFolder.value = it },
                        includeSubfolders = includeSubfolders,
                        onIncludeSubfoldersChange = { viewModel.includeSubfolders.value = it },
                        changed = changed,
                        excluded = viewModel.excludedFields(changed, toggledFields),
                        onToggleField = viewModel::toggleField,
                    )
                }

                item {
                    Spacer(Modifier.height(88.dp))
                }
            }
        }
    }

    if (showImageOptions) {
        TagEditorImageOptionsDialog(
            onDismissRequest = { showImageOptions = false },
            onPickFromStorage = {
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onSearchOnline = {
                openWebSearch("${tags.artist} ${tags.album.ifEmpty { tags.title }} album cover")
            },
            onRestoreDefault = { viewModel.restoreArtwork() },
            onRemoveCover = { viewModel.deleteArtwork() },
        )
    }

    if (showGenreDialog) {
        TagEditorGenreDialog(
            onDismissRequest = { showGenreDialog = false },
            onGenreSelected = { selected ->
                val current = tags.genre.trim()
                val updated = if (current.isEmpty()) selected else "$current; $selected"
                viewModel.updateTags(tags.copy(genre = updated))
            },
        )
    }
    }
}
