package com.music.bitchord.ui

import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.ShelfLayout
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.WatchTarget
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.Locale

/**
 * What a manual pull does to the Home feed, YouTube Music style: the first
 * song list (Quick picks) is rebuilt from radios of a few songs drawn at
 * random from recent listening, mixed with what YouTube sent, and the card
 * shelves come back in a new order. Each pull therefore looks different even
 * when YouTube returns an identical feed.
 */
object HomeRefreshMix {
    private const val SEED_POOL = 30
    private const val SEEDS = 3
    private const val RADIO_TAKE = 15

    /** Shelves that are a record of the past rather than suggestions; left as they are. */
    private val keepAsIs = listOf("listen again", "recently played", "再次收聽", "最近播放", "重新聆聽", "再听一遍", "最近播放的")

    suspend fun refresh(shelves: List<HomeShelf>): List<HomeShelf> {
        val quickIndex = shelves.indexOfFirst { it.layout == ShelfLayout.LIST && !isKept(it) }
        val radio = if (quickIndex >= 0) radioSongs() else emptyList()
        return shelves.mapIndexed { index, shelf ->
            when {
                index == quickIndex -> shelf.copy(items = mixQuickPicks(shelf.items, radio))
                isKept(shelf) -> shelf
                shelf.layout == ShelfLayout.CARDS -> shelf.copy(items = shelf.items.shuffled())
                else -> shelf
            }
        }
    }

    internal fun mixQuickPicks(original: List<ShelfItem>, radio: List<Song>): List<ShelfItem> {
        val size = original.size.coerceAtLeast(12)
        val fromRadio = radio.asSequence()
            .filterNot { it.isVideo }
            .map { ShelfItem(it.title, it.artist, it.thumbnailUrl, it.videoId, null) }
            .distinctBy { it.videoId }
            .toList()
            .shuffled()
        val keepOriginal = original.shuffled().take(size / 3)
        val seen = HashSet<String?>()
        // The same song comes back under more than one id — the single and the
        // album cut, or YouTube's own copy and a radio's — so a row is also
        // dropped when a title and a credited name are already on the shelf.
        val titles = HashMap<String, MutableList<Set<String>>>()
        return (keepOriginal + fromRadio + original.shuffled())
            .filter { it.videoId == null || seen.add(it.videoId) }
            .filter { item ->
                val title = com.music.bitchord.playback.QueueBuilder.titleKey(item.title)
                if (title.isEmpty()) return@filter true
                val credit = com.music.bitchord.playback.QueueBuilder.artistSet(item.subtitle)
                val held = titles.getOrPut(title) { mutableListOf() }
                if (held.any { it.isEmpty() || credit.isEmpty() || it.any(credit::contains) }) return@filter false
                held += credit
                true
            }
            .take(size)
            .shuffled()
    }

    private fun isKept(shelf: HomeShelf): Boolean {
        val t = shelf.title.lowercase(Locale.ROOT)
        return keepAsIs.any { t.contains(it) }
    }

    private suspend fun radioSongs(): List<Song> = coroutineScope {
        val history = YtMusicRepository.recentHistory().getOrNull().orEmpty()
        val seeds = history.take(SEED_POOL).distinctBy { it.videoId }.shuffled().take(SEEDS)
        seeds.map { seed ->
            async {
                YtMusicRepository.watchQueue(WatchTarget(playlistId = "RDAMVM${seed.videoId}", videoId = seed.videoId))
                    .getOrNull().orEmpty()
                    .filter { it.videoId != seed.videoId }
                    .shuffled()
                    .take(RADIO_TAKE)
            }
        }.awaitAll().flatten()
    }
}
