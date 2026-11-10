package com.music.bitchord.data.lyrics

/**
 * Enhanced ("A2") LRC — a normal LRC line with a stamp in front of each word:
 *
 * ```
 * [00:27.39]<00:27.39>I <00:27.54>been <00:27.74>tryna <00:28.07>call
 * ```
 *
 * This is what Musixmatch's rich sync becomes, and it is what SimpMusic
 * serves. Only starts are written down, so each word runs until the next one
 * begins, and the last word of a line until the next line does.
 */
object EnhancedLrc {

    private val LINE = Regex("""^\[(\d{1,3}):(\d{2})[.:](\d{2,3})](.*)$""")
    private val WORD = Regex("""<(\d{1,3}):(\d{2})[.:](\d{2,3})>([^<]*)""")

    /** Empty when [lrc] carries no word stamps — the caller can then fall back. */
    fun parse(lrc: String): List<LyricLine> {
        val rows = lrc.lineSequence()
            .mapNotNull { line -> LINE.matchEntire(line.trim()) }
            .map { match ->
                // A leading `v1:` / `bg:` says who sings the line, see [VoiceTags].
                val (voice, body) = VoiceTags.split(match.groupValues[4])
                Row(
                    timeMs = stamp(match.groupValues[1], match.groupValues[2], match.groupValues[3]),
                    words = WORD.findAll(body).toList(),
                    plain = body.trim(),
                    voice = voice,
                )
            }
            .sortedBy { it.timeMs }
            .toList()
        // Nothing word-stamped in here: this is an ordinary LRC file and the
        // caller is better served parsing it as one.
        if (rows.none { it.words.isNotEmpty() }) return emptyList()

        val voiced = rows.mapIndexedNotNull { index, row ->
            if (row.words.isEmpty()) {
                val text = decodeEntities(row.plain)
                return@mapIndexedNotNull if (text.isEmpty()) null else row.voice to LyricLine(row.timeMs, text)
            }
            // A word runs until the next one starts; the last runs until the
            // next line does. Without a next line — the closing word of the
            // song — give it a beat rather than zero, or its sweep never runs.
            // A backing line (`bg:`) sings over this one, so it is not where
            // this one stops: the next lead line is.
            val lineEnd = rows.drop(index + 1).firstOrNull { it.voice != "bg" }?.timeMs
                ?: (stamp(row.words.last()) + TAIL_MS)

            // Kept as written, spacing and all: a run with no space after it
            // is a syllable of a word that carries on into the next one.
            val runs = row.words.mapIndexed { i, match ->
                val wordStart = stamp(match)
                val wordEnd = row.words.getOrNull(i + 1)?.let { stamp(it) } ?: lineEnd
                TimedRun(wordStart, wordEnd, decodeEntities(match.groupValues[4]))
            }
            val words = wordsFromRuns(runs)
            if (words.isEmpty()) return@mapIndexedNotNull null
            row.voice to LyricLine(
                timeMs = minOf(row.timeMs, words.first().startMs),
                text = lineText(runs, words),
                words = words,
            )
        }
        return VoiceTags.apply(voiced.map { it.second }, voiced.map { it.first }).withInstrumentalGaps()
    }

    /**
     * The line as written. Joining the words with a space each put one between
     * every character of a Japanese line, which is timed a character at a time.
     * Where the writer spaced the line, that spacing is kept; where nothing is
     * spaced at all, a space goes only between words of spaced scripts.
     */
    internal fun lineText(runs: List<TimedRun>, words: List<LyricWord>): String {
        if (runs.any { run -> run.raw.any(Char::isWhitespace) }) {
            val written = runs.joinToString("") { it.raw }.replace(SPACES, " ").trim()
            return if (spacesAreSeparators(runs)) written.withoutSpacesBetweenUnspaced() else written
        }
        val out = StringBuilder()
        for (word in words) {
            val previous = out.lastOrNull()
            if (previous != null && !previous.isUnspacedScript() && !word.text.first().isUnspacedScript()) {
                out.append(' ')
            }
            out.append(word.text)
        }
        return out.toString()
    }

    /**
     * Whether the spaces in a Japanese (or Chinese) line only separate its
     * stamps rather than its phrases: `<t>君 <t>の <t>名 <t>前`. Tinque itself
     * wrote word-synced lyrics that way up to v2.4.32, and some tools do the
     * same. A space after nearly every character is that; a space here and
     * there is the writer's own phrasing and stays.
     */
    private fun spacesAreSeparators(runs: List<TimedRun>): Boolean {
        val filled = runs.filter { it.raw.isNotBlank() }
        var boundaries = 0
        var spaced = 0
        for (i in 0 until filled.lastIndex) {
            val left = filled[i].raw
            val right = filled[i + 1].raw
            if (!left.trimEnd().last().isUnspacedScript() || !right.trimStart().first().isUnspacedScript()) continue
            boundaries++
            if (left.last().isWhitespace() || right.first().isWhitespace()) spaced++
        }
        return boundaries >= SEPARATOR_MIN_BOUNDARIES && spaced >= boundaries * SEPARATOR_SHARE
    }

    private fun String.withoutSpacesBetweenUnspaced(): String {
        val out = StringBuilder(length)
        for (i in indices) {
            val c = this[i]
            if (c == ' ' && out.lastOrNull()?.isUnspacedScript() == true &&
                getOrNull(i + 1)?.isUnspacedScript() == true
            ) continue
            out.append(c)
        }
        return out.toString()
    }

    private const val SEPARATOR_MIN_BOUNDARIES = 3
    private const val SEPARATOR_SHARE = 0.8

    private val SPACES = Regex("""\s+""")

    private class Row(val timeMs: Long, val words: List<MatchResult>, val plain: String, val voice: String?)

    private fun stamp(match: MatchResult): Long =
        stamp(match.groupValues[1], match.groupValues[2], match.groupValues[3])

    private fun stamp(minutes: String, seconds: String, fraction: String): Long {
        // Two digits mean centiseconds, three mean milliseconds.
        val fractionMs = if (fraction.length == 3) fraction.toLong() else fraction.toLong() * 10
        return minutes.toLong() * 60_000 + seconds.toLong() * 1_000 + fractionMs
    }

    /**
     * SimpMusic serves its rich sync HTML-escaped, so an apostrophe arrives as
     * `&#x27;` and would be sung literally. Metrolist shipped that bug; this
     * is the fix.
     */
    internal fun decodeEntities(text: String): String {
        if ('&' !in text) return text
        return text
            .replace(Regex("&#x([0-9a-fA-F]+);")) { it.groupValues[1].toInt(16).toChar().toString() }
            .replace(Regex("&#(\\d+);")) { it.groupValues[1].toInt().toChar().toString() }
            .replace("&apos;", "'")
            .replace("&quot;", "\"")
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            // Last, so "&amp;#x27;" doesn't decode twice into an apostrophe.
            .replace("&amp;", "&")
    }

    private const val TAIL_MS = 800L
}
