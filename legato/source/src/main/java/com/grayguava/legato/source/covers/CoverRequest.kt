package com.grayguava.legato.source.covers

import com.grayguava.legato.base.model.Song

/**
 * One cover lookup: the song as the player knows it, with the parts every
 * catalogue matches on already cleaned and reduced to one artist.
 *
 * [title] and [artist] are what the sources search with, not what the screen
 * shows: a title with its "(Remastered 2011)" taken off matches, and a credit
 * of five names matches as its first.
 */
internal data class CoverRequest(
    val song: Song,
    val title: String,
    val artist: String,
    val album: String?,
    /** The player's exact length, or 0 when it isn't known yet. */
    val durationMs: Long,
)
