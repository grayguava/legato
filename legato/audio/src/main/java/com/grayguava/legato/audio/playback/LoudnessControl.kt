package com.grayguava.legato.audio.playback

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import com.grayguava.legato.base.DebugLog as Log
import com.grayguava.legato.base.NerdStats
import com.grayguava.legato.settings.AppSettings
import com.grayguava.legato.settings.VolumeLevel
import com.grayguava.legato.source.innertube.StreamResolver
import com.grayguava.legato.source.isYouTubeId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Loudness normalization the way YouTube does it: one fixed gain per track,
 * from YouTube's own measurement of that track ([StreamResolver.loudnessDbFor]),
 * applied with the platform's LoudnessEnhancer. Loud masters come down to the
 * reference and quiet ones come up a little (at most +3 dB), with no pumping
 * and nothing touching the peaks in between. Without a figure, or with the
 * setting off, the gain is zero and the effect is off.
 *
 * It also keeps the phone's own sound effects (Dolby Atmos, Samsung's
 * SoundAlive and Adapt Sound, the system equalizer and similar) attached to
 * this app the way they are to the stock players. Without that many phones
 * leave Legato unprocessed and it sounds flatter and quieter next to other apps.
 */
internal class LoudnessControl(
    private val context: Context,
    private val scope: CoroutineScope,
    private val player: () -> ExoPlayer?,
    private val pendingStartupSelection: (Uri) -> Boolean,
    private val onPhoneSpeaker: () -> Boolean,
) {
    private var enhancer: LoudnessEnhancer? = null
    private var enhancerSession = C.AUDIO_SESSION_ID_UNSET
    private var effectSession = C.AUDIO_SESSION_ID_UNSET
    private var retry: Job? = null

    /** Tells the system a music session is open, so the phone's own sound effects attach. */
    fun openEffectSession(session: Int) {
        PhoneEqualizer.session = session
        if (!AppSettings.playback.value.systemEffects || BitPerfectUsb.active) return closeEffectSession()
        if (session == C.AUDIO_SESSION_ID_UNSET || session == effectSession) return
        closeEffectSession()
        effectSession = session
        context.sendBroadcast(
            Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
                .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, session)
                .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC),
        )
    }

    fun closeEffectSession() {
        if (effectSession == C.AUDIO_SESSION_ID_UNSET) return
        context.sendBroadcast(
            Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
                .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, effectSession)
                .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName),
        )
        effectSession = C.AUDIO_SESSION_ID_UNSET
    }

    /** Lets go of the platform effect, for when the player goes away. */
    fun release() {
        enhancer?.release()
        enhancer = null
    }

    fun apply() {
        val target = player() ?: return
        val session = target.audioSessionId
        if (session == C.AUDIO_SESSION_ID_UNSET) return
        openEffectSession(session)
        val id = target.currentMediaItem?.mediaId
        val rawUri = target.currentMediaItem?.localConfiguration?.uri
        val pendingSelection = rawUri?.let { pendingStartupSelection(it) } == true
        val db = id?.let { StreamResolver.loudnessDbFor(it) }
        // A track playing from the cache never resolved, so its figure may not
        // be known yet. Look it up once, a moment in, then apply it.
        retry?.cancel()
        if (!pendingSelection && db == null && id != null && isYouTubeId(id) && AppSettings.playback.value.loudnessNormalization) {
            retry = scope.launch {
                delay(LOUDNESS_RETRY_MS)
                withContext(Dispatchers.IO) { runCatching { StreamResolver.resolve(id) } }
                if (target.currentMediaItem?.mediaId == id && StreamResolver.loudnessDbFor(id) != null) apply()
            }
        }
        // On the phone's own speaker, levelling only takes the loud masters
        // down, and a small speaker needs every dB; there it waits for headphones.
        val pb = AppSettings.playback.value
        val speakerHold = pb.loudnessNormalization && !pb.normalizeOnSpeaker && onPhoneSpeaker()
        NerdStats.onLoudnessOffOnSpeaker(speakerHold)
        val on = pb.loudnessNormalization && db != null && !BitPerfectUsb.active
        val effect = enhancer?.takeIf { enhancerSession == session }
            ?: runCatching { LoudnessEnhancer(session) }
                .onFailure { Log.w(TAG, "LoudnessEnhancer unavailable", it) }
                .getOrNull()
                ?.also {
                    enhancer?.release()
                    enhancer = it
                    enhancerSession = session
                }
            ?: return
        runCatching {
            if (on) {
                val gainMb = (loudnessGainDb(db!!, pb.volumeLevel, speakerHold) * 100).roundToInt().coerceIn(MIN_LOUDNESS_GAIN_MB, MAX_LOUDNESS_GAIN_MB)
                effect.setTargetGain(gainMb)
                effect.enabled = true
                NerdStats.onLoudnessGain(gainMb / 100f)
            } else {
                effect.setTargetGain(0)
                effect.enabled = false
                NerdStats.onLoudnessGain(null)
            }
        }.onFailure { Log.w(TAG, "Couldn't apply loudness gain", it) }
    }

    private companion object {
        const val TAG = "LoudnessControl"

        /** How long to wait before looking a track's loudness figure up again. */
        const val LOUDNESS_RETRY_MS = 6_000L

        /** Bounds on the loudness gain, in millibels: down 15 dB, up 3 dB at most. */
        const val MIN_LOUDNESS_GAIN_MB = -1500
        const val MAX_LOUDNESS_GAIN_MB = 900
    }
}

/**
 * The gain, in dB, for a song YouTube measured at [loudnessDb] against its
 * reference: brought to the reference, moved by [level]'s offset, and lifted
 * no more than its cap. With [speakerHold] (the phone speaker, normalization
 * held off there) it's never negative: a song is only lifted, never cut.
 */
internal fun loudnessGainDb(loudnessDb: Double, level: VolumeLevel, speakerHold: Boolean): Double {
    val target = (-loudnessDb + level.offsetDb).coerceAtMost(level.maxGainDb.toDouble())
    return if (speakerHold) target.coerceAtLeast(0.0) else target
}
