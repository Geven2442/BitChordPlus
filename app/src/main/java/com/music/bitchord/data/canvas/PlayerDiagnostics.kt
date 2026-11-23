package com.music.bitchord.data.canvas

import android.content.Context
import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.durationMillis
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.replay.year.BestPart
import com.music.bitchord.ui.replay.year.BestParts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A plain-text report of why the playing song has, or hasn't, an animated
 * cover and a Best Part button: each source asked on its own, outside every
 * cache, with the answer or the error it gave. Contains no credentials.
 */
object PlayerDiagnostics {

    suspend fun report(context: Context, song: Song?, durationMs: Long = 0L): String = withContext(Dispatchers.IO) {
        if (song == null) return@withContext "沒有正在播放的歌。先播一首歌再回來按。"
        val out = StringBuilder()
        out.appendLine("${song.title} — ${song.artist}")
        out.appendLine("videoId: ${song.videoId}  (${if (BestParts.isYouTubeId(song.videoId)) "YouTube ID" else "不是 YouTube ID"})")
        out.appendLine("專輯: ${song.albumName ?: "（未知）"}")
        out.appendLine()

        out.appendLine("【Best Part】")
        out.appendLine("按鈕開關: ${if (AppSettings.showBestPart.value) "開" else "關"}　同步歌詞: ${if (AppSettings.syncedLyrics.value) "開" else "關"}")
        val heatLine = try {
            val answer = withTimeoutOrNull(10_000L) { Innertube.webNext(song.videoId) }
            when {
                answer == null -> "YouTube 10 秒內沒有回應"
                else -> {
                    val heat = BestPart.parseHeatmap(answer)
                    if (heat == null) "YouTube 有回應，但這支影片沒有「最多人重播」曲線"
                    else "有曲線（${heat.intensity.size} 段）"
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "失敗：${e.javaClass.simpleName} ${e.message?.take(120)}"
        }
        out.appendLine("重播曲線: $heatLine")
        out.appendLine("歌長: ${if (durationMs > 0) "${durationMs / 1000}s（播放器提供）" else "未知"}　歌曲資料自帶: ${song.durationText ?: "無"}")
        val sized = if (song.durationText.durationMillis() <= 0 && durationMs > 0) {
            song.copy(durationText = "%d:%02d".format(durationMs / 60000, durationMs / 1000 % 60))
        } else song
        val start = runCatching { BestParts.startFor(context, sized) }
        start.onSuccess { out.appendLine("結果: 來源=${it.source}　位置=${it.startMs / 1000}s") }
            .onFailure { out.appendLine("結果: 失敗 ${it.javaClass.simpleName} ${it.message?.take(120)}") }
        out.appendLine("（來源 FALLBACK = 沒有資料，按鈕不會顯示）")
        out.appendLine()

        out.appendLine("【動態封面來源】")
        val title = song.title
        val artist = song.artist
        val album = song.albumName
        suspend fun probe(name: String, call: suspend () -> CanvasArtwork?) {
            val t0 = System.currentTimeMillis()
            val line = try {
                val hit = withTimeoutOrNull(25_000L) { call() }
                val ms = System.currentTimeMillis() - t0
                when {
                    hit == null -> "沒有結果（${ms}ms）"
                    !hit.matches(title, artist, album) -> "找到但對不上這首歌（${ms}ms）"
                    else -> "有（${ms}ms）"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "失敗：${e.javaClass.simpleName} ${e.message?.take(100)}"
            }
            out.appendLine("$name: $line")
        }
        probe("Apple") { AppleMusicCanvas.search(title, artist, album) }
        probe("Tidal") { TidalCanvas.search(title, artist, album) }
        probe("社群") { CommunityCanvas.search(title, artist, album) }
        if (AppSettings.disableSpotifyCanvas.value) out.appendLine("Spotify: 已停用")
        else probe("Spotify") { SpotifyCanvas.search(title, artist, album) }
        out.toString().trimEnd()
    }
}
