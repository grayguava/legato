package com.grayguava.legato.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class CoverSettingsTest {
    @Test
    fun youtubeMusicSitsBetweenAppleAndTheSmallerCatalogues() {
        // The whole rule in one assertion: Apple may replace the song's own art,
        // Deezer and the archive may not.
        assertEquals(
            listOf(CoverSource.APPLE, CoverSource.YOUTUBE_MUSIC, CoverSource.DEEZER, CoverSource.COVER_ART_ARCHIVE),
            CoverSettings().ordered.map { it.source },
        )
    }

    @Test
    fun savedOrderIsKeptAndDisabledSourcesStayInPlace() {
        val s = CoverSettings(sources = listOf(CoverSourceEntry(CoverSource.DEEZER), CoverSourceEntry(CoverSource.APPLE, enabled = false)))
        assertEquals(
            listOf(CoverSource.DEEZER, CoverSource.APPLE, CoverSource.YOUTUBE_MUSIC, CoverSource.COVER_ART_ARCHIVE),
            s.ordered.map { it.source },
        )
        assertEquals(false, s.ordered[1].enabled)
    }

    @Test
    fun aSourceAddedLaterJoinsTheEndOfASavedOrder() {
        val s = CoverSettings(sources = listOf(CoverSourceEntry(CoverSource.APPLE)))
        assertEquals(
            listOf(CoverSource.APPLE, CoverSource.YOUTUBE_MUSIC, CoverSource.DEEZER, CoverSource.COVER_ART_ARCHIVE),
            s.ordered.map { it.source },
        )
    }

    @Test
    fun positionFollowsTheOrder() {
        val s = CoverSettings()
        assertEquals(0, s.positionOf(CoverSource.APPLE))
        assertEquals(1, s.positionOf(CoverSource.YOUTUBE_MUSIC))
        assertEquals(3, s.positionOf(CoverSource.COVER_ART_ARCHIVE))
    }
}
