package com.grayguava.legato.audio.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.grayguava.legato.base.model.Song
import com.grayguava.legato.base.model.isPlaceholderArtist
import com.grayguava.legato.library.MusicRepository
import com.grayguava.legato.source.isYouTubeId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

/**
 * Who really made the song, when the title says "Various Artists".
 *
 * YouTube's own metadata for a compilation entry carries the placeholder, not
 * the name, so the real credit is looked up in the watch queue for the same
 * video \u2014 never guessed from the title.
 */
internal class ArtistCredits(
    private val scope: CoroutineScope,
    private val player: () -> Player?,
) {
    private val credits = LinkedHashMap<String, Song>()
    private var job: Pair<String, Job>? = null

    /** Repair compilation placeholders using the watch entry for the same video, never a title guess. */
    fun refresh(item: MediaItem?) {
        val id = item?.mediaId
        if (id != null && job?.let { it.first == id && it.second.isActive } == true) return
        job?.second?.cancel()
        if (item == null || id == null || !isYouTubeId(id) ||
            !isPlaceholderArtist(item.mediaMetadata.artist?.toString())) return
        job = id to scope.launch {
            // A metadata replacement must happen outside the player's transition callback.
            yield()
            val credit = credits[id] ?: try {
                MusicRepository.watchQueue(id).firstOrNull {
                    it.videoId == id && !isPlaceholderArtist(it.artist)
                }?.also {
                    if (credits.size >= 128) credits.remove(credits.keys.first())
                    credits[id] = it
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) { null } ?: return@launch
            val target = player() ?: return@launch
            val current = target.currentMediaItem?.takeIf { it.mediaId == id } ?: return@launch
            val corrected = current.withTrackArtistCredit(credit)
            if (corrected !== current) target.replaceMediaItem(target.currentMediaItemIndex, corrected)
        }
    }
}
