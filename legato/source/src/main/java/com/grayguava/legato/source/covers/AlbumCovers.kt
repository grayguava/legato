package com.grayguava.legato.source.covers

import android.content.ContentResolver
import android.content.Context
import androidx.core.net.toUri
import com.grayguava.legato.base.DebugLog as Log
import com.grayguava.legato.base.model.Song
import com.grayguava.legato.base.model.TrackNameCleaner
import com.grayguava.legato.settings.AppSettings
import com.grayguava.legato.settings.CoverSourceEntry
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Sharp album covers for the playing song, from the catalogues the listener
 * has switched on and in their order (Apple's masters up to 3000px, Deezer's
 * and the Cover Art Archive's up to 1200px, YouTube Music's own).
 *
 * The order is the whole rule: the first source with an answer wins, and
 * YouTube Music answers "keep what the song came with". So a catalogue placed
 * above it may replace the cover, and one placed below it never will, which
 * is what keeps a 1200px re-encode from displacing a sharper master.
 *
 * The matching rules belong to the sources, not here: this only decides what
 * to ask, in what order, and what to remember. Answers, including "no cover"
 * and "nothing better", are kept on disk per source, so re-ordering the list
 * asks only the sources that haven't answered yet. A source that couldn't be
 * reached isn't an answer and is asked again next time.
 */
public object AlbumCovers {
    private const val TAG = "AlbumCovers"
    private const val MAX_ENTRIES = 3_000
    /** A source's answer, stored per song: no match. */
    private const val NONE = ""
    /** A source's answer, stored per song: the art the song already has. */
    private const val THERE = "~"
    private const val SEP = "\u0001"
    private const val LOCAL_ART = "content://media/external/audio/albumart"

    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cache = ConcurrentHashMap<String, String>()
    private var file: File? = null
    /** Only for opening local files' album art; a resolver rather than a Context. */
    private var resolver: ContentResolver? = null

    public fun init(context: Context) {
        resolver = context.applicationContext.contentResolver
        // Earlier files held a single answer per song from a fixed order of catalogues, with
        // covers from compilations the song is also on; every answer is looked up again.
        File(context.filesDir, "album-covers.json").delete()
        File(context.filesDir, "album-covers-v2.json").delete()
        File(context.filesDir, "album-covers-v3.json").delete()
        val f = File(context.filesDir, "cover-cache-v1.json")
        file = f
        scope.launch {
            runCatching { json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), f.readText()) }
                .getOrNull()?.let(cache::putAll)
        }
    }

    /**
     * Whether [song] is a track a catalogue could have a cover for: anything
     * but a radio station. A local file's album-art address is checked in
     * [coverFor] (MediaStore gives one even when the file has no art).
     */
    public fun wants(song: Song): Boolean = true

    private fun keyOf(song: Song): String {
        val cleaned = TrackNameCleaner.clean(song.title, song.artist)
        return listOf(primaryArtist(cleaned.artist), cleaned.title, song.albumName.orEmpty()).joinToString("|") { it.lowercase(Locale.ROOT) }
    }

    private fun cacheKey(songKey: String, entry: CoverSourceEntry): String = songKey + SEP + entry.source.name

    private fun entries(): List<CoverSourceEntry> = AppSettings.covers.value.ordered.filter { it.enabled }

    /**
     * A previously resolved cover, ready before the player displays anything:
     * the first answer in the current order that has one.
     */
    public fun cached(song: Song): String? {
        if (!wants(song)) return null
        val key = keyOf(song)
        for (entry in entries()) {
            when (val answer = cache[cacheKey(key, entry)]) {
                null -> return null
                THERE -> return null
                NONE -> continue
                else -> return answer
            }
        }
        return null
    }

    /** A local file's album art, if the phone actually has an image for it. */
    private suspend fun hasLocalArt(uri: String): Boolean = withContext(Dispatchers.IO) {
        val resolver = resolver ?: return@withContext true
        runCatching { resolver.openInputStream(uri.toUri())?.use { it.read() >= 0 } ?: false }.getOrDefault(false)
    }

    /**
     * The best cover for [song], or null when the song's own art is the
     * answer. [durationMs] is the player's exact length; sources that match
     * on the length aren't asked until it is known.
     */
    public suspend fun coverFor(song: Song, durationMs: Long = 0): String? {
        if (!wants(song) || song.artist.isBlank() || song.title.isBlank()) return null
        val thumbnail = song.thumbnailUrl
        if (thumbnail?.startsWith(LOCAL_ART) == true && hasLocalArt(thumbnail)) return null
        val key = keyOf(song)
        val cleaned = TrackNameCleaner.clean(song.title, song.artist)
        val request = CoverRequest(song, cleaned.title, primaryArtist(cleaned.artist), song.albumName, durationMs)
        // A source that couldn't be reached isn't a "no": its silence is asked again next time.
        var unreachable = false
        for (entry in entries()) {
            val k = cacheKey(key, entry)
            val known = cache[k]
            if (known != null) {
                when (known) {
                    THERE -> return null
                    NONE -> continue
                    else -> return known
                }
            }
            val answer = runCatching { CoverSources.of(entry.source).find(request) }
                .onFailure { unreachable = true; Log.w(TAG, "${entry.source.label} lookup failed for $key", it) }
                .getOrNull() ?: continue
            when (answer) {
                is CoverAnswer.Found -> {
                    cache[k] = answer.url
                    save()
                    return answer.url
                }
                CoverAnswer.AlreadyThere -> {
                    // Nothing above this answered, so there is nothing better to look for - unless
                    // one of them merely couldn't be reached, in which case it is asked again.
                    if (!unreachable) {
                        cache[k] = THERE
                        save()
                    }
                    return null
                }
                CoverAnswer.None -> {
                    cache[k] = NONE
                    save()
                }
            }
        }
        return null
    }

    private fun save() {
        val f = file ?: return
        scope.launch {
            runCatching {
                if (cache.size > MAX_ENTRIES) cache.keys.take(cache.size - MAX_ENTRIES).forEach(cache::remove)
                val tmp = File(f.parentFile, "${f.name}.tmp")
                tmp.writeText(json.encodeToString(MapSerializer(String.serializer(), String.serializer()), HashMap(cache)))
                tmp.renameTo(f)
            }
        }
    }

    private fun primaryArtist(artist: String): String =
        artist.split(", ", " & ", " x ", " feat. ", " ft. ").first().trim().ifEmpty { artist }
}
