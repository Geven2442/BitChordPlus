package com.music.bitchord.voice

/** What a spoken phrase asks the player to do. */
sealed interface VoiceCommand {
    data object Next : VoiceCommand
    data object Previous : VoiceCommand
    data object Pause : VoiceCommand
    data object Play : VoiceCommand
    data object VolumeUp : VoiceCommand
    data object VolumeDown : VoiceCommand
    data object Mute : VoiceCommand
    data object Unmute : VoiceCommand
    data object Restart : VoiceCommand
    data object ToggleShuffle : VoiceCommand
    data class VolumePercent(val percent: Int) : VoiceCommand
    data class Seek(val deltaMs: Long) : VoiceCommand
    data class Repeat(val kind: RepeatKind) : VoiceCommand
}

enum class RepeatKind { OFF, ONE, ALL }

/**
 * Turns what a recogniser heard into a [VoiceCommand].
 *
 * Understands Traditional and Simplified Chinese plus a few English words.
 * [strict] is for listening that never stops: the whole utterance has to be
 * the command (with polite filler allowed around it), so a lyric that merely
 * contains "下一首" does not skip the song. Button-triggered listening is
 * lenient and accepts the command anywhere in the phrase.
 */
object VoiceCommandParser {

    private val simplified = mapOf(
        '暂' to '暫', '继' to '繼', '续' to '續', '声' to '聲', '静' to '靜', '调' to '調',
        '复' to '復', '换' to '換', '个' to '個', '减' to '減', '转' to '轉', '进' to '進',
        '单' to '單', '环' to '環', '机' to '機', '随' to '隨', '开' to '開', '关' to '關',
        '点' to '點', '钟' to '鐘', '后' to '後', '设' to '設', '为' to '為', '头' to '頭',
        '乱' to '亂', '节' to '節', '来' to '來', '放' to '放', '听' to '聽', '会' to '會',
        '该' to '該', '无' to '無', '这' to '這', '歌' to '歌', '曲' to '曲', '页' to '頁',
    )

    private val leading = listOf(
        "請你", "麻煩你", "請", "麻煩", "幫我", "給我", "可以", "能不能", "能", "我要", "我想", "把", "嘿", "欸", "hey", "ok",
    )
    private val trailing = listOf(
        "一下", "吧", "啦", "喔", "哦", "啊", "謝謝", "拜託", "好嗎", "可以嗎", "一點", "點", "了",
    )

    private const val NUM = "\\d{1,3}|[零一二兩三四五六七八九十百]{1,5}"

    private class Rule(val pattern: String, val build: (MatchResult) -> VoiceCommand)

