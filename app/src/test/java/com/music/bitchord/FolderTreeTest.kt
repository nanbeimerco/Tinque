package com.music.bitchord

import com.music.bitchord.data.model.Song
import com.music.bitchord.feature.localmusic.domain.FolderTreeBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderTreeTest {

    private fun makeSong(id: String, path: String, album: String? = null, art: String? = null): Song {
        return Song(
            videoId = id,
            title = "Track $id",
            artist = "Artist $id",
            thumbnailUrl = art,
            localPath = path,
            albumName = album,
        )
    }

    @Test
    fun testCommonRootDetection() {
        val paths = listOf(
            "/storage/emulated/0/Music/Rock/Queen",
            "/storage/emulated/0/Music/Rock/Beatles",
            "/storage/emulated/0/Music/Pop/ABBA",
        )
        val root = FolderTreeBuilder.findCommonRoot(paths)
        assertEquals("/storage/emulated/0/Music", root)
    }

    @Test
    fun testTreeHierarchy() {
        val songs = listOf(
            makeSong("1", "/storage/emulated/0/Music/Rock/Queen/Bohemian.mp3", "A Night at the Opera", "art1"),
            makeSong("2", "/storage/emulated/0/Music/Rock/Queen/RadioGaGa.mp3", "The Works", "art2"),
            makeSong("3", "/storage/emulated/0/Music/Rock/Beatles/HeyJude.mp3", "Single", "art3"),
            makeSong("4", "/storage/emulated/0/Music/Pop/ABBA/DancingQueen.mp3", "Arrival", "art4"),
            makeSong("5", "/storage/emulated/0/Music/TopTrack.mp3", null, null),
        )

        val rootNode = FolderTreeBuilder.build(songs)
        assertEquals("/storage/emulated/0/Music", rootNode.path)
        assertEquals("Music", rootNode.name)
        assertEquals(5, rootNode.totalSongCount)
        assertEquals(1, rootNode.directSongs.size) // TopTrack.mp3
        assertEquals(2, rootNode.subfolders.size) // Pop and Rock
        assertFalse(rootNode.isLeaf)

        val pop = rootNode.subfolders.find { it.name == "Pop" }
        assertNotNull(pop)
        assertEquals(1, pop!!.subfolders.size)
        val abba = pop.subfolders.find { it.name == "ABBA" }
        assertNotNull(abba)
        assertTrue(abba!!.isLeaf)
        assertEquals(1, abba.directSongs.size)
        assertEquals(1, abba.coverSongs.size)

        val rock = rootNode.subfolders.find { it.name == "Rock" }
        assertNotNull(rock)
        assertFalse(rock!!.isLeaf)
        assertEquals(2, rock.subfolders.size) // Beatles and Queen

        val queen = rock.subfolders.find { it.name == "Queen" }
        assertNotNull(queen)
        assertTrue(queen!!.isLeaf)
        assertEquals(2, queen.directSongs.size)
        assertEquals(2, queen.coverSongs.size)
    }

    @Test
    fun testCustomRoot() {
        val songs = listOf(
            makeSong("1", "/storage/emulated/0/Music/Rock/song1.mp3"),
            makeSong("2", "/storage/emulated/0/Download/song2.mp3"),
        )
        val tree = FolderTreeBuilder.build(songs, customRoot = "/storage/emulated/0/Music")
        assertEquals("/storage/emulated/0/Music", tree.path)
        assertEquals(1, tree.totalSongCount)
        assertEquals(1, tree.subfolders.size)
        assertEquals("Rock", tree.subfolders[0].name)
    }

    @Test
    fun testFindNodeByPath() {
        val songs = listOf(
            makeSong("1", "/storage/emulated/0/Music/Rock/Queen/Bohemian.mp3"),
        )
        val root = FolderTreeBuilder.build(songs)
        val queenNode = root.findByPath("/storage/emulated/0/Music/Rock/Queen")
        assertNotNull(queenNode)
        assertEquals("Queen", queenNode!!.name)
        assertTrue(queenNode.isLeaf)
    }
}
