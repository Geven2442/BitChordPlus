package com.music.bitchord.playback

import com.music.bitchord.data.model.Song
import java.util.Locale

/**
 * Builds the station that plays on after a one-off song.
 *
 * Shared: Android and the desktop assemble the same station from the same
 * watch queue, so the de-duplication and the per-artist caps are one
 * implementation rather than two that drift.
 *
 * Two problems this exists to solve:
 *
 *  - YouTube's watch queues routinely carry the same recording twice — the
 *    official audio and the music video are separate videoIds with near
 *    identical titles — so de-duping on id alone lets a track play, then play
 *    again as its video. Songs are matched on a normalised title plus lead
 *    artist instead.
 *
 *  - A radio mix leans hard on the seed's own artist. Capping how many tracks
 *    any one artist contributes keeps a station from turning into a single
 *    artist's discography.
 */
object QueueBuilder {

    /** No more than this many tracks by one artist in a single batch. */
    const val PER_ARTIST_LIMIT = 2

    /** The artist the station was seeded on gets more room, but not the run of it. */
    const val SEED_ARTIST_LIMIT = 4

    /** Fewer videos than this in a mix and a video station plays on with its songs too. */
    const val MIN_VIDEO_STATION = 3

    /**
     * The subset of [candidates] worth appending after [existing]: nothing
     * already queued, nothing repeated, at most [limit] tracks.
     */
    fun extend(existing: List<Song>, candidates: List<Song>, limit: Int, preferVideos: Boolean = false): List<Song> {
        val taken = existing.toMutableList()
        val perArtist = mutableMapOf<String, Int>()
        val seedArtists = existing.lastOrNull()?.artist?.let(::artistSet).orEmpty()
        val out = mutableListOf<Song>()

        // A mix pairs all but every track with its own music-video upload —
        // the same recording under a different id, titled far enough apart
        // ("Dildaara (Stand By Me)" against "Lyrical Video: Dildara Song")
        // that no title match will catch it. The catalogue cut is the one a
        // music app wants; videos only stand in when there is nothing else.
        // [preferVideos] turns that round for a station playing on from a music
        // video: its videos, where the mix has enough of them to be one.
        val songsOnly = candidates.filterNot { it.isVideo }
        val videosOnly = candidates.filter { it.isVideo }
        val picked = when {
            preferVideos && videosOnly.size >= MIN_VIDEO_STATION -> videosOnly
            preferVideos -> candidates
            else -> songsOnly.ifEmpty { candidates }
        }
        // Where a song and its music-video upload share a name, the video is
        // the one to drop, whichever the mix happened to list first.
        val ordered = if (picked.any { !it.isVideo } && picked.any { it.isVideo }) {
            picked.filterNot { v -> v.isVideo && picked.any { !it.isVideo && isSameTitle(it, v) } }
        } else {
            picked
        }

        for (candidate in ordered) {
            if (out.size >= limit) break
            if (taken.any { isSameRecording(it, candidate) || isSameTitle(it, candidate) }) continue

            val artists = artistSet(candidate.artist)
            val key = artists.minOrNull()
            if (key != null) {
                val cap = if (artists.any { it in seedArtists }) {
                    SEED_ARTIST_LIMIT
                } else {
                    PER_ARTIST_LIMIT
                }
                val played = perArtist[key] ?: 0
                if (played >= cap) continue
                perArtist[key] = played + 1
            }

            taken += candidate
            out += candidate
        }
        return out
    }

    /**
     * The same song name, whoever is billed. A station that offers "Can I Call
     * You Now" twice — once by each uploader — reads as a repeat even when the
     * credits differ enough that [isSameRecording] lets it through. Remix and
     * cover markers stay in [normalisedTitle], so those remain separate tracks.
     */
    fun isSameTitle(a: Song, b: Song): Boolean {
        val title = foldedTitle(a.title)
        if (title.isNotEmpty() && title == foldedTitle(b.title)) return true
        val left = coreKey(a.title) ?: return false
        return left == coreKey(b.title)
    }

