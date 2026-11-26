package com.grayguava.legato.audio.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.ContentMetadataMutations
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.grayguava.legato.base.model.Song
import com.grayguava.legato.library.history.History
import com.grayguava.legato.settings.AppSettings
import com.grayguava.legato.source.isYouTubeId
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/** One track the song cache is holding: its song, what it costs, when it was last touched. */
public data class CachedTrack(
    public val song: Song,
    /** Bytes on disk across every kept copy of the track. */
    public val bytes: Long,
    /** When any of those bytes were last read or written, epoch millis. */
    public val lastUsedMs: Long,
    /** Every byte is here, so it plays with no connection at all. */
    public val complete: Boolean,
)

/**
 * Streamed audio kept on disk, keyed by video id rather than by URL (stream
 * URLs change every resolve), so replays and seeks inside played audio don't
 * touch the network. Least recently played tracks go first once the limit set
 * in Settings is reached. One instance per process, as SimpleCache requires;
 * a new limit takes effect the next time the app starts.
 *
 * The cache also carries each track's name and cover, in its own metadata, so
 * the Cached songs folder can list what is on disk without a second index to
 * fall out of step. Eviction takes the metadata with the audio.
 */
@OptIn(UnstableApi::class)
public object AudioCache {
    @Volatile
    private var cache: SimpleCache? = null

    /** Bumped whenever a track is added or dropped, so an open folder can re-read itself. */
    private val _changes = MutableStateFlow(0)
    public val changes: StateFlow<Int> = _changes.asStateFlow()

    /** A track's audio and a better copy of it share one id, told apart by this suffix. */
    private const val HQ = ":hq"

    // Custom keys, so Media3's own (content length, redirects) are left alone.
    private const val META = ContentMetadata.KEY_CUSTOM_PREFIX
    private const val META_TITLE = "${META}title"
    private const val META_ARTIST = "${META}artist"
    private const val META_ALBUM = "${META}album"
    private const val META_THUMB = "${META}thumb"
    private const val META_DURATION = "${META}duration"

    public fun get(context: Context): SimpleCache = cache ?: synchronized(this) {
        cache ?: SimpleCache(
            File(context.cacheDir, "audio"),
            LeastRecentlyUsedCacheEvictor(AppSettings.library.value.songCacheMb.toLong() * 1024 * 1024),
            StandaloneDatabaseProvider(context),
        ).also { cache = it }
    }

    public fun usedBytes(context: Context): Long = runCatching { get(context).cacheSpace }.getOrDefault(0L)

    /** Whether any of [videoId]'s audio is kept here, in any quality or from any source. */
    public fun holds(context: Context, videoId: String): Boolean =
        runCatching { get(context).keys.any { videoId in it } }.getOrDefault(false)

    /**
     * Remembers what [song] is, on the copies the cache already holds. Called
     * as a track starts, so the Cached songs folder can name it later; a key
     * with no audio is skipped, because metadata alone would leave an empty
     * entry in the cache.
     */
    public fun note(context: Context, song: Song) {
        if (!isYouTubeId(song.videoId)) return
        val c = get(context)
        val mutations = ContentMetadataMutations().apply {
            set(META_TITLE, song.title)
            set(META_ARTIST, song.artist)
            set(META_ALBUM, song.albumName.orEmpty())
            set(META_THUMB, song.thumbnailUrl.orEmpty())
            set(META_DURATION, song.durationText.orEmpty())
        }
        runCatching {
            val keys = c.keys
            listOf(song.videoId, song.videoId + HQ).filter { it in keys }.forEach { key ->
                c.applyContentMetadataMutations(key, mutations)
            }
        }
        _changes.update { it + 1 }
    }

    /**
     * What the cache is holding, most recently touched first. A track's better
     * copy and its ordinary one are one row, their bytes added together.
     *
     * A track with no name in the cache falls back to listening history, which
     * covers everything cached before this build; without either it is left
     * out rather than listed as an unknown title, and returns on its next play.
     */
    public suspend fun tracks(context: Context): List<CachedTrack> = withContext(Dispatchers.IO) {
        val c = get(context)
        val history = History.records.value.groupBy { it.videoId }.mapValues { (_, plays) -> plays.maxBy { it.playedAt } }
        c.keys
            .groupBy { it.removeSuffix(HQ) }
            .mapNotNull { (videoId, keys) -> track(c, videoId, keys, history[videoId]?.toSong()) }
            .sortedByDescending { it.lastUsedMs }
    }

    private fun track(c: SimpleCache, videoId: String, keys: List<String>, known: Song?): CachedTrack? {
        val spans = keys.flatMap { key -> runCatching { c.getCachedSpans(key) }.getOrDefault(emptySet()) }
            .filter { it.isCached && it.file?.exists() == true }
        val bytes = spans.sumOf { it.length }
        if (bytes <= 0L) return null
        val meta = keys.mapNotNull { key -> runCatching { c.getContentMetadata(key) }.getOrNull() }
            .firstOrNull { !it.get(META_TITLE, null as String?).isNullOrEmpty() }
        val song = meta?.let { songOf(videoId, it) } ?: known ?: return null
        val length = meta?.get(ContentMetadata.KEY_CONTENT_LENGTH, 0L) ?: 0L
        val complete = length > 0 && keys.any { key -> runCatching { c.isCached(key, 0, length) }.getOrDefault(false) }
        return CachedTrack(song, bytes, spans.maxOfOrNull { it.lastTouchTimestamp } ?: 0L, complete)
    }

    private fun songOf(videoId: String, meta: ContentMetadata): Song? {
        val title = meta.get(META_TITLE, null as String?)?.takeIf { it.isNotBlank() } ?: return null
        return Song(
            videoId = videoId,
            title = title,
            artist = meta.get(META_ARTIST, null as String?).orEmpty(),
            thumbnailUrl = meta.get(META_THUMB, null as String?)?.takeIf { it.isNotBlank() },
            durationText = meta.get(META_DURATION, null as String?)?.takeIf { it.isNotBlank() },
            albumName = meta.get(META_ALBUM, null as String?)?.takeIf { it.isNotBlank() },
        )
    }

    /** Drops [videoId]'s kept audio, every copy of it; it's fetched again the next time it plays. */
    public fun remove(context: Context, videoId: String) {
        val c = get(context)
        c.keys.filter { it == videoId || it == videoId + HQ }.forEach { runCatching { c.removeResource(it) } }
        _changes.update { it + 1 }
    }

    public fun clear(context: Context) {
        val c = get(context)
        c.keys.toList().forEach { runCatching { c.removeResource(it) } }
        _changes.update { it + 1 }
    }
}
