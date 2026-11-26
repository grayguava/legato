package com.grayguava.legato.source.covers

import java.util.Locale

/**
 * A title, artist or album reduced to what actually decides a match:
 * lowercase letters and digits, nothing else. Punctuation and spacing differ
 * between every catalogue's spelling of the same name, and none of it is a
 * difference worth missing a cover over.
 */
internal fun normalizeForMatch(s: String): String = s.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