    /**
     * The song's own name out of a Chinese upload title that dresses it up:
     * `【姚晓棠】《迷人的危险》独唱版来啦…` and `姚曉棠 - 迷人的危險『為什麼最迷人的…`
     * are one song, and neither title is the other. The name is what sits in
     * 《》, or after the artist's dash up to the next bracket; both are folded to
     * simplified characters so the two scripts meet. Only for titles that
     * carry such a frame and are Chinese, and only a name of three characters or
     * more, so a bare "Remix" after a dash never stands for a song.
     */
    fun coreKey(raw: String): String? {
        if (!HAN.containsMatchIn(raw)) return null
        val core = QUOTED_NAME.find(raw)?.groupValues?.get(1)
            ?: raw.substringAfter(" - ", "").takeIf { it.isNotEmpty() }
                ?.let { after -> after.substringBefore('『').substringBefore('「').substringBefore('《').substringBefore('(').substringBefore('（') }
            ?: return null
        val key = foldToSimplified(core).lowercase(Locale.ROOT).replace(PUNCTUATION, "")
        return key.takeIf { it.length >= 3 && HAN.containsMatchIn(it) }
    }

    /** [normalisedTitle] with traditional and simplified characters read as one. */
    private fun foldedTitle(raw: String): String = foldToSimplified(normalisedTitle(raw))

    /** [foldedTitle] for callers outside the queue: the same song under two ids shares it. */
    fun titleKey(raw: String): String = foldedTitle(raw)

    private fun foldToSimplified(s: String): String {
        val out = StringBuilder(s.length)
        for (ch in s) {
            val i = TRADITIONAL.indexOf(ch)
            out.append(if (i >= 0) SIMPLIFIED[i] else ch)
        }
        return out.toString()
    }

    private val HAN = Regex("\\p{IsHan}")
    private val QUOTED_NAME = Regex("[《〈]([^》〉]+)[》〉]")

    // The commonest characters that differ between the two scripts, index for index.
    private const val TRADITIONAL = "愛嗎們這來個說對還經過時會為麼樣從張開關頭見點無車東長門問間題當發電話書聽讀寫畫龍鳳鳥魚馬風雲飛樂歡歲歷氣決準燈與傳隊陽陰險曉韓國學寧實寶將幾線給緊總處覺親習讓誰讚請謝邊進遠連選遲錄鐘鏡雙雜離靜韻響願顧顯驚體鬥齊齡憶懷戀淚歸夢獨雖難傷麗營藝幫帶帥幣幹廣應徹憂戰擁擇擔據數斷暫曆條機橋歐殘漢淺滿濃煙熱爭牆現環畢異療監盡盤眾確禮禍種積穩競筆節範簡紅約級純絕絲綠維網緣練縮繞繼續羅義聖聞職聯聰臉臺舊薩裡規視觀觸計訂許註評詞試誠該詳語誤課論謎謠講識變貓賓賞賢賴贈軟載較輕輸辦農運達違遞適邁郵鄉針鈴鋼錯錢鎖閉閒閃陣陸階隨雞項順須預領額顏類飄餘館驗髮鴻黃黨後殺藍銀鐵鎮嚴勵勞動務勝區協單賣買貴費資賽兒內兩劇剛創劍參擴隻聲屬層嶺島峽巔庫廳廢彈彎彌態懲懼戲戶擾擊攝敵斬晝暈朧檔櫃欄殼毀洶渦湧溫滾漲潔潤澀濕灣烏煩爺犧猶獎獵獸瑪疊瘋癡皺盞睜碼礦禪窮窩籠糧糾縱織繪纏罵翹腦膠艙艷蘭蘿蠟衛補裝複襯誘諒謊譜賬趕躍軌輝轉轟邏鄭醫釋銳鑒鑽閱闖闊隱霧靈靚頁頻顆飯飲餓驅驕驟髒魯鮮鯨鷹萬沒"
    private const val SIMPLIFIED = "爱吗们这来个说对还经过时会为么样从张开关头见点无车东长门问间题当发电话书听读写画龙凤鸟鱼马风云飞乐欢岁历气决准灯与传队阳阴险晓韩国学宁实宝将几线给紧总处觉亲习让谁赞请谢边进远连选迟录钟镜双杂离静韵响愿顾显惊体斗齐龄忆怀恋泪归梦独虽难伤丽营艺帮带帅币干广应彻忧战拥择担据数断暂历条机桥欧残汉浅满浓烟热争墙现环毕异疗监尽盘众确礼祸种积稳竞笔节范简红约级纯绝丝绿维网缘练缩绕继续罗义圣闻职联聪脸台旧萨里规视观触计订许注评词试诚该详语误课论谜谣讲识变猫宾赏贤赖赠软载较轻输办农运达违递适迈邮乡针铃钢错钱锁闭闲闪阵陆阶随鸡项顺须预领额颜类飘余馆验发鸿黄党后杀蓝银铁镇严励劳动务胜区协单卖买贵费资赛儿内两剧刚创剑参扩只声属层岭岛峡巅库厅废弹弯弥态惩惧戏户扰击摄敌斩昼晕胧档柜栏壳毁汹涡涌温滚涨洁润涩湿湾乌烦爷牺犹奖猎兽玛叠疯痴皱盏睁码矿禅穷窝笼粮纠纵织绘缠骂翘脑胶舱艳兰萝蜡卫补装复衬诱谅谎谱账赶跃轨辉转轰逻郑医释锐鉴钻阅闯阔隐雾灵靓页频颗饭饮饿驱骄骤脏鲁鲜鲸鹰万没"

