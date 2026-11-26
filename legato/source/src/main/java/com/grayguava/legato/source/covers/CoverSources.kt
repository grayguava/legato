package com.grayguava.legato.source.covers

import com.grayguava.legato.settings.CoverSource
import com.grayguava.legato.source.covers.sources.AppleCover
import com.grayguava.legato.source.covers.sources.CoverArtArchiveCover
import com.grayguava.legato.source.covers.sources.DeezerCover
import com.grayguava.legato.source.covers.sources.YoutubeMusicCover

/**
 * Every cover catalogue there is, by the source it answers for.
 *
 * The one place a catalogue is registered. Adding one is its own file plus a
 * line here; a source missing from this map fails
 * [com.grayguava.legato.source.covers.CoverSourcesTest] rather than silently
 * never being asked.
 */
internal object CoverSources {
    private val bySource: Map<CoverSource, CoverResolver> = mapOf(
        CoverSource.APPLE to AppleCover,
        CoverSource.YOUTUBE_MUSIC to YoutubeMusicCover,
        CoverSource.DEEZER to DeezerCover,
        CoverSource.COVER_ART_ARCHIVE to CoverArtArchiveCover,
    )

    fun of(source: CoverSource): CoverResolver =
        bySource[source] ?: error("No cover resolver registered for $source")

    /** The registered sources, for the test that keeps this map complete. */
    val registered: Set<CoverSource> get() = bySource.keys
}
