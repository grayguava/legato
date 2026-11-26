package com.grayguava.legato.source.songinfo

import com.grayguava.legato.base.model.Song
import com.grayguava.legato.base.model.durationMillis
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One song's information from every source that has it, each asked on its own.
 * A provider that fails only loses its own fields: the sheet still shows what
 * the others returned, and offers a single retry when a provider that could
 * still supply a missing field actually failed.
 */
public object SongInfoRepository {

    private const val DAY_MS = 86_400_000L
    private data class Entry(val info: SongInfo, val at: Long)

    private val cache = object : LinkedHashMap<String, Entry>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?) = size > 200
    }
    private val lock = Mutex()

    public suspend fun load(
        song: Song,
        durationMs: Long,
        country: String = Locale.getDefault().country,
    ): SongInfo {
        val key = "${song.videoId}|$durationMs|$country"
        val now = System.nanoTime() / 1_000_000
        lock.withLock { cache[key]?.takeIf { now - it.at < DAY_MS } }?.let { return it.info }
        val info = fetch(song, durationMs, country)
        // Only complete answers are cached; a failed source must be asked again.
        if (info.failed.isEmpty()) lock.withLock { cache[key] = Entry(info, now) }
        return info
    }

    private suspend fun fetch(song: Song, durationMs: Long, country: String): SongInfo = coroutineScope {
        val length = durationMs.takeIf { it > 0 } ?: song.durationMillis()
        if (song.videoId.startsWith("local:")) {
            return@coroutineScope SongInfo(album = song.albumName, lengthMs = length)
        }
        val youtube = async { attempt { YouTubeSongInfo.load(song) } }
        val apple = async { attempt { apple(song, length, country) } }
        val brainz = async { attempt { brainz(song, length) } }
        val yt = youtube.await()
        val ap = apple.await()
        val mb = brainz.await()

        val ytInfo = yt.getOrNull()?.info
        // A page that couldn't be read is what a retry can fix; a page that
        // simply states no release date is not, so the two are kept apart.
        val watchFailed = yt.exceptionOrNull() != null || yt.getOrNull()?.watchFailed == true
        val apInfo = ap.getOrNull()
        val mbInfo = mb.getOrNull()
        val album = song.albumName?.takeIf { it.isNotBlank() }
            ?: ytInfo?.album ?: apInfo?.album ?: mbInfo?.album
        val releaseDate = ytInfo?.releaseDate ?: apInfo?.releaseDate ?: mbInfo?.releaseDate
        val credits = ytInfo?.credits?.takeIf { it.isNotEmpty() } ?: mbInfo?.credits.orEmpty()
        val plays = ytInfo?.plays
        val mergedLength = length.takeIf { it > 0 }
            ?: ytInfo?.lengthMs?.takeIf { it > 0 }
            ?: apInfo?.lengthMs?.takeIf { it > 0 }
            ?: mbInfo?.lengthMs?.takeIf { it > 0 }
            ?: 0
        val failed = buildSet {
            if (watchFailed && (releaseDate == null || plays == null)) add(SongInfoSource.YOUTUBE)
            if (ap.isFailure && (album == null || releaseDate == null)) add(SongInfoSource.APPLE)
            if (mb.isFailure && (album == null || releaseDate == null || credits.isEmpty())) {
                add(SongInfoSource.MUSICBRAINZ)
            }
        }
        SongInfo(album, mergedLength, releaseDate, plays, credits, failed)
    }

    private suspend fun apple(song: Song, lengthMs: Long, country: String): SongInfo? =
        AppleTrackMetadataRepository.lookup(song, lengthMs, country)?.let {
            SongInfo(album = it.album, releaseDate = it.releaseDate)
        }

    private suspend fun brainz(song: Song, lengthMs: Long): SongInfo? =
        TrackMetadataRepository.lookup(song, lengthMs)?.let { data ->
            val names = data.credits.split(',').map(String::trim).filter(String::isNotEmpty)
            SongInfo(
                album = data.album,
                releaseDate = data.releaseDate,
                credits = if (names.isEmpty()) emptyList() else listOf(SongCredit("Performed by", names)),
            )
        }

    private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
