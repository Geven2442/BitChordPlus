package com.music.bitchord.data.lyrics

import androidx.appcompat.app.AppCompatDelegate
import com.music.bitchord.data.settings.AppSettings
import java.util.Locale

/**
 * Shows Simplified Chinese lyrics in Traditional characters when the app is in
 * a Traditional Chinese language and the setting is on. ICU does the
 * conversion on the device, with no network and no translation step.
 */
object LyricsScriptHook {
    private val transliterator by lazy { android.icu.text.Transliterator.getInstance("Hans-Hant") }

    fun install() {
        LyricsRepository.postProcess = { lines ->
            if (AppSettings.lyricsToTraditional.value && appIsTraditional()) {
                LyricsScript.toTraditional(lines) { transliterator.transliterate(it) }
            } else {
                lines
            }
        }
    }

    private fun appIsTraditional(): Boolean {
        val locale = AppCompatDelegate.getApplicationLocales().get(0) ?: Locale.getDefault()
        if (locale.language != "zh") return false
        return locale.script == "Hant" || locale.country in setOf("TW", "HK", "MO")
    }
}
