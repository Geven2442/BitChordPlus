package com.music.bitchord.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The last few background (lock screen / notification) lyric translations and
 * how each went, shown in Settings so a failure with the screen off can be read
 * afterwards instead of guessed at.
 */
object TranslationDiag {
    private val _entries = MutableStateFlow<List<String>>(emptyList())
    val entries: StateFlow<List<String>> = _entries

    @Synchronized
    fun add(line: String) {
        _entries.value = (listOf(line) + _entries.value).take(12)
    }
}
