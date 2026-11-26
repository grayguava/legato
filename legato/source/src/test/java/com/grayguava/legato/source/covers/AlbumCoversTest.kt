package com.grayguava.legato.source.covers

import com.grayguava.legato.base.model.Song
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumCoversTest {
    @Test
    fun everyTrackButARadioStationIsLookedUp() {
        assertTrue(AlbumCovers.wants(Song("a", "t", "x", "https://i.ytimg.com/vi/a/hqdefault.jpg")))
        assertTrue(AlbumCovers.wants(Song("local:1", "t", "x", null)))
        // MediaStore's address for a local file's art, which may hold nothing.
        assertTrue(AlbumCovers.wants(Song("local:1", "t", "x", "content://media/external/audio/albumart/7")))
        // YouTube Music's own covers are smaller than Apple's masters, so they're upgraded too.
        assertTrue(AlbumCovers.wants(Song("a", "t", "x", "https://lh3.googleusercontent.com/abc=w544-h544")))
    }
}
