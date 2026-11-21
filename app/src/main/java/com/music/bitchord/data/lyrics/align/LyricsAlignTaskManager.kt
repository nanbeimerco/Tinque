package com.music.bitchord.data.lyrics.align

import android.content.Context
import com.music.bitchord.R
import com.music.bitchord.data.lyrics.LyricsCandidate
import com.music.bitchord.data.lyrics.LyricsFormat
import com.music.bitchord.data.lyrics.LyricsOverrideStore
import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.data.model.Song
import com.music.bitchord.feature.lyricscatalog.LyricsCatalogAiSessionCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * Application-scoped sequential queue for background on-device AI lyrics alignment.
 *
 * Allows closing dialogs or switching screens while alignment continues uninterrupted, and lets
 * multiple songs be queued cleanly without exhausting RAM via concurrent MMS-FA sessions.
 */
object LyricsAlignTaskManager {

    enum class TaskStatus {
        QUEUED,
        RUNNING,
        COMPLETED,
        FAILED,
    }

    data class AlignTask(
        val taskId: String,
        val songKey: String,
        val song: Song,
        val candidateId: String,
        val baseCandidate: LyricsCandidate,
        val plainText: String,
        val status: TaskStatus,
        val progress: OnDeviceLyricsAligner.AlignProgress? = null,
        val resultCandidate: LyricsCandidate? = null,
        val errorMessage: String? = null,
        /** What the user has already done with a completed result, so the popup can stay open and mark it. */
        val doneActions: Set<ResultAction> = emptySet(),
    )

    /** Things the user can do with a completed alignment from the floating popup. */
    enum class ResultAction {
        APPLIED,
        SAVED_LRC,
        EMBEDDED,
    }

    data class QueueSnapshot(
        val active: AlignTask? = null,
        val queued: List<AlignTask> = emptyList(),
        val completed: List<AlignTask> = emptyList(),
        val failed: List<AlignTask> = emptyList(),
    )

    internal typealias AlignerFn = suspend (
        context: Context?,
        song: Song,
        plainText: String,
        abort: () -> Boolean,
        onProgress: (OnDeviceLyricsAligner.AlignProgress) -> Unit,
    ) -> OnDeviceLyricsAligner.AlignResult?

    internal var alignerOverride: AlignerFn? = null

    private const val MAX_COMPLETED = 20
    private const val MAX_FAILED = 10

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val seq = AtomicLong(0L)
    private val stateLock = Any()

    private var activeTask: AlignTask? = null
    private val queuedTasks = ArrayList<AlignTask>()
    private val completedTasks = ArrayList<AlignTask>()
    private val failedTasks = ArrayList<AlignTask>()
    private val cancelledIds = HashSet<String>()

    private var workerJob: Job? = null
    private var activeAttemptJob: Job? = null

    private val _state = MutableStateFlow(QueueSnapshot())
    val state: StateFlow<QueueSnapshot> = _state.asStateFlow()

    fun songKeyFor(song: Song): String = LyricsOverrideStore.keyFor(song.videoId, song.localUri)

    fun taskFor(songKey: String, candidateId: String): AlignTask? = synchronized(stateLock) {
        activeTask?.takeIf { it.songKey == songKey && it.candidateId == candidateId }
            ?: queuedTasks.firstOrNull { it.songKey == songKey && it.candidateId == candidateId }
            ?: completedTasks.firstOrNull { it.songKey == songKey && it.candidateId == candidateId }
            ?: failedTasks.firstOrNull { it.songKey == songKey && it.candidateId == candidateId }
    }

    fun enqueue(
        context: Context,
        song: Song,
        candidate: LyricsCandidate,
        plainText: String,
        forceReanalyze: Boolean = false,
    ): String = enqueueInternal(
        context = context.applicationContext ?: context,
        song = song,
        candidate = candidate,
        plainText = plainText,
        forceReanalyze = forceReanalyze,
    )

