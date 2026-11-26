package com.grayguava.legato.audio.playback

import com.grayguava.legato.base.model.durationMillis
import kotlinx.coroutines.ensureActive

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.session.MediaSession
import androidx.media3.session.LibraryResult
import com.grayguava.legato.audio.sponsorblock.SponsorBlock
import android.os.Bundle
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionCommand
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionError
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.guava.future
import com.grayguava.legato.base.DebugLog as Log
import com.grayguava.legato.source.isYouTubeId
import com.grayguava.legato.library.LibraryStore
import com.grayguava.legato.library.SongRef
import com.grayguava.legato.base.NerdStats
import com.grayguava.legato.library.history.History
import com.grayguava.legato.source.local.LocalMusic
import com.grayguava.legato.source.innertube.StreamResolver
import com.grayguava.legato.base.model.QueueTier
import com.grayguava.legato.base.model.Song
import com.grayguava.legato.library.reco.Recommendations
import com.grayguava.legato.settings.AppSettings
import com.grayguava.legato.audio.playback.dsp.DspAudioProcessor
import com.grayguava.legato.audio.playback.dsp.DspParams
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.media3.common.Tracks
import android.content.IntentFilter
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Foreground media session hosting playback, the queue, autoplay, the audio
 * chain and listening history.
 *
 * Data path: an `legato://track/<id>` item is looked up in [AudioCache]
 * first, by video id; only on a miss is it resolved through [StreamResolver]
 * and fetched. Local files (`content://`) skip both.
 *
 * Audio path: decoder → [DspAudioProcessor] (EQ, tone, balance, widening,
 * levelling, limiter) → ExoPlayer's silence skipping and speed/pitch → output.
 */
@OptIn(UnstableApi::class)
public class PlaybackService : MediaLibraryService() {

    private var mediaSession: MediaLibrarySession? = null

