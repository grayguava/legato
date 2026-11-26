package com.grayguava.legato.source.covers.sources

import com.grayguava.legato.source.covers.CoverRequest
import com.grayguava.legato.source.covers.CoverAnswer
import com.grayguava.legato.source.covers.CoverResolver
import com.grayguava.legato.source.songinfo.AppleTrackMetadataRepository

/**
 * Apple Music's cover for the exact recording, as its catalogue lookup
 * matched it. Apple's masters run to 3000px, the sharpest of the catalogues,
 * and its match is the strictest: title, credited artist, length and album
 * all have to agree.
 */
internal object AppleCover : CoverResolver {
    override val name: String = "Apple"

    override suspend fun find(request: CoverRequest): CoverAnswer {
        // The lookup is keyed on the length, so it is only asked once that is known.
        if (request.durationMs <= 0) return CoverAnswer.None
        val url = AppleTrackMetadataRepository.release(request.song, request.durationMs)?.artworkUrl
        return if (url != null) CoverAnswer.Found(url) else CoverAnswer.None
    }
}
