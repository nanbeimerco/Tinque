package com.music.bitchord.data.lyrics.align

import com.music.bitchord.data.lyrics.LrcParser
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricsCandidate
import com.music.bitchord.data.lyrics.LyricsFormat
import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.data.lyrics.toEnhancedLrc
import com.music.bitchord.feature.lyricscatalog.LyricsCatalogAiSessionCache
import com.music.bitchord.playback.smart.VocalSpectrogram
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class OnDeviceLyricsAlignerTest {

    @Test
    fun g2pMapperHandlesFuriganaKuromojiEnglishKoreanAndRoundTrips() {
        val rawLyrics = """
            [ti:Test Song]
            [Verse 1]
            夜((よる))に駆((か))ける
            今日は空へ行く
            君を待つ Hello, don't stop!
            夜にDriveする
            사랑해
        """.trimIndent()

        val mapped = LyricsG2pMapper.map(rawLyrics, vocabSize = 29)
        assertEquals(5, mapped.lines.size)

        // Furigana annotations should strip ((reading)) from displayText while keeping phonemes
        val line0 = mapped.lines[0]
        assertEquals("夜に駆ける", line0.displayText)
        assertFalse(line0.displayText.contains("(("))
        assertTrue(line0.units.any { it.text == "夜" && it.tokenCount >= 3 }) // y-o-r-u

        // Check particle overrides (空へ行く -> e, 君を待つ -> o)
        assertTrue(mapped.lines[1].units.sumOf { it.tokenCount } >= 6)
        assertTrue(mapped.lines[2].displayText.contains("Hello, don't stop!"))
        assertTrue(mapped.lines[3].displayText == "夜にDriveする")
        assertTrue(mapped.lines[4].units.first().tokenCount >= 6)

        // Verify all token IDs are in valid non-blank 1..28 range
        assertTrue(mapped.tokenIds.all { it in 1..28 })

        // Verify round-trip through buildSyncedLines -> toEnhancedLrc -> LrcParser preserves text & spans
        val syntheticSpans = mapped.tokenIds.indices.map { idx ->
            CtcForcedAligner.TokenSpan(startFrame = idx * 4, endFrame = idx * 4 + 3)
        }
        val lines = CtcForcedAligner.buildSyncedLines(
            mapped = mapped,
            spans = syntheticSpans,
            frameDurationMs = 20.0,
            totalDurationMs = 60_000L,
        )
        val enhanced = lines.toEnhancedLrc()
        assertTrue(enhanced.contains("<00:"))
        val reparsed = LrcParser.parseLrc(enhanced).filter { !it.isGap }
        assertEquals(mapped.lines.map { it.displayText }, reparsed.map { it.text })
        for (line in reparsed) {
            assertTrue("Expected word sync for ${line.text}", line.isWordSynced)
            // Every word span must map cleanly inside line.text
            for (span in line.wordSpans) {
                assertTrue(span.first >= 0 && span.last < line.text.length)
            }
        }
    }

    @Test
    fun ctcForcedAlignerAlignsWordsAndInsertsInstrumentalGaps() {
        val lyrics = "あいう\nかき"
        val mapped = LyricsG2pMapper.map(lyrics, vocabSize = 29)
        assertEquals(2, mapped.lines.size)

        // Expected tokens: a(1), i(2), u(6), k(11), a(1), k(11), i(2) -> 7 tokens
        assertEquals(7, mapped.tokenIds.size)

        val totalFrames = 600 // 600 * 20ms = 12,000 ms
        val vocabSize = 29
        val emissions = FloatArray(totalFrames * vocabSize) { -8f }

        // Default high probability for blank (id 0) across all frames
        for (f in 0 until totalFrames) {
            emissions[f * vocabSize] = 4f
        }

        fun paintToken(tokenId: Int, startFrame: Int, endFrameExclusive: Int) {
            for (f in startFrame until endFrameExclusive) {
                emissions[f * vocabSize] = -6f
                emissions[f * vocabSize + tokenId] = 6f
            }
        }

        // Line 1 ("あいう"): frames 210..269 (4200ms..5400ms) -> leaves 4200ms intro gap
        paintToken(mapped.tokenIds[0], 210, 230) // a
        paintToken(mapped.tokenIds[1], 230, 250) // i
        paintToken(mapped.tokenIds[2], 250, 270) // u

        // Frames 270..519 remain blank -> 5000ms instrumental solo break

        // Line 2 ("かき"): frames 520..569 (10400ms..11400ms)
        paintToken(mapped.tokenIds[3], 520, 532) // k
        paintToken(mapped.tokenIds[4], 532, 545) // a
        paintToken(mapped.tokenIds[5], 545, 557) // k
        paintToken(mapped.tokenIds[6], 557, 570) // i

        CtcForcedAligner.logSoftmaxInPlace(emissions, totalFrames, vocabSize)
        // Verify row sum exp(logP) ~= 1.0 on sample frames
        val row0Sum = (0 until vocabSize).sumOf { v -> exp(emissions[v].toDouble()) }
        assertEquals(1.0, row0Sum, 1e-3)

        // Check idempotency if model outputs log-probs already
        val row0Before = emissions[0]
        CtcForcedAligner.logSoftmaxInPlace(emissions, totalFrames, vocabSize)
        assertEquals(row0Before, emissions[0], 1e-4f)

        val spans = CtcForcedAligner.align(emissions, totalFrames, vocabSize, mapped.tokenIds, blankId = 0)
        assertNotNull(spans)
        spans!!
        assertEquals(mapped.tokenIds.size, spans.size)
        assertTrue("Line 1 start frame ${spans[0].startFrame} should be near 210", spans[0].startFrame in 205..215)
        assertTrue("Line 2 start frame ${spans[3].startFrame} should be near 520", spans[3].startFrame in 515..525)

        val synced = CtcForcedAligner.buildSyncedLines(
            mapped = mapped,
            spans = spans,
            frameDurationMs = 20.0,
            totalDurationMs = 12_000L,
        )

        // Expect: [0ms gap, Line 1 (~4200ms), ~5400ms gap, Line 2 (~10400ms)]
        assertEquals(4, synced.size)
        assertTrue(synced[0].isGap)
        assertEquals(0L, synced[0].timeMs)

        assertEquals("あいう", synced[1].text)
        assertTrue(synced[1].timeMs in 4_100L..4_300L)
        assertTrue(synced[1].isWordSynced)

        assertTrue(synced[2].isGap)
        assertTrue(synced[2].timeMs in 5_200L..5_500L)

        assertEquals("かき", synced[3].text)
        assertTrue(synced[3].timeMs in 10_300L..10_500L)
    }

    @Test
    fun vocalVadLineAlignerSkipsIntroSoloAndOutroBreaks() {
        val frameSec = VocalSpectrogram.HOP.toDouble() / VocalSpectrogram.SAMPLE_RATE // ~0.02322s
        val totalSec = 60.0
        val totalFrames = (totalSec / frameSec).roundToInt()
        val curve = FloatArray(totalFrames) { idx ->
            val t = idx * frameSec
            when {
                t < 10.0 -> 0.02f   // 10s intro instrumental
                t < 25.0 -> 0.78f   // Verse vocals
                t < 36.0 -> 0.03f   // 11s guitar solo break
                t < 55.0 -> 0.82f   // Chorus vocals
                else -> 0.02f       // 5s outro
            }
        }

        val segments = VocalVadLineAligner.detectVocalSegments(
            vocalCurve = curve,
            frameDurationSec = frameSec,
            totalDurationMs = 60_000L,
        )
        assertEquals(2, segments.size)
        assertTrue(segments[0].startMs in 9_300L..10_700L)
        assertTrue(segments[0].endMs in 24_300L..25_700L)
        assertTrue(segments[1].startMs in 35_300L..36_700L)
        assertTrue(segments[1].endMs in 54_300L..55_700L)

        val lyrics = """
            Intro skipped line one
            Still singing in verse one

            Chorus line after solo
            Final chorus line
        """.trimIndent()
        val aligned = VocalVadLineAligner.align(
            plainText = lyrics,
            vocalCurve = curve,
            frameDurationSec = frameSec,
            totalDurationMs = 60_000L,
        )

        val sung = aligned.filter { !it.isGap }
        val gaps = aligned.filter { it.isGap }
        assertEquals(4, sung.size)
        assertTrue("Intro gap should be present", gaps.any { it.timeMs == 0L })
        assertTrue("First sung line ${sung[0].timeMs}ms must skip 10s intro", sung[0].timeMs >= 9_300L)
        assertTrue("Mid-song instrumental break gap should be inserted", gaps.any { it.timeMs in 24_000L..26_000L })
        assertTrue("Final line must finish before outro", (sung.last().sungUntilMs ?: sung.last().timeMs) <= 56_000L)
    }

    @Test
    fun vocalSpectrogramReconstructsUnityMaskWaveform() {
        val n = VocalSpectrogram.SAMPLE_RATE.toInt() // 1 second = 44,100 samples
        val left = FloatArray(n) { i -> sin(2.0 * PI * 1000.0 * i / VocalSpectrogram.SAMPLE_RATE).toFloat() }
        val right = FloatArray(n) { i -> 0.5f * sin(2.0 * PI * 440.0 * i / VocalSpectrogram.SAMPLE_RATE).toFloat() }

        val complex = VocalSpectrogram.computeComplex(left, right, VocalSpectrogram.SAMPLE_RATE)
        assertNotNull(complex)
        complex!!

        // Build target magnitude buffer with unity mask (target == mix)
        val unityBuf = ByteBuffer.allocateDirect(complex.magnitude.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        unityBuf.put(complex.magnitude)
        unityBuf.rewind()

        val reconstructed = VocalSpectrogram.reconstructMonoFromMask(
            complex = complex,
            targetMag = unityBuf,
            fixedFrames = complex.frames,
        )
        assertEquals(n, reconstructed.size)

        var sumSq = 0.0
        for (sample in reconstructed) {
            assertTrue("Reconstructed sample must be finite", sample.isFinite())
            sumSq += sample.toDouble() * sample.toDouble()
        }
        val rms = sqrt(sumSq / n)
        assertTrue("Unity-mask ISTFT RMS ($rms) should closely preserve stereo average energy", rms > 0.35)
    }

    @Test
    fun sessionCacheStoresReplacesAndEvictsCleanly() {
        LyricsCatalogAiSessionCache.clear()
        fun makeCandidate(id: String, raw: String) = LyricsCandidate(
            id = id,
            source = LyricsSource("on_device_ai", "AI", wordSynced = true),
            title = "Open Invitation",
            artist = "CINDY",
            album = null,
            durationSec = 240,
            raw = raw,
            lines = listOf(LyricLine(1_000L, "Hello")),
            format = LyricsFormat.WORD,
            hasTranslation = false,
            hasFurigana = false,
            hasBackgroundVocals = false,
            match = 1f,
        )

        val first = makeCandidate("petit_1", "[00:01.00]<00:01.00>Hello<00:02.00>")
        LyricsCatalogAiSessionCache.put("songA", "petit_1", first)
        assertEquals(first, LyricsCatalogAiSessionCache.get("songA", "petit_1"))
        assertEquals(listOf(first), LyricsCatalogAiSessionCache.allForSong("songA"))

        val updated = makeCandidate("petit_1", "[00:01.50]<00:01.50>Hello<00:02.50>")
        LyricsCatalogAiSessionCache.put("songA", "petit_1", updated)
        assertEquals(updated, LyricsCatalogAiSessionCache.get("songA", "petit_1"))

        for (i in 0 until 40) {
            LyricsCatalogAiSessionCache.put("song_$i", "cand_$i", makeCandidate("cand_$i", "raw_$i"))
        }
        assertNull(LyricsCatalogAiSessionCache.get("song_0", "cand_0"))
        assertNotNull(LyricsCatalogAiSessionCache.get("song_39", "cand_39"))
        LyricsCatalogAiSessionCache.clear()
    }

    @Test
    fun lyricsAlignTaskManagerRunsSequentiallyUpdatesCacheAndSupportsCancel() = kotlinx.coroutines.runBlocking {
        LyricsAlignTaskManager.clearAllForTesting()
        LyricsCatalogAiSessionCache.clear()

        fun makeCandidate(id: String, raw: String) = LyricsCandidate(
            id = id,
            source = LyricsSource("petitlyrics", "PetitLyrics", wordSynced = false),
            title = "Title",
            artist = "Artist",
            album = null,
            durationSec = 180,
            raw = raw,
            lines = listOf(LyricLine(0L, raw)),
            format = LyricsFormat.PLAIN,
            hasTranslation = false,
            hasFurigana = false,
            hasBackgroundVocals = false,
            match = 1f,
        )

        val songA = com.music.bitchord.data.model.Song(
            videoId = "vid_a",
            title = "Song A",
            artist = "Artist A",
            thumbnailUrl = null,
        )
        val songB = com.music.bitchord.data.model.Song(
            videoId = "vid_b",
            title = "Song B",
            artist = "Artist B",
            thumbnailUrl = null,
        )
        val songC = com.music.bitchord.data.model.Song(
            videoId = "vid_c",
            title = "Song C",
            artist = "Artist C",
            thumbnailUrl = null,
        )
        val songD = com.music.bitchord.data.model.Song(
            videoId = "vid_d",
            title = "Song D",
            artist = "Artist D",
            thumbnailUrl = null,
        )

        val concurrent = java.util.concurrent.atomic.AtomicInteger(0)
        val maxConcurrent = java.util.concurrent.atomic.AtomicInteger(0)
        val startedA = kotlinx.coroutines.CompletableDeferred<Unit>()
        val releaseA = kotlinx.coroutines.CompletableDeferred<Unit>()
        val startedC = kotlinx.coroutines.CompletableDeferred<Unit>()

        LyricsAlignTaskManager.alignerOverride = { _, song, plainText, _, onProgress ->
            val now = concurrent.incrementAndGet()
            maxConcurrent.updateAndGet { prev -> kotlin.math.max(prev, now) }
            try {
                when (song.videoId) {
                    "vid_a" -> {
                        onProgress(OnDeviceLyricsAligner.AlignProgress(0.4f, OnDeviceLyricsAligner.AlignStage.CTC_INFERENCE))
                        startedA.complete(Unit)
                        releaseA.await()
                    }
                    "vid_c" -> {
                        startedC.complete(Unit)
                        while (true) {
                            kotlinx.coroutines.delay(25L)
                        }
                    }
                }
                val lrc = "[00:01.00]<00:01.00>$plainText"
                OnDeviceLyricsAligner.AlignResult(
                    lines = listOf(LyricLine(1000L, plainText)),
                    mode = OnDeviceLyricsAligner.AlignMode.CTC_WORD_AI,
                    enhancedLrc = lrc,
                    lineLrc = "[00:01.00]$plainText",
                )
            } finally {
                concurrent.decrementAndGet()
            }
        }

        try {
            val taskA = LyricsAlignTaskManager.enqueueInternal(null, songA, makeCandidate("candA", "Hello A"), "Hello A")
            val taskB = LyricsAlignTaskManager.enqueueInternal(null, songB, makeCandidate("candB", "Hello B"), "Hello B")

            kotlinx.coroutines.withTimeout(5_000L) { startedA.await() }
            val midSnapshot = LyricsAlignTaskManager.state.value
            assertEquals(taskA, midSnapshot.active?.taskId)
            assertEquals(1, midSnapshot.queued.size)
            assertEquals(taskB, midSnapshot.queued[0].taskId)
            assertEquals(1, maxConcurrent.get())

            releaseA.complete(Unit)
            kotlinx.coroutines.withTimeout(5_000L) {
                while (LyricsAlignTaskManager.state.value.completed.size < 2 ||
                    LyricsAlignTaskManager.state.value.active != null ||
                    LyricsAlignTaskManager.state.value.queued.isNotEmpty()
                ) {
                    kotlinx.coroutines.delay(15L)
                }
            }

            assertEquals(1, maxConcurrent.get())
            assertNotNull(LyricsCatalogAiSessionCache.get(LyricsAlignTaskManager.songKeyFor(songA), "candA"))
            assertNotNull(LyricsCatalogAiSessionCache.get(LyricsAlignTaskManager.songKeyFor(songB), "candB"))

            LyricsAlignTaskManager.dismissCompleted(taskA)
            LyricsAlignTaskManager.dismissCompleted(taskB)
            assertTrue(LyricsAlignTaskManager.state.value.completed.isEmpty())

            // Cancel active task C and verify queued task D still completes cleanly
            val taskC = LyricsAlignTaskManager.enqueueInternal(null, songC, makeCandidate("candC", "Hello C"), "Hello C")
            val taskD = LyricsAlignTaskManager.enqueueInternal(null, songD, makeCandidate("candD", "Hello D"), "Hello D")
            kotlinx.coroutines.withTimeout(5_000L) { startedC.await() }
            LyricsAlignTaskManager.cancel(taskC)

            kotlinx.coroutines.withTimeout(5_000L) {
                while (LyricsAlignTaskManager.state.value.active != null ||
                    LyricsAlignTaskManager.state.value.queued.isNotEmpty() ||
                    LyricsAlignTaskManager.state.value.completed.none { it.taskId == taskD }
                ) {
                    kotlinx.coroutines.delay(15L)
                }
            }

            val afterCancel = LyricsAlignTaskManager.state.value
            assertTrue(afterCancel.completed.none { it.taskId == taskC })
            assertTrue(afterCancel.failed.none { it.taskId == taskC })
            assertTrue(afterCancel.completed.any { it.taskId == taskD })
        } finally {
            LyricsAlignTaskManager.alignerOverride = null
            LyricsAlignTaskManager.clearAllForTesting()
            LyricsCatalogAiSessionCache.clear()
        }
    }

    @Test
    fun isSameTrackForLyricsDistinguishesTracksAndCueOffsets() {
        val onlineA = com.music.bitchord.data.model.Song("vidA", "A", "Art", null)
        val onlineASame = com.music.bitchord.data.model.Song("vidA", "A (Edit)", "Art", null)
        val onlineB = com.music.bitchord.data.model.Song("vidB", "B", "Art", null)

        assertTrue(com.music.bitchord.ui.isSameTrackForLyrics(onlineA, onlineASame))
        assertFalse(com.music.bitchord.ui.isSameTrackForLyrics(onlineA, onlineB))

        val localUriMatch1 = com.music.bitchord.data.model.Song("id1", "T", "A", null, localUri = "content://media/1")
        val localUriMatch2 = com.music.bitchord.data.model.Song("id2", "T", "A", null, localUri = "content://media/1")
        assertTrue(com.music.bitchord.ui.isSameTrackForLyrics(localUriMatch1, localUriMatch2))

        val localPathMatch1 = com.music.bitchord.data.model.Song("id3", "T", "A", null, localPath = "/music/song.flac")
        val localPathMatch2 = com.music.bitchord.data.model.Song("id4", "T", "A", null, localPath = "/music/song.flac")
        assertTrue(com.music.bitchord.ui.isSameTrackForLyrics(localPathMatch1, localPathMatch2))

        val cue1 = com.music.bitchord.data.model.Song("cue_id", "Track 1", "Art", null, localUri = "file:///album.flac", localStartMs = 0L)
        val cue2 = com.music.bitchord.data.model.Song("cue_id", "Track 2", "Art", null, localUri = "file:///album.flac", localStartMs = 180_000L)
        assertFalse(com.music.bitchord.ui.isSameTrackForLyrics(cue1, cue2))
    }
}
