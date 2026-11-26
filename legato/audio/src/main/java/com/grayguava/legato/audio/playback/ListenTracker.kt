package com.grayguava.legato.audio.playback

import android.os.SystemClock
import androidx.media3.common.Player
import com.grayguava.legato.base.model.Song
import com.grayguava.legato.library.ResumePoints
import com.grayguava.legato.library.history.History
import com.grayguava.legato.library.history.YouTubeHistory
import com.grayguava.legato.library.history.YouTubeHistorySession
import com.grayguava.legato.settings.AppSettings
import com.grayguava.legato.source.local.LocalMusic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * What the listener has heard: the song, how long it has been playing, and the
 * history that follows from it.
 *
 * The history is written twice over: the app's own list, and YouTube's, which
 * is what the recommendations are built from. The two are told apart by the
 * account they belong to \u2014 a sign-in during a song must not write the
 * previous account's listening into the new one, which is what the generation
 * counter is for.
 */
internal class ListenTracker(
    private val scope: CoroutineScope,
    private val player: () -> Player?,
) {
    private var song: Song? = null

    /** Bumped when the account changes, so the old session's history is not written to. */
    @Volatile private var generation = 0L

    private var work = SupervisorJob(scope.coroutineContext[Job])
    private var youtubeListen: YouTubeHistorySession? = null
    private var youtubeReport: Job? = null
    private var youtubeSyncStartMs = 0L

    private var listenedMs = 0L
    private var listenSince = -1L
    private var record: Long? = null
    private var trackLength: Long? = null

    /** Wall-clock start of the current listen, for the scrobble's timestamp. */
    private var startedAt = 0L
    private var scrobbled = false
    private var nowPlayingSent = false

    /** A different account is signed in: the old one's history is not ours to write. */
    fun onAccountChanged() {
        generation++
        work.cancel()
        work = SupervisorJob(scope.coroutineContext[Job])
        resetAccountSync()
    }

    fun resetAccountSync() {
        youtubeSyncStartMs = listenedSoFar()
        val captured = generation
        youtubeListen = song?.let { YouTubeHistory.session(it.videoId) { generation == captured } }
        youtubeReport = null
    }

    fun startListen(song: Song?, playing: Boolean) {
        this.song = song
        val captured = generation
        youtubeListen = this.song?.let { YouTubeHistory.session(it.videoId) { generation == captured } }
        youtubeReport = null
        youtubeSyncStartMs = 0
        listenedMs = 0
        listenSince = if (playing) SystemClock.elapsedRealtime() else -1
        record = null
        trackLength = player()?.duration?.takeIf { it > 0 }
        startedAt = System.currentTimeMillis()
        scrobbled = false
        nowPlayingSent = false
        if (playing) sendNowPlaying()
    }

    fun sendNowPlaying() {
        val song = song ?: return
        if (nowPlayingSent) return
        nowPlayingSent = true
    }

    fun pauseListen() {
        if (listenSince >= 0) listenedMs += SystemClock.elapsedRealtime() - listenSince
        listenSince = -1
    }

    /** The playhead moved again, so the clock starts running. */
    fun markResumed() {
        if (listenSince < 0) listenSince = SystemClock.elapsedRealtime()
    }

    private fun listenedSoFar(): Long =
        listenedMs + if (listenSince >= 0) SystemClock.elapsedRealtime() - listenSince else 0

    fun finishListen(report: Boolean = true) {
        val heard = listenedSoFar()
        record?.let { History.updateListened(it, heard) }
        record = null
        val session = youtubeListen
        val length = trackLength
        val syncHeard = (heard - youtubeSyncStartMs).coerceAtLeast(0)
        if (report && session != null && !AppSettings.playback.value.pauseListeningHistory) {
            scope.launch(work + Dispatchers.IO) { session.heard(syncHeard, length, final = true) }
        }
        youtubeListen = null
    }

    /** Keeps a podcast episode's place, or that of anything as long as one, so it resumes there. */
    fun saveEpisodePosition(player: Player) {
        val id = player.currentMediaItem?.mediaId ?: return
        val length = player.duration.takeIf { it > 0 } ?: 0L
        ResumePoints.save(id, player.currentPosition, length)
    }

    /** A track counts as played after 30 seconds, or half its length if shorter. */
    suspend fun trackListening(player: Player) {
        var ticks = 0
        while (scope.isActive) {
            delay(5_000)
            if (player.isPlaying && ++ticks % 3 == 0) saveEpisodePosition(player)
            val song = song ?: continue
            val heard = listenedSoFar()
            val known = player.duration.takeIf { it > 0 }
            trackLength = known
            val session = youtubeListen
            if (session != null && youtubeReport?.isActive != true && player.isPlaying) {
                val syncHeard = (heard - youtubeSyncStartMs).coerceAtLeast(0)
                youtubeReport = scope.launch(work + Dispatchers.IO) { session.heard(syncHeard, known) }
            }
            // The usual scrobble rule: a song over 30 s, heard for half its length or 4 minutes.
            if (!scrobbled && known != null && known > 30_000 && heard >= minOf(known / 2, 240_000L)) {
                scrobbled = true
            }
            if (record != null) continue
            val length = known ?: Long.MAX_VALUE
            val threshold = minOf(30_000L, length / 2)
            if (heard >= threshold) {
                record = History.record(song, heard)
            }
        }
    }
}
