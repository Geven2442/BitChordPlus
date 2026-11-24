package com.music.bitchord.data.lyrics

import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import java.net.URLDecoder
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TranslationFallbackTest {
    private val server = MockWebServer()

    /** Fake translator: answers in the Google format, optionally mangling batch markers. */
    private class FakeTranslate(
        val breakMarkers: Boolean,
        val detected: String,
        val convert: (String) -> String,
    ) : Dispatcher() {
        var batchCalls = 0
        var lineCalls = 0

        override fun dispatch(request: RecordedRequest): MockResponse {
            val form = request.body?.utf8().orEmpty().split('&').associate {
                val (k, v) = it.split('=', limit = 2)
                k to URLDecoder.decode(v, "UTF-8")
            }
            val q = form["q"].orEmpty()
            val isBatch = q.contains('')
            if (isBatch) batchCalls++ else lineCalls++
            var out = convert(q)
            if (isBatch && breakMarkers) out = out.replace(Regex("[^]*"), " ")
            val escaped = out.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
            return MockResponse.Builder()
                .body("""[[["$escaped","x",null,null,1]],null,"$detected"]""")
                .build()
        }
    }

    @BeforeTest fun setUp() {
        server.start()
        LyricsTranslation.endpoint = server.url("/translate_a/single").toString()
        LyricsTranslation.cacheDir = null
    }

    @AfterTest fun tearDown() {
        server.close()
    }

    private fun lines(vararg t: String) = t.mapIndexed { i, s -> LyricLine(timeMs = i * 1000L, text = s) }

    @Test
    fun brokenBatchFallsBackToLineByLine(): Unit = runBlocking<Unit> {
        val fake = FakeTranslate(breakMarkers = true, detected = "en") { it.uppercase() }
        server.dispatcher = fake
        val result = LyricsTranslation.translate("t1", lines("hello", "good night", "see you"), "fr")
        val translated = assertIs<LyricsTranslation.Result.Translated>(result)
        assertEquals(listOf("HELLO", "GOOD NIGHT", "SEE YOU"), translated.lines.map { it.text })
        assertEquals(1, fake.batchCalls)
        assertEquals(3, fake.lineCalls)
    }

    @Test
    fun simplifiedLyricsAreConvertedForTraditionalTarget(): Unit = runBlocking<Unit> {
        // Google reports Chinese as zh-CN; the target is Traditional.
        val map = mapOf('爱' to '愛', '会' to '會', '这' to '這', '时' to '時', '个' to '個', '远' to '遠')
        server.dispatcher = FakeTranslate(breakMarkers = false, detected = "zh-CN") { s -> s.map { map[it] ?: it }.joinToString("") }
        val result = LyricsTranslation.translate("t2", lines("我会永远爱你", "这个时候"), "zh-TW")
        val translated = assertIs<LyricsTranslation.Result.Translated>(result)
        assertEquals(listOf("我會永遠愛你", "這個時候"), translated.lines.map { it.text })
    }

    @Test
    fun traditionalLyricsWithTraditionalTargetAreSameLanguage(): Unit = runBlocking<Unit> {
        server.dispatcher = FakeTranslate(breakMarkers = false, detected = "zh-CN") { it }
        val result = LyricsTranslation.translate("t3", lines("我會永遠愛你", "這個時候"), "zh-TW")
        assertIs<LyricsTranslation.Result.SameLanguage>(result)
    }

    @Test
    fun chineseScriptsAreDistinct() {
        assertFalse(LyricsTranslation.sameLanguage("zh-CN", "zh-TW"))
        assertFalse(LyricsTranslation.sameLanguage("zh", "zh-Hant"))
        assertTrue(LyricsTranslation.sameLanguage("zh-TW", "zh-HK"))
        assertTrue(LyricsTranslation.sameLanguage("zh-CN", "zh"))
        assertTrue(LyricsTranslation.sameLanguage("en-US", "en"))
        assertTrue(LyricsTranslation.sameLanguage("iw", "he"))
    }

    private fun resetHooks() {
        LyricsTranslation.localEnabled = { false }
        LyricsTranslation.online = { true }
        LyricsTranslation.localTranslator = null
        LyricsTranslation.llmTranslator = null
    }

    @Test
    fun offlineUsesTheLocalEngine(): Unit = runBlocking<Unit> {
        server.dispatcher = FakeTranslate(breakMarkers = false, detected = "en") { it.uppercase() }
        LyricsTranslation.localEnabled = { true }
        LyricsTranslation.online = { false }
        LyricsTranslation.localTranslator = { l, _ -> l.map { "L:$it" } }
        try {
            val result = LyricsTranslation.translate("t4", lines("hello", "bye"), "fr")
            val translated = assertIs<LyricsTranslation.Result.Translated>(result)
            assertEquals(listOf("L:hello", "L:bye"), translated.lines.map { it.text })
            assertEquals(0, server.requestCount)
        } finally {
            resetHooks()
        }
    }

    @Test
    fun googleFailureFallsBackToTheLocalEngine(): Unit = runBlocking<Unit> {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse.Builder().code(500).build()
        }
        LyricsTranslation.localEnabled = { true }
        LyricsTranslation.localTranslator = { l, _ -> l.map { "L:$it" } }
        try {
            val result = LyricsTranslation.translate("t5", lines("hello", "bye"), "fr")
            assertEquals(listOf("L:hello", "L:bye"), assertIs<LyricsTranslation.Result.Translated>(result).lines.map { it.text })
        } finally {
            resetHooks()
        }
    }

    @Test
    fun aConfiguredModelIsTriedBeforeGoogle(): Unit = runBlocking<Unit> {
        server.dispatcher = FakeTranslate(breakMarkers = false, detected = "en") { it.uppercase() }
        LyricsTranslation.llmTranslator = { l, _ -> l.map { "M:$it" } }
        try {
            val result = LyricsTranslation.translate("t6", lines("hello", "bye"), "fr")
            assertEquals(listOf("M:hello", "M:bye"), assertIs<LyricsTranslation.Result.Translated>(result).lines.map { it.text })
            assertEquals(0, server.requestCount)
        } finally {
            resetHooks()
        }
    }

    @Test
    fun aFailingModelFallsBackToGoogle(): Unit = runBlocking<Unit> {
        server.dispatcher = FakeTranslate(breakMarkers = false, detected = "en") { it.uppercase() }
        LyricsTranslation.llmTranslator = { _, _ -> null }
        try {
            val result = LyricsTranslation.translate("t7", lines("hello", "bye"), "fr")
            assertEquals(listOf("HELLO", "BYE"), assertIs<LyricsTranslation.Result.Translated>(result).lines.map { it.text })
        } finally {
            resetHooks()
        }
    }

    @Test
    fun mixedEnglishKoreanLinesAreSplitByScript(): Unit = runBlocking<Unit> {
        // Auto-detect leaves Korean alone; only an explicit source language translates it.
        val table = mapOf(
            "Oh, my, oh, my," to "哦，我的，",
            "지금 이 순간" to "此刻",
        )
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val form = request.body?.utf8().orEmpty().split('&').associate {
                    val (k, v) = it.split('=', limit = 2)
                    k to URLDecoder.decode(v, "UTF-8")
                }
                val sl = form["sl"]
                var out = form["q"].orEmpty()
                if (sl == "en") out = out.replace("Oh, my, oh, my,", table.getValue("Oh, my, oh, my,"))
                // Whole mixed lines stay half done even under sl=ko.
                if (sl == "ko" && !out.contains("Oh,")) out = out.replace("지금 이 순간", table.getValue("지금 이 순간"))
                if (sl == "auto") out = out.replace("Oh, my, oh, my,", table.getValue("Oh, my, oh, my,"))
                val escaped = out.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                return MockResponse.Builder().body("""[[["$escaped","x",null,null,1]],null,"en"]""").build()
            }
        }
        val result = LyricsTranslation.translate("t8", lines("Oh, my, oh, my, 지금 이 순간", "Hello there"), "zh-TW")
        val translated = assertIs<LyricsTranslation.Result.Translated>(result)
        assertEquals("哦，我的， 此刻", translated.lines[0].text)
    }
}
