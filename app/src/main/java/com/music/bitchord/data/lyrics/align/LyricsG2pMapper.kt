package com.music.bitchord.data.lyrics.align

import com.atilika.kuromoji.ipadic.Tokenizer
import com.music.bitchord.data.lyrics.LyricsFurigana
import java.text.Normalizer
import java.util.Locale

/**
 * Converts plain or timestamped multilingual lyrics (Japanese Kanji/Kana + inline furigana,
 * English/Latin with diacritics/contractions, Korean Hangul) into character-level acoustic tokens
 * expected by the Meta MMS-FA CTC forced-alignment ONNX model, while tracking lossless display
 * units so word-level sweep and Enhanced LRC serialization align cleanly with [displayText].
 */
object LyricsG2pMapper {

    data class DisplayUnit(
        val text: String,
        val tokenStartIndex: Int,
        val tokenCount: Int,
    )

    data class MappedLine(
        val displayText: String,
        val units: List<DisplayUnit>,
    )

    data class MappedLyrics(
        val lines: List<MappedLine>,
        val tokenIds: IntArray,
        val tokenLineIndex: IntArray,
        val tokenUnitIndex: IntArray,
    )

    private data class RawUnit(
        var text: String,
        val phonemes: String,
        val spaceBefore: Boolean,
    )

    // 29-token vocabulary of `xycld/lyric-align-mms-fa` (`mms_fa_uint8.onnx`):
    // 0: '-' (blank), 1..28: a..*
    private val MMS_FA_29 = mapOf(
        '-' to 0, 'a' to 1, 'i' to 2, 'e' to 3, 'n' to 4, 'o' to 5,
        'u' to 6, 't' to 7, 's' to 8, 'r' to 9, 'm' to 10, 'k' to 11,
        'l' to 12, 'd' to 13, 'g' to 14, 'h' to 15, 'y' to 16, 'b' to 17,
        'p' to 18, 'w' to 19, 'c' to 20, 'v' to 21, 'j' to 22, 'z' to 23,
        'f' to 24, '\'' to 25, 'q' to 26, 'x' to 27, '*' to 28,
    )

    // 31-token vocabulary of `onnx-community/mms-fa` / torchaudio MMS_FA:
    // 0: <blank>, 1..3 special/pad/unk, 4..30: a..x
    private val MMS_FA_31 = mapOf(
        '-' to 0, 'a' to 4, 'i' to 5, 'e' to 6, 'n' to 7, 'o' to 8,
        'u' to 9, 't' to 10, 's' to 11, 'r' to 12, 'm' to 13, 'k' to 14,
        'l' to 15, 'd' to 16, 'g' to 17, 'h' to 18, 'y' to 19, 'b' to 20,
        'p' to 21, 'w' to 22, 'c' to 23, 'v' to 24, 'j' to 25, 'z' to 26,
        'f' to 27, '\'' to 28, 'q' to 29, 'x' to 30,
    )

    private val STAMP_REGEX = Regex("""\[\d{1,3}:\d{2}(?:[.:]\d{1,3})?]|<\d{1,3}:\d{2}(?:[.:]\d{1,3})?>""")
    private val VOICE_TAG_REGEX = Regex("""^\s*(?:v\d+|bg)\s*:\s*""", RegexOption.IGNORE_CASE)
    private val LRC_METADATA_REGEX = Regex("""^\[[a-zA-Z]{1,10}\s*:[^]]*]$""")
    private val SECTION_HEADER_REGEX = Regex(
        """^\[(?:verse|chorus|pre-?chorus|post-?chorus|bridge|intro|outro|hook|interlude|instrumental|solo|refrain|coda|outro|part|break|rap|ad-?lib)[^]]*]$""",
        RegexOption.IGNORE_CASE,
    )
    private val COMBINING_MARKS_REGEX = Regex("""\p{Mn}+""")

