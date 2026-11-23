package com.music.bitchord.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class CarLyricsMetadataTest {
    @Test
    fun lyricGetsSecondLineByDefault() {
        assertEquals(
            "Love Me Like This · NMIXX" to "♪ 그냥 날 사랑해줘",
            carLines("Love Me Like This", "NMIXX", "그냥 날 사랑해줘", onTitle = false),
        )
    }

    @Test
    fun lyricOnTitleKeepsTitleAndArtistBelow() {
        assertEquals(
            "♪ 그냥 날 사랑해줘" to "Love Me Like This · NMIXX",
            carLines("Love Me Like This", "NMIXX", "그냥 날 사랑해줘", onTitle = true),
        )
    }

    @Test
    fun missingArtistStillShowsLyric() {
        assertEquals("Song" to "♪ la", carLines("Song", null, "la", onTitle = false))
    }
}
