package com.music.bitchord.data.canvas

import com.music.bitchord.data.model.ArtistRef
import com.music.bitchord.data.model.DetailPage
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UiState
import org.junit.Assert.assertEquals
import org.junit.Test

class AppleAliasesTest {
    private fun page(songs: List<Song>) = DetailPage(
        browseId = "UC1", title = "格蕾西·亞柏拉罕", subtitle = "", thumbnailUrl = null,
        songs = UiState.Success(songs),
    )

    @Test
    fun usesTheArtistsOwnCreditAsAlias() {
        val songs = listOf(
            Song("a", "That's So True", "Gracie Abrams", null, artistId = "UC1",
                artists = listOf(ArtistRef("Gracie Abrams", "UC1"))),
            Song("b", "Other", "Somebody", null, artistId = "UC2",
                artists = listOf(ArtistRef("Somebody", "UC2"))),
        )
        assertEquals(listOf("Gracie Abrams"), page(songs).appleAliases())
    }

    @Test
    fun fallsBackToFirstNameOfLeadCredit() {
        val songs = listOf(Song("a", "x", "Gracie Abrams, GALI", null, artistId = "UC1"))
        assertEquals(listOf("Gracie Abrams"), page(songs).appleAliases())
    }

    @Test
    fun noAliasWhenNameAlreadyMatchesTitle() {
        val p = page(listOf(Song("a", "x", "格蕾西·亞柏拉罕", null, artistId = "UC1")))
        assertEquals(emptyList<String>(), p.appleAliases())
    }
}
