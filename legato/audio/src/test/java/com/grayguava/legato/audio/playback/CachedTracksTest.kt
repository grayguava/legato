package com.grayguava.legato.audio.playback

import android.app.Application
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import com.grayguava.legato.base.model.Song
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CachedTracksTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    @Before
    fun emptyTheCache() {
        AudioCache.clear(context)
    }

    /** Puts [bytes] of audio in the cache under [key], as a completed fetch would. */
    private fun keep(key: String, bytes: Int = 2048) {
        val cache = AudioCache.get(context)
        // What CacheWriter does: take the range, write the file, commit it.
        cache.startReadWriteNonBlocking(key, 0, bytes.toLong())
        val file = cache.startFile(key, 0, bytes.toLong())
        file.writeBytes(ByteArray(bytes))
        cache.commitFile(file, bytes.toLong())
    }

    @Test
    fun aKeptTrackIsListedWithItsName() = runBlocking {
        keep("aaaaaaaaaa1")
        AudioCache.note(context, Song("aaaaaaaaaa1", "Blinding Lights", "The Weeknd", null))
        val tracks = AudioCache.tracks(context)
        assertEquals(listOf("aaaaaaaaaa1"), tracks.map { it.song.videoId })
        assertEquals("Blinding Lights", tracks.first().song.title)
        assertEquals("The Weeknd", tracks.first().song.artist)
        assertEquals(2048L, tracks.first().bytes)
    }

    @Test
    fun bothCopiesOfATrackAreOneRow() = runBlocking {
        keep("dddddddd1", 2048)
        keep("dddddddd1:hq", 4096)
        AudioCache.note(context, Song("dddddddd1", "Blinding Lights", "The Weeknd", null))
        val tracks = AudioCache.tracks(context)
        assertEquals(1, tracks.size)
        assertEquals(2048L + 4096L, tracks.first().bytes)
    }

    @Test
    fun aTrackWithNoNameIsLeftOut() = runBlocking {
        // Cached before the folder existed, and never played since: nothing to show it as.
        keep("eeeeeeeeee2")
        assertTrue(AudioCache.tracks(context).isEmpty())
    }

    @Test
    fun removingATrackDropsItFromTheList() = runBlocking {
        keep("fffffffff3")
        AudioCache.note(context, Song("fffffffff3", "Third", "Three", null))
        assertEquals(1, AudioCache.tracks(context).size)
        AudioCache.remove(context, "fffffffff3")
        assertTrue(AudioCache.tracks(context).isEmpty())
    }

    @Test
    fun theListIsMostRecentlyTouchedFirst() = runBlocking {
        keep("gggggg1", 2048)
        AudioCache.note(context, Song("gggggg1", "First", "One", null))
        Thread.sleep(20)
        keep("hhhhhh2", 4096)
        AudioCache.note(context, Song("hhhhhh2", "Second", "Two", null))
        assertEquals(listOf("hhhhhh2", "gggggg1"), AudioCache.tracks(context).map { it.song.videoId })
    }
}
