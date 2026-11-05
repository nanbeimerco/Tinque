package com.music.bitchord.data.lyrics

import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.feature.lyrics.engine.LyricsExtensionExecutor
import com.music.bitchord.feature.lyrics.manager.LyricsExtensionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

/** How a lyric is timed. */
enum class LyricsFormat { PLAIN, LINE, WORD }

/**
 * How closely a search has to match the song. Each level says which of the
 * song's details are sent to the sources, and how far a result's length may
 * be from the song's.
 */
enum class LyricsStrictness(val useArtist: Boolean, val useAlbum: Boolean, val useDuration: Boolean) {
    STRICT(useArtist = true, useAlbum = true, useDuration = true),
    STANDARD(useArtist = true, useAlbum = false, useDuration = true),
    LOOSE(useArtist = true, useAlbum = false, useDuration = false),
    TITLE_ONLY(useArtist = false, useAlbum = false, useDuration = false),
}

/** What a catalog search sends: the song's details, only those switched on. */
data class LyricsQuery(
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long,
    val useArtist: Boolean = true,
    val useAlbum: Boolean = false,
    val useDuration: Boolean = true,
    val toleranceSec: Int = 5,
) {
    companion object {
        fun of(
            title: String,
            artist: String,
            album: String?,
            durationMs: Long,
            strictness: LyricsStrictness,
            toleranceSec: Int = 5,
        ) = LyricsQuery(title, artist, album, durationMs, strictness.useArtist, strictness.useAlbum, strictness.useDuration, toleranceSec)
    }
}

/** One result in the catalog: one source's lyric for one matching track. */
data class LyricsCandidate(
    val id: String,
    val source: LyricsSource,
    val title: String,
    val artist: String,
    val album: String?,
    val durationSec: Int,
    /** As the source returned it (LRC, enhanced LRC, TTML or text): what is stored or saved. */
    val raw: String,
    val lines: List<LyricLine>,
    val format: LyricsFormat,
    val hasTranslation: Boolean,
    val hasFurigana: Boolean,
    val hasBackgroundVocals: Boolean,
    /** 0..1: how well the result's details match the song's. */
    val match: Float,
)

/** One source's progress in a catalog search. */
sealed interface SourceStatus {
    data object Searching : SourceStatus
    data class Found(val count: Int) : SourceStatus
    data object None : SourceStatus
    data object Failed : SourceStatus
}

sealed interface CatalogEvent {
    data class Status(val source: LyricsSource, val status: SourceStatus) : CatalogEvent
    data class Results(val source: LyricsSource, val candidates: List<LyricsCandidate>) : CatalogEvent
}

/**
 * The lyrics catalog: asks every source at once for every lyric it has for a
 * song, not just its best one, and describes each — how it is timed, what it
 * comes with, how well it matches — for the listener to choose from.
 */
object LyricsCatalog {

    private val furiganaPattern = Regex("""(?<=[㐀-鿿々〆ヵヶ])(?:\(\([ぁ-ゖァ-ヺー]+\)\)|（（[ぁ-ゖァ-ヺー]+））|[（(][ぁ-ゖァ-ヺー]+[)）])""")

    fun search(query: LyricsQuery, sources: List<LyricsSource>): Flow<CatalogEvent> = callbackFlow {
        val gate = Semaphore(8)
        sources.forEach { source -> trySend(CatalogEvent.Status(source, SourceStatus.Searching)) }
        val jobs = sources.map { source ->
            launch(Dispatchers.IO) {
                gate.withPermit {
                    val found = runCatching { searchSource(source, query) }
                    found.onSuccess { list ->
                        if (list.isNotEmpty()) trySend(CatalogEvent.Results(source, list))
                        trySend(CatalogEvent.Status(source, if (list.isEmpty()) SourceStatus.None else SourceStatus.Found(list.size)))
                    }.onFailure {
                        trySend(CatalogEvent.Status(source, SourceStatus.Failed))
                    }
                }
            }
        }
        launch {
            jobs.forEach { it.join() }
            close()
        }
        awaitClose { }
    }.flowOn(Dispatchers.IO)

