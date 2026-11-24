package com.music.bitchord.data.lyrics

import com.music.bitchord.data.Http
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.LlmProvider
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Translates a whole song with a language model the person has a key for.
 * The key stays on this phone and goes only to the provider they chose.
 */
object LlmTranslator {
    private val json = Json { ignoreUnknownKeys = true }
    private val client by lazy {
        Http.client.newBuilder()
            .callTimeout(60, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    fun defaultModel(provider: LlmProvider): String = when (provider) {
        LlmProvider.GEMINI -> "gemini-2.5-flash"
        LlmProvider.OPENAI -> "gpt-4o-mini"
        LlmProvider.CLAUDE -> "claude-haiku-5-5"
        LlmProvider.OFF -> ""
    }

    fun configured(): Boolean =
        AppSettings.llmProvider.value != LlmProvider.OFF && AppSettings.llmApiKey.value.isNotBlank()

    /** Null when no model is set up or the request failed, so the caller falls back. */
    suspend fun translate(lines: List<String>, target: String): List<String>? {
        if (!configured()) return null
        val out = ArrayList<String>(lines.size)
        var start = 0
        while (start < lines.size) {
            val end = minOf(start + CHUNK, lines.size)
            val chunk = lines.subList(start, end)
            val reply = request(prompt(chunk, target)) ?: return null
            out += OnDeviceTranslator.parse(reply, chunk.size) ?: return null
            start = end
        }
        return out
    }

    private fun prompt(chunk: List<String>, target: String): String {
        val language = Locale.forLanguageTag(target).getDisplayName(Locale.ENGLISH).ifBlank { target }
        return buildString {
            append("Translate these song lyric lines into $language. ")
            append("Read them as one song: keep the tone, idioms and meaning, not word for word. ")
            append("Reply with exactly the same numbering, one translated line per number and nothing else. ")
            append("Keep a line unchanged if it is already in $language or is only sounds or a name.\n\n")
            chunk.forEachIndexed { i, line -> append(i + 1).append(". ").append(line).append('\n') }
        }
    }

    private suspend fun request(prompt: String): String? = withContext(Dispatchers.IO) {
        val provider = AppSettings.llmProvider.value
        val key = AppSettings.llmApiKey.value.trim()
        val model = AppSettings.llmModel.value.trim().ifBlank { defaultModel(provider) }
        val system = "You are a careful lyric translator."
        val media = "application/json".toMediaType()
        val builder = Request.Builder()
        val bodyJson: JsonObject
        when (provider) {
            LlmProvider.GEMINI -> {
                builder.url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
                    .header("x-goog-api-key", key)
                bodyJson = buildJsonObject {
                    putJsonObject("systemInstruction") {
                        putJsonArray("parts") { add(buildJsonObject { put("text", system) }) }
                    }
                    putJsonArray("contents") {
                        add(
                            buildJsonObject {
                                put("role", "user")
                                putJsonArray("parts") { add(buildJsonObject { put("text", prompt) }) }
                            },
                        )
                    }
                }
            }
            LlmProvider.OPENAI -> {
                val base = AppSettings.llmBaseUrl.value.trim().trimEnd('/').ifBlank { "https://api.openai.com/v1" }
                builder.url("$base/chat/completions").header("Authorization", "Bearer $key")
                bodyJson = buildJsonObject {
                    put("model", model)
                    putJsonArray("messages") {
                        add(buildJsonObject { put("role", "system"); put("content", system) })
                        add(buildJsonObject { put("role", "user"); put("content", prompt) })
                    }
                }
            }
            LlmProvider.CLAUDE -> {
                builder.url("https://api.anthropic.com/v1/messages")
                    .header("x-api-key", key)
                    .header("anthropic-version", "2023-06-01")
                bodyJson = buildJsonObject {
                    put("model", model)
                    put("max_tokens", 8192)
                    put("system", system)
                    putJsonArray("messages") {
                        add(buildJsonObject { put("role", "user"); put("content", prompt) })
                    }
                }
            }
            LlmProvider.OFF -> return@withContext null
        }
        val request = builder.post(bodyJson.toString().toRequestBody(media)).build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val root = json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
                when (provider) {
                    LlmProvider.GEMINI -> root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                        ?.get("content")?.jsonObject?.get("parts")?.jsonArray
                        ?.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty() }
                    LlmProvider.OPENAI -> root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                        ?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
                    LlmProvider.CLAUDE -> root["content"]?.jsonArray
                        ?.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty() }
                    LlmProvider.OFF -> null
                }?.takeIf { it.isNotBlank() }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private const val CHUNK = 80
}
