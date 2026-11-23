package com.music.bitchord.playback

import androidx.media3.common.MediaMetadata

/** The two lines Android Auto shows while a lyric line is playing. */
internal fun carLines(title: String?, artist: String?, line: String, onTitle: Boolean): Pair<String, String> {
    val lyric = "♪ $line"
    val song = listOfNotNull(title, artist).filter { it.isNotBlank() }.joinToString(" · ")
    // Only two lines exist, so the title and artist share one and the lyric
    // always gets a line of its own.
    return if (onTitle) lyric to song else song to lyric
}

internal fun carMetadata(base: MediaMetadata, line: String, onTitle: Boolean): MediaMetadata {
    val (top, bottom) = carLines(base.title?.toString(), base.artist?.toString(), line, onTitle)
    return base.buildUpon()
        .setTitle(top)
        .setDisplayTitle(top)
        .setArtist(bottom)
        .setSubtitle(bottom)
        .build()
}