    /** What Android Auto and other media browsers can browse; see [CarLibrary]. */
    private val car by lazy { CarLibrary(this) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val queueRadio = QueueRadio(scope) { mediaSession?.player }
    private val listenTracker = ListenTracker(scope) { mediaSession?.player }
    private val loudness: LoudnessControl = LoudnessControl(
        this,
        scope,
        { mediaSession?.player as? ExoPlayer },
        { uri -> startupToken(uri)?.let { startupSelections[it]?.selected == null } == true },
        { outputs.onPhoneSpeaker() },
    )
    private val outputs: OutputDevices = OutputDevices(
        this,
        loudness,
        { mediaSession?.player as? ExoPlayer },
        { audioManager },
    )
    private val artistCredits = ArtistCredits(scope) { mediaSession?.player }
    private val upgrader: StreamUpgrader = StreamUpgrader(
        scope,
        loudness,
        { mediaSession?.player as? ExoPlayer },
        { crossfade },
        { uri -> selectedUri(uri) },
        { uri -> startupToken(uri) },
        { token -> startupSelections[token] },
    )


    /** Seeds whose radio added nothing new; asking again would only repeat that. */

    /** Every track queued since the service started, for "don't repeat songs". */

    /** The item a failed load was last retried for. See [recover]. */
    private var retriedMediaId: String? = null

    private var soundEffects: SoundEffects? = null
    private val dsp = DspAudioProcessor()
    /** The same effects, for the bridge player that carries a song across a quality swap. */
    private val bridgeDsp = DspAudioProcessor()
    private var audioManager: AudioManager? = null
    private val startupSelections = ConcurrentHashMap<String, StartupSelection>()

    private fun selectedUri(uri: Uri): Uri = startupToken(uri)?.let { startupSelections[it]?.selected } ?: uri

    override fun onCreate() {
        super.onCreate()
        val context: Context = this

        val songSources = SongSources(context)
        val cached = songSources.cached
        val direct = songSources.direct
        val sources = DefaultMediaSourceFactory(songSources.routing)
        val qualitySources = object : MediaSource.Factory by sources {
            override fun createMediaSource(mediaItem: MediaItem): MediaSource {
                val input = mediaItem.localConfiguration?.uri ?: return sources.createMediaSource(mediaItem)
                if (videoIdOf(input) == null) return sources.createMediaSource(mediaItem)
                val uri = withoutStartup(input)
                val id = videoIdOf(uri) ?: return sources.createMediaSource(mediaItem)
                if (!isYouTubeId(id) || uri.getQueryParameter("fallback") == "youtube") return sources.createMediaSource(mediaItem)
                val token = java.util.UUID.randomUUID().toString()
                val tagged = uri.buildUpon().appendQueryParameter("startup", token).build()
                val selection = StartupSelection(mediaItem.toSong(), uri)
                val choosing = ResolvingDataSource.Factory(cached) { spec ->
                    startupSelections[token] = selection
                    val resolved = runBlocking { selection.resolve(spec) }
                    scope.launch { loudness.apply() }
                    resolved
                }
                return DefaultMediaSourceFactory(SchemeRoutingDataSource.Factory(choosing, direct))
                    .createMediaSource(mediaItem.buildUpon().setUri(tagged).build())
            }
        }

        // The main player and the bridge decode the same way: the bridge used to
        // take the phone's default FLAC decoder, which some phones can't play,
        // so the swap to it failed and the song stayed silent.
        fun renderersFor(effects: DspAudioProcessor, exclusive: Boolean) = object : DefaultRenderersFactory(context) {
            init {
                setEnableDecoderFallback(true)
                setMediaCodecSelector { mime, secure, tunneling ->
                    val selector = if (mime == "audio/flac") androidx.media3.exoplayer.mediacodec.MediaCodecSelector.PREFER_SOFTWARE
                        else androidx.media3.exoplayer.mediacodec.MediaCodecSelector.DEFAULT
                    selector.getDecoderInfos(mime, secure, tunneling).filterNot {
                        mime == "audio/flac" && FlacInfo.unsafeDecoder(it.name)
                    }
                }
            }
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink = com.grayguava.legato.audio.playback.dsp.PrecisionAudioSink(
                // The effects run in the precision sink, on float samples, not in Media3's chain.
                // Float is always allowed through; the precision sink decides, song by song, whether to use it.
                DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(true)
                    .setAudioProcessorChain(DefaultAudioSink.DefaultAudioProcessorChain())
                    .apply {
                        // Only the main player takes the DAC exclusively: the bridge opening its
                        // own output would switch the DAC under the song that's playing.
                        if (exclusive) setAudioOutputProvider(ExclusiveUsbOutputProvider(
                            androidx.media3.exoplayer.audio.AudioTrackAudioOutputProvider.Builder(context).build()))
                    }
                    .build(),
                effects,
                // Float where asked for, and for an exclusive USB DAC whenever the song has more
                // than 16 bits: float carries 24-bit samples exactly, and they go out whole.
                // Not while the song is stretched in time: Media3 changes speed and pitch, skips
                // silence and nudges a listening room's sync only on 16-bit output, and on float
                // it quietly plays at normal speed. The effects still run on float before that.
                floatWanted = { source ->
                    !outputs.stretching() && (
                        AppSettings.playback.value.floatOutput ||
                            (BitPerfectUsb.wanted && source != C.ENCODING_PCM_16BIT && source != C.ENCODING_PCM_8BIT)
                        )
                },
            )
        }
        val renderers = renderersFor(dsp, exclusive = true)

        // Start as soon as a little audio is in: the defaults wait for 2.5 s,
        // which is video-sized caution. A stream starts after half a second buffered
        // and a local file after a quarter second.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMsForStreaming(30_000, FAR_BUFFER_MS, 500, 2_000)
            .setBufferDurationsMsForLocalPlayback(5_000, FAR_BUFFER_MS, 250, 500)
            // The byte ceiling decides, not the time one: a whole song at CD
            // quality, a couple of minutes at 24-bit/96 kHz, so a fetched track is
            // fetched once and seeks within it are instant. 8 MB held only about
            // 15 s of hi-res FLAC, and any dip in the connection stalled it.
            .setTargetBufferBytes(FAR_BUFFER_BYTES)
            // However heavy the stream, at least the minimum time ahead is held.
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(BACK_BUFFER_MS, true)
            .build()

        val player = ExoPlayer.Builder(context, renderers)
            .setMediaSourceFactory(qualitySources)
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus= */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            // Music waits for Bluetooth headphones rather than playing from the
            // phone's speaker. Other devices are unaffected.
            .setSuppressPlaybackOnUnsuitableOutput(true)
            .build()
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setPreferredAudioMimeTypes("audio/flac", "audio/alac", "audio/raw").build()
        // A silent, never-playing copy of the player for upgrades: it loads the
        // better stream from where the song is, through the same sources and
        // so into the same cache, and the swap then reads what it fetched.
        upgrader.auditionPlayer = {
            ExoPlayer.Builder(context, renderersFor(bridgeDsp, exclusive = false))
                .setMediaSourceFactory(qualitySources)
                .setLoadControl(
                    DefaultLoadControl.Builder()
                        .setBufferDurationsMsForStreaming((AUDITION_AHEAD_MS + 10_000).toInt(), (AUDITION_AHEAD_MS + 20_000).toInt(), 500, 1_000)
                        .setPrioritizeTimeOverSizeThresholds(true)
                        .build(),
                )
                .build()
                .apply { volume = 0f; playWhenReady = false }
        }
        player.addListener(playerListener)

        val effects = SoundEffects(player)
        soundEffects = effects
        scope.launch { AppSettings.sound.collect(effects::apply) }
        scope.launch {
            combine(AppSettings.equalizer, AppSettings.sound, AppSettings.playback, BitPerfectUsb.status, outputs.volumeTick) { eq, sound, pb, bp, _ ->
                // Bit-perfect means nothing but the volume touches the samples.
                if (bp is BitPerfectUsb.Status.Active) {
                    DspParams(outputGainDb = audioManager?.let(BitPerfectUsb::softwareGainDb) ?: 0f)
                } else {
                    val remix = sound.enabled
                    DspParams(
                        eq,
                        if (remix) sound.bassBoost else 0,
                        pb.spatialAudio,
                        pb.clarity,
                        eightDPeriod = if (remix) sound.eightD.periodSeconds else 0f,
                        spacePeriod = if (remix) sound.space.periodSeconds else 0f,
                    )
                }
            }.distinctUntilChanged().collect { dsp.params = it; bridgeDsp.params = it }
        }
        scope.launch {
            AppSettings.playback.map { it.skipSilence }.distinctUntilChanged().collect { player.skipSilenceEnabled = it }
        }
        // Turning autoplay back on near the end of the queue should fetch now,
        // not at the next track change.
        scope.launch {
            AppSettings.playback.map { it.autoplay to it.recommender }.distinctUntilChanged().collect {
                queueRadio.onQueueChanged()
            }
        }
        scope.launch {
            combine(
                AppSettings.playback.map { it.pauseListeningHistory to it.listeningHistoryTarget }.distinctUntilChanged(),
                com.grayguava.legato.library.account.AccountStore.accounts.map { list -> list.firstOrNull { it.active }?.id }.distinctUntilChanged(),
            ) { policy, account -> policy to account }.distinctUntilChanged().drop(1).collect {
                // Never backfill time heard while paused, or report it to a different account.
                listenTracker.onAccountChanged()
            }
        }
        scope.launch { listenTracker.trackListening(player) }
        scope.launch {
            AppSettings.playback.map { listOf(it.wifiQuality, it.mobileQuality) }
                .distinctUntilChanged().collect { upgrader.scheduleUpgrade() }
        }
        scope.launch {
            AppSettings.playback.map { Triple(it.loudnessNormalization, it.normalizeOnSpeaker, it.volumeLevel) }.distinctUntilChanged().collect { loudness.apply() }
        }
        scope.launch {
            AppSettings.playback.map { it.preloadUpcoming }.distinctUntilChanged().collect { on ->
                // ExoPlayer prepares and buffers the next item in play order
                // while this one plays, so it starts from memory.
                player.preloadConfiguration =
                    if (on) ExoPlayer.PreloadConfiguration(PRELOAD_US) else ExoPlayer.PreloadConfiguration.DEFAULT
                if (on) warmNeighbours()
            }
        }

        scope.launch { runSleepTimer(player) }
        scope.launch {
            AppSettings.playback.map { it.systemEffects }.distinctUntilChanged().collect { loudness.openEffectSession(player.audioSessionId) }
        }
        crossfade = Crossfade(context, scope, sources, player).also { it.start() }
        scope.launch { PlaybackRequests.upgrade.collect { upgrader.scheduleUpgrade(force = true) } }
        scope.launch { upgrader.guardQuality() }

        audioManager = getSystemService(AudioManager::class.java)?.also { am ->
            BitPerfectUsb.init(am)
            am.registerAudioDeviceCallback(outputs.deviceCallback, null)
        }
        scope.launch {
            AppSettings.playback.map { it.preferUsbDac }.distinctUntilChanged().collect { outputs.applyPreferredDevice() }
        }
        scope.launch {
            AppSettings.playback.map { it.bitPerfectUsb to it.floatOutput }.distinctUntilChanged().collect { outputs.applyBitPerfect(reopen = true) }
        }
        scope.launch {
            // Speed or pitch moved off normal (or back), or skip silence turned on
            // or off: the output is set up again, so the song changes over at once
            // rather than at the next.
            combine(AppSettings.sound, AppSettings.playback) { _, _ -> outputs.stretching() }
                .distinctUntilChanged().drop(1).collect { outputs.rebuildOutput() }
        }
        registerReceiver(outputs.volumeReceiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION"), VOLUME_RECEIVER_FLAGS)

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            com.grayguava.legato.audio.AppHooks.openApp(this),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        mediaSession = MediaLibrarySession.Builder(this, player, sessionCallback)
            .setSessionActivity(sessionActivity)
            .build()
        // Earlier versions posted the song as a Live Update on its own channel. That's
        // gone; clear the channel so it doesn't linger in the phone's settings.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            getSystemService(android.app.NotificationManager::class.java)?.run {
                cancel(7_160)
                deleteNotificationChannel("live_update")
            }
        }
        // Likes changed anywhere (the app, the widget) show on the heart at once.
        scope.launch { LibraryStore.liked.collect { refreshButtons() } }
        scope.launch { skipDisliked(player) }

        restoreQueue(player)
        scope.launch { keepQueueSaved(player) }
    }