    internal fun enqueueInternal(
        context: Context?,
        song: Song,
        candidate: LyricsCandidate,
        plainText: String,
        forceReanalyze: Boolean = false,
    ): String {
        val appContext = context?.applicationContext ?: context
        val songKey = songKeyFor(song)
        synchronized(stateLock) {
            val running = activeTask
            if (running != null && running.songKey == songKey && running.candidateId == candidate.id) {
                if (!forceReanalyze) return running.taskId
                cancelledIds.add(running.taskId)
                activeAttemptJob?.cancel()
            }

            val queuedIdx = queuedTasks.indexOfFirst { it.songKey == songKey && it.candidateId == candidate.id }
            if (queuedIdx >= 0) {
                val existing = queuedTasks[queuedIdx]
                if (!forceReanalyze) return existing.taskId
                queuedTasks.removeAt(queuedIdx)
            }

            completedTasks.removeAll { it.songKey == songKey && it.candidateId == candidate.id }
            failedTasks.removeAll { it.songKey == songKey && it.candidateId == candidate.id }

            val id = "${songKey}::${candidate.id}::${seq.incrementAndGet()}"
            val newTask = AlignTask(
                taskId = id,
                songKey = songKey,
                song = song,
                candidateId = candidate.id,
                baseCandidate = candidate,
                plainText = plainText,
                status = TaskStatus.QUEUED,
            )
            queuedTasks += newTask
            emitSnapshotLocked()
            ensureWorkerLocked(appContext)
            return id
        }
    }

    fun cancel(taskId: String) {
        synchronized(stateLock) {
            cancelledIds.add(taskId)
            val removedQueued = queuedTasks.removeAll { it.taskId == taskId }
            if (activeTask?.taskId == taskId) {
                activeAttemptJob?.cancel()
            }
            if (removedQueued) {
                emitSnapshotLocked()
            }
        }
    }

    fun cancelForCandidate(songKey: String, candidateId: String) {
        val targetId = synchronized(stateLock) {
            activeTask?.takeIf { it.songKey == songKey && it.candidateId == candidateId }?.taskId
                ?: queuedTasks.firstOrNull { it.songKey == songKey && it.candidateId == candidateId }?.taskId
        }
        if (targetId != null) cancel(targetId)
    }

    fun dismissCompleted(taskId: String) {
        synchronized(stateLock) {
            if (completedTasks.removeAll { it.taskId == taskId }) {
                emitSnapshotLocked()
            }
        }
    }

    /** Records that [action] was done with a completed task's result; the task stays until dismissed. */
    fun markActionDone(taskId: String, action: ResultAction) {
        synchronized(stateLock) {
            val idx = completedTasks.indexOfFirst { it.taskId == taskId }
            if (idx < 0) return
            val task = completedTasks[idx]
            if (action in task.doneActions) return
            completedTasks[idx] = task.copy(doneActions = task.doneActions + action)
            emitSnapshotLocked()
        }
    }

    fun dismissFailed(taskId: String) {
        synchronized(stateLock) {
            if (failedTasks.removeAll { it.taskId == taskId }) {
                emitSnapshotLocked()
            }
        }
    }

    internal fun clearAllForTesting() {
        synchronized(stateLock) {
            activeTask?.let { cancelledIds.add(it.taskId) }
            activeAttemptJob?.cancel()
            workerJob?.cancel()
            workerJob = null
            activeAttemptJob = null
            activeTask = null
            queuedTasks.clear()
            completedTasks.clear()
            failedTasks.clear()
            emitSnapshotLocked()
        }
    }

    private fun ensureWorkerLocked(appContext: Context?) {
        if (workerJob?.isActive == true) return
        workerJob = scope.launch {
            runWorkerLoop(appContext)
        }
    }

