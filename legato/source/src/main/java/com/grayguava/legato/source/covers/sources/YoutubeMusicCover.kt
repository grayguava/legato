package com.grayguava.legato.source.covers.sources

import com.grayguava.legato.source.covers.CoverAnswer
import com.grayguava.legato.source.covers.CoverRequest
import com.grayguava.legato.source.covers.CoverResolver

/**
 * The song's own cover, as YouTube Music serves it.
 *
 * It answers [CoverAnswer.AlreadyThere] rather than a URL, because there is
 * nothing to fetch or replace: it stands in the order as the point where
 * "keep what the song came with" is decided. A catalogue above it may
 * replace that cover; one below it is never reached.
 */
internal object YoutubeMusicCover : CoverResolver {
    override val name: String = "YouTube Music"

    override suspend fun find(request: CoverRequest): CoverAnswer = CoverAnswer.AlreadyThere
}
