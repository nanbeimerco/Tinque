package com.music.bitchord.feature.localmusic.domain

import com.music.bitchord.data.model.Song
import com.music.bitchord.feature.localsearch.domain.LocalFileNameOrdering
import java.io.File
import java.util.Locale

/**
 * Represents a directory node in the local music hierarchy.
 *
 * @property path Absolute filesystem directory path (e.g. "/storage/emulated/0/Music/Rock")
 * @property name Folder display name (e.g. "Rock")
 * @property relativePath Path relative to the browsed root (e.g. "Rock")
 * @property subfolders Direct subdirectories containing audio tracks
 * @property directSongs Audio tracks stored directly in this folder
 * @property totalSongCount Total number of tracks in this folder and all subfolders
 * @property coverSongs Up to 4 songs from this folder/subfolders used to display artwork thumbnails
 * @property isLeaf True if this folder contains direct songs and has no further subfolders
 */
data class FolderNode(
    val path: String,
    val name: String,
    val relativePath: String,
    val subfolders: List<FolderNode>,
    val directSongs: List<Song>,
    val totalSongCount: Int,
    val coverSongs: List<Song>,
    val isLeaf: Boolean = subfolders.isEmpty() && directSongs.isNotEmpty(),
) {
    /** All songs in this folder and all its descendant subfolders */
    val allSongs: List<Song> by lazy {
        directSongs + subfolders.flatMap { it.allSongs }
    }

    private val pathIndex: Map<String, FolderNode> by lazy {
        val map = HashMap<String, FolderNode>()
        fun index(node: FolderNode) {
            map[pathKey(node.path)] = node
            for (sub in node.subfolders) {
                index(sub)
            }
        }
        index(this)
        map
    }

    /**
     * Finds a node by its path within this subtree. Letter case is ignored,
     * as the tree itself ignores it (see [FolderTreeBuilder.canonicalDirectories]):
     * a folder remembered under one spelling is found under the other.
     */
    fun findByPath(targetPath: String): FolderNode? = pathIndex[pathKey(targetPath)]

    private fun pathKey(path: String): String =
        path.trimEnd('/', '\\').replace('\\', '/').lowercase(Locale.ROOT)
}

object FolderTreeBuilder {

    /**
     * Extracts up to [max] songs having distinct artwork/thumbnails for folder previews.
     */
    fun extractCoverSongs(songs: List<Song>, max: Int = 4): List<Song> {
        val withArt = songs.filter { !it.thumbnailUrl.isNullOrBlank() }
        if (withArt.isEmpty()) return emptyList()

        val byAlbum = withArt.groupBy { it.albumName?.trim()?.ifEmpty { null } ?: it.videoId }
        val result = mutableListOf<Song>()
        for ((_, albumSongs) in byAlbum) {
            if (result.size >= max) break
            result.add(albumSongs.first())
        }
        if (result.size < max) {
            for (s in withArt) {
                if (result.size >= max) break
                if (s !in result) result.add(s)
            }
        }
        return result
    }

    /**
     * One spelling for every directory that differs from another only in
     * letter case, mapping each directory as given to that spelling.
     *
     * Shared storage on Android ignores letter case, so "Music" and "music"
     * are the same folder there; but the media store keeps a file's path
     * exactly as the app that saved it wrote it. One song saved by another
     * app under "music/" was enough to show a second, one-song "music"
     * folder beside "Music". Each part of a path is spelled the way most of
     * the songs under it spell it (ties go to the one sorting first, which
     * prefers capitals).
     *
     * @param songCounts each directory as given, with how many songs are in it
     */
    fun canonicalDirectories(songCounts: Map<String, Int>): Map<String, String> {
        // For every path prefix (lower-cased): how many songs spell its last part which way.
        val votes = HashMap<String, HashMap<String, Int>>()
        for ((dir, count) in songCounts) {
            val key = StringBuilder()
            for (part in dir.split('/')) {
                key.append('/').append(part.lowercase(Locale.ROOT))
                votes.getOrPut(key.toString()) { HashMap() }.merge(part, count, Int::plus)
            }
        }
        val chosen = HashMap<String, String>(votes.size)
        for ((key, spellings) in votes) {
            chosen[key] = spellings.entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .first().key
        }
        return songCounts.keys.associateWith { dir ->
            val key = StringBuilder()
            dir.split('/').joinToString("/") { part ->
                key.append('/').append(part.lowercase(Locale.ROOT))
                chosen[key.toString()] ?: part
            }
        }
    }

