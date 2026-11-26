package com.grayguava.legato.source.covers

/**
 * One catalogue a cover can come from.
 *
 * A source owns everything about its own answer: the request it makes, the
 * rules that decide a match, the size it asks for. Nothing outside it knows
 * those, so adding a catalogue is a file of its own and a line in
 * [CoverSources], and removing one is deleting both.
 *
 * A source that couldn't be reached throws rather than answering [CoverAnswer.None],
 * so its silence is never mistaken for "no cover exists". */
internal interface CoverResolver {
    /** Named in the log when this source fails, and nothing else. */
    val name: String

    suspend fun find(request: CoverRequest): CoverAnswer
}