    private val kuromojiTokenizer: Tokenizer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        Tokenizer()
    }

    /** Cleans one raw lyric line (removes stamps/voice tags; returns null for blank/metadata/section headers). */
    internal fun sanitizeLine(rawLine: String): String? {
        val noStamps = rawLine.replace(STAMP_REGEX, "").replace(VOICE_TAG_REGEX, "").trim()
        if (noStamps.isEmpty()) return null
        if (LRC_METADATA_REGEX.matches(noStamps) || SECTION_HEADER_REGEX.matches(noStamps)) return null
        // Normalize consecutive whitespace to single spaces.
        return noStamps.replace(Regex("""\s+"""), " ").trim()
    }

    fun map(plainText: String, vocabSize: Int = 29): MappedLyrics {
        val vocab = if (vocabSize == 31) MMS_FA_31 else MMS_FA_29
        val starId = if (vocabSize == 31) 3 else 28

        val mappedLines = mutableListOf<MappedLine>()
        val tokenIds = ArrayList<Int>(2048)
        val tokenLineIndex = ArrayList<Int>(2048)
        val tokenUnitIndex = ArrayList<Int>(2048)

        plainText.lineSequence().forEach { rawLine ->
            val clean = sanitizeLine(rawLine) ?: return@forEach
            val rawUnits = tokenizeLine(clean)
            if (rawUnits.isEmpty()) return@forEach

            val displayText = buildString {
                rawUnits.forEachIndexed { idx, unit ->
                    if (idx > 0 && unit.spaceBefore) append(' ')
                    append(unit.text)
                }
            }.trim()
            if (displayText.isEmpty()) return@forEach

            val lineIdx = mappedLines.size
            val units = ArrayList<DisplayUnit>(rawUnits.size)
            rawUnits.forEachIndexed { unitIdx, raw ->
                val startToken = tokenIds.size
                for (ch in raw.phonemes) {
                    val id = vocab[ch] ?: continue
                    if (id == 0) continue
                    tokenIds.add(id)
                    tokenLineIndex.add(lineIdx)
                    tokenUnitIndex.add(unitIdx)
                }
                val count = tokenIds.size - startToken
                if (count == 0 && raw.phonemes.any { it == '*' } && starId != 0) {
                    tokenIds.add(starId)
                    tokenLineIndex.add(lineIdx)
                    tokenUnitIndex.add(unitIdx)
                }
                units += DisplayUnit(
                    text = raw.text,
                    tokenStartIndex = startToken,
                    tokenCount = tokenIds.size - startToken,
                )
            }

            if (units.any { it.tokenCount > 0 }) {
                mappedLines += MappedLine(displayText = displayText, units = units)
            } else {
                // Roll back empty lines with no alignable tokens
                // (Nothing was added to tokenIds because count==0 for all units.)
            }
        }

        return MappedLyrics(
            lines = mappedLines,
            tokenIds = tokenIds.toIntArray(),
            tokenLineIndex = tokenLineIndex.toIntArray(),
            tokenUnitIndex = tokenUnitIndex.toIntArray(),
        )
    }

    private fun tokenizeLine(line: String): List<RawUnit> {
        val annotations = LyricsFurigana.annotations(line)
        val units = ArrayList<RawUnit>()
        var pendingSpace = false
        var pendingPrefix = StringBuilder()

        fun addUnit(text: String, phonemes: String) {
            val combinedText = if (pendingPrefix.isNotEmpty()) {
                val s = pendingPrefix.toString() + text
                pendingPrefix.clear()
                s
            } else {
                text
            }
            units += RawUnit(
                text = combinedText,
                phonemes = phonemes,
                spaceBefore = pendingSpace && units.isNotEmpty(),
            )
            pendingSpace = false
        }

        fun appendTrailingPunctuation(punct: String) {
            if (units.isNotEmpty() && !pendingSpace) {
                units[units.lastIndex].text += punct
            } else {
                pendingPrefix.append(punct)
            }
        }

        fun processUnannotatedSlice(slice: String) {
            var i = 0
            while (i < slice.length) {
                val cp = slice.codePointAt(i)
                val cc = Character.charCount(cp)
                when {
                    Character.isWhitespace(cp) -> {
                        if (pendingPrefix.isNotEmpty()) {
                            pendingPrefix.append(' ')
                        } else {
                            pendingSpace = true
                        }
                        i += cc
                    }
                    isJapaneseScript(cp) -> {
                        var end = i + cc
                        while (end < slice.length) {
                            val ncp = slice.codePointAt(end)
                            if (!isJapaneseScript(ncp)) break
                            end += Character.charCount(ncp)
                        }
                        val run = slice.substring(i, end)
                        tokenizeJapaneseRun(run, ::addUnit)
                        i = end
                    }
                    isHangul(cp) -> {
                        var end = i + cc
                        while (end < slice.length) {
                            val ncp = slice.codePointAt(end)
                            if (!isHangul(ncp)) break
                            end += Character.charCount(ncp)
                        }
                        val word = slice.substring(i, end)
                        val romaji = hangulToRomaji(word)
                        addUnit(word, romaji)
                        i = end
                    }
                    isLatinWordChar(cp) -> {
                        var end = i + cc
                        while (end < slice.length) {
                            val ncp = slice.codePointAt(end)
                            if (isLatinWordChar(ncp)) {
                                end += Character.charCount(ncp)
                            } else if ((ncp == '\''.code || ncp == 0x2019) && end + Character.charCount(ncp) < slice.length) {
                                val after = slice.codePointAt(end + Character.charCount(ncp))
                                if (isLatinWordChar(after)) {
                                    end += Character.charCount(ncp)
                                } else {
                                    break
                                }
                            } else {
                                break
                            }
                        }
                        val word = slice.substring(i, end)
                        val phonemes = normalizeLatinWord(word)
                        addUnit(word, phonemes)
                        i = end
                    }
                    Character.isDigit(cp) -> {
                        var end = i + cc
                        while (end < slice.length && Character.isDigit(slice.codePointAt(end))) {
                            end += Character.charCount(slice.codePointAt(end))
                        }
                        val digits = slice.substring(i, end)
                        addUnit(digits, digitsToEnglishApprox(digits))
                        i = end
                    }
                    isOpeningPunct(cp) -> {
                        pendingPrefix.appendCodePoint(cp)
                        i += cc
                    }
                    else -> {
                        appendTrailingPunctuation(String(Character.toChars(cp)))
                        i += cc
                    }
                }
            }
        }

        var cursor = 0
        for (ann in annotations) {
            if (ann.sourceStart > cursor) {
                processUnannotatedSlice(line.substring(cursor, ann.sourceStart))
            }
            val romaji = kanaToRomaji(ann.reading)
            addUnit(ann.base, romaji)
            cursor = ann.sourceEndExclusive
        }
        if (cursor < line.length) {
            processUnannotatedSlice(line.substring(cursor))
        }
        if (pendingPrefix.isNotEmpty() && units.isNotEmpty()) {
            units[units.lastIndex].text += pendingPrefix.toString()
            pendingPrefix.clear()
        }
        return units
    }

    private fun tokenizeJapaneseRun(
        run: String,
        emit: (text: String, phonemes: String) -> Unit,
    ) {
        val tokens = kuromojiTokenizer.tokenize(run)
        for (token in tokens) {
            val surface = token.surface
            if (surface.isEmpty()) continue
            val isParticle = token.partOfSpeechLevel1 == "助詞"
            val reading = when {
                isParticle && surface == "は" -> "ワ"
                isParticle && surface == "へ" -> "エ"
                isParticle && surface == "を" -> "オ"
                else -> token.reading?.takeIf { it.isNotBlank() && it != "*" }
                    ?: token.pronunciation?.takeIf { it.isNotBlank() && it != "*" }
                    ?: surface
            }
            val romaji = kanaToRomaji(reading)
            emit(surface, romaji)
        }
    }

    internal fun normalizeLatinWord(word: String): String {
        val nfkd = Normalizer.normalize(word.replace('\u2019', '\''), Normalizer.Form.NFKD)
        val noMarks = COMBINING_MARKS_REGEX.replace(nfkd, "").lowercase(Locale.ROOT)
        return buildString(noMarks.length) {
            for (c in noMarks) {
                if ((c in 'a'..'z') || c == '\'') append(c)
            }
        }
    }

    /** Pure-Kotlin Hiragana/Katakana -> lowercase Romaji converter suitable for MMS-FA Latin phonemes. */
    internal fun kanaToRomaji(kanaInput: String): String {
        if (kanaInput.isEmpty()) return ""
        // Normalize full-width/half-width and convert Hiragana (3041..3096) -> Katakana (30A1..30F6)
        val nfkc = Normalizer.normalize(kanaInput, Normalizer.Form.NFKC)
        val katakana = buildString(nfkc.length) {
            for (ch in nfkc) {
                when (ch) {
                    in '\u3041'..'\u3096' -> append((ch.code + 0x60).toChar())
                    '\u309D', '\u309E' -> append((ch.code + 0x60).toChar())
                    else -> append(ch)
                }
            }
        }

        val out = StringBuilder(katakana.length * 2)
        var i = 0
        var geminateNext = false

        while (i < katakana.length) {
            val ch = katakana[i]
            if (ch == 'ッ') {
                geminateNext = true
                i++
                continue
            }
            if (ch == 'ー') {
                // Prolong previous vowel if present
                val lastVowel = out.lastOrNull { it == 'a' || it == 'i' || it == 'u' || it == 'e' || it == 'o' }
                if (lastVowel != null) out.append(lastVowel)
                geminateNext = false
                i++
                continue
            }
            if (ch == 'ン') {
                if (geminateNext) {
                    out.append('n')
                    geminateNext = false
                }
                out.append('n')
                i++
                continue
            }

            // Check 2-char digraph first
            val romaji: String? = if (i + 1 < katakana.length) {
                KANA_DIGRAPHS[katakana.substring(i, i + 2)]
            } else {
                null
            }
            val tokenStr: String?
            if (romaji != null) {
                tokenStr = romaji
                i += 2
            } else {
                tokenStr = KANA_MONOGRAPHS[ch]
                i += 1
            }

            if (tokenStr != null) {
                if (geminateNext && tokenStr.isNotEmpty()) {
                    val first = tokenStr[0]
                    if (first != 'a' && first != 'i' && first != 'u' && first != 'e' && first != 'o') {
                        out.append(if (first == 'c') 't' else first)
                    }
                    geminateNext = false
                }
                out.append(tokenStr)
            } else if (ch in 'a'..'z' || ch in 'A'..'Z') {
                geminateNext = false
                out.append(ch.lowercaseChar())
            } else {
                geminateNext = false
            }
        }
        if (geminateNext) out.append('t')
        return out.toString()
    }

    internal fun hangulToRomaji(input: String): String {
        val sb = StringBuilder(input.length * 3)
        for (ch in input) {
            val code = ch.code
            if (code in 0xAC00..0xD7A3) {
                val syl = code - 0xAC00
                val cho = syl / (21 * 28)
                val jung = (syl % (21 * 28)) / 28
                val jong = syl % 28
                sb.append(HANGUL_CHO[cho])
                sb.append(HANGUL_JUNG[jung])
                sb.append(HANGUL_JONG[jong])
            } else if (ch in 'a'..'z' || ch in 'A'..'Z') {
                sb.append(ch.lowercaseChar())
            }
        }
        return sb.toString()
    }

    private fun digitsToEnglishApprox(digits: String): String {
        if (digits.length > 4) return digits.mapNotNull { DIGIT_ROM[it] }.joinToString("")
        return digits.mapNotNull { DIGIT_ROM[it] }.joinToString("")
    }

    private fun isJapaneseScript(cp: Int): Boolean =
        Character.UnicodeScript.of(cp) in JAPANESE_SCRIPTS || cp == 0x30FC || cp == 0x3005

    private fun isHangul(cp: Int): Boolean =
        Character.UnicodeScript.of(cp) == Character.UnicodeScript.HANGUL

    private fun isLatinWordChar(cp: Int): Boolean =
        Character.isLetter(cp) && !isJapaneseScript(cp) && !isHangul(cp)

    private fun isOpeningPunct(cp: Int): Boolean =
        cp == '('.code || cp == '['.code || cp == '{'.code || cp == '"'.code ||
            cp == '「'.code || cp == '『'.code || cp == '（'.code || cp == '【'.code ||
            cp == '“'.code || cp == '‘'.code || cp == '¿'.code || cp == '¡'.code

    private val JAPANESE_SCRIPTS = setOf(
        Character.UnicodeScript.HIRAGANA,
        Character.UnicodeScript.KATAKANA,
        Character.UnicodeScript.HAN,
    )

    private val DIGIT_ROM = mapOf(
        '0' to "zero", '1' to "one", '2' to "two", '3' to "three", '4' to "four",
        '5' to "five", '6' to "six", '7' to "seven", '8' to "eight", '9' to "nine",
    )

    private val HANGUL_CHO = arrayOf(
        "g", "kk", "n", "d", "tt", "r", "m", "b", "pp",
        "s", "ss", "", "j", "jj", "ch", "k", "t", "p", "h",
    )
    private val HANGUL_JUNG = arrayOf(
        "a", "ae", "ya", "yae", "eo", "e", "yeo", "ye", "o", "wa",
        "wae", "oe", "yo", "u", "wo", "we", "wi", "yu", "eu", "ui", "i",
    )
    private val HANGUL_JONG = arrayOf(
        "", "k", "k", "k", "n", "n", "n", "t", "l", "k",
        "m", "p", "l", "t", "p", "l", "m", "p", "p", "t",
        "t", "ng", "t", "t", "k", "t", "p", "t",
    )

    private val KANA_DIGRAPHS = mapOf(
        "キャ" to "kya", "キュ" to "kyu", "キョ" to "kyo",
        "シャ" to "sha", "シュ" to "shu", "シェ" to "she", "ショ" to "sho",
        "チャ" to "cha", "チュ" to "chu", "チェ" to "che", "チョ" to "cho",
        "ニャ" to "nya", "ニュ" to "nyu", "ニョ" to "nyo",
        "ヒャ" to "hya", "ヒュ" to "hyu", "ヒョ" to "hyo",
        "ミャ" to "mya", "ミュ" to "myu", "ミョ" to "myo",
        "リャ" to "rya", "リュ" to "ryu", "リョ" to "ryo",
        "ギャ" to "gya", "ギュ" to "gyu", "ギョ" to "gyo",
        "ジャ" to "ja", "ジュ" to "ju", "ジェ" to "je", "ジョ" to "jo",
        "ヂャ" to "ja", "ヂュ" to "ju", "ヂョ" to "jo",
        "ビャ" to "bya", "ビュ" to "byu", "ビョ" to "byo",
        "ピャ" to "pya", "ピュ" to "pyu", "ピョ" to "pyo",
        "ファ" to "fa", "フィ" to "fi", "フェ" to "fe", "フォ" to "fo", "フュ" to "fyu",
        "ティ" to "ti", "ディ" to "di", "テュ" to "tyu", "デュ" to "dyu",
        "トゥ" to "tu", "ドゥ" to "du",
        "ウィ" to "wi", "ウェ" to "we", "ウォ" to "wo",
        "ヴァ" to "va", "ヴィ" to "vi", "ヴェ" to "ve", "ヴォ" to "vo", "ヴュ" to "vyu",
        "ツァ" to "tsa", "ツィ" to "tsi", "ツェ" to "tse", "ツォ" to "tso",
        "スィ" to "si", "ズィ" to "zi", "イェ" to "ye", "キェ" to "kye", "ニェ" to "nye",
    )

    private val KANA_MONOGRAPHS = mapOf(
        'ア' to "a", 'イ' to "i", 'ウ' to "u", 'エ' to "e", 'オ' to "o",
        'ァ' to "a", 'ィ' to "i", 'ゥ' to "u", 'ェ' to "e", 'ォ' to "o",
        'カ' to "ka", 'キ' to "ki", 'ク' to "ku", 'ケ' to "ke", 'コ' to "ko",
        'サ' to "sa", 'シ' to "shi", 'ス' to "su", 'セ' to "se", 'ソ' to "so",
        'タ' to "ta", 'チ' to "chi", 'ツ' to "tsu", 'テ' to "te", 'ト' to "to",
        'ナ' to "na", 'ニ' to "ni", 'ヌ' to "nu", 'ネ' to "ne", 'ノ' to "no",
        'ハ' to "ha", 'ヒ' to "hi", 'フ' to "fu", 'ヘ' to "he", 'ホ' to "ho",
        'マ' to "ma", 'ミ' to "mi", 'ム' to "mu", 'メ' to "me", 'モ' to "mo",
        'ヤ' to "ya", 'ユ' to "yu", 'ヨ' to "yo",
        'ャ' to "ya", 'ュ' to "yu", 'ョ' to "yo",
        'ラ' to "ra", 'リ' to "ri", 'ル' to "ru", 'レ' to "re", 'ロ' to "ro",
        'ワ' to "wa", 'ヰ' to "wi", 'ヱ' to "we", 'ヲ' to "o",
        'ガ' to "ga", 'ギ' to "gi", 'グ' to "gu", 'ゲ' to "ge", 'ゴ' to "go",
        'ザ' to "za", 'ジ' to "ji", 'ズ' to "zu", 'ゼ' to "ze", 'ゾ' to "zo",
        'ダ' to "da", 'ヂ' to "ji", 'ヅ' to "zu", 'デ' to "de", 'ド' to "do",
        'バ' to "ba", 'ビ' to "bi", 'ブ' to "bu", 'ベ' to "be", 'ボ' to "bo",
        'パ' to "pa", 'ピ' to "pi", 'プ' to "pu", 'ペ' to "pe", 'ポ' to "po",
        'ヴ' to "vu", 'ヵ' to "ka", 'ヶ' to "ke",
    )
}