    /**
     * Finds the longest common ancestor directory among the given directory paths.
     */
    fun findCommonRoot(paths: Collection<String>): String {
        if (paths.isEmpty()) return ""
        val splitPaths = paths.map { path ->
            path.replace('\\', '/').split('/').filter(String::isNotEmpty)
        }
        val minLen = splitPaths.minOf { it.size }
        val common = mutableListOf<String>()
        for (i in 0 until minLen) {
            val segment = splitPaths[0][i]
            if (splitPaths.all { it[i].equals(segment, ignoreCase = true) }) {
                common.add(segment)
            } else {
                break
            }
        }
        return if (common.isEmpty()) "/" else "/" + common.joinToString("/")
    }

    /**
     * Builds a [FolderNode] tree from the provided list of local songs.
     *
     * @param songs List of local songs with valid `localPath`
     * @param customRoot Optional custom directory path to use as root
     */
    fun build(songs: List<Song>, customRoot: String? = null): FolderNode {
        val validSongs = songs.filter { !it.localPath.isNullOrBlank() }
        if (validSongs.isEmpty()) {
            val rootName = customRoot?.substringAfterLast('/')?.ifEmpty { "Music" } ?: "Music"
            return FolderNode(
                path = customRoot ?: "",
                name = rootName,
                relativePath = "",
                subfolders = emptyList(),
                directSongs = emptyList(),
                totalSongCount = 0,
                coverSongs = emptyList(),
            )
        }

        val rawDir = { song: Song -> song.localPath!!.replace('\\', '/').substringBeforeLast('/') }
        val canonical = canonicalDirectories(validSongs.groupingBy(rawDir).eachCount())
        val songsByDir = validSongs.groupBy { canonical.getValue(rawDir(it)) }

        val normalizedCustomRoot = customRoot?.trimEnd('/', '\\')?.replace('\\', '/')
            ?.takeIf { it.isNotBlank() }

        val rootPath = normalizedCustomRoot ?: findCommonRoot(songsByDir.keys).trimEnd('/')

        val rootName = rootPath.substringAfterLast('/').ifEmpty { "Music" }
        val rootBuilder = FolderNodeBuilder(rootPath, rootName)

        for ((dirPath, dirSongs) in songsByDir) {
            val normDir = dirPath.trimEnd('/')
            if (normalizedCustomRoot != null && !normDir.startsWith(normalizedCustomRoot, ignoreCase = true)) {
                // If custom root is specified, skip songs outside that root
                continue
            }

            val rel = if (normDir.startsWith(rootPath, ignoreCase = true)) {
                normDir.substring(rootPath.length).trim('/')
            } else {
                normDir.trim('/')
            }

            if (rel.isEmpty()) {
                rootBuilder.directSongs.addAll(dirSongs)
            } else {
                val segments = rel.split('/')
                var current = rootBuilder
                var currPath = rootPath
                for (seg in segments) {
                    currPath = if (currPath.isEmpty() || currPath == "/") "/$seg" else "$currPath/$seg"
                    current = current.subfolders.getOrPut(seg) {
                        FolderNodeBuilder(currPath, seg)
                    }
                }
                current.directSongs.addAll(dirSongs)
            }
        }

        return rootBuilder.build(rootPath)
    }

    private class FolderNodeBuilder(val path: String, val name: String) {
        val subfolders = mutableMapOf<String, FolderNodeBuilder>()
        val directSongs = mutableListOf<Song>()

        fun build(rootPath: String): FolderNode {
            val builtSubs = subfolders.values
                .sortedWith { a, b -> LocalFileNameOrdering.naturalCompare(a.name, b.name) }
                .map { it.build(rootPath) }
            val sortedDirectSongs = LocalFileNameOrdering.sort(directSongs)
            val allSubSongs = builtSubs.flatMap { it.allSongs }
            val totalCount = sortedDirectSongs.size + allSubSongs.size
            val allSubAndDirect = sortedDirectSongs + allSubSongs
            val covers = extractCoverSongs(allSubAndDirect)
            val rel = if (path.startsWith(rootPath, ignoreCase = true)) {
                path.substring(rootPath.length).trim('/')
            } else {
                path.trim('/')
            }
            return FolderNode(
                path = path,
                name = name,
                relativePath = rel,
                subfolders = builtSubs,
                directSongs = sortedDirectSongs,
                totalSongCount = totalCount,
                coverSongs = covers,
                isLeaf = builtSubs.isEmpty() && sortedDirectSongs.isNotEmpty(),
            )
        }
    }
}
