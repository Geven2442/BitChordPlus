package com.music.bitchord

import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.ui.HomeRefreshMix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRefreshMixTest {
    private val original = (1..20).map { ShelfItem("t$it", "a$it", null, "o$it", null) }
    private val radio = (1..45).map { Song(videoId = "r$it", title = "r$it", artist = "x", thumbnailUrl = null) }

    @Test
    fun keepsSizeAndHasNoDuplicates() {
        val mixed = HomeRefreshMix.mixQuickPicks(original, radio)
        assertEquals(original.size, mixed.size)
        assertEquals(mixed.size, mixed.map { it.videoId }.toSet().size)
    }

    @Test
    fun bringsInNewSongsEveryPull() {
        val mixed = HomeRefreshMix.mixQuickPicks(original, radio)
        assertTrue(mixed.count { it.videoId!!.startsWith("r") } >= 10)
        val pulls = (1..5).map { HomeRefreshMix.mixQuickPicks(original, radio).map { it.videoId } }
        assertTrue("five pulls should not all be identical", pulls.toSet().size > 1)
    }

    @Test
    fun withoutRadioStillReshuffles() {
        val pulls = (1..5).map { HomeRefreshMix.mixQuickPicks(original, emptyList()).map { it.videoId } }
        pulls.forEach { assertEquals(original.map { it.videoId }.toSet(), it.toSet()) }
        assertNotEquals(1, pulls.toSet().size)
    }
}
