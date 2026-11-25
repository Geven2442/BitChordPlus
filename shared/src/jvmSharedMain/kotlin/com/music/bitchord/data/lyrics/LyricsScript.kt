package com.music.bitchord.data.lyrics

/**
 * Simplified Chinese lyrics shown in Traditional characters.
 *
 * A conversion, not a translation: it needs no network and no translation
 * button, and it keeps every line's timing because the text is rewritten one
 * character for one character. Only a song that is really Simplified is
 * touched. Traditional lyrics hold a few characters that a Simplified
 * converter would "correct" (后, 干), and Japanese lyrics are full of kanji
 * that mean something else once converted (国 → 國 is fine, 発 is not), so a
 * song with a fair share of kana or hangul is left alone, as is one whose
 * characters barely change.
 */
object LyricsScript {
    private val HAN = Regex("[\\u4E00-\\u9FFF]")
    private val KANA_OR_HANGUL = Regex("[\\u3040-\\u30FF\\u1100-\\u11FF\\uAC00-\\uD7AF]")

    /** [convert] turns Simplified into Traditional; anything it leaves alone is already right. */
    fun toTraditional(lines: List<LyricLine>, convert: (String) -> String): List<LyricLine> {
        val sample = buildString {
            lines.forEach { line ->
                append(line.text).append('\n')
                line.background?.let { append(it.text).append('\n') }
            }
        }
        val han = HAN.findAll(sample).count()
        if (han < MIN_HAN) return lines
        if (KANA_OR_HANGUL.findAll(sample).count() * 20 > han) return lines
        val converted = convert(sample)
        val changed = sample.zip(converted).count { (a, b) -> a != b && HAN.matches(a.toString()) }
        if (changed < MIN_CHANGED || changed * 10 < han) return lines
        return lines.map { convertLine(it, convert) }
    }

    private fun convertLine(line: LyricLine, convert: (String) -> String): LyricLine {
        val background = line.background?.let { convertLine(it, convert) }
        val text = convert(line.text)
        if (text.length != line.text.length) return line.copy(background = background)
        val words = line.words.map { it.copy(text = convert(it.text)) }
        if (words.indices.any { words[it].text.length != line.words[it].text.length }) {
            return line.copy(text = text, background = background)
        }
        // The words are located in the line by text, so they must still be there.
        var offset = 0
        for (word in words) {
            val at = text.indexOf(word.text, offset)
            if (at < 0) return line.copy(text = text, background = background)
            offset = at + word.text.length
        }
        return line.copy(text = text, words = words, background = background)
    }

    private const val MIN_HAN = 8
    private const val MIN_CHANGED = 3
}
