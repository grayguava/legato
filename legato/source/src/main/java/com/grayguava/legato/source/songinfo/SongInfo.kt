package com.grayguava.legato.source.songinfo

/** One credit line from a song's credits, e.g. "Written by" → Jeff Bass, Luis Resto. */
public data class SongCredit(public val role: String, public val names: List<String>) {
    public val text: String get() = names.joinToString(", ")
}

/** A provider the song-info sheet reads; a failure is retried on its own. */
public enum class SongInfoSource(public val label: String) {
    YOUTUBE("YouTube Music"),
    APPLE("Apple Music"),
    MUSICBRAINZ("MusicBrainz"),
}

/**
 * What the song-info sheet knows about one track. Fields that are null or empty
 * are left out of the sheet rather than shown as "not available".
 */
public data class SongInfo(
    public val album: String? = null,
    public val lengthMs: Long = 0,
    public val releaseDate: String? = null,
    public val plays: String? = null,
    public val credits: List<SongCredit> = emptyList(),
    /** Providers that failed and whose fields are still missing. */
    public val failed: Set<SongInfoSource> = emptySet(),
) {
    /** The credit line for [role], matched without regard to case. */
    public fun credit(role: String): SongCredit? =
        credits.firstOrNull { it.role.equals(role, ignoreCase = true) }
}

/** The credit roles the sheet shows, in the order it shows them. */
public val SONG_INFO_ROLES: List<String> = listOf(
    "Performed by",
    "Written by",
    "Produced by",
    "Music metadata provided by",
)

/**
 * YouTube-style short form of a raw count: 950 → "950", 1234 → "1.2K",
 * 100000 → "100K", 2345678 → "2.3M". Truncated rather than rounded, as YouTube
 * does, so 999999 never shows as "1000K". Text that isn't a plain number is
 * returned unchanged.
 */
public fun compactCount(raw: String): String {
    val n = raw.toLongOrNull()?.takeIf { it >= 0 } ?: return raw
    val (unit, suffix) = when {
        n >= 1_000_000_000 -> 1_000_000_000L to "B"
        n >= 1_000_000 -> 1_000_000L to "M"
        n >= 1_000 -> 1_000L to "K"
        else -> return n.toString()
    }
    val whole = n / unit
    if (whole >= 10) return "$whole$suffix"
    val tenth = n % unit * 10 / unit
    val separator = java.text.DecimalFormatSymbols.getInstance().decimalSeparator
    return if (tenth == 0L) "$whole$suffix" else "$whole$separator$tenth$suffix"
}