    /**
     * A song disliked anywhere (the app, the notification, a car) stops being
     * heard: if it's playing it's skipped, and the copies of it still to come
     * leave the queue. Songs already played stay, so going back still works.
     */
    private suspend fun skipDisliked(player: ExoPlayer) {
        var before: Set<String>? = null
        LibraryStore.disliked.collect { now ->
            val fresh = before?.let { now - it }.orEmpty()
            before = now
            if (fresh.isEmpty()) return@collect
            val current = player.currentMediaItemIndex
            for (i in player.mediaItemCount - 1 downTo current + 1) {
                if (player.getMediaItemAt(i).mediaId in fresh) player.removeMediaItem(i)
            }
            if (player.currentMediaItem?.mediaId in fresh) {
                if (player.hasNextMediaItem()) player.seekToNextMediaItem() else player.pause()
            }
        }
    }

    /**
     * Puts back the queue from last time, paused where it was left and not
     * prepared: nothing is fetched until play is pressed.
     */
    private fun restoreQueue(player: ExoPlayer) {
        if (player.mediaItemCount > 0) return
        val saved = QueueStore.load() ?: return
        val items = saved.songs.mapIndexed { i, ref ->
            ref.toSong().copy(queueTier = saved.tiers?.getOrNull(i) ?: QueueTier.CONTEXT).toMediaItem()
        }
        // A saved queue with no songs in it has no index to start at.
        if (items.isEmpty()) return
        player.setMediaItems(items, saved.index.coerceIn(items.indices), saved.positionMs.coerceAtLeast(0))
        player.shuffleModeEnabled = saved.shuffle
        player.repeatMode = saved.repeat
    }