    /**
     * Whether two entries are the same recording. Ids differ between the audio
     * and video cuts of a track, which is exactly the case id equality misses.
     */
    fun isSameRecording(a: Song, b: Song): Boolean {
        if (a.videoId == b.videoId) return true
        val title = foldedTitle(a.title)
        if (title.isEmpty() || title != foldedTitle(b.title)) return false
        // One shared name is enough. The two cuts of a track get billed in
        // whatever order the upload used — "Pritam, Arijit Singh & Shilpa Rao"
        // against "Shilpa Rao, Arijit Singh, & Pritam" is one song, twice.
        val left = artistSet(a.artist)
        val right = artistSet(b.artist)
        return left.isEmpty() || right.isEmpty() || left.any { it in right }
    }

    /**
     * `Kesariya (From "Brahmastra") | Official Video` and `Kesariya` are one
     * recording as far as a queue is concerned. Remix and cover markers are
     * deliberately left in — those really are different tracks.
     */
    fun normalisedTitle(raw: String): String = raw.lowercase(Locale.ROOT)
        .substringBefore(" | ")
        .replace(NOISE, " ")
        .replace(PUNCTUATION, " ")
        .replace(SPACES, " ")
        .trim()

    /** The cast behind a credit, split out so billing order stops mattering. */
    fun artistSet(raw: String): Set<String> = raw.lowercase(Locale.ROOT)
        .replace(TOPIC, " ")
        .split(
            ",", "&", "·", "•", ";", "、", "/", "×", " feat", " ft.", " ft ", " x ", " with ", " and ",
            // "艾薇 和 吳霏" — Chinese credits join with 和 / 與 / 及, spaced from the names.
            " 和 ", " 與 ", " 及 ",
        )
        .map { it.replace(PUNCTUATION, " ").replace(SPACES, " ").trim() }
        .filter { it.isNotEmpty() }
        .toSet()

    private val NOISE = Regex(
        """\((?:official|lyric|lyrics|lyrical|audio|video|visuali[sz]er|full song|hd|4k)[^)]*\)""" +
            """|\(from[^)]*\)""" +
            """|\[[^]]*]""" +
            """|\b(?:official (?:video|audio|music video)|lyrical video|full video|4k video)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val TOPIC = Regex("""\s*-\s*topic\b""", RegexOption.IGNORE_CASE)
    private val PUNCTUATION = Regex("""[^\p{L}\p{N}]+""")
    private val SPACES = Regex("""\s+""")
}