    private suspend fun searchSource(source: LyricsSource, query: LyricsQuery): List<LyricsCandidate> {
        val artist = if (query.useArtist) query.artist else ""
        val album = if (query.useAlbum) query.album else null
        val durationMs = if (query.useDuration) query.durationMs else 0L
        val results = ArrayList<LyricsCandidate>()

        if (com.music.bitchord.data.lyrics.providers.NativeLyricsProviders.isNativeProvider(source.id)) {
            val nativeItems = runCatching {
                com.music.bitchord.data.lyrics.providers.NativeLyricsProviders.searchLyrics(
                    providerId = source.id,
                    title = query.title,
                    artist = artist,
                    album = album,
                    durationMs = durationMs,
                )
            }.getOrDefault(emptyList())

            for (item in nativeItems) {
                val raw = item.bestRaw ?: continue
                candidate(
                    source = source,
                    id = "${source.id}:${item.id}",
                    title = item.title.ifBlank { query.title },
                    artist = item.artist.ifBlank { query.artist },
                    album = item.album ?: query.album,
                    durationSec = item.durationSeconds,
                    raw = raw,
                    query = query,
                )?.let(results::add)
            }
        }

        if (results.isEmpty() && !com.music.bitchord.data.lyrics.providers.NativeLyricsProviders.isNativeProvider(source.id)) {
            val ext = LyricsExtensionManager.getExtension(source.id) ?: return emptyList()
            if (!ext.scriptFile.exists()) return emptyList()

            val searched = runCatching {
                LyricsExtensionExecutor.searchLyrics(ext.id, ext.scriptFile, query.title, artist, album)
            }.getOrNull()
            if (!searched.isNullOrBlank()) {
                val array = runCatching { JSONArray(searched) }.getOrNull()
                if (array != null) {
                    for (i in 0 until array.length()) {
                        val obj = array.optJSONObject(i) ?: continue
                        val synced = obj.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
                        val plain = obj.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }
                        val raw = synced ?: plain ?: continue
                        candidate(
                            source = source,
                            id = "${source.id}:${obj.optString("id", i.toString())}",
                            title = obj.optString("title").ifBlank { query.title },
                            artist = obj.optString("artist").ifBlank { query.artist },
                            album = obj.optString("album").takeIf { it.isNotBlank() && it != "null" },
                            durationSec = obj.optInt("durationSeconds", 0),
                            raw = raw,
                            query = query,
                        )?.let(results::add)
                    }
                }
            }

            if (results.isEmpty()) {
                val raw = runCatching {
                    LyricsExtensionExecutor.getLyrics(
                        extensionId = ext.id,
                        scriptFile = ext.scriptFile,
                        title = query.title,
                        artist = artist,
                        durationMs = durationMs,
                        album = album,
                        videoId = "",
                    )
                }.getOrNull()
                if (!raw.isNullOrBlank()) {
                    candidate(
                        source = source,
                        id = "${source.id}:best",
                        title = query.title,
                        artist = query.artist,
                        album = query.album,
                        durationSec = 0,
                        raw = raw,
                        query = query,
                    )?.let(results::add)
                }
            }
        }

