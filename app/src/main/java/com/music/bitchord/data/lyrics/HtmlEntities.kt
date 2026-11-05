package com.music.bitchord.data.lyrics

/**
 * Character references left in lyrics by sources that store them HTML-escaped
 * (`Darlin&apos;` for `Darlin'`), turned back into the characters they stand
 * for. Applied to the parsed lines, never to raw TTML, whose own XML parser
 * has already done this and must see the escapes it needs.
 */
internal object HtmlEntities {
    private val named = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
        "nbsp" to " ", "hellip" to "…", "ndash" to "–", "mdash" to "—",
        "lsquo" to "‘", "rsquo" to "’", "ldquo" to "“", "rdquo" to "”",
    )
    private val pattern = Regex("&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[a-zA-Z]{2,8});")

    fun decode(text: String): String {
        if ('&' !in text) return text
        return pattern.replace(text) { match ->
            val body = match.groupValues[1]
            when {
                body.startsWith("#x") || body.startsWith("#X") ->
                    body.substring(2).toIntOrNull(16)?.let(::codePoint) ?: match.value
                body.startsWith("#") -> body.substring(1).toIntOrNull()?.let(::codePoint) ?: match.value
                else -> named[body] ?: named[body.lowercase()] ?: match.value
            }
        }
    }

    private fun codePoint(value: Int): String? =
        if (Character.isValidCodePoint(value) && value != 0) String(Character.toChars(value)) else null
}

/** [line] with any character references in its text, words and syllables decoded. */
internal fun LyricLine.withDecodedEntities(): LyricLine {
    val decodedText = HtmlEntities.decode(text)
    val decodedWords = if (words.any { '&' in it.text }) {
        // A word timed by syllable keeps its text: the syllables index into it
        // by character, and decoding would shift them.
        words.map { word -> if (word.syllables.isEmpty()) word.copy(text = HtmlEntities.decode(word.text)) else word }
    } else {
        words
    }
    return copy(
        text = decodedText,
        words = decodedWords,
        background = background?.withDecodedEntities(),
        timingSource = timingSource?.withDecodedEntities(),
    )
}
