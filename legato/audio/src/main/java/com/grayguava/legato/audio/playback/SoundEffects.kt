package com.grayguava.legato.audio.playback

import android.media.audiofx.PresetReverb
import androidx.annotation.OptIn
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.grayguava.legato.base.DebugLog as Log
import com.grayguava.legato.settings.ReverbLevel
import com.grayguava.legato.settings.SoundSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Applies the Remix settings that aren't done in [dsp.DspAudioProcessor]:
 * speed and pitch through ExoPlayer's time-stretching, and reverb through
 * Android's PresetReverb. Reverb is device-dependent; when it can't be created
 * [reverbAvailable] goes false so the UI can say so. Bass boost is a filter in
 * the app's own DSP, so it works on every phone.
 */
/**
 * Whether a song is stretched in time: speed or pitch off normal, or silence
 * skipped. Media3 does all of that only on 16-bit output, so float output is
 * set aside while it is.
 */
public fun isStretched(sound: SoundSettings, skipSilence: Boolean): Boolean =
    skipSilence || (sound.enabled && (
        kotlin.math.abs(sound.speed - 1f) > 0.001f || kotlin.math.abs(sound.pitch - 1f) > 0.001f))

@OptIn(UnstableApi::class)
public class SoundEffects(private val player: ExoPlayer) {

    private var reverb: PresetReverb? = null

    public fun apply(settings: SoundSettings) {
        if (!settings.enabled) {
            player.playbackParameters = PlaybackParameters(1f, 1f)
            applyReverb(ReverbLevel.OFF)
            return
        }
        player.playbackParameters = PlaybackParameters(settings.speed, settings.pitch)
        applyReverb(settings.reverb)
    }

    public fun release() {
        reverb?.release()
        reverb = null
    }

    private fun applyReverb(level: ReverbLevel) {
        if (level == ReverbLevel.OFF) {
            reverb?.enabled = false
            player.setAuxEffectInfo(AuxEffectInfo(AuxEffectInfo.NO_AUX_EFFECT_ID, 0f))
            return
        }
        try {
            // An auxiliary effect lives on the output mix (session 0); the
            // player sends to it at full level.
            val effect = reverb ?: PresetReverb(0, 0).also { reverb = it }
            effect.preset = level.preset
            effect.enabled = true
            player.setAuxEffectInfo(AuxEffectInfo(effect.id, 1f))
            _reverbAvailable.value = true
        } catch (e: Exception) {
            Log.w(TAG, "Reverb unavailable", e)
            reverb?.release()
            reverb = null
            _reverbAvailable.value = false
        }
    }

    public companion object {
        private const val TAG = "SoundEffects"

        private val _reverbAvailable = MutableStateFlow(true)
        public val reverbAvailable: StateFlow<Boolean> = _reverbAvailable.asStateFlow()
    }
}
