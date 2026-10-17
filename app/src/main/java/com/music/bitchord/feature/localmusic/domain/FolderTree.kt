package com.music.bitchord.feature.localmusic.domain

import com.music.bitchord.data.model.Song
import com.music.bitchord.feature.localsearch.domain.LocalFileNameOrdering
import java.io.File

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
            map[node.path.trimEnd('/', '\\').replace('\\', '/')] = node
            for (sub in node.subfolders) {
                index(sub)
            }
        }
        index(this)
        map
    }

    /** Finds a node by its path within this subtree */
    fun findByPath(targetPath: String): FolderNode? {
        val normTarget = targetPath.trimEnd('/', '\\').replace('\\', '/')
        return pathIndex[normTarget]
    }
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

        val songsByDir = validSongs.groupBy {
            it.localPath!!.replace('\\', '/').substringBeforeLast('/')
        }

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
                normDir.removePrefix(rootPath).trim('/')
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
                path.removePrefix(rootPath).trim('/')
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
