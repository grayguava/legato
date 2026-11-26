package com.grayguava.legato.source

import com.grayguava.legato.source.local.LocalMusic

/**
 * Whether a song id is a YouTube video id, as opposed to a file on this
 * phone, a radio station, or a liked/playlist song from elsewhere. Only YouTube
 * songs can be resolved, downloaded, upgraded, shared, followed with radio or
 * measured for loudness by YouTube.
 */
public fun isYouTubeId(id: String): Boolean = !LocalMusic.isLocal(id)