    private suspend fun runWorkerLoop(appContext: Context?) {
        while (true) {
            val next = synchronized(stateLock) {
                if (queuedTasks.isEmpty()) {
                    activeTask = null
                    activeAttemptJob = null
                    workerJob = null
                    emitSnapshotLocked()
                    return
                }
                val popped = queuedTasks.removeAt(0).copy(
                    status = TaskStatus.RUNNING,
                    progress = OnDeviceLyricsAligner.AlignProgress(0.01f, OnDeviceLyricsAligner.AlignStage.DECODING_VOCALS),
                )
                activeTask = popped
                emitSnapshotLocked()
                popped
            }

            var alignedResult: OnDeviceLyricsAligner.AlignResult? = null
            var failureMsg: String? = null
            var wasCancelled = false

            val attempt = scope.async {
                val alignFn = alignerOverride ?: { ctx, s, txt, ab, onProg ->
                    OnDeviceLyricsAligner.align(requireNotNull(ctx), s, txt, ab, onProg)
                }
                alignFn(
                    appContext,
                    next.song,
                    next.plainText,
                    { synchronized(stateLock) { next.taskId in cancelledIds } },
                    { progress ->
                        synchronized(stateLock) {
                            val cur = activeTask
                            if (cur != null && cur.taskId == next.taskId && next.taskId !in cancelledIds) {
                                activeTask = cur.copy(progress = progress)
                                emitSnapshotLocked()
                            }
                        }
                    },
                )
            }
            synchronized(stateLock) {
                activeAttemptJob = attempt
                if (next.taskId in cancelledIds) {
                    attempt.cancel()
                }
            }

            try {
                alignedResult = attempt.await()
            } catch (_: CancellationException) {
                wasCancelled = true
            } catch (e: Exception) {
                failureMsg = e.localizedMessage ?: "Alignment failed"
            }

            synchronized(stateLock) {
                if (cancelledIds.remove(next.taskId)) {
                    wasCancelled = true
                }
                activeAttemptJob = null
                activeTask = null

                if (!wasCancelled) {
                    if (alignedResult != null) {
                        val isWord = alignedResult.mode == OnDeviceLyricsAligner.AlignMode.CTC_WORD_AI
                        val sourceLabel = resolveSourceLabel(appContext, isWord)
                        val updatedCandidate = next.baseCandidate.copy(
                            source = LyricsSource(
                                id = "on_device_ai",
                                label = sourceLabel,
                                wordSynced = isWord,
                            ),
                            raw = alignedResult.enhancedLrc,
                            lines = alignedResult.lines,
                            format = if (isWord) LyricsFormat.WORD else LyricsFormat.LINE,
                            hasTranslation = false,
                            hasFurigana = false,
                        )
                        LyricsCatalogAiSessionCache.put(next.songKey, next.candidateId, updatedCandidate)
                        completedTasks.removeAll { it.songKey == next.songKey && it.candidateId == next.candidateId }
                        completedTasks.add(
                            0,
                            next.copy(
                                status = TaskStatus.COMPLETED,
                                progress = OnDeviceLyricsAligner.AlignProgress(1f, OnDeviceLyricsAligner.AlignStage.ALIGNING),
                                resultCandidate = updatedCandidate,
                            ),
                        )
                        while (completedTasks.size > MAX_COMPLETED) {
                            completedTasks.removeAt(completedTasks.lastIndex)
                        }
                    } else {
                        failedTasks.removeAll { it.songKey == next.songKey && it.candidateId == next.candidateId }
                        failedTasks.add(
                            0,
                            next.copy(
                                status = TaskStatus.FAILED,
                                errorMessage = failureMsg,
                            ),
                        )
                        while (failedTasks.size > MAX_FAILED) {
                            failedTasks.removeAt(failedTasks.lastIndex)
                        }
                    }
                }
                emitSnapshotLocked()
            }
        }
    }

    private fun resolveSourceLabel(context: Context?, isWord: Boolean): String {
        val raw = context?.let { ctx ->
            runCatching {
                ctx.getString(if (isWord) R.string.lyrics_ai_source_ctc else R.string.lyrics_ai_source_vad)
            }.getOrNull()
        }
        return raw?.takeIf { it.isNotBlank() } ?: if (isWord) {
            "On-device AI (MMS-FA word sync)"
        } else {
            "On-device AI (Vocal VAD line sync)"
        }
    }

    private fun emitSnapshotLocked() {
        _state.value = QueueSnapshot(
            active = activeTask,
            queued = queuedTasks.toList(),
            completed = completedTasks.toList(),
            failed = failedTasks.toList(),
        )
    }
}
