package com.grayguava.legato.source.covers

import com.grayguava.legato.base.model.PLAYER_ART_PX
import com.grayguava.legato.base.model.Song
import com.grayguava.legato.base.model.artworkAt

/**
 * One high-resolution image per playing track, even if metadata arrives again
 * later. The one exception is [better]: a sharper catalogue cover found for
 * this very track while it plays, which replaces the first image once.
 */
public class PlaybackArtwork(
    // The cover to start with, when it is better than the song's own: one already
    // saved for this track. A local file's cover stays, as it's what was kept with
    // the file, so a catalogue cover never replaces it.
    private val preferred: (Song) -> String? = { null },
) {
    private var id: String? = null
    private var url: String? = null
    private var upgraded = false
    public fun choose(song: Song, better: String? = null): String? {
        if (id != song.videoId || url == null) {
            id = song.videoId
            upgraded = false
            url = (preferred(song) ?: song.thumbnailUrl).artworkAt(PLAYER_ART_PX)
        }
        if (better != null && !upgraded && url?.startsWith("file:") != true) {
            upgraded = true
            url = better.artworkAt(PLAYER_ART_PX)
        }
        return url
    }
    public fun clear() { id = null; url = null; upgraded = false }
}
