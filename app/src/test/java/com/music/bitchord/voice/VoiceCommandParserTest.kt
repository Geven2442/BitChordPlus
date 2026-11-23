package com.music.bitchord.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceCommandParserTest {
    private fun p(s: String, strict: Boolean = true) = VoiceCommandParser.parse(s, strict)

    @Test fun skipsAndGoesBack() {
        assertEquals(VoiceCommand.Next, p("下一首"))
        assertEquals(VoiceCommand.Next, p("幫我切歌"))
        assertEquals(VoiceCommand.Next, p("下一首歌吧"))
        assertEquals(VoiceCommand.Next, p("换一首"))
        assertEquals(VoiceCommand.Previous, p("上一首"))
        assertEquals(VoiceCommand.Previous, p("請你播放上一首", strict = false))
    }

    @Test fun pausePlay() {
        assertEquals(VoiceCommand.Pause, p("暫停"))
        assertEquals(VoiceCommand.Pause, p("暂停播放"))
        assertEquals(VoiceCommand.Play, p("繼續播放"))
        assertEquals(VoiceCommand.Play, p("播放"))
    }

    @Test fun volume() {
        assertEquals(VoiceCommand.VolumeUp, p("大聲一點"))
        assertEquals(VoiceCommand.VolumeUp, p("音量大一點"))
        assertEquals(VoiceCommand.VolumeDown, p("小声点"))
        assertEquals(VoiceCommand.VolumePercent(50), p("音量調到五十"))
        assertEquals(VoiceCommand.VolumePercent(30), p("音量30%"))
        assertEquals(VoiceCommand.VolumePercent(100), p("音量調到一百"))
        assertEquals(VoiceCommand.VolumePercent(15), p("聲音十五"))
        assertEquals(VoiceCommand.Mute, p("靜音"))
        assertEquals(VoiceCommand.Unmute, p("取消靜音"))
    }

    @Test fun others() {
        assertEquals(VoiceCommand.Restart, p("重新播放"))
        assertEquals(VoiceCommand.Seek(10_000), p("快轉"))
        assertEquals(VoiceCommand.Seek(30_000), p("快轉三十秒"))
        assertEquals(VoiceCommand.Seek(-15_000), p("倒退十五秒"))
        assertEquals(VoiceCommand.ToggleShuffle, p("隨機播放"))
        assertEquals(VoiceCommand.Repeat(RepeatKind.ONE), p("單曲循環"))
        assertEquals(VoiceCommand.Repeat(RepeatKind.OFF), p("取消循環"))
        assertEquals(VoiceCommand.Repeat(RepeatKind.ALL), p("全部循環"))
    }

    @Test fun strictIgnoresLyricsButLooseAcceptsThem() {
        assertNull(p("我想要你說下一首歌是我們的"))
        assertEquals(VoiceCommand.Next, p("我想要你說下一首歌是我們的", strict = false))
        assertNull(p("今天天氣很好"))
    }

    @Test fun englishWords() {
        assertEquals(VoiceCommand.Next, p("Next"))
        assertEquals(VoiceCommand.Pause, p("pause"))
        assertEquals(VoiceCommand.VolumeUp, p("volume up"))
    }

    @Test fun chineseNumbers() {
        assertEquals(55, VoiceCommandParser.number("五十五"))
        assertEquals(105, VoiceCommandParser.number("一百零五"))
        assertEquals(10, VoiceCommandParser.number("十"))
        assertEquals(20, VoiceCommandParser.number("二十"))
    }
}
