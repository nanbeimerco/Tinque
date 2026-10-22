package com.music.bitchord.data.lyrics

/**
 * The voice marks some LRC files carry at the start of a line:
 *
 * ```
 * [03:05.32]v1:<03:05.32>I'll <03:05.63>show <03:06.10>you
 * [03:07.10]v2:<03:07.10>Show <03:07.40>me
 * [03:07.60]bg:<03:07.60>(show <03:07.90>me)
 * ```
 *
 * They are what an Apple TTML duet's `ttm:agent` becomes when a tool writes it
 * out as LRC: `v1`, `v2`… for the singers, `bg` for the backing voice. Not part
 * of any LRC spec, so a parser that does not know them shows them as words.
 * Here they are taken off the text and put to the same use as in TTML: the
 * singers decide the line's side, the backing voice goes under the line it
 * answers.
 */
internal object VoiceTags {
    private val TAG = Regex("""^\s*(v\d{1,4}|bg)\s*:\s*""", RegexOption.IGNORE_CASE)
    private const val BACKGROUND = "bg"

    /** The line's voice (lower case) and the rest of the line, or null and the line as is. */
    fun split(body: String): Pair<String?, String> {
        val match = TAG.find(body) ?: return null to body
        return match.groupValues[1].lowercase() to body.substring(match.range.last + 1)
    }

    /**
     * [lines] with their [voices] (same order, same size) applied: backing
     * lines folded under the line before them, then each line given its side.
     * Unchanged when no line had a voice.
     */
    fun apply(lines: List<LyricLine>, voices: List<String?>): List<LyricLine> {
        if (voices.all { it == null }) return lines
        val kept = ArrayList<LyricLine>(lines.size)
        val keptVoices = ArrayList<String?>(lines.size)
        lines.forEachIndexed { index, line ->
            val voice = voices.getOrNull(index)
            val lead = kept.lastOrNull()
            if (voice == BACKGROUND && lead != null && lead.background == null && !lead.isGap && !line.isGap) {
                kept[kept.lastIndex] = lead.copy(background = line)
            } else {
                kept += line
                keptVoices += voice.takeUnless { it == BACKGROUND }
            }
        }
        val sides = lineAlignments(keptVoices, emptyMap())
        return kept.mapIndexed { index, line -> line.copy(alignment = sides[index]) }
    }

    /**
     * The mark written in front of a line sung from [alignment]. Only a song
     * with lines on both sides ([duet]) is marked, and then every line is, so
     * reading it back alternates the two voices as they were.
     */
    fun markFor(alignment: LyricAlignment, duet: Boolean): String = when {
        !duet -> ""
        alignment == LyricAlignment.End -> "v2:"
        else -> "v1:"
    }
}
