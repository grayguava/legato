package com.grayguava.legato.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.grayguava.legato.lyrics.Lyrics
import com.grayguava.legato.lyrics.LyricsRepository
import com.grayguava.legato.base.model.Song
import com.grayguava.legato.base.model.durationMillis
import com.grayguava.legato.audio.playback.PlayerConnection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import com.grayguava.legato.audio.playback.ShuffleMode
import com.grayguava.legato.audio.playback.AudioFormatInfo
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.grayguava.legato.settings.AppSettings
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.grayguava.legato.base.model.artworkAt

public sealed interface LyricsState {
    public data object Loading : LyricsState
    public data class Found(val lyrics: Lyrics) : LyricsState
    public data object NotFound : LyricsState
}

/** Playback state and actions shared by every screen, plus the current track's lyrics. */
public class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    public val player: PlayerConnection = PlayerConnection(application)

    private val artwork = com.grayguava.legato.source.covers.PlaybackArtwork()
    /** A sharper catalogue cover found for the playing track, by its id. */
    private val betterCover = MutableStateFlow<Pair<String, String>?>(null)
    public val currentSong: StateFlow<Song?> = combine(player.currentSong, betterCover) { song, better ->
        song?.copy(thumbnailUrl = artwork.choose(song, better?.takeIf { it.first == song.videoId }?.second)) ?: run { artwork.clear(); null }
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, null)

    public val queue: StateFlow<List<Song>> = player.queue
    public val currentIndex: StateFlow<Int> = player.currentIndex
    public val upNext: StateFlow<List<Int>> = player.upNext
    public val hasNext: StateFlow<Boolean> = player.hasNext
    public val isPlaying: StateFlow<Boolean> = player.isPlaying
    public val isBuffering: StateFlow<Boolean> = player.isBuffering
    public val loadingMessage: StateFlow<String?> = player.loadingMessage
    public val shuffleEnabled: StateFlow<Boolean> = player.shuffleEnabled
    public val shuffleMode: StateFlow<ShuffleMode> = player.shuffleMode
    public val smartShuffleAvailable: StateFlow<Boolean> = player.smartShuffleAvailable
    public val repeatMode: StateFlow<Int> = player.repeatMode
    public val durationMs: StateFlow<Long> = player.durationMs
    public val playbackError: StateFlow<String?> = player.error
    public val source: StateFlow<String?> = player.source
    public val audioFormat: StateFlow<AudioFormatInfo?> = player.audioFormat
    /** Fires on each seek and each silence skipped, for the lyrics clock to take a jump cleanly. */
    public val seeks: SharedFlow<Unit> = player.jumps

    private val _lyrics = MutableStateFlow<LyricsState>(LyricsState.Loading)
    public val lyrics: StateFlow<LyricsState> = _lyrics.asStateFlow()

    init {
        viewModelScope.launch { player.connect() }
        viewModelScope.launch { watchLyrics() }
        viewModelScope.launch {
            combine(player.currentSong, player.queue, player.upNext) { current, queue, next ->
                listOfNotNull(current) + next.take(2).mapNotNull { queue.getOrNull(it) }
            }.distinctUntilChanged().collectLatest { songs ->
                kotlinx.coroutines.coroutineScope {
                    songs.forEach { song -> launch {
                        val cached = if (AppSettings.ui.value.albumCovers) com.grayguava.legato.source.covers.AlbumCovers.cached(song) else null
                        warmArtwork(cached ?: song.thumbnailUrl)
                    } }
                }
            }
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            player.currentSong.distinctUntilChangedBy { it?.videoId }.collectLatest { song ->
                if (song == null || !com.grayguava.legato.source.covers.AlbumCovers.wants(song)) return@collectLatest
                val ui = AppSettings.ui.value
                if (!ui.albumCovers) return@collectLatest
                // A sharper cover found for this song before: straight away once it's shown to load,
                // rather than after waiting for the song's length.
                if (ui.albumCovers) com.grayguava.legato.source.covers.AlbumCovers.cached(song)?.let {
                    if (warmArtwork(it) && player.currentSong.value?.videoId == song.videoId) betterCover.value = song.videoId to it
                }
                // Apple and Deezer are matched on the exact length, so wait for the player's.
                val duration = withTimeoutOrNull(20_000) {
                    player.durationOf.first { (id, ms) -> id == song.videoId && ms > 0 }.second
                } ?: 0L
                kotlinx.coroutines.coroutineScope {
                    if (ui.albumCovers && betterCover.value?.first != song.videoId) launch {
                        com.grayguava.legato.source.covers.AlbumCovers.coverFor(song, duration)?.let {
                            // Keep the visible cover until its replacement is decoded and cached.
                            if (warmArtwork(it)) betterCover.value = song.videoId to it
                        }
                    }
                }
            }
        }
    }

    private suspend fun warmArtwork(url: String?): Boolean {
        if (url == null) return false
        val app = getApplication<Application>()
        val loader = coil3.SingletonImageLoader.get(app)
        return withTimeoutOrNull(5_000) {
            kotlinx.coroutines.coroutineScope {
                val small = launch {
                    loader.execute(coil3.request.ImageRequest.Builder(app)
                        .data(url.artworkAt(com.grayguava.legato.base.model.ROW_ART_PX)).size(com.grayguava.legato.base.model.ROW_ART_PX).build())
                }
                val result = loader.execute(coil3.request.ImageRequest.Builder(app)
                    .data(url.artworkAt(com.grayguava.legato.base.model.PLAYER_ART_PX)).size(com.grayguava.legato.base.model.PLAYER_ART_PX).build())
                small.join()
                result is coil3.request.SuccessResult
            }
        } ?: false
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun watchLyrics() {
        combine(
            // A new title on the same id (a song from an add-on matched to an upload) is a new song too.
            currentSong.distinctUntilChangedBy { it?.let { s -> Triple(s.videoId, s.title, s.artist) } },
            AppSettings.lyrics.map { Triple(it.ordered, it.preferWordSynced, it.chosen) }.distinctUntilChanged(),
        ) { song, _ -> song }.collectLatest { song ->
            if (song == null) return@collectLatest
            _lyrics.value = LyricsState.Loading
            // LRCLIB matches on duration, and the player's figure is exact
            // where the row's "3:45" is rounded. Without it any version's
            // lyrics can match, so wait for it while the stream starts. It
            // has to be this song's: right after a change the player can
            // still report the last one's, and lyrics found with that are
            // another version's, out of step from the first line.
            //
            // The waiting is what kept the lyrics off the screen until the
            // song was playing, though: the exact length only turns up once
            // the stream starts. So the catalogue's own length is used to
            // begin with - rounded, but enough to tell one version's lyrics
            // from another's - and the lyrics are looked up again with the
            // player's exact figure when it turns up.
            flow {
                emit(song.durationMillis())
                val exact = withTimeoutOrNull(20_000) {
                    player.durationOf.first { (id, ms) -> id == song.videoId && ms > 0 }.second
                }
                if (exact != null) emit(exact)
            }.flatMapLatest { duration ->
                // Plain text often means a synced source was busy just then;
                // the repository asks again a little later and sends better
                // lyrics on.
                LyricsRepository.lyricsUpdates(song.videoId, song.title, song.artist, duration)
            }.collect { found ->
                _lyrics.value = found?.let(LyricsState::Found) ?: LyricsState.NotFound
            }
        }
    }

    public fun play(song: Song, source: String? = null) { player.play(song, source) }
    public fun playAll(songs: List<Song>, startIndex: Int = 0, shuffle: Boolean = false, source: String? = null, fromList: Boolean = true) {
        player.playAll(songs, startIndex, shuffle, source, fromList = fromList)
    }
    /** Shuffles [songs] with similar songs mixed in. */
    public fun smartShuffle(songs: List<Song>, source: String? = null) {
        player.playAll(songs, 0, shuffle = true, source = source, smart = true)
    }
    public fun playNext(song: Song) { player.playNext(song) }
    public fun addToQueue(song: Song) { player.addToQueue(song) }
    public fun togglePlayPause() { player.togglePlayPause() }
    public fun skipNext() { player.skipNext() }
    public fun skipPrevious() { player.skipPrevious() }
    public fun toggleShuffle(): Unit = player.toggleShuffle()
    /** Off, shuffle, then smart shuffle for a queue from a list; returns the new mode. */
    public fun cycleShuffle(): com.grayguava.legato.audio.playback.ShuffleMode = player.cycleShuffle()
    public fun cycleRepeatMode(): Unit = player.cycleRepeatMode()
    public fun playQueueItem(index: Int): Unit = player.playQueueItem(index)
    public fun removeQueueItem(index: Int): Unit = player.removeQueueItem(index)
    public fun moveQueueItem(from: Int, to: Int): Unit = player.moveQueueItem(from, to)
    /** A song on its own; the service follows it with radio. */
    public fun startRadio(song: Song) { player.play(song, "${song.title} radio") }
    public fun seekTo(positionMs: Long) { player.seekTo(positionMs) }
    public fun positionMs(): Long = player.currentPositionMs()
    public fun bufferedPositionMs(): Long = player.bufferedPositionMs()

    override fun onCleared() {
        player.disconnect()
        super.onCleared()
    }
}
