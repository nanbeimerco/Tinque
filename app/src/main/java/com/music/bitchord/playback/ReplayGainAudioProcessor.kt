package com.music.bitchord.playback

import androidx.media3.common.C
import androidx.media3.common.Timeline
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Applies the song's gain (levelling plus the listener's trim, see [TrackGain])
 * to decoded PCM only; encoded audio and stream resolution are untouched.
 *
 * The gain belongs to a song, so it changes where the *audio* changes song,
 * not where the player later says it has: the sink tells this processor which
 * media item the next stream is, at the stream boundary, and the gain worked
 * out ahead of time for that item ([gainLookup]) takes over from its first
 * sample. Without that, the start of every song went out at the previous
 * song's gain until the transition was reported.
 *
 * A change that does not come at a boundary — a setting moved, or a gain that
 * was not ready in time — is eased in over a few milliseconds rather than
 * stepped, so it cannot click.
 */
@UnstableApi
class ReplayGainAudioProcessor : BaseAudioProcessor() {
    /** The gain to apply, in dB. Set from outside; eased in from the gain in use. */
    @Volatile
    var gainDb: Float = 0f
        set(value) { field = value.coerceIn(-60f, 12f) }

    @Volatile
    var preventClipping: Boolean = true

    /** The media item whose audio is going through now, when the sink has said. */
    @Volatile
    var currentMediaId: String? = null
        private set

    /**
     * Set when the audio reached a song whose gain was not worked out yet: its
     * audio is held back (not taken in) until the gain arrives, so the song
     * starts a moment late rather than at the wrong level. Past
     * [WAIT_LIMIT_MS] it starts at [TrackGain.fallbackDb], which errs quiet.
     */
    private var waitingSince: Long? = null

    /** The linear gain actually applied to the last sample, for easing. */
    private var appliedGain = 1f

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val handled = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT || inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        android.util.Log.d("TinqueGain", "configure: $inputAudioFormat -> ${if (handled) "gain applied" else "passed through untouched"}")
        return if (handled) inputAudioFormat else AudioProcessor.AudioFormat.NOT_SET
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        val id = mediaIdOf(streamMetadata)
        android.util.Log.d("TinqueGain", "boundary: $id (known gain ${id?.let { TrackGain.gains[it] }})")
        if (id == null) return
        currentMediaId = id
        val known = TrackGain.gains[id]
        if (known != null) {
            // A new song starts at its own gain from the first sample, no easing.
            startAt(known)
        } else {
            waitingSince = System.currentTimeMillis()
            TrackGain.onGainNeeded?.invoke(id)
        }
    }

    override fun onReset() {
        waitingSince = null
        currentMediaId = null
    }

    private fun startAt(db: Float) {
        waitingSince = null
        gainDb = db
        appliedGain = 10f.pow(gainDb / 20f)
    }

    private fun mediaIdOf(metadata: AudioProcessor.StreamMetadata): String? = runCatching {
        val timeline = metadata.timeline
        val uid = metadata.periodUid ?: return@runCatching null
        if (timeline.isEmpty) return@runCatching null
        val index = timeline.getIndexOfPeriod(uid)
        if (index == C.INDEX_UNSET) return@runCatching null
        val period = timeline.getPeriod(index, Timeline.Period())
        timeline.getWindow(period.windowIndex, Timeline.Window()).mediaItem.mediaId
    }.getOrNull()

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytes = inputBuffer.remaining()
        if (bytes == 0) return
        waitingSince?.let { since ->
            val known = currentMediaId?.let { TrackGain.gains[it] }
            when {
                known != null -> startAt(known)
                System.currentTimeMillis() - since < WAIT_LIMIT_MS -> return // not taken: the sink offers it again
                else -> startAt(TrackGain.fallbackDb())
            }
        }
        val output = replaceOutputBuffer(bytes)
        val target = 10f.pow(gainDb / 20f)
        if (target == 1f && appliedGain == 1f) {
            output.put(inputBuffer)
            output.flip()
            return
        }

        inputBuffer.order(ByteOrder.nativeOrder())
        output.order(ByteOrder.nativeOrder())
        val channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        val sampleBytes = if (inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) Float.SIZE_BYTES else Short.SIZE_BYTES
        val frames = (bytes / (sampleBytes * channels)).coerceAtLeast(1)
        // Eased over this buffer (or EASE_FRAMES, if shorter) when the gain moved.
        val easeFrames = if (appliedGain != target) minOf(frames, EASE_FRAMES) else 0
        val start = appliedGain
        var frame = 0
        var channel = 0
        fun gainNow(): Float =
            if (frame >= easeFrames) target else start + (target - start) * (frame.toFloat() / easeFrames)

        when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT -> {
                while (inputBuffer.remaining() >= Short.SIZE_BYTES) {
                    val scaled = inputBuffer.short * gainNow()
                    output.putShort(scaled.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
                    if (++channel == channels) { channel = 0; frame++ }
                }
            }
            C.ENCODING_PCM_FLOAT -> {
                while (inputBuffer.remaining() >= Float.SIZE_BYTES) {
                    val sample = inputBuffer.float
                    val scaled = if (sample.isFinite()) sample * gainNow() else 0f
                    output.putFloat(if (preventClipping) scaled.coerceIn(-1f, 1f) else scaled)
                    if (++channel == channels) { channel = 0; frame++ }
                }
            }
        }
        appliedGain = target
        if (inputBuffer.hasRemaining()) output.put(inputBuffer)
        output.flip()
    }

    private companion object {
        /** About 5 ms at 48 kHz: long enough not to click, short enough not to be heard as a fade. */
        const val EASE_FRAMES = 256

        /** The longest a song's start is held for its gain. */
        const val WAIT_LIMIT_MS = 1_000L
    }
}
