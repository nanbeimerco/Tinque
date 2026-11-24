package com.music.bitchord.feature.lyricseditor.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.music.bitchord.R
import com.music.bitchord.data.lyrics.LyricsCatalog
import com.music.bitchord.data.lyrics.LyricsFormat
import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.data.lyrics.align.LyricsAlignTaskManager
import com.music.bitchord.data.lyrics.align.OnDeviceLyricsAligner
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.durationMillis
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.feature.lyricseditor.data.LocalLyricsManager
import com.music.bitchord.feature.lyricseditor.domain.model.LyricsEditorSource
import com.music.bitchord.feature.lyricseditor.domain.model.LyricsSearchResultItem
import com.music.bitchord.feature.lyricseditor.ui.components.LyricsSelectionMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LyricsEditorViewModel(app: Application) : AndroidViewModel(app) {

    private val _selectedSource = MutableStateFlow(LyricsEditorSource.Embedded)
    val selectedSource: StateFlow<LyricsEditorSource> = _selectedSource.asStateFlow()

    private val _currentText = MutableStateFlow("")
    val currentText: StateFlow<String> = _currentText.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _candidateResult = MutableStateFlow<LocalLyricsManager.DownloadedLyricsResult?>(null)
    val candidateResult: StateFlow<LocalLyricsManager.DownloadedLyricsResult?> = _candidateResult.asStateFlow()

    private val _searchResults = MutableStateFlow<List<LyricsSearchResultItem>>(emptyList())
    val searchResults: StateFlow<List<LyricsSearchResultItem>> = _searchResults.asStateFlow()

    private val _isSearchingResults = MutableStateFlow(false)
    val isSearchingResults: StateFlow<Boolean> = _isSearchingResults.asStateFlow()

    private val _aiAlignProgress = MutableStateFlow<OnDeviceLyricsAligner.AlignProgress?>(null)
    val aiAlignProgress: StateFlow<OnDeviceLyricsAligner.AlignProgress?> = _aiAlignProgress.asStateFlow()

    private val _aiAlignQueuedAhead = MutableStateFlow<Int?>(null)
    val aiAlignQueuedAhead: StateFlow<Int?> = _aiAlignQueuedAhead.asStateFlow()

    private val _saveResult = MutableSharedFlow<Boolean>()
    val saveResult: SharedFlow<Boolean> = _saveResult.asSharedFlow()
    private val _saveOutcome = MutableSharedFlow<LocalLyricsManager.LrcSaveResult>()
    val saveOutcome: SharedFlow<LocalLyricsManager.LrcSaveResult> = _saveOutcome.asSharedFlow()

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    private val originalLyricsMap = mutableMapOf<LyricsEditorSource, String>()
    private val editedLyricsMap = mutableMapOf<LyricsEditorSource, String>()
    private var activeSong: Song? = null
    private var activeEditorTaskId: String? = null
    private val consumedTaskIds = HashSet<String>()

    init {
        viewModelScope.launch {
            LyricsAlignTaskManager.state.collect { snapshot ->
                syncAlignTaskSnapshot(snapshot)
            }
        }
    }

    private fun editorCandidateId(source: LyricsEditorSource): String = "editor:${source.name}"

    private suspend fun syncAlignTaskSnapshot(snapshot: LyricsAlignTaskManager.QueueSnapshot) {
        val song = activeSong ?: run {
            _aiAlignProgress.value = null
            _aiAlignQueuedAhead.value = null
            return
        }
        val songKey = LyricsAlignTaskManager.songKeyFor(song)
        val matchesTask: (LyricsAlignTaskManager.AlignTask) -> Boolean = { task ->
            task.songKey == songKey && (
                (activeEditorTaskId != null && task.taskId == activeEditorTaskId) ||
                    task.candidateId == editorCandidateId(_selectedSource.value)
                )
        }

        val active = snapshot.active?.takeIf(matchesTask)
        if (active != null) {
            activeEditorTaskId = active.taskId
            _aiAlignQueuedAhead.value = null
            _aiAlignProgress.value = active.progress
                ?: OnDeviceLyricsAligner.AlignProgress(0.01f, OnDeviceLyricsAligner.AlignStage.DECODING_VOCALS)
            return
        }

        val queuedIdx = snapshot.queued.indexOfFirst(matchesTask)
        if (queuedIdx >= 0) {
            activeEditorTaskId = snapshot.queued[queuedIdx].taskId
            _aiAlignQueuedAhead.value = queuedIdx + if (snapshot.active != null) 1 else 0
            _aiAlignProgress.value = OnDeviceLyricsAligner.AlignProgress(0f, OnDeviceLyricsAligner.AlignStage.DECODING_VOCALS)
            return
        }

        _aiAlignProgress.value = null
        _aiAlignQueuedAhead.value = null

        val completed = snapshot.completed.firstOrNull { task ->
            matchesTask(task) && task.taskId !in consumedTaskIds
        }
        if (completed != null) {
            consumedTaskIds.add(completed.taskId)
            activeEditorTaskId = null
            val cand = completed.resultCandidate
            if (cand != null) {
                val app = getApplication<Application>()
                val useWordSync = cand.format == LyricsFormat.WORD && AppSettings.lyricsSaveWordSync.value
                val generatedText = LyricsCatalog.serialize(
                    candidate = cand,
                    wordSync = useWordSync,
                    keepTranslation = false,
                    keepFurigana = true,
                )
                if (generatedText.isNotBlank()) {
                    updateText(generatedText)
                    val msgRes = if (cand.format == LyricsFormat.WORD) {
                        R.string.lyrics_ai_generated_ctc_toast
                    } else {
                        R.string.lyrics_ai_generated_vad_toast
                    }
                    _toastMessage.emit(app.getString(msgRes))
                    LyricsAlignTaskManager.dismissCompleted(completed.taskId)
                }
            }
            return
        }

        val failed = snapshot.failed.firstOrNull { task ->
            matchesTask(task) && task.taskId !in consumedTaskIds
        }
        if (failed != null) {
            consumedTaskIds.add(failed.taskId)
            activeEditorTaskId = null
            val app = getApplication<Application>()
            _toastMessage.emit(app.getString(R.string.lyrics_ai_failed_toast))
            LyricsAlignTaskManager.dismissFailed(failed.taskId)
        }
    }

    var lastSearchTitle: String? = null
        private set
    var lastSearchArtist: String? = null
        private set
    var lastSearchAlbum: String? = null
        private set
    var lastSearchProviders: Set<LyricsSource>? = null
        private set

    fun loadSong(song: Song) {
        if (activeSong?.videoId != song.videoId) {
            activeEditorTaskId = null
            _aiAlignProgress.value = null
            _aiAlignQueuedAhead.value = null
            clearSearchResults()
            lastSearchTitle = null
            lastSearchArtist = null
            lastSearchAlbum = null
            lastSearchProviders = null
        }
        activeSong = song
        viewModelScope.launch {
            _isLoading.value = true
            originalLyricsMap.clear()
            editedLyricsMap.clear()

            for (source in LyricsEditorSource.entries) {
                val text = LocalLyricsManager.readLyrics(getApplication(), song, source)
                originalLyricsMap[source] = text
                editedLyricsMap[source] = text
            }

            _currentText.value = editedLyricsMap[_selectedSource.value].orEmpty()
            _isLoading.value = false
            syncAlignTaskSnapshot(LyricsAlignTaskManager.state.value)
        }
    }

    fun selectSource(source: LyricsEditorSource) {
        // Save currently active text into map before switching
        editedLyricsMap[_selectedSource.value] = _currentText.value
        _selectedSource.value = source
        _currentText.value = editedLyricsMap[source].orEmpty()
        activeEditorTaskId = null
        viewModelScope.launch {
            syncAlignTaskSnapshot(LyricsAlignTaskManager.state.value)
        }
    }

    fun updateText(text: String) {
        _currentText.value = text
        editedLyricsMap[_selectedSource.value] = text
    }

    fun undoChanges() {
        val original = originalLyricsMap[_selectedSource.value].orEmpty()
        _currentText.value = original
        editedLyricsMap[_selectedSource.value] = original
    }

    fun autoDownload(
        title: String,
        artist: String,
        album: String? = null,
        providers: Set<LyricsSource>? = null,
    ) {
        lastSearchTitle = title
        lastSearchArtist = artist
        lastSearchAlbum = album
        lastSearchProviders = providers

        viewModelScope.launch {
            _isLoading.value = true
            val sources = providers ?: AppSettings.lyricsSources.value
            val durationMs = activeSong?.durationMillis() ?: 0L
            val result = LocalLyricsManager.autoDownload(
                title = title,
                artist = artist,
                album = album,
                durationMs = durationMs,
                sources = sources,
            )
            _isLoading.value = false

            if (result.hasBoth) {
                _candidateResult.value = result
            } else if (!result.synced.isNullOrBlank()) {
                updateText(result.synced)
                _toastMessage.emit("Synced lyrics loaded")
            } else if (!result.plain.isNullOrBlank()) {
                updateText(result.plain)
                _toastMessage.emit("Plain lyrics loaded")
            } else {
                _toastMessage.emit("No lyrics found online")
            }
        }
    }

    fun searchAllProviders(
        title: String,
        artist: String,
        album: String? = null,
        providers: Set<LyricsSource>,
    ) {
        lastSearchTitle = title
        lastSearchArtist = artist
        lastSearchAlbum = album
        lastSearchProviders = providers

        viewModelScope.launch {
            _isSearchingResults.value = true
            _searchResults.value = emptyList()
            val durationMs = activeSong?.durationMillis() ?: 0L
            val items = LocalLyricsManager.searchAllProviders(
                title = title,
                artist = artist,
                album = album,
                durationMs = durationMs,
                providers = providers,
                onPartialResults = { partial ->
                    _searchResults.value = partial
                },
            )
            _searchResults.value = items
            _isSearchingResults.value = false
        }
    }

    fun research() {
        val song = activeSong
        val title = lastSearchTitle ?: song?.title.orEmpty()
        val artist = lastSearchArtist ?: song?.artist.orEmpty()
        val album = lastSearchAlbum ?: song?.albumName
        val providers = lastSearchProviders ?: AppSettings.lyricsSources.value
        if (title.isNotBlank() || artist.isNotBlank()) {
            searchAllProviders(title, artist, album, providers)
        }
    }

    fun clearSearchResults() {
        _searchResults.value = emptyList()
        _isSearchingResults.value = false
    }

    fun applySearchResult(item: LyricsSearchResultItem) {
        if (item.hasSynced && item.hasPlain) {
            _candidateResult.value = LocalLyricsManager.DownloadedLyricsResult(
                plain = item.plainLyrics,
                synced = item.syncedLyrics,
            )
        } else if (item.hasSynced) {
            updateText(item.syncedLyrics!!)
            viewModelScope.launch {
                _toastMessage.emit("Loaded synced lyrics from ${item.provider}")
            }
        } else if (item.hasPlain) {
            updateText(item.plainLyrics!!)
            viewModelScope.launch {
                _toastMessage.emit("Loaded plain lyrics from ${item.provider}")
            }
        }
        // Do NOT call clearSearchResults() here: retain search results so user can switch or re-check.
    }

    fun downloadLyrics(title: String, artist: String) {
        autoDownload(title, artist)
    }

    fun applyCandidateSelection(mode: LyricsSelectionMode) {
        val candidate = _candidateResult.value ?: return
        val text = when (mode) {
            LyricsSelectionMode.Synced -> candidate.synced ?: candidate.plain
            LyricsSelectionMode.Plain -> candidate.plain ?: candidate.synced
        }
        if (!text.isNullOrBlank()) {
            updateText(text)
        }
        _candidateResult.value = null
    }

    fun dismissCandidateDialog() {
        _candidateResult.value = null
    }

    fun cancelAiAlign() {
        val taskId = activeEditorTaskId
        if (taskId != null) {
            LyricsAlignTaskManager.cancel(taskId)
        } else {
            val song = activeSong
            if (song != null) {
                LyricsAlignTaskManager.cancelForCandidate(
                    LyricsAlignTaskManager.songKeyFor(song),
                    editorCandidateId(_selectedSource.value),
                )
            }
        }
        activeEditorTaskId = null
        _aiAlignProgress.value = null
        _aiAlignQueuedAhead.value = null
    }

    fun generateAiSyncedLyrics(song: Song) {
        val rawText = _currentText.value
        val app = getApplication<Application>()
        if (rawText.isBlank()) {
            viewModelScope.launch { _toastMessage.emit(app.getString(R.string.lyrics_ai_failed_toast)) }
            return
        }
        viewModelScope.launch {
            val canAlign = withContext(Dispatchers.IO) {
                OnDeviceLyricsAligner.canAlignAudio(app, song)
            }
            if (!canAlign) {
                _toastMessage.emit(app.getString(R.string.lyrics_ai_no_audio_toast))
                return@launch
            }
            val source = _selectedSource.value
            val sourceLabel = when (source) {
                LyricsEditorSource.Embedded -> app.getString(R.string.lyrics_local_embedded_source)
                LyricsEditorSource.File -> app.getString(R.string.lyrics_local_lrc_source)
                LyricsEditorSource.Downloaded -> app.getString(R.string.downloaded)
            }
            val durationSec = (song.durationMillis() / 1000L).toInt().coerceAtLeast(0)
            val baseCandidate = LyricsCatalog.localCandidate(
                id = editorCandidateId(source),
                source = LyricsSource(
                    id = "editor_${source.name.lowercase()}",
                    label = sourceLabel,
                    wordSynced = false,
                ),
                title = song.title,
                artist = song.artist,
                album = song.albumName,
                durationSec = durationSec,
                raw = rawText,
            )
            if (baseCandidate == null) {
                _toastMessage.emit(app.getString(R.string.lyrics_ai_failed_toast))
                return@launch
            }
            val plainForAi = baseCandidate.lines
                .filter { it.timingSource == null && it.text.isNotBlank() }
                .joinToString("\n") { it.text }
                .ifBlank { rawText }
            val taskId = LyricsAlignTaskManager.enqueue(
                context = app,
                song = song,
                candidate = baseCandidate,
                plainText = plainForAi,
                forceReanalyze = true,
            )
            activeEditorTaskId = taskId
        }
    }

    fun saveLyrics(song: Song) {
        viewModelScope.launch {
            _isSaving.value = true
            editedLyricsMap[_selectedSource.value] = _currentText.value
            val currentSource = _selectedSource.value
            val lyricsToSave = _currentText.value

            val outcome = LocalLyricsManager.saveLyricsDetailed(
                context = getApplication(),
                song = song,
                source = currentSource,
                lyricsText = lyricsToSave,
            )

            val success = outcome == LocalLyricsManager.LrcSaveResult.Success
            if (success) {
                originalLyricsMap[currentSource] = lyricsToSave
            }

            _isSaving.value = false
            _saveResult.emit(success)
            _saveOutcome.emit(outcome)
        }
    }
}
