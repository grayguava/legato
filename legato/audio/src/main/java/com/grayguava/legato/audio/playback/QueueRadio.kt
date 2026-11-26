package com.grayguava.legato.audio.playback

import androidx.media3.common.Player
import com.grayguava.legato.base.DebugLog as Log
import com.grayguava.legato.base.model.QueueTier
import com.grayguava.legato.base.model.Song
import com.grayguava.legato.library.LibraryStore
import com.grayguava.legato.library.reco.Recommendations
import com.grayguava.legato.settings.AppSettings
import com.grayguava.legato.source.isYouTubeId
import com.grayguava.legato.source.local.LocalMusic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Radio for the end of the queue.
 *
 * When playback nears the end of the queue, more songs are asked for, so the
 * music doesn't stop. Starting a single song goes through here too: a
 * one-track queue is already "near the end". With repeat on the queue never
 * ends, so there is nothing to extend.
 */
internal class QueueRadio(
    private val scope: CoroutineScope,
    private val currentPlayer: () -> Player?,
) {
    private var job: Job? = null
    private var currentSeed: String? = null
    private val exhausted = mutableSetOf<String>()

    /** What the queue has held this session, so the same song is not queued twice. */
    private val sessionIds = mutableSetOf<String>()

    /** Notes what the queue holds. */
    fun noteQueued(ids: List<String>) {
        sessionIds += ids
    }

    /** The queue changed: the radio asked for the old one is not wanted. */
    fun onQueueChanged() {
        job?.cancel()
        currentSeed = null
        exhausted.clear()
        extend()
    }

    fun extend() {
        val player = currentPlayer() ?: return
        if (!AppSettings.playback.value.autoplay || player.repeatMode != Player.REPEAT_MODE_OFF) return
        val remaining = player.upcomingPlayOrder().size - 1
        if (!Autoplay.shouldExtend(remaining, player.mediaItemCount)) return
        val seedItem = player.getMediaItemAt(player.mediaItemCount - 1)
        val seed = seedItem.mediaId
        // Radio is a YouTube feature; a local file has none. A song on your own
        // server carries on with more of the server's songs, picked at random.
        if (LocalMusic.isLocal(seed)) return
        if (seed in exhausted) return
        if (job?.isActive == true && currentSeed == seed) return

        // A request for an older seed is answering a queue that no longer exists.
        job?.cancel()
        currentSeed = seed
        job = scope.launch {
            // A song started on its own asks for radio while its own stream is
            // still loading, which is when a request is most likely to fail; a
            // failure must not leave the song to end in silence, so ask again.
            // The last song leads; a couple of earlier ones in the queue, by other
            // artists, are asked for too, so what comes next follows the whole
            // queue rather than only its last song.
            val engine = AppSettings.playback.value.recommender
            val queue = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).toSong() }
            val others = Autoplay.seeds(queue) { s -> isYouTubeId(s.videoId) }.drop(1)
            val queuedAtStart = queue.mapTo(HashSet()) { it.videoId }
            fun usable(song: Song): Boolean = song.videoId !in queuedAtStart &&
                !LibraryStore.isDisliked(song.videoId) && !LibraryStore.hidden.value.hides(song.videoId, song.artist)
            fun ask() = (listOf(seedItem.toSong()) + others).mapIndexed { index, song ->
                async {
                    var answer = emptyList<Song>()
                    val waits = if (index == 0) RADIO_RETRY_MS else longArrayOf(0)
                    for ((attempt, wait) in waits.withIndex()) {
                        if (wait > 0) delay(wait)
                        answer = try {
                            withTimeoutOrNull(RADIO_REQUEST_MS) {
                                Recommendations.after(song, engine).songs
                            }.orEmpty()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w(TAG, "Radio for ${song.videoId} failed (try ${attempt + 1})", e)
                            emptyList()
                        }
                        if (answer.any(::usable)) break
                    }
                    answer
                }
            }
            // A fast secondary seed is useful even if the primary is retrying.
            // Preserve seed order for ranking, but don't await every slow catalogue.
            // Nothing at all (the network was down, every catalogue busy) is asked
            // again a little later while the song plays, rather than waiting for the
            // next thing the player happens to do: that left a single song with no
            // queue after it until something, such as opening the player, stirred it.
            var ready: List<List<Song>> = emptyList()
            for ((round, pause) in RADIO_AGAIN_MS.withIndex()) {
                if (pause > 0) delay(pause)
                val count = player.mediaItemCount
                if (!AppSettings.playback.value.autoplay || count == 0 || player.getMediaItemAt(count - 1).mediaId != seed) return@launch
                ready = Autoplay.readyRadios(ask(), ::usable)
                if (ready.any { it.isNotEmpty() }) break
                Log.i(TAG, "No radio for $seed yet (round ${round + 1}), asking again shortly")
            }
            if (ready.all { it.isEmpty() }) return@launch
            val radio = Autoplay.blend(ready, queue)
            if (!AppSettings.playback.value.autoplay || AppSettings.playback.value.recommender != engine ||
                player.repeatMode != Player.REPEAT_MODE_OFF) return@launch
            // The queue may have been replaced while the request was out; radio
            // for a track that's no longer last doesn't belong at the end.
            val count = player.mediaItemCount
            if (count == 0 || player.getMediaItemAt(count - 1).mediaId != seed) return@launch

            val queued = (0 until count).mapTo(HashSet()) { player.getMediaItemAt(it).mediaId }
            // Disliked and hidden songs and artists are never queued on their own.
            val liked = LibraryStore.hidden.value.songs(radio.filterNot { LibraryStore.isDisliked(it.videoId) })
            // Songs already heard this session are skipped when asked, unless that leaves nothing to play.
            val additions = Autoplay.newTracks(if (AppSettings.playback.value.noRepeatInSession) queued + sessionIds else queued, liked)
                .ifEmpty { Autoplay.newTracks(queued, liked) }
            if (additions.isEmpty()) {
                exhausted += seed
                return@launch
            }
            val ended = player.playbackState == Player.STATE_ENDED
            player.addMediaItems(additions.map { it.copy(queueTier = QueueTier.AUTOPLAY).toMediaItem() })
            // The song finished before the next ones came; carry on into them.
            if (ended && player.playWhenReady) {
                player.seekToNextMediaItem()
                player.prepare()
                player.play()
            }
        }
    }

    private companion object {
        const val TAG = "QueueRadio"

        /** How many songs to ask the server for when the queue ends on one of its own. */
        const val RADIO_FROM_SERVER = 25
        /** How long to wait for an answer before asking again. */
        const val RADIO_REQUEST_MS = 12_000L

        /** How long to wait between the attempts for the first song. */
        val RADIO_RETRY_MS = longArrayOf(0L, 3_000L, 10_000L)

        /** How long to wait between the rounds of asking for radio at all. */
        val RADIO_AGAIN_MS = longArrayOf(0L, 20_000L, 45_000L, 90_000L)
    }
}
