package com.grayguava.legato.audio.playback

import com.grayguava.legato.base.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class MediaItemsTest {
    private fun song(thumbnailUrl: String?) = Song("abcdefghijk", "Song", "Artist", thumbnailUrl)

    @Test fun theNotificationGetsTheCoverAtItsOwnSize() {
        val art = song("https://i.ytimg.com/vi/abcdefghijk/hqdefault.jpg?w60-h60").toMediaItem()
            .mediaMetadata.artworkUri
        assertEquals("https", art?.scheme)
        assertTrue(art.toString().endsWith("w544-h544"))
    }

    @Test fun aSongWithoutACoverSetsNoArtwork() {
        assertNull(song(null).toMediaItem().mediaMetadata.artworkUri)
    }

    @Test fun aLocalPathIsNotOfferedAsArtwork() {
        assertNull(song("/storage/emulated/0/Music/cover.jpg").toMediaItem().mediaMetadata.artworkUri)
    }
}
