package com.music.bitchord.data.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class LyricsScriptTest {
    private val table = mapOf('爱' to '愛', '没' to '沒', '这' to '這', '个' to '個', '们' to '們', '时' to '時', '间' to '間', '说' to '說')
    private val convert: (String) -> String = { s -> s.map { table[it] ?: it }.joinToString("") }

    private fun line(text: String, ms: Long = 0) = LyricLine(timeMs = ms, text = text)

    @Test
    fun simplifiedSongBecomesTraditionalWithTimingKept() {
        val lines = listOf(
            line("我猜你也没有多爱我", 1000),
            line("这个时间我们说爱", 2000),
            line("没有爱的时间", 3000),
        )
        val out = LyricsScript.toTraditional(lines, convert)
        assertEquals("我猜你也沒有多愛我", out[0].text)
        assertEquals("這個時間我們說愛", out[1].text)
        assertEquals(listOf(1000L, 2000L, 3000L), out.map { it.timeMs })
    }

    @Test
    fun traditionalSongIsLeftAlone() {
        val lines = listOf(line("我猜你也沒有多愛我"), line("這個時間我們說愛"), line("皇后在這裡"))
        assertSame(lines, LyricsScript.toTraditional(lines, convert))
    }

    @Test
    fun koreanAndJapaneseSongsAreLeftAlone() {
        val ko = listOf(line("지금 이 순간 爱 我们 这个"), line("오늘 밤 没 时间 爱"), line("사랑해 说 爱 们 这"))
        assertSame(ko, LyricsScript.toTraditional(ko, convert))
    }

    @Test
    fun wordTimingSurvives() {
        val words = listOf(LyricWord(0, 500, "我们"), LyricWord(500, 1000, "说爱"))
        val lines = listOf(
            LyricLine(timeMs = 0, text = "我们说爱", words = words),
            line("这个时间没有爱"), line("我们说这个时间"),
        )
        val out = LyricsScript.toTraditional(lines, convert)
        assertEquals("我們說愛", out[0].text)
        assertEquals(listOf("我們", "說愛"), out[0].words.map { it.text })
    }
}
