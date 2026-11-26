package com.grayguava.legato.source.covers.sources

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverArtArchiveCoverTest {
    /** A real MusicBrainz answer for "Blinding Lights" by The Weeknd. */
    private val response = Json.parseToJsonElement(
        checkNotNull(javaClass.classLoader?.getResource("musicbrainz-blinding-lights.json")).readText(),
    ).jsonObject

    @Test
    fun officialSingleComesBeforeCompilations() {
        val groups = CoverArtArchiveCover.releaseGroups(response, "The Weeknd")
        // The "Blinding Lights" single's release group.
        assertEquals("9b905cac-df8d-4942-bc4e-4fe0f2ed635f", groups.first())
    }

    @Test
    fun aCompilationsCoverIsNeverTheSongs() {
        // MusicBrainz on 2026-10-24: the best-scoring "Desi Kalakaar" is its copy on the
        // "World Music Day" compilation, whose cover is a stock picture.
        val desi = Json.parseToJsonElement(checkNotNull(javaClass.classLoader?.getResource("musicbrainz-desi-kalakaar.json")).readText()).jsonObject
        val album = "1cac17ba-b59d-4835-94fd-5d4a6fb470ea"
        assertEquals(listOf(album), CoverArtArchiveCover.releaseGroups(desi, "Yo Yo Honey Singh"))
        assertEquals(listOf(album), CoverArtArchiveCover.releaseGroups(desi, "Yo Yo Honey Singh", "Desi Kalakaar", 255_000))
        // Another album named, or another length: no guess.
        assertTrue(CoverArtArchiveCover.releaseGroups(desi, "Yo Yo Honey Singh", "Blue Eyes").isEmpty())
        assertTrue(CoverArtArchiveCover.releaseGroups(desi, "Yo Yo Honey Singh", null, 300_000).isEmpty())
    }

    @Test
    fun anotherArtistMatchesNothing() {
        assertTrue(CoverArtArchiveCover.releaseGroups(response, "Rick Astley").isEmpty())
    }
}
