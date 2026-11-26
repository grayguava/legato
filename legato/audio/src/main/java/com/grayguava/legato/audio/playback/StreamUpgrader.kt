package com.grayguava.legato.audio.playback

import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.grayguava.legato.base.DebugLog as Log
import com.grayguava.legato.base.NerdStats
import com.grayguava.legato.settings.AppSettings
import com.grayguava.legato.source.innertube.StreamResolver
import com.grayguava.legato.source.isYouTubeId
import com.grayguava.legato.source.local.LocalMusic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

    /** An upgrade is fetched this far past the playing position before it's swapped in. */
    internal const val AUDITION_AHEAD_MS: Long = 2_000L
    /** It starts loading a little ahead, for the time the loading itself takes. */
    internal const val AUDITION_LEAD_MS: Long = 1_500L
    internal const val AUDITION_TIMEOUT_MS: Long = 12_000L

/**
 * The better stream of the song that is playing.
 *
 * As each song starts, a better stream of it is looked for and fetched ahead
 * of the player needing it, then swapped to in place once the song is sounding
 * with a little buffered. The swap is the delicate part: the sound has to
 * carry across without a gap or a click, and a stream that turns out not to
 * play has to leave the song where it was rather than cost it.
 */
internal class StreamUpgrader(
    private val scope: CoroutineScope,
    private val loudness: LoudnessControl,
    private val player: () -> ExoPlayer?,
    private val crossfade: () -> Crossfade?,
    private val selectedUri: (Uri) -> Uri,
    private val startupToken: (Uri) -> String?,
    private val startupSelection: (String) -> StartupSelection?,
) {
    /** The stream the song last switched from, to go back to if the new one fails. */
    var beforeSwap: Pair<String, Uri>? = null

    /** Whether an upgrade is being looked for or fetched right now. */
    val isUpgrading: Boolean get() = upgradeJob?.isActive == true


    private var upgradeJob: Job? = null

    /** The song being swapped onto its better stream; its transition isn't a new listen. */
    var swappingTo: String? = null

    /**
     * As each song starts, looks for a better stream of it ([StreamResolver.findUpgrade])
     * and swaps to it in place once the song is sounding with a little buffered, so the
     * search runs alongside the start rather than after it. Also run from the player's menu.
     */
    fun scheduleUpgrade(
        force: Boolean = false,
        /** Asked for from the menu: "upgrading" shows and the outcome is told. */
        asked: Boolean = force,
    ) {
        upgradeJob?.cancel()
        NerdStats.onUpgrade(null)
        if (!force && !AppSettings.playback.value.qualityUpgrade) return
        val player = player() ?: return
        val item = player.currentMediaItem ?: return
        val uri = item.localConfiguration?.uri ?: return
        val id = videoIdOf(uri) ?: return
        upgradeAsked = asked
        if (asked) NerdStats.onUpgrade(id)
        // Asked for from the menu: say how it went, whatever the outcome.
        fun tell(message: String) { if (asked) PlaybackRequests.report(message) }
        fun swapped(ok: Boolean) = tell(
            if (ok) "Switched to a better stream"
            else "The better stream didn't load in time, so this one keeps playing",
        )
        /**
         * Before a swap: the song is playing with some of it buffered, so the
         * second stream's fetch doesn't hold up the first one's start. Paused,
         * or slow to start, it goes ahead after a while anyway.
         */
        suspend fun started() {
            if (asked) return
            kotlinx.coroutines.withTimeoutOrNull(UPGRADE_START_WAIT_MS) {
                while (!(player.isPlaying && player.currentPosition >= UPGRADE_SETTLE_MS && player.totalBufferedDuration >= UPGRADE_BUFFERED_MS)) delay(50)
            }
        }
        upgradeJob = scope.launch {
            val startup = startupToken(uri)?.let { startupSelection(it) }
            if (startup != null && startup.selected == null) {
                if (kotlinx.coroutines.withTimeoutOrNull(30_000) {
                    while (startup.selected == null) delay(100)
                    true
                } != true) return@launch
            }
            val playing = player.currentMediaItem?.localConfiguration?.uri ?: return@launch
            if (isUpgradedUri(playing)) { tell("Already playing the best quality there is for this song"); return@launch }
            if (!force && !AppSettings.playback.value.qualityUpgrade) return@launch
            val found = withContext(Dispatchers.IO) { runCatching { StreamResolver.findUpgrade(id, force) }.getOrDefault(false) }
            if (player.currentMediaItem?.mediaId != id) return@launch
            if (!found) { tell("No better stream found; this is the best quality there is for this song"); return@launch }
            started()
            if (player.currentMediaItem?.mediaId != id) return@launch
            val left = player.duration - player.currentPosition
            if (player.duration > 0 && left < UPGRADE_MIN_REMAINING_MS) { tell("Too close to the end of the song to switch"); return@launch }
            swapped(swapToUpgrade(player, id))
        }
        val currentJob = upgradeJob
        currentJob?.invokeOnCompletion { if (upgradeJob === currentJob) NerdStats.onUpgrade(null) }
    }

    /**
     * The safety net under the upgrade. Every few seconds while a song plays, with nothing
     * already at work on it: if a better stream is known for it than the one playing, or its
     * FLAC search never ran or came back empty only because servers erred, the upgrade runs
     * again, a few times a song at most. Skipping fast, a search cut short, a switch that
     * didn't load or a fallback all used to leave a song on a lower stream to its end, with
     * a verified FLAC on record.
     */
    suspend fun guardQuality() {
        var song: String? = null
        var tries = 0
        var lastAt = 0L
        while (true) {
            delay(GUARD_EVERY_MS)
            val player = player() ?: continue
            val item = player.currentMediaItem ?: continue
            val id = item.mediaId
            val now = SystemClock.elapsedRealtime()
            if (id != song) { song = id; tries = 0; lastAt = now - GUARD_GAP_MS }
            if (!player.isPlaying || upgradeJob?.isActive == true || swappingTo != null || swapRamping || crossfade()?.blending == true) continue
            if (!isYouTubeId(id)) continue
            val raw = item.localConfiguration?.uri ?: continue
            if (raw.scheme == "file") continue
            if (startupToken(raw) != null && startupToken(raw)?.let { startupSelection(it)?.selected } == null) continue
            if (player.duration > 0 && player.duration - player.currentPosition < UPGRADE_MIN_REMAINING_MS + GUARD_EVERY_MS) continue
            if (tries >= GUARD_TRIES) continue
            val uri = selectedUri(raw)
            // Already on YouTube's better stream: nothing to do.
            if (isUpgradedUri(uri)) continue
            if (player.currentPosition < GUARD_SETTLE_MS) continue
            if (now - lastAt < GUARD_GAP_MS) continue
            tries++
            lastAt = now
            Log.d(TAG, "Quality check for $id: upgrading again (try $tries)")
            scheduleUpgrade()
        }
    }

    /** Why the last stream [prebuffer] or the bridge tried didn't load. */
    private var loadFailure: String? = null

    /** What went wrong on [p], which was asked to load from [from]: an error, or how little arrived in time. */
    private fun whyNotLoaded(p: ExoPlayer, from: Long): String {
        val error = p.playerError
        if (error != null) {
            var cause: Throwable? = error
            while (cause != null) {
                if (cause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) return "its server answered HTTP ${cause.responseCode}"
                cause = cause.cause
            }
            return "playback error (${error.errorCodeName.removePrefix("ERROR_CODE_").lowercase().replace('_', ' ')})"
        }
        if (p.playbackState == Player.STATE_ENDED) return "its stream ended as soon as it started"
        val got = (p.bufferedPosition - from).coerceAtLeast(0L) / 1000
        return "only ${got}s of audio arrived in ${AUDITION_TIMEOUT_MS / 1000}s"
    }

    /** Builds the silent player [prebuffer] loads an upgrade with; set in [onCreate]. */
    var auditionPlayer: (() -> ExoPlayer)? = null

    /**
     * Loads [item] on a silent player from a little ahead of where [main] is,
     * until enough past [main]'s position is fetched to cover the swap, so the
     * swapped-in stream plays from the cache rather than the network. False
     * when the stream fails, ends, or doesn't fill in time: then there's no swap.
     */
    private suspend fun prebuffer(main: ExoPlayer, item: MediaItem, superseded: () -> Boolean): Boolean {
        val make = auditionPlayer ?: return true
        val audition = make()
        loadFailure = null
        val from = main.currentPosition + AUDITION_LEAD_MS
        return try {
            audition.setMediaItem(item)
            audition.seekTo(from)
            audition.prepare()
            val result = kotlinx.coroutines.withTimeoutOrNull(AUDITION_TIMEOUT_MS) {
                awaitUpgradeBuffer(
                    superseded = superseded,
                    invalid = { audition.playerError != null || audition.playbackState == Player.STATE_ENDED || main.currentMediaItem?.mediaId != item.mediaId },
                    ready = {
                        val covered = audition.bufferedPosition >= main.currentPosition + AUDITION_AHEAD_MS ||
                            (audition.duration > 0 && audition.bufferedPosition >= audition.duration - 500)
                        audition.playbackState == Player.STATE_READY && covered
                    },
                )
            }
            (result == UpgradeBufferResult.READY).also {
                if (!it && result != UpgradeBufferResult.SUPERSEDED && main.currentMediaItem?.mediaId == item.mediaId) loadFailure = whyNotLoaded(audition, from)
            }
        } finally {
            audition.release()
            // Let the cache's lock on what it wrote go before the main player reads it.
            delay(250)
        }
    }

    /** Moves [player]'s volume to [to] over [ms], in small steps. */
    private suspend fun rampVolume(player: ExoPlayer, to: Float, ms: Long) {
        val from = player.volume
        val steps = 8
        for (i in 1..steps) {
            player.volume = from + (to - from) * i / steps
            delay(ms / steps)
        }
    }

    /**
     * Swaps the playing song onto its better stream without a stall: the new
     * stream is fetched ahead by [prebuffer], the volume dips for a moment to
     * hide the seam, and the song carries on from where it was. Returns
     * whether it swapped.
     */
    private suspend fun swapToUpgrade(
        player: ExoPlayer, id: String, uri: android.net.Uri = youtubeFallbackUri(id, upgraded = true),
        superseded: () -> Boolean = { false },
        /** A background retry: the switch happens without "upgrading" showing. */
        quiet: Boolean = false,
    ): Boolean {
        if (superseded()) return false
        if (!quiet) NerdStats.onUpgrade(id)
        return try {
            // Done: a note from an earlier try that it couldn't be is no longer true.
            switchStream(player, id, uri, superseded)
        } finally {
            if (!upgradeAsked && upgradeJob?.isActive != true) NerdStats.onUpgrade(null)
        }
    }

    /** Whether the running upgrade was asked for from the menu, so "upgrading" stays up through all of it. */
    private var upgradeAsked = false

    /** What [uri] plays, for the log: the external source's label, or YouTube's better stream. */
    private fun streamName(id: String, uri: android.net.Uri): String =
        "YouTube's better stream"

    private suspend fun switchStream(player: ExoPlayer, id: String, uri: android.net.Uri, superseded: () -> Boolean): Boolean {
        Log.d(TAG, "Switching $id to ${streamName(id, uri)}${if (player.isPlaying) "" else " (not playing yet, so without the bridge)"}")
        val index = player.currentMediaItemIndex
        val item = player.currentMediaItem?.takeIf { it.mediaId == id } ?: return false
        item.localConfiguration?.uri?.let(selectedUri)?.let { beforeSwap = id to it }
        val next = item.buildUpon().setUri(uri).build()
        loadFailure = null
        return performUpgradeHandoff(
            bridge = {
                if (player.isPlaying && !BitPerfectUsb.active) bridgedSwap(player, index, id, next, superseded)
                else UpgradeBridgeResult.UNAVAILABLE
            },
            stillCurrent = {
                !superseded() && player.currentMediaItemIndex == index && player.currentMediaItem?.mediaId == id &&
                    player.currentMediaItem?.localConfiguration?.uri == item.localConfiguration?.uri && crossfade()?.blending != true
            },
            prebuffer = { prebuffer(player, next, superseded) },
            replace = { replaceUpgrade(player, index, id, next) },
        )
    }

    private suspend fun replaceUpgrade(player: ExoPlayer, index: Int, id: String, next: MediaItem): Boolean {
        val original = player.currentMediaItem ?: return false
        // The queue may have moved on, or a blend begun, while it loaded.
        if (player.currentMediaItemIndex != index || player.currentMediaItem?.mediaId != id || crossfade()?.blending == true) return false
        val smooth = player.isPlaying && !BitPerfectUsb.active
        // Outside a blend (ruled out above) the main player plays at full level.
        val level = 1f
        swapRamping = smooth
        try {
            if (smooth) rampVolume(player, 0f, SWAP_DIP_MS)
            val position = player.currentPosition
            // Through the switch the song is silent but carries on; the lyrics carry on with it.
            if (smooth) {
                val at = SystemClock.elapsedRealtime()
                PlaybackRequests.soundingPosition = { position + (SystemClock.elapsedRealtime() - at) }
            }
            swappingTo = id
            // If the player updates the item in place there's no transition to clear this.
            scope.launch { delay(2_000); if (swappingTo == id) swappingTo = null }
            player.replaceMediaItem(index, next)
            loudness.apply()
            if (player.currentMediaItemIndex != index || player.currentPosition < position - 1_000) player.seekTo(index, position)
            if (!awaitUpgradeReady(player, next)) {
                restoreFailedUpgrade(player, index, original, next, position)
                return false
            }
            PlaybackRequests.soundingPosition = null
            if (smooth) rampVolume(player, level, SWAP_RISE_MS)
            // Only now does the label say the better quality: it's what's heard.
            updateExternalStats()
            Log.d(TAG, "Swapped $id to ${streamName(id, next.localConfiguration!!.uri)} at ${position}ms")
            return true
        } finally {
            PlaybackRequests.soundingPosition = null
            swapRamping = false
            // Cancelled or failed between the dip and the rise: never leave the music silent.
            if (smooth && crossfade()?.blending != true && player.volume < level) player.volume = level
        }
    }

    /** Do not report a queue URI change as success before the new stream is decoding. */
    private suspend fun awaitUpgradeReady(player: ExoPlayer, next: MediaItem): Boolean =
        kotlinx.coroutines.withTimeoutOrNull(BRIDGE_CATCH_UP_MS) {
            val uri = next.localConfiguration?.uri
            while (true) {
                if (player.currentMediaItem?.mediaId != next.mediaId ||
                    player.currentMediaItem?.localConfiguration?.uri != uri || player.playerError != null ||
                    player.playbackState == Player.STATE_ENDED) return@withTimeoutOrNull false
                if (player.playbackState == Player.STATE_READY) {
                    if (player.audioFormat?.sampleMimeType != null) return@withTimeoutOrNull true
                }
                delay(30)
            }
            @Suppress("UNREACHABLE_CODE") false
        } == true

    private fun restoreFailedUpgrade(player: ExoPlayer, index: Int, original: MediaItem, attempted: MediaItem, position: Long) {
        loadFailure = "the main player did not start decoding the requested stream"
        // Never overwrite a user skip or the error listener's newer fallback.
        if (player.currentMediaItemIndex == index && player.currentMediaItem?.mediaId == original.mediaId &&
            player.currentMediaItem?.localConfiguration?.uri == attempted.localConfiguration?.uri) {
            swappingTo = original.mediaId
            scope.launch { delay(2_000); if (swappingTo == original.mediaId) swappingTo = null }
            player.replaceMediaItem(index, original)
            player.seekTo(index, position)
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
        }
        updateExternalStats()
    }

    /** True while a swap has the volume dipped, so nothing else puts it back mid-ramp. */
    var swapRamping = false

    /**
     * The swap with nothing to hear: a second player (the "bridge") loads the
     * better stream ahead, starts it silently in step with the song, and takes
     * the sound over in a short crossfade. The main player changes streams
     * while silent, catches up with the bridge, and takes the sound back the
     * same way. Its rebuffering after the change happens while the bridge is
     * the one sounding, so no gap or dip reaches the ears.
     *
     * A loaded stream that cannot align is reused by the plain replacement.
     * A failed load moves to the next verified candidate instead of loading the same file twice.
     */
    private suspend fun bridgedSwap(player: ExoPlayer, index: Int, id: String, next: MediaItem, superseded: () -> Boolean): UpgradeBridgeResult {
        val make = auditionPlayer ?: return UpgradeBridgeResult.UNAVAILABLE
        val original = player.currentMediaItem?.takeIf { it.mediaId == id } ?: return UpgradeBridgeResult.ABORTED
        val bridge = make()
        swapRamping = true
        var handedOver = false
        try {
            if (player.audioSessionId != C.AUDIO_SESSION_ID_UNSET) bridge.setAudioSessionId(player.audioSessionId)
            bridge.playbackParameters = player.playbackParameters
            bridge.setMediaItem(next)
            val from = player.currentPosition + AUDITION_LEAD_MS
            bridge.seekTo(from)
            bridge.prepare()
            val prepared = kotlinx.coroutines.withTimeoutOrNull(AUDITION_TIMEOUT_MS) {
                awaitUpgradeBuffer(
                    superseded = superseded,
                    invalid = { bridge.playerError != null || bridge.playbackState == Player.STATE_ENDED || player.currentMediaItem?.mediaId != id },
                    ready = {
                        val covered = bridge.bufferedPosition >= player.currentPosition + AUDITION_AHEAD_MS ||
                            (bridge.duration > 0 && bridge.bufferedPosition >= bridge.duration - 500)
                        bridge.playbackState == Player.STATE_READY && covered
                    },
                )
            }
            if (prepared == UpgradeBufferResult.SUPERSEDED || superseded()) return UpgradeBridgeResult.ABORTED
            val loaded = prepared == UpgradeBufferResult.READY
            if (!loaded) {
                if (player.currentMediaItem?.mediaId == id) loadFailure = whyNotLoaded(bridge, from)
                Log.w(TAG, "The better stream for $id didn't load ($loadFailure); trying another verified source")
                return UpgradeBridgeResult.ABORTED
            }
            // The queue may have moved on or a blend begun while it loaded.
            if (player.currentMediaItemIndex != index || player.currentMediaItem?.mediaId != id || crossfade()?.blending == true) return UpgradeBridgeResult.ABORTED
            // Paused while it loaded: the plain swap needs no bridge.
            if (!player.isPlaying) return UpgradeBridgeResult.READY_FOR_REPLACEMENT

            // 1. The bridge plays, silent, in step with the song as it is heard now.
            bridge.volume = 0f
            bridge.playWhenReady = true
            val alignment = lineUp(follower = bridge, leader = player)
            if (superseded()) return UpgradeBridgeResult.ABORTED
            if (player.currentMediaItemIndex != index || player.currentMediaItem?.mediaId != id ||
                player.currentMediaItem?.localConfiguration?.uri != original.localConfiguration?.uri || crossfade()?.blending == true
            ) return UpgradeBridgeResult.ABORTED
            if (!player.isPlaying) return UpgradeBridgeResult.READY_FOR_REPLACEMENT
            if (alignment == UpgradeAlignment.SEEK) {
                Log.w(TAG, "Couldn't line the better stream for $id up with the song; trying the plain swap")
                return UpgradeBridgeResult.READY_FOR_REPLACEMENT
            }
            // 2. The sound moves onto it. From here the bridge is what's heard, so it's
            // also where the lyrics and seek bar read the song's position.
            PlaybackRequests.soundingPosition = { bridge.currentPosition }
            if (alignment == UpgradeAlignment.BLEND) crossfadeVolumes(from = player, to = bridge, BRIDGE_FADE_MS)
            else {
                rampVolume(player, 0f, BRIDGE_CUT_MS)
                rampVolume(bridge, 1f, BRIDGE_CUT_MS)
            }
            handedOver = true

            // 3. Silent now, the main player changes streams and catches up with the bridge.
            swappingTo = id
            scope.launch { delay(4_000); if (swappingTo == id) swappingTo = null }
            player.replaceMediaItem(index, next)
            loudness.apply()
            player.seekTo(index, bridge.currentPosition + BRIDGE_SEEK_LEAD_MS)
            if (!awaitUpgradeReady(player, next)) {
                restoreFailedUpgrade(player, index, original, next, bridge.currentPosition)
                return UpgradeBridgeResult.ABORTED
            }
            // Lined up within a few milliseconds, or the two would be heard as an echo.
            val aligned = lineUp(follower = player, leader = bridge) == UpgradeAlignment.BLEND
            if (!awaitUpgradeReady(player, next)) {
                restoreFailedUpgrade(player, index, original, next, bridge.currentPosition)
                return UpgradeBridgeResult.ABORTED
            }

            // 4. The sound comes back to the main player, now on the better stream: blended
            // when the two play the same moment, otherwise one out before the other comes
            // in, so two copies of the song are never heard at once out of step.
            if (aligned) crossfadeVolumes(from = bridge, to = player, BRIDGE_FADE_MS)
            else {
                Log.w(TAG, "Main player for $id didn't line up with the bridge; handing back without a blend")
                rampVolume(bridge, 0f, BRIDGE_CUT_MS)
                rampVolume(player, 1f, BRIDGE_CUT_MS)
            }
            PlaybackRequests.soundingPosition = null
            handedOver = false
            updateExternalStats()
            Log.d(TAG, "Swapped $id to ${next.localConfiguration?.uri?.let { streamName(id, it) }} at ${player.currentPosition}ms, bridged")
            return UpgradeBridgeResult.COMPLETED
        } finally {
            PlaybackRequests.soundingPosition = null
            swapRamping = false
            bridge.release()
            // However it ended, the main player sounds at full level again.
            if (crossfade()?.blending != true && player.volume < 1f) player.volume = 1f
            if (handedOver) Log.w(TAG, "Bridge for $id ended early; the main player took the sound back")
        }
    }

    /** Small offsets take a short fade immediately; only a large offset needs another seek. */
    private suspend fun lineUp(follower: ExoPlayer, leader: ExoPlayer): UpgradeAlignment = boundedUpgradeAlignment {
        var lead = BRIDGE_SEEK_LEAD_MS
        repeat(BRIDGE_ALIGN_TRIES) {
            if (!settledPlaying(follower)) return@boundedUpgradeAlignment UpgradeAlignment.SEEK
            val drift = follower.currentPosition - leader.currentPosition
            val alignment = upgradeAlignment(drift)
            if (alignment != UpgradeAlignment.SEEK) return@boundedUpgradeAlignment alignment
            lead = (lead - drift).coerceIn(0L, 2_000L)
            follower.seekTo(leader.currentPosition + lead)
        }
        if (settledPlaying(follower)) upgradeAlignment(follower.currentPosition - leader.currentPosition)
        else UpgradeAlignment.SEEK
    }

    /** Waits for [p] to be playing from a buffer, then a moment more for its position to settle. */
    private suspend fun settledPlaying(p: ExoPlayer): Boolean {
        val ok = kotlinx.coroutines.withTimeoutOrNull(3_000) {
            while (p.playbackState != Player.STATE_READY || !p.isPlaying) delay(15)
            true
        } == true
        if (ok) delay(BRIDGE_SETTLE_MS)
        return ok
    }

    /** Equal-power fade from [from] to [to] over [ms]: the level stays even through it. */
    private suspend fun crossfadeVolumes(from: ExoPlayer, to: ExoPlayer, ms: Long) {
        val start = SystemClock.elapsedRealtime()
        while (true) {
            val p = ((SystemClock.elapsedRealtime() - start).toFloat() / ms).coerceIn(0f, 1f)
            from.volume = kotlin.math.cos(p * Math.PI / 2).toFloat()
            to.volume = kotlin.math.sin(p * Math.PI / 2).toFloat()
            if (p >= 1f) break
            delay(10)
        }
    }

    fun updateExternalStats() {
        val item = player()?.currentMediaItem
        val uri = item?.localConfiguration?.uri?.let(selectedUri)
        val provider = when {
            item == null -> "No track selected"
            uri?.let { startupToken(it) } != null -> "Loading audio…"
            LocalMusic.isLocal(item.mediaId) -> "Local file"
            uri?.scheme == "file" -> "Downloaded file"
            else -> StreamResolver.engineFor(item.mediaId, uri?.let(::isUpgradedUri) == true)?.let { "YouTube · $it" } ?: "YouTube · cached stream or resolving"
        }
        NerdStats.onPlaybackSource(NerdStats.Source(provider, null, null,
            if (item != null && uri?.let(::videoIdOf) != null) StreamResolver.bitrateFor(item.mediaId, isUpgradedUri(uri)) else null,
            mediaId = item?.mediaId))
    }

    private companion object {
        const val TAG = "StreamUpgrader"

    /** Bound attempts before using the already buffered stream with a short fade. */
    const val BRIDGE_ALIGN_TRIES = 2
    /** How long the bridge carries the song while the main player loads the new stream. */
    const val BRIDGE_CATCH_UP_MS = 8_000L
    /** Each half of a hand-back without a blend: one player out, then the other in. */
    const val BRIDGE_CUT_MS = 70L
    /** The volume dip around a swap: quickly down, gently back. */
    /** Each crossfade between the main player and the bridge. */
    const val BRIDGE_FADE_MS = 160L
    /** A first guess at how long a seek takes to sound, landed ahead by. */
    const val BRIDGE_SEEK_LEAD_MS = 120L
    const val BRIDGE_SETTLE_MS = 120L
    /** How often [guardQuality] looks, how far into a song it starts, how long between its tries, and how many a song gets. */
    const val GUARD_EVERY_MS = 1_000L
    const val GUARD_GAP_MS = 12_000L
    const val GUARD_SETTLE_MS = 8_000L
    const val GUARD_TRIES = 4
    const val SWAP_DIP_MS = 120L
    const val SWAP_RISE_MS = 260L
    const val UPGRADE_BUFFERED_MS = 2_000L
    const val UPGRADE_MIN_REMAINING_MS = 20_000L
    /** A swap waits for the song to have played this much, with [UPGRADE_BUFFERED_MS] ahead of it... */
    const val UPGRADE_SETTLE_MS = 300L
    /** ...or for this long at most, when it's paused or slow to start. */
    const val UPGRADE_START_WAIT_MS = 6_000L
    }
}
