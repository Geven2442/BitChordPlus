package com.music.bitchord.data.lyrics

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.LocalTranslationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale
import kotlinx.coroutines.CancellationException

/**
 * Gemini Nano through ML Kit's Prompt API, as the offline fallback for lyric
 * translation. Not every phone has it: [status] says whether this one does.
 */
object OnDeviceTranslator {
    enum class Support { UNKNOWN, AVAILABLE, DOWNLOADABLE, DOWNLOADING, UNSUPPORTED }

    private var model: GenerativeModel? = null

    private fun client(): GenerativeModel =
        model ?: Generation.getClient().also { model = it }

    suspend fun status(): Support = try {
        when (client().checkStatus()) {
            FeatureStatus.AVAILABLE -> Support.AVAILABLE
            FeatureStatus.DOWNLOADABLE -> Support.DOWNLOADABLE
            FeatureStatus.DOWNLOADING -> Support.DOWNLOADING
            else -> Support.UNSUPPORTED
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        Support.UNSUPPORTED
    }

    /** Fetches Gemini Nano when the phone supports it but has not downloaded it yet. */
    suspend fun download(): Boolean = try {
        var done = false
        client().download().collect { if (it is DownloadStatus.DownloadCompleted) done = true }
        done || status() == Support.AVAILABLE
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        false
    }

    fun install(context: Context) {
        val app = context.applicationContext
        LyricsTranslation.online = { isOnline(app) }
        LyricsTranslation.localEnabled = { AppSettings.onDeviceTranslation.value }
        LyricsTranslation.localTranslator = { lines, target -> translateLocally(lines, target) }
        LyricsTranslation.llmTranslator = { lines, target -> LlmTranslator.translate(lines, target) }
        LyricsTranslation.onTranslated = { source, target ->
            if (AppSettings.onDeviceTranslation.value && AppSettings.localTranslationEngine.value != LocalTranslationEngine.NANO) {
                preloadScope.launch { MlKitTranslator.preload(source, target) }
            }
        }
    }

    private fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private val preloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Picks the engine: Nano where the person asked for it or where it is there, ML Kit otherwise. */
    private suspend fun translateLocally(lines: List<String>, target: String): List<String>? =
        when (AppSettings.localTranslationEngine.value) {
            LocalTranslationEngine.NANO -> translate(lines, target)
            LocalTranslationEngine.MLKIT -> MlKitTranslator.translate(lines, target)
            LocalTranslationEngine.AUTO ->
                if (status() == Support.AVAILABLE) translate(lines, target) ?: MlKitTranslator.translate(lines, target)
                else MlKitTranslator.translate(lines, target)
        }

    /** One numbered line in, one numbered line out, in chunks the small model can hold. */
    private suspend fun translate(lines: List<String>, target: String): List<String>? {
        if (status() != Support.AVAILABLE) return null
        val language = Locale.forLanguageTag(target).getDisplayName(Locale.ENGLISH).ifBlank { target }
        val out = ArrayList<String>(lines.size)
        var start = 0
        while (start < lines.size) {
            val end = minOf(start + CHUNK, lines.size)
            val chunk = lines.subList(start, end)
            val prompt = buildString {
                append("Translate each numbered song lyric line into $language. ")
                append("Keep the same numbering, one translated line per number, no notes, ")
                append("and leave lines that are only sounds or names unchanged.\n")
                chunk.forEachIndexed { i, line -> append(i + 1).append(". ").append(line).append('\n') }
            }
            val reply = try {
                client().generateContent(
                    generateContentRequest(TextPart(prompt)) { temperature = 0.2f },
                ).candidates.firstOrNull()?.text
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                null
            } ?: return null
            val parsed = parse(reply, chunk.size) ?: return null
            out += parsed
            start = end
        }
        return out
    }

    private val numbered = Regex("^\\s*(\\d+)[.)、]\\s*(.*)$")

    internal fun parse(reply: String, expected: Int): List<String>? {
        val byNumber = HashMap<Int, String>()
        reply.lineSequence().forEach { raw ->
            numbered.find(raw)?.let { byNumber[it.groupValues[1].toInt()] = it.groupValues[2].trim() }
        }
        if ((1..expected).any { byNumber[it].isNullOrBlank() }) return null
        return (1..expected).map { byNumber.getValue(it) }
    }

    private const val CHUNK = 12
}
