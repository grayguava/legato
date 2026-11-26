package com.grayguava.legato.source.covers

/**
 * What one catalogue had to say about a song.
 *
 * Three answers rather than a nullable URL, because "no match here" and "the
 * song's own cover is the answer" are not the same thing: the first lets the
 * next source be asked, the second stops the search where it is.
 */
internal sealed interface CoverAnswer {
    /** A cover to show instead of the one the song came with. */
    data class Found(val url: String) : CoverAnswer

    /** This catalogue's answer is the art already on screen. */
    data object AlreadyThere : CoverAnswer

    /** No confident match here. */
    data object None : CoverAnswer
}