    private val rules: List<Rule> = listOf(
        Rule("(?:音量|聲音)(?:調到|調成|設為|設定為|設定|設成|開到|到|為|是)?(?:百分之)?($NUM)(?:%|％|趴|百分比)?") {
            VoiceCommand.VolumePercent(number(it.groupValues[1]).coerceIn(0, 100))
        },
        Rule("取消靜音|解除靜音|恢復音量|恢復聲音|開啟聲音|unmute") { VoiceCommand.Unmute },
        Rule("靜音|關聲音|關掉聲音|mute") { VoiceCommand.Mute },
        Rule("大聲|音量(?:加|調大|調高|大|增加|提高|加大)|調大|調高|加大|增加音量|提高音量|聲音(?:調)?大|volumeup|louder|turnitup") {
            VoiceCommand.VolumeUp
        },
        Rule("小聲|音量(?:減|調小|調低|小|降低|減少|縮小)|調小|調低|降低|減小|減少音量|聲音(?:調)?小|volumedown|quieter|turnitdown") {
            VoiceCommand.VolumeDown
        },
        Rule("重播|重頭播放|從頭播放|重新播放|從頭開始|重來|replay|restart") { VoiceCommand.Restart },
        Rule("(?:快轉|快進|前進)(?:($NUM)秒(?:鐘)?)?|forward") {
            VoiceCommand.Seek(secondsOrDefault(it.groupValues.getOrNull(1)) * 1000L)
        },
        Rule("(?:倒轉|倒退|後退|往回)(?:($NUM)秒(?:鐘)?)?|rewind") {
            VoiceCommand.Seek(-secondsOrDefault(it.groupValues.getOrNull(1)) * 1000L)
        },
        Rule("隨機播放|隨機|打亂|洗牌|shuffle") { VoiceCommand.ToggleShuffle },
        Rule("取消循環|關閉循環|不要循環|別循環|不循環|關掉循環|repeatoff") { VoiceCommand.Repeat(RepeatKind.OFF) },
        Rule("單曲循環|單曲重複|循環這首|重複這首|repeatone") { VoiceCommand.Repeat(RepeatKind.ONE) },
        Rule("全部循環|列表循環|循環播放|重複全部|repeatall") { VoiceCommand.Repeat(RepeatKind.ALL) },
        Rule("下一首歌?|下一曲|下首|下一個|下個|換一首|換首|換歌|切歌|切下一首|跳過|跳下一首|nextsong|next|skip") {
            VoiceCommand.Next
        },
        Rule("上一首歌?|上一曲|上首|上一個|上個|前一首|前首|previoussong|previous|prev") { VoiceCommand.Previous },
        Rule("暫停播放|暫停|停止播放|停止|停一下|先停|pause|stop") { VoiceCommand.Pause },
        Rule("播放音樂|繼續播放|繼續|開始播放|恢復播放|放歌|放音樂|播放|play|resume") { VoiceCommand.Play },
    )

    private val strictRules = rules.map { Regex("^(?:${it.pattern})$") to it }
    private val looseRules = rules.map { Regex(it.pattern) to it }

    /** The command in [heard], or null when it is not one. */
    fun parse(heard: String, strict: Boolean): VoiceCommand? {
        val text = clean(heard)
        if (text.isEmpty()) return null
        val candidates = if (strict) strictRules else looseRules
        for ((regex, rule) in candidates) {
            val match = (if (strict) regex.matchEntire(text) else regex.find(text)) ?: continue
            return rule.build(match)
        }
        return null
    }

    /** The first alternative that is a command, in the recogniser's own order of confidence. */
    fun parseAny(alternatives: List<String>, strict: Boolean): Pair<VoiceCommand, String>? {
        for (heard in alternatives) {
            val cmd = parse(heard, strict) ?: continue
            return cmd to heard
        }
        return null
    }

    private fun clean(heard: String): String {
        var text = buildString {
            heard.lowercase().forEach { c ->
                if (c.isWhitespace() || c in "，。,.!！?？、~～…「」\"'") return@forEach
                append(simplified[c] ?: c)
            }
        }
        var changed = true
        while (changed && text.isNotEmpty()) {
            changed = false
            leading.firstOrNull { text.startsWith(it) && text.length > it.length }?.let {
                text = text.removePrefix(it); changed = true
            }
            trailing.firstOrNull { text.endsWith(it) && text.length > it.length }?.let {
                text = text.removeSuffix(it); changed = true
            }
        }
        return text
    }

    private fun secondsOrDefault(raw: String?): Long =
        raw?.takeIf { it.isNotEmpty() }?.let { number(it).coerceIn(1, 600).toLong() } ?: 10L

    /** "50", "五十", "一百", "十五", "一百零五" → the number; anything unreadable is 0. */
    internal fun number(raw: String): Int {
        raw.toIntOrNull()?.let { return it }
        var total = 0
        var current = 0
        for (c in raw) {
            when (c) {
                '零' -> current = 0
                '一' -> current = 1
                '二', '兩' -> current = 2
                '三' -> current = 3
                '四' -> current = 4
                '五' -> current = 5
                '六' -> current = 6
                '七' -> current = 7
                '八' -> current = 8
                '九' -> current = 9
                '十' -> { total += if (current == 0) 10 else current * 10; current = 0 }
                '百' -> { total += if (current == 0) 100 else current * 100; current = 0 }
            }
        }
        return total + current
    }
}
