package com.music.bitchord.data.lyrics

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * ML Kit's offline translator. Each language is a small model (about 30 MB)
 * that must have been downloaded once; [preload] does that while online.
 * Chinese comes out Simplified, so a Traditional target is converted after.
 */
object MlKitTranslator {
    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
        addOnCanceledListener { cont.cancel() }
    }

    private fun modelCode(tag: String): String? {
        val lower = tag.lowercase()
        return if (lower.startsWith("zh")) TranslateLanguage.CHINESE else TranslateLanguage.fromLanguageTag(lower.substringBefore('-'))
    }

    private fun wantsTraditional(target: String): Boolean {
        val t = target.lowercase()
        return t.startsWith("zh") && (t.contains("hant") || t.endsWith("tw") || t.endsWith("hk") || t.endsWith("mo"))
    }

    private fun toTraditional(text: String): String = try {
        android.icu.text.Transliterator.getInstance("Hans-Hant").transliterate(text)
    } catch (_: Throwable) {
        text
    }

    /** Translates [lines]; null when no line could be translated (a missing model, say). */
    suspend fun translate(lines: List<String>, target: String): List<String>? {
        val to = modelCode(target) ?: return null
        val identifier = LanguageIdentification.getClient()
        val sources = lines.map { line ->
            val tag = try { identifier.identifyLanguage(line).await() } catch (e: CancellationException) { throw e } catch (_: Throwable) { "und" }
            if (tag == "und") null else modelCode(tag)
        }
        val result = lines.toMutableList()
        var changed = 0
        sources.indices.filter { sources[it] != null && sources[it] != to }.groupBy { sources[it]!! }.forEach { (from, indices) ->
            val translator = Translation.getClient(
                TranslatorOptions.Builder().setSourceLanguage(from).setTargetLanguage(to).build(),
            )
            try {
                // No network here: a model that is not on the phone fails, and those lines stay as they are.
                translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
                for (i in indices) {
                    val out = translator.translate(lines[i]).await()
                    if (out.isNotBlank()) {
                        result[i] = if (wantsTraditional(target)) toTraditional(out) else out
                        changed++
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
            } finally {
                translator.close()
            }
        }
        return result.takeIf { changed > 0 }
    }

    /** Fetches the model pair for a song Google just translated, so offline play finds it. */
    suspend fun preload(sourceLanguage: String, target: String) {
        val from = modelCode(sourceLanguage) ?: return
        val to = modelCode(target) ?: return
        if (from == to) return
        val translator = Translation.getClient(
            TranslatorOptions.Builder().setSourceLanguage(from).setTargetLanguage(to).build(),
        )
        try {
            translator.downloadModelIfNeeded(DownloadConditions.Builder().requireWifi().build()).await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
        } finally {
            translator.close()
        }
    }
}