        return results
            .filter { candidate ->
                // Strict about length only where the result says its length.
                !query.useDuration || candidate.durationSec <= 0 || query.durationMs <= 0 ||
                    abs(candidate.durationSec - query.durationMs / 1000) <= query.toleranceSec
            }
            .distinctBy { it.raw.hashCode() }
            .sortedByDescending { it.match }
    }

    private fun candidate(
        source: LyricsSource,
        id: String,
        title: String,
        artist: String,
        album: String?,
        durationSec: Int,
        raw: String,
        query: LyricsQuery,
    ): LyricsCandidate? {
        val lines = ProviderLyrics.parse(raw)?.map { it.withDecodedEntities() }?.withBackgroundVocals() ?: return null
        val format = formatOf(lines)
        val lastSec = (lines.maxOfOrNull { it.timeMs } ?: 0L) / 1000
        val length = if (durationSec > 0) durationSec else 0
        return LyricsCandidate(
            id = id,
            source = source,
            title = HtmlEntities.decode(title),
            artist = HtmlEntities.decode(artist),
            album = album?.let(HtmlEntities::decode),
            durationSec = length,
            raw = raw,
            lines = lines,
            format = format,
            hasTranslation = lines.any { it.timingSource != null },
            hasFurigana = lines.any { furiganaPattern.containsMatchIn(it.text) },
            hasBackgroundVocals = lines.any { it.background != null },
            match = matchOf(query, title, artist, album, length, lastSec),
        )
    }

    fun formatOf(lines: List<LyricLine>): LyricsFormat = when {
        lines.any { it.isWordSynced } -> LyricsFormat.WORD
        lines.any { it.timeMs > 0 } -> LyricsFormat.LINE
        else -> LyricsFormat.PLAIN
    }

    /**
     * How well a result matches the song, 0..1: the title counts most, then
     * the artist, the album and the length (when the result gives them).
     */
    fun matchOf(query: LyricsQuery, title: String, artist: String, album: String?, durationSec: Int, lastLineSec: Long): Float {
        var score = 0f
        var weight = 0f
        fun part(w: Float, s: Float) { score += w * s; weight += w }
        part(0.45f, similarity(query.title, title))
        if (query.artist.isNotBlank() && artist.isNotBlank()) part(0.3f, similarity(query.artist, artist))
        if (!query.album.isNullOrBlank() && !album.isNullOrBlank()) part(0.1f, similarity(query.album, album))
        val songSec = query.durationMs / 1000
        if (songSec > 0) {
            when {
                durationSec > 0 -> part(0.15f, (1f - abs(durationSec - songSec) / 20f).coerceIn(0f, 1f))
                // No length given: lyrics running well past the song's end are another song's.
                lastLineSec > songSec + 20 -> part(0.15f, 0f)
            }
        }
        return if (weight == 0f) 0f else score / weight
    }

    private fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
            .replace(Regex("""[(\[（【].*?[)\]）】]"""), " ")
            .replace(Regex("""\b(feat|ft)\.?\s.*$"""), " ")
            .replace(Regex("""[^\p{L}\p{N}]+"""), "")

    /** Similarity of two names, 0..1, by shared character pairs (ignoring case, width and brackets). */
    fun similarity(a: String, b: String): Float {
        val x = normalize(a)
        val y = normalize(b)
        if (x.isEmpty() || y.isEmpty()) return 0f
        if (x == y) return 1f
        if (x.contains(y) || y.contains(x)) return 0.85f
        fun pairs(s: String) = if (s.length < 2) setOf(s) else (0 until s.length - 1).map { s.substring(it, it + 2) }.toSet()
        val px = pairs(x)
        val py = pairs(y)
        val shared = px.intersect(py).size
        return (2f * shared / (px.size + py.size)).coerceIn(0f, 1f)
    }

    /**
     * [candidate] as text to save or embed: word timing kept as enhanced LRC
     * or dropped to line timing ([wordSync]), translations and furigana kept
     * or removed.
     */
    fun serialize(candidate: LyricsCandidate, wordSync: Boolean, keepTranslation: Boolean, keepFurigana: Boolean): String {
        var lines = candidate.lines
        if (!keepTranslation) lines = lines.filter { it.timingSource == null }
        if (!keepFurigana) {
            lines = lines.map { line ->
                if (furiganaPattern.containsMatchIn(line.text) && line.words.isEmpty()) {
                    line.copy(text = furiganaPattern.replace(line.text, ""))
                } else {
                    line
                }
            }
        }
        return when {
            candidate.format == LyricsFormat.PLAIN -> lines.joinToString("\n") { it.text }
            candidate.format == LyricsFormat.WORD && wordSync -> lines.toEnhancedLrc()
            else -> lines.toLrc()
        }
    }

    /** Whether a lyric lookup should take [lines] for a song of [durationMs]: not if it runs well past the end. */
    fun plausibleFor(lines: List<LyricLine>, durationMs: Long): Boolean {
        if (durationMs <= 0) return true
        val last = lines.maxOfOrNull { it.timeMs } ?: return true
        return last <= durationMs + 20_000
    }

    /** The strictness set for automatic lookups. */
    fun autoStrictness(): LyricsStrictness =
        runCatching { LyricsStrictness.valueOf(AppSettings.lyricsAutoStrictness.value) }.getOrDefault(LyricsStrictness.STANDARD)
}
