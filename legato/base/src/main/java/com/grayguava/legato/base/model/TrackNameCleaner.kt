package com.grayguava.legato.base.model

import java.util.Locale

/**
 * Turns a YouTube upload's title and channel into something a lyrics database
 * will match: "The Weeknd - Blinding Lights (Official Video)" by
 * "TheWeekndVEVO" becomes "Blinding Lights" by "The Weeknd".
 */
public object TrackNameCleaner {
    private val NOISE = Regex(
        """\s*[(\[](?:[^)\]]*\b(?:official|lyrics?|lyric video|audio|video|visuali[sz]er|mv|m/v|hd|hq|4k|remaster(?:ed)?|explicit|clean|color coded|deluxe|bonus|mono|stereo|radio edit|(?:single|album) version)\b[^)\]]*)[)\]]""",
        RegexOption.IGNORE_CASE,
    )
    private val FEAT = Regex("""\s*[(\[]?\s*\b(?:feat\.?|ft\.?|featuring)\s.*$""", RegexOption.IGNORE_CASE)
    /** A release note after a dash, as catalogues write them: "Song - 2011 Remaster", "Song - Radio Edit". */
    private val RELEASE_SUFFIX = Regex(
        """\s+[-–—]\s+(?:(?:\d{4}\s+)?remaster(?:ed)?(?:\s+(?:version|\d{4}))?|radio edit|(?:single|album|mono|stereo) version|mono|stereo|bonus track|deluxe(?:\s+edition)?)\s*$""",
        RegexOption.IGNORE_CASE,
    )
    private val CHANNEL_SUFFIX = Regex("""\s*(?:VEVO|- Topic|Official|Music)$""", RegexOption.IGNORE_CASE)

    public data class Cleaned(val title: String, val artist: String)

    public fun clean(title: String, artist: String): Cleaned {
        var t = RELEASE_SUFFIX.replace(NOISE.replace(title, ""), "")
        var a = CHANNEL_SUFFIX.replace(artist.trim(), "").trim()
        // "Artist - Title" uploads: take the artist from the title when it
        // agrees with the channel (which is often the same name, unspaced).
        val dash = Regex("""\s+[-–—]\s+""").find(t)
        if (dash != null) {
            val left = t.substring(0, dash.range.first).trim()
            val right = t.substring(dash.range.last + 1).trim()
            val leftKey = left.lowercase(Locale.ROOT).replace(" ", "")
            val artistKey = a.lowercase(Locale.ROOT).replace(" ", "")
            if (a.isEmpty() || leftKey.contains(artistKey) || artistKey.contains(leftKey)) {
                a = left
                t = right
            }
        }
        t = FEAT.replace(t, "")
        a = FEAT.replace(a, "").substringBefore(",").substringBefore(" & ").trim()
        t = t.replace(Regex("""\s*["“”]\s*"""), " ").replace(Regex("\\s+"), " ").trim()
        return Cleaned(t.ifEmpty { title.trim() }, a.ifEmpty { artist.trim() })
    }
}
