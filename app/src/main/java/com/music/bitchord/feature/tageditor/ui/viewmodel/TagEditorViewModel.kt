package com.music.bitchord.feature.tageditor.ui.viewmodel

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.music.bitchord.data.LocalMediaRepository
import com.music.bitchord.data.model.Song
import com.music.bitchord.feature.tageditor.data.TagLibWriter
import com.music.bitchord.feature.tageditor.domain.model.SongTagData
import com.music.bitchord.feature.tageditor.domain.model.TagField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** The other songs a save can also go to: the folder's own, and with its subfolders'. */
data class FolderScope(val direct: List<Song>, val withSubfolders: List<Song>)

/** How a save went, for the message after it. */
data class TagSaveResult(val total: Int, val written: Int, val failedTitles: List<String>) {
    val success: Boolean get() = written == total
}

class TagEditorViewModel(app: Application) : AndroidViewModel(app) {

    private val _tags = MutableStateFlow(SongTagData())
    val tags: StateFlow<SongTagData> = _tags.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _saveEvent = MutableSharedFlow<TagSaveResult>()
    val saveEvent: SharedFlow<TagSaveResult> = _saveEvent.asSharedFlow()

    /** Editing several songs at once (a folder), rather than one. */
    private val _batch = MutableStateFlow<FolderScope?>(null)
    val batch: StateFlow<FolderScope?> = _batch.asStateFlow()

    /** For one song: the rest of its folder, once found. */
    private val _folder = MutableStateFlow<FolderScope?>(null)
    val folder: StateFlow<FolderScope?> = _folder.asStateFlow()

    /** Fields whose value differs between the songs being edited together. */
    private val _mixed = MutableStateFlow<Set<TagField>>(emptySet())
    val mixed: StateFlow<Set<TagField>> = _mixed.asStateFlow()

    val applyToFolder = MutableStateFlow(false)
    val includeSubfolders = MutableStateFlow(false)

    /**
     * Fields whose tick the user has flipped from its default (changed fields
     * start ticked, except the per-song ones, which start unticked).
     */
    val toggledFields = MutableStateFlow<Set<TagField>>(emptySet())

    fun toggleField(field: TagField) {
        toggledFields.value = if (field in toggledFields.value) toggledFields.value - field else toggledFields.value + field
    }

    private var initialTags = SongTagData()
    private var song: Song? = null

    fun loadTags(song: Song) {
        this.song = song
        _batch.value = null
        viewModelScope.launch {
            _isLoading.value = true
            val data = TagLibWriter.readTags(getApplication(), song)
            initialTags = data
            _tags.value = data
            _isLoading.value = false
            _folder.value = findFolder(song)
        }
    }

    /**
     * Opens [direct] (a folder's own songs) for editing together; [withSubfolders]
     * is the same folder with its subfolders' songs, for the switch that adds them.
     * A field every song agrees on shows that value; one they differ in shows
     * empty, marked as differing, and is not written unless it is changed.
     */
    fun loadBatch(direct: List<Song>, withSubfolders: List<Song>) {
        song = direct.firstOrNull()
        _batch.value = FolderScope(direct, withSubfolders)
        viewModelScope.launch {
            _isLoading.value = true
            val all = withContext(Dispatchers.IO) { direct.map { TagLibWriter.readTags(getApplication(), it) } }
            var merged = all.firstOrNull() ?: SongTagData()
            val mixed = LinkedHashSet<TagField>()
            for (field in TagField.entries) {
                if (field == TagField.ARTWORK) continue
                val values = all.map { field.get(it).trim() }.toSet()
                if (values.size > 1) {
                    mixed += field
                    merged = field.set(merged, "")
                }
            }
            initialTags = merged.copy(artworkChanged = false, artworkDeleted = false)
            _tags.value = initialTags
            _mixed.value = mixed
            _isLoading.value = false
        }
    }

    private suspend fun findFolder(song: Song): FolderScope? = withContext(Dispatchers.IO) {
        val path = song.localPath ?: return@withContext null
        val dir = File(path).parentFile?.absolutePath ?: return@withContext null
        val library = runCatching { LocalMediaRepository.getLocalMusic(getApplication()) }.getOrNull() ?: return@withContext null
        fun dirOf(s: Song) = s.localPath?.let { File(it).parentFile?.absolutePath }
        val direct = library.filter { it.videoId != song.videoId && dirOf(it) == dir }
        val deeper = library.filter { other ->
            other.videoId != song.videoId && dirOf(other)?.let { it == dir || it.startsWith("$dir/") } == true
        }
        if (deeper.isEmpty()) null else FolderScope(direct, deeper)
    }

    fun updateTags(newTags: SongTagData) {
        _tags.value = newTags
    }

    fun updateArtworkFromUri(uri: Uri) {
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                }.getOrNull()
            }
            if (bitmap != null) {
                _tags.value = _tags.value.copy(
                    artworkBitmap = bitmap,
                    artworkChanged = true,
                    artworkDeleted = false,
                )
            }
        }
    }

    fun deleteArtwork() {
        _tags.value = _tags.value.copy(
            artworkBitmap = null,
            artworkChanged = true,
            artworkDeleted = true,
        )
    }

    fun restoreArtwork() {
        _tags.value = _tags.value.copy(
            artworkBitmap = initialTags.artworkBitmap,
            artworkChanged = false,
            artworkDeleted = false,
        )
    }

    fun resetTags() {
        _tags.value = initialTags
    }

    /** The fields changed in this session. */
    fun changedFields(): Set<TagField> = TagField.changed(initialTags, _tags.value)

    /** The changed fields that are unticked for the other songs. */
    fun excludedFields(changed: Set<TagField> = changedFields(), toggled: Set<TagField> = toggledFields.value): Set<TagField> =
        changed.filterTo(HashSet()) { it.perSong xor (it in toggled) }

    /** The changed fields that go to the other songs. */
    fun fieldsForOthers(): Set<TagField> = changedFields().let { it - excludedFields(it) }

    /** The other songs this save will also write to, if any. */
    fun otherTargets(): List<Song> {
        val scope = _batch.value?.let { batch ->
            return if (includeSubfolders.value) batch.withSubfolders else batch.direct
        } ?: _folder.value
        if (scope == null || !applyToFolder.value) return emptyList()
        return if (includeSubfolders.value) scope.withSubfolders else scope.direct
    }

    /** Every song the save will write to. */
    fun allTargets(): List<Song> {
        val others = otherTargets()
        return if (_batch.value != null) others else listOfNotNull(song) + others
    }

    fun save(song: Song) {
        viewModelScope.launch {
            _isSaving.value = true
            val data = _tags.value
            val failed = ArrayList<String>()
            var written = 0
            val inBatch = _batch.value != null
            if (!inBatch) {
                if (TagLibWriter.writeTags(getApplication(), song, data)) written++ else failed += song.title
            }
            val others = otherTargets()
            val fields = fieldsForOthers()
            if (fields.isNotEmpty()) {
                for (other in others) {
                    if (TagLibWriter.writeTags(getApplication(), other, data, fields)) written++ else failed += other.title
                }
            }
            val total = (if (inBatch) 0 else 1) + if (fields.isNotEmpty()) others.size else 0
            _isSaving.value = false
            _saveEvent.emit(TagSaveResult(total, written, failed))
        }
    }
}