    /**
     * With the whole queue on repeat, songs queued by hand play once: when a
     * song of the list comes round, the ones already played leave.
     */
    private fun dropPlayedHandQueued() {
        val player = mediaSession?.player ?: return
        if (player.repeatMode != Player.REPEAT_MODE_ALL || player.shuffleModeEnabled) return
        val tiers = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).queueTier }
        QueueTimeline.playedUser(tiers, player.currentMediaItemIndex).asReversed().forEach(player::removeMediaItem)
    }

    /** Saves the queue every few seconds while it changes or plays. */
    private suspend fun keepQueueSaved(player: ExoPlayer) {
        var last: QueueStore.Saved? = null
        while (true) {
            delay(QUEUE_SAVE_MS)
            if (player.mediaItemCount == 0) continue
            val items = (0 until player.mediaItemCount).map { player.getMediaItemAt(it) }
            val tiers = items.map { it.queueTier }
            val saved = QueueStore.Saved(
                items.map { SongRef.of(it.toSong()) }, player.currentMediaItemIndex, player.currentPosition,
                tiers = tiers.takeIf { t -> t.any { it != QueueTier.CONTEXT } },
                shuffle = player.shuffleModeEnabled,
                repeat = player.repeatMode,
            )
            if (saved == last) continue
            last = saved
            withContext(Dispatchers.IO) { QueueStore.save(saved) }
        }
    }

    /**
     * Pauses at the time the sleep timer names, or at the end of the current
     * track (ExoPlayer's own pause-at-end, so it lands on the boundary).
     */
    private suspend fun runSleepTimer(player: ExoPlayer) {
        SleepTimer.state.collectLatest { state ->
            player.pauseAtEndOfMediaItems = state is SleepTimer.State.EndOfTrack
            try {
                when (state) {
                    is SleepTimer.State.At -> {
                        // Winding down: the volume eases away over the last few minutes.
                        val fadeMs = SleepTimer.fadeMsFor(state.endsAtMs - state.startedAtMs)
                        while (true) {
                            val left = state.endsAtMs - System.currentTimeMillis()
                            if (left <= 0) break
                            if (AppSettings.playback.value.sleepWindDown && left < fadeMs) {
                                val f = 1f - left.toFloat() / fadeMs
                                com.grayguava.legato.audio.playback.dsp.DspAudioProcessor.masterGain = SleepTimer.gainAt(f)
                                SleepTimer.setWindDown(f)
                                delay(200)
                            } else {
                                delay(minOf(left - fadeMs, 1_000L).coerceAtLeast(50))
                            }
                        }
                        player.pause()
                        SleepTimer.cancel()
                    }
                    SleepTimer.State.EndOfTrack -> {
                        // The last seconds of the song fade out, then pauseAtEndOfMediaItems pauses.
                        val stopped = scope.launch { playWhenReadyFlow(player).first { !it } }
                        while (stopped.isActive) {
                            val left = player.duration - player.currentPosition
                            if (AppSettings.playback.value.sleepWindDown && player.duration > 0 && left in 0 until END_FADE_MS) {
                                val f = 1f - left.toFloat() / END_FADE_MS
                                com.grayguava.legato.audio.playback.dsp.DspAudioProcessor.masterGain = SleepTimer.gainAt(f)
                                SleepTimer.setWindDown(f)
                            }
                            delay(200)
                        }
                        SleepTimer.cancel()
                    }
                    SleepTimer.State.Off -> Unit
                }
            } finally {
                com.grayguava.legato.audio.playback.dsp.DspAudioProcessor.masterGain = 1f
                SleepTimer.setWindDown(0f)
            }
        }
    }

    /** Emits playWhenReady, now and on every change. */
    private fun playWhenReadyFlow(player: Player) = callbackFlow {
        trySend(player.playWhenReady)
        val l = object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) { trySend(playWhenReady) }
        }
        player.addListener(l)
        awaitClose { player.removeListener(l) }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player ?: return
        if (AppSettings.playback.value.stopOnTaskRemoved) {
            player.pause()
            player.stop()
            stopSelf()
            return
        }
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        (mediaSession?.player as? ExoPlayer)?.takeIf { it.mediaItemCount > 0 }?.let { p ->
            QueueStore.save(QueueStore.Saved((0 until p.mediaItemCount).map { SongRef.of(p.getMediaItemAt(it).toSong()) }, p.currentMediaItemIndex, p.currentPosition))
        }
        com.grayguava.legato.audio.AppHooks.onPlayerStopped(this)
        crossfade?.release()
        crossfade = null
        loudness.closeEffectSession()
        listenTracker.finishListen()
        // The listen just finished, and anything else not yet saved, before the process goes.
        LibraryStore.flush()
        History.flush()
        loudness.release()
        audioManager?.unregisterAudioDeviceCallback(outputs.deviceCallback)
        runCatching { unregisterReceiver(outputs.volumeReceiver) }
        BitPerfectUsb.setWanted(false)
        scope.cancel()
        startupSelections.clear()
        soundEffects?.release()
        soundEffects = null
        mediaSession?.run {
            player.removeListener(playerListener)
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    /**
     * The session's callback, for the app's own controller and for media
     * browsers like Android Auto.
     *
     * Items that arrive from a controller may have lost their URI on the way
     * (Media3 only carries it across a binder in some cases), so it's rebuilt
     * from the media id, which always survives. A browser's items carry the
     * list they were shown in; [CarLibrary.resolve] queues that list.
     */
    private val sessionCallback = object : MediaLibrarySession.Callback {
        /** Every controller (notification, Android Auto, Bluetooth) also gets Like and Shuffle. */
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .add(LIKE_COMMAND)
                .add(SHUFFLE_COMMAND)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .setMediaButtonPreferences(mediaButtons())
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            val player = session.player
            when (customCommand.customAction) {
                ACTION_LIKE -> player.currentMediaItem?.toSong()?.let { song ->
                    LibraryStore.setLiked(song, !LibraryStore.isLiked(song.videoId))
                }
                ACTION_SHUFFLE -> player.shuffleModeEnabled = !player.shuffleModeEnabled
                else -> return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
            }
            refreshButtons()
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        /**
         * "Resume" from the system's media controls or a Bluetooth play button
         * when the app isn't running: the queue saved last time, where it was.
         */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val saved = QueueStore.load()
                ?: return Futures.immediateFailedFuture(UnsupportedOperationException("Nothing to resume"))
            val items = saved.songs.map { it.toSong().toMediaItem() }
            if (items.isEmpty()) return Futures.immediateFailedFuture(UnsupportedOperationException("Nothing to resume"))
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(items, saved.index.coerceIn(items.indices), saved.positionMs.coerceAtLeast(0)),
            )
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(
            mediaItems.map(car::toQueueItem).toMutableList(),
        )

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
            car.resolve(mediaItems, startIndex, startPositionMs)
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> = Futures.immediateFuture(
            LibraryResult.ofItem(car.root(), LibraryParams.Builder().setExtras(car.rootExtras()).build()),
        )

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = Futures.immediateFuture(
            car.item(mediaId)?.let { LibraryResult.ofItem(it, null) } ?: LibraryResult.ofError(SessionError.ERROR_BAD_VALUE),
        )

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
            try {
                LibraryResult.ofItemList(car.children(parentId, browser).page(page, pageSize), params)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't list $parentId for ${browser.packageName}", e)
                LibraryResult.ofError(SessionError.ERROR_IO)
            }
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> = scope.future {
            val count = runCatching { car.search(query, browser).also { searchResults[query] = it }.size }
                .onFailure { Log.w(TAG, "Search failed for ${browser.packageName}", it) }
                .getOrDefault(0)
            session.notifySearchResultChanged(browser, query, count, params)
            LibraryResult.ofVoid()
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
            val items = searchResults[query] ?: runCatching { car.search(query, browser) }.getOrDefault(emptyList())
            LibraryResult.ofItemList(items.page(page, pageSize), params)
        }
    }

    /** Like (heart) and Shuffle, as the notification and Android Auto show them now. */
    private fun mediaButtons(): List<CommandButton> {
        val player = mediaSession?.player
        val liked = player?.currentMediaItem?.mediaId?.let(LibraryStore::isLiked) == true
        val shuffle = player?.shuffleModeEnabled == true
        return listOf(
            CommandButton.Builder(if (liked) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED)
                .setDisplayName(if (liked) "Remove from Liked" else "Like")
                .setSessionCommand(LIKE_COMMAND)
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build(),
            CommandButton.Builder(if (shuffle) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF)
                .setDisplayName(if (shuffle) "Shuffle off" else "Shuffle on")
                .setSessionCommand(SHUFFLE_COMMAND)
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build(),
        )
    }

    private fun refreshButtons() {
        mediaSession?.setMediaButtonPreferences(mediaButtons())
        com.grayguava.legato.audio.AppHooks.onPlayerChanged(this, mediaSession?.player)
    }

    /** The last browser search's results, between onSearch and onGetSearchResult. */
    private val searchResults = object : LinkedHashMap<String, List<MediaItem>>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<MediaItem>>?) = size > 4
    }

    private fun List<MediaItem>.page(page: Int, pageSize: Int): List<MediaItem> =
        if (pageSize <= 0 || pageSize == Int.MAX_VALUE) this else drop(page * pageSize).take(pageSize)

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_TIMELINE_CHANGED)) {
                queueRadio.noteQueued((0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId })
            }
            if (events.containsAny(
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_MEDIA_METADATA_CHANGED,
                )
            ) {
                refreshButtons()
            }
            if (events.containsAny(
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                    Player.EVENT_REPEAT_MODE_CHANGED,
                    // Asked again as playback starts and ends, in case an earlier ask came to nothing.
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                )
            ) {
                queueRadio.extend()
                warmNeighbours()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // The cache keeps the track's name alongside its audio, so the Cached songs
            // folder can list it. Before the swap check below: the upgraded copy is cached
            // under its own key and is named the same way.
            mediaItem?.toSong()?.let { song -> scope.launch(Dispatchers.IO) { AudioCache.note(this@PlaybackService, song) } }
            artistCredits.refresh(mediaItem)
            // The same song put onto its better stream: the swap that did it is
            // still running, waiting to bring the volume back up. A new upgrade
            // check here used to cancel it and leave every song after silent.
            val swap = mediaItem != null && mediaItem.mediaId == upgrader.swappingTo
            // A quality switch says what's playing once the new stream really is (see swapToUpgrade).
            if (!swap) upgrader.updateExternalStats()
            loudness.apply()
            if (!swap) upgrader.scheduleUpgrade()
            // After this callback, not inside it: the queue changes under it otherwise.
            if (!swap) scope.launch(Dispatchers.Main) { dropPlayedHandQueued() }
            // Nothing but a blend or a swap turns the main player down; outside
            // one, a new song always starts at full level.
            (mediaSession?.player as? ExoPlayer)?.let { p ->
                if (!swap && !upgrader.swapRamping && crossfade?.blending != true && p.volume < 1f) {
                    Log.w(TAG, "Player volume was left at ${p.volume}; back to full")
                    p.volume = 1f
                }
            }
            watchSegments(mediaItem)
            // A podcast episode, or anything else much longer than a song, picks up where it was left.
            mediaItem?.mediaId?.let { com.grayguava.legato.library.ResumePoints.resumeAt(it) }?.let { at ->
                mediaSession?.player?.takeIf { it.currentPosition < RESUME_SLACK_MS }?.seekTo(at)
            }
            // The same song on a better stream is not a new listen.
            if (mediaItem != null && mediaItem.mediaId == upgrader.swappingTo) {
                upgrader.swappingTo = null
                return
            }
            listenTracker.finishListen()
            listenTracker.startListen(mediaItem?.toSong(), mediaSession?.player?.isPlaying == true)
            // Only time starts meant to sound now, not a skip made while paused.
            startRequestedAt = if (mediaSession?.player?.playWhenReady == true) SystemClock.elapsedRealtime() else 0L
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            mediaSession?.player?.let { watchPlayed(it, isPlaying) }
            if (!isPlaying) mediaSession?.player?.let { listenTracker.saveEpisodePosition(it) }
            if (isPlaying && startRequestedAt > 0) {
                NerdStats.onStartup(SystemClock.elapsedRealtime() - startRequestedAt)
                startRequestedAt = 0
            }
            if (isPlaying) {
                listenTracker.sendNowPlaying()
                listenTracker.markResumed()
            } else {
                listenTracker.pauseListen()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            watchStall(playbackState)
            if (playbackState == Player.STATE_READY) {
                upgrader.updateExternalStats()
                // The figure arrives with the stream; on a first play it's
                // only known once the track has resolved.
                loudness.apply()
                if (mediaSession?.player?.currentMediaItem?.localConfiguration?.uri?.let(::startupToken) != null && !upgrader.isUpgrading) upgrader.scheduleUpgrade()
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            val format = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
                ?.let { g -> (0 until g.length).firstOrNull(g::isTrackSelected)?.let(g::getTrackFormat) } ?: return
            val item = mediaSession?.player?.currentMediaItem
            // The DAC follows the song's rate by itself: it's set as each song's output opens.
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            loudness.release()
            loudness.openEffectSession(audioSessionId)
            loudness.apply()
        }

        override fun onPlayerError(error: PlaybackException) {
            val item = mediaSession?.player?.currentMediaItem
            Log.e(TAG, "Playback error on ${item?.mediaId}: ${error.errorCodeName}", error)
            recover(error)
        }
    }


    // ---- Crossfade ----------------------------------------------------------------

    private var crossfade: Crossfade? = null



    // ---- Latency -----------------------------------------------------------------

    /** When the current item was asked for, to time how long it took to sound. */
    private var startRequestedAt = 0L

    private var warmJob: Job? = null

    /** Video ids whose stream URL was warmed, and when; StreamResolver holds them 20 minutes. */
    private val warmedAt = ConcurrentHashMap<String, Long>()

    /**
     * Resolve stream URLs for the tracks either side of this one in play
     * order (the next two and the previous one) before they're asked for.
     * Finding a working stream is the slow part of starting a track, a few
     * YouTube requests; with the URL already in [StreamResolver]'s cache a
     * skip or a jump back only has to fetch audio. ExoPlayer's own preload
     * then buffers the very next one.
     */
    private fun warmNeighbours() {
        // Maximum also starts immediately; warm its normal stream while quality
        // searches run independently, so next/previous do not repeat resolution.
        if (!AppSettings.playback.value.preloadUpcoming) return
        val player = mediaSession?.player ?: return
        val timeline = player.currentTimeline
        if (timeline.isEmpty) return
        val current = player.currentMediaItemIndex
        val previous = timeline.getPreviousWindowIndex(current, Player.REPEAT_MODE_OFF, player.shuffleModeEnabled)
        val indices = player.upcomingPlayOrder().drop(1).take(WARM_AHEAD) +
            listOfNotNull(previous.takeIf { it != C.INDEX_UNSET })
        val now = SystemClock.elapsedRealtime()
        val ids = indices.map { player.getMediaItemAt(it).mediaId }
            .filter { isYouTubeId(it) && now - (warmedAt[it] ?: 0L) > WARM_TTL_MS }
            .distinct()
        if (ids.isEmpty()) return
        ids.forEach { warmedAt[it] = now }
        if (warmedAt.size > 64) warmedAt.entries.removeAll { now - it.value > WARM_TTL_MS }

        val songs = indices.map { player.getMediaItemAt(it).toSong() }.filter { it.videoId in ids }.distinctBy { it.videoId }
        val previousJob = warmJob
        warmJob = scope.launch(Dispatchers.IO) {
            previousJob?.join()
            // One at a time: these compete with the playing track for bandwidth.
            for (id in ids) {
                runCatching { StreamResolver.resolve(id) }
                    .onFailure { e ->
                        if (e is CancellationException) throw e
                        Log.w(TAG, "Warm-up for $id failed: ${e.message}")
                        warmedAt.remove(id)
                    }
            }
        }
    }

    // ---- SponsorBlock ------------------------------------------------------------

    private var segmentsJob: Job? = null

    /**
     * Looks up [item]'s SponsorBlock segments and, while it plays, jumps
     * over each one the first time playback enters it. Seeking back into a
     * segment afterwards plays it: that was asked for.
     */
    private fun watchSegments(item: MediaItem?) {
        segmentsJob?.cancel()
        val id = item?.mediaId ?: return
        val pb = AppSettings.playback.value
        if (!pb.sponsorBlock || !isYouTubeId(id)) return
        segmentsJob = scope.launch {
            val segments = SponsorBlock.segments(id, pb.sponsorBlockCategories)
            if (segments.isEmpty()) return@launch
            val player = mediaSession?.player ?: return@launch
            val skipped = HashSet<String>()
            while (player.currentMediaItem?.mediaId == id && skipped.size < segments.size) {
                delay(if (player.isPlaying) SEGMENT_POLL_MS else SEGMENT_IDLE_POLL_MS)
                if (!player.isPlaying) continue
                val at = player.currentPosition
                val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
                val segment = segments.firstOrNull {
                    it.uuid !in skipped && at >= it.startMs && at < it.endMs - SEGMENT_END_SLACK_MS && SponsorBlock.fits(it, duration)
                } ?: continue
                skipped += segment.uuid
                Log.i(TAG, "SponsorBlock: skipping ${segment.category} ${segment.startMs}-${segment.endMs} ms")
                if (duration > 0 && segment.endMs >= duration - SEGMENT_END_SLACK_MS) {
                    // The segment runs to the end: that's the song over.
                    if (player.hasNextMediaItem()) player.seekToNextMediaItem() else player.seekTo(duration)
                } else {
                    player.seekTo(segment.endMs)
                }
                com.grayguava.legato.base.Pills.done("Skipped ${SponsorBlock.CATEGORIES[segment.category]?.one ?: "a segment"}", this@PlaybackService)
            }
        }
    }


    /**
     * What to do when the current track fails:
     *  - an upgraded stream goes back to the stream the track started on, at
     *    the same place, rather than costing the track;
     *  - anything that may pass is retried once;
     *  - then the track is skipped, but only up to [MAX_FAILED_IN_ROW] tracks
     *    in a row. Past that, every stream is failing for the same reason
     *    (network, a YouTube change), and skipping on would only run through
     *    the queue and autoplay without a sound. Playback stops on the error
     *    instead, so it's shown, and play tries again.
     */
    private var stallJob: Job? = null

    /**
     * A better stream that stops to buffer mid-song and doesn't come back
     * within [STALL_FALLBACK_MS] goes back to the stream the song started on,
     * from where it stopped, the way a failed one does: a hi-res file over a
     * weak connection shouldn't leave the music standing still.
     */
    private fun watchStall(state: Int) {
        stallJob?.cancel()
        if (state != Player.STATE_BUFFERING) return
        val player = mediaSession?.player as? ExoPlayer ?: return
        val item = player.currentMediaItem ?: return
        val uri = item.localConfiguration?.uri ?: return
        if (!isUpgradedUri(uri)) return
        // Only mid-song: the start and a seek buffer by design.
        if (player.currentPosition < STALL_MIN_POSITION_MS) return
        val at = player.currentPosition
        stallJob = scope.launch {
            delay(STALL_FALLBACK_MS)
            if (player.playbackState != Player.STATE_BUFFERING || !player.playWhenReady) return@launch
            if (player.currentMediaItem?.localConfiguration?.uri != uri || kotlin.math.abs(player.currentPosition - at) > 1_000) return@launch
            Log.w(TAG, "Better stream for ${item.mediaId} stalled for ${STALL_FALLBACK_MS}ms at ${at}ms; back to the one before it")
            StreamResolver.dropUpgrade(item.mediaId)
            val stalled = selectedUri(uri)
            val index = player.currentMediaItemIndex
            upgrader.swappingTo = item.mediaId
            player.replaceMediaItem(index, item.buildUpon().setUri(fallbackUri(item.mediaId, stalled)).setMimeType(null).build())
            upgrader.updateExternalStats()
            loudness.apply()
            player.seekTo(index, at)
            player.prepare()
            PlaybackRequests.report("The better stream kept stopping, so this song went back to the one before it")
            upgradeAgainSoon(item.mediaId)
        }
    }

    /**
     * Where [mediaId] goes when its better stream [failed] or stalled: back to the
     * stream it played before the switch, else YouTube's own.
     */
    private fun fallbackUri(mediaId: String, failed: android.net.Uri?): android.net.Uri {
        upgrader.beforeSwap?.takeIf { it.first == mediaId }?.second?.takeIf { it != failed }
            ?.let { return it }
        return youtubeFallbackUri(mediaId)
    }

    /**
     * After a fallback, the upgrade runs again once the song is sounding: the file that
     * failed is off the list, so the next verified one is tried, rather than the song
     * staying on the fallback to its end.
     */
    private fun upgradeAgainSoon(mediaId: String) {
        scope.launch {
            delay(FALLBACK_RETRY_MS)
            val player = mediaSession?.player ?: return@launch
            if (player.currentMediaItem?.mediaId == mediaId && !upgrader.isUpgrading) upgrader.scheduleUpgrade()
        }
    }

    private var offlineRecovery: Job? = null

    private fun recover(error: PlaybackException) {
        val player = mediaSession?.player as? ExoPlayer ?: return
        val item = player.currentMediaItem ?: return
        val mediaId = item.mediaId
        val uri = item.localConfiguration?.uri?.let(::selectedUri)



        // Loss of connectivity is not evidence that this recording or source is bad.
        // Keep the queue, source and position; resume only if the listener still wants play.
        if (!AppSettings.isOnline() && error.errorCode in IO_ERRORS) {
            offlineRecovery?.cancel()
            offlineRecovery = scope.launch {
                while (player.currentMediaItem?.mediaId == mediaId && player.playWhenReady) {
                    delay(2_000)
                    if (AppSettings.isOnline()) {
                        if (player.currentMediaItem?.mediaId == mediaId && player.playWhenReady && player.playerError != null) player.prepare()
                        break
                    }
                }
            }
            return
        }
        if (uri != null && isUpgradedUri(uri)) {
            Log.w(TAG, "Upgraded stream for $mediaId failed (${error.errorCodeName}); back to the one before it")
            StreamResolver.dropUpgrade(mediaId)
            val index = player.currentMediaItemIndex
            val position = player.currentPosition
            upgrader.swappingTo = mediaId
            player.replaceMediaItem(index, item.buildUpon().setUri(fallbackUri(mediaId, uri)).setMimeType(null).build())
            upgrader.updateExternalStats()
            loudness.apply()
            player.seekTo(index, position)
            player.prepare()
            upgradeAgainSoon(mediaId)
            return
        }
        val permanent = generateSequence<Throwable>(error) { it.cause }
            .any { it is StreamResolver.PermanentlyUnplayableException }

        if (!permanent && retriedMediaId != mediaId) {
            Log.w(TAG, "Retrying $mediaId after ${error.errorCodeName}")
            retriedMediaId = mediaId
            player.prepare()
            return
        }
        failedInRow++
        if (failedInRow >= MAX_FAILED_IN_ROW) {
            Log.w(TAG, "$failedInRow tracks in a row failed (last: $mediaId, ${error.errorCodeName}); stopping")
            failedInRow = 0
            return
        }
        if (player.hasNextMediaItem()) {
            Log.w(TAG, "Skipping $mediaId after ${error.errorCodeName}")
            player.seekToNextMediaItem()
            player.prepare()
        }
    }

    /** Tracks in a row that failed and were skipped; see [recover]. */
    private var failedInRow = 0
    private var playedCheck: Job? = null

    /**
     * A track counts as working once it has really played for a while, not
     * when it first reports ready: a stream refused a few seconds in is
     * ready first and fails after. Until then its retry stays spent and
     * the failures in a row stand.
     */
    private fun watchPlayed(player: Player, playing: Boolean) {
        playedCheck?.cancel()
        if (!playing) return
        val id = player.currentMediaItem?.mediaId ?: return
        playedCheck = scope.launch {
            delay(PLAYED_OK_MS)
            if (player.isPlaying && player.currentMediaItem?.mediaId == id) {
                failedInRow = 0
                retriedMediaId = null
            }
        }
    }

    private companion object {
        const val TAG = "PlaybackService"

        /**
         * The volume broadcast comes from the system, so the receiver is
         * exported; a spoofed one only makes the gain re-read the real volume.
         */
        val VOLUME_RECEIVER_FLAGS = if (android.os.Build.VERSION.SDK_INT >= 33) Context.RECEIVER_EXPORTED else 0
        const val QUEUE_SAVE_MS = 5_000L
        /** After a fallback, how long the song sounds before the upgrade runs again. */
        const val FALLBACK_RETRY_MS = 3_000L
        /** Songs a server queue carries on with when it runs out. */
        const val RADIO_FROM_SERVER = 25
        const val RADIO_REQUEST_MS = 12_000L
        /** With "end of song", how long the song fades out before it stops. */
        const val END_FADE_MS = 15_000L
        /** Waits before each ask for radio: at once, then twice more if it fails. */
        val RADIO_RETRY_MS = longArrayOf(0L, 3_000L, 10_000L)
        /** Whole rounds of asking, when a round brings nothing: now, then again while the song plays. */
        val RADIO_AGAIN_MS = longArrayOf(0L, 20_000L, 45_000L, 90_000L)
        /** An episode only jumps to its saved place if it hasn't got going yet. */
        const val RESUME_SLACK_MS = 5_000L
        const val SEGMENT_POLL_MS = 250L
        const val SEGMENT_IDLE_POLL_MS = 1_000L
        const val SEGMENT_END_SLACK_MS = 500L
        const val ACTION_LIKE = "com.grayguava.legato.LIKE"
        const val ACTION_SHUFFLE = "com.grayguava.legato.SHUFFLE"
        val LIKE_COMMAND = SessionCommand(ACTION_LIKE, Bundle.EMPTY)
        val SHUFFLE_COMMAND = SessionCommand(ACTION_SHUFFLE, Bundle.EMPTY)
        /** Failed tracks in a row before playback stops on the error. */
        const val MAX_FAILED_IN_ROW = 3
        /** Playing this long means the track's stream really works. */
        const val PLAYED_OK_MS = 10_000L
        /** Network and input errors (Media3's 2000s): what a lost connection fails with. */
        val IO_ERRORS = 2000..2999
        /** Stuck buffering this long mid-song, a better stream gives way to the regular one. */
        const val STALL_FALLBACK_MS = 6_000L
        const val STALL_MIN_POSITION_MS = 3_000L
        const val FAR_BUFFER_MS = 15 * 60 * 1000
        const val FAR_BUFFER_BYTES = 48 * 1024 * 1024
        const val BACK_BUFFER_MS = 30_000

        /** How much of the next track ExoPlayer buffers ahead of time. */
        const val PRELOAD_US = 10_000_000L

        /** Tracks ahead of the current one whose stream URLs are resolved early. */
        const val WARM_AHEAD = 2

        /** A little under StreamResolver's 20-minute URL lifetime. */
        const val WARM_TTL_MS = 15 * 60 * 1000L
    }
}



