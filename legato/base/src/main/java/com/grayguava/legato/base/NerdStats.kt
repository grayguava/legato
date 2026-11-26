package com.grayguava.legato.base

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Figures for "Stats for nerds": the bitrate [StreamResolver][com.grayguava.legato.source.innertube.StreamResolver]
 * picked for the track playing, and how long the last track took from being
 * asked for to making sound.
 */
public object NerdStats {
    private val _upgrading = MutableStateFlow<String?>(null)
    public val upgrading: StateFlow<String?> = _upgrading.asStateFlow()
    public fun onUpgrade(mediaId: String?) { _upgrading.value = mediaId }

    public data class Source(val provider: String, val bits: Int? = null, val sampleRate: Int? = null, val kbps: Int? = null,
        val mediaId: String? = null)
    private val _playbackSource = MutableStateFlow<Source?>(null)
    public val playbackSource: StateFlow<Source?> = _playbackSource.asStateFlow()
    public fun onPlaybackSource(source: Source) { _playbackSource.value = source }

    private val _lastPicked = MutableStateFlow<Pair<String, Int>?>(null)
    public val lastPicked: StateFlow<Pair<String, Int>?> = _lastPicked.asStateFlow()

    public fun onStreamPicked(videoId: String, kbps: Int) {
        _lastPicked.value = videoId to kbps
    }

    /** The loudness-normalization gain on the current track, in dB, or null when none. */
    private val _loudnessGainDb = MutableStateFlow<Float?>(null)
    public val loudnessGainDb: StateFlow<Float?> = _loudnessGainDb.asStateFlow()

    /** True while normalization is on but held off because the sound is on the phone speaker. */
    private val _loudnessOffOnSpeaker = MutableStateFlow(false)
    public val loudnessOffOnSpeaker: StateFlow<Boolean> = _loudnessOffOnSpeaker.asStateFlow()

    public fun onLoudnessOffOnSpeaker(off: Boolean) {
        _loudnessOffOnSpeaker.value = off
    }

    public fun onLoudnessGain(db: Float?) {
        _loudnessGainDb.value = db
    }

    /** Which engine found the playing track's stream: Legato's walk or InnerTubeX. */
    private val _engine = MutableStateFlow<String?>(null)
    public val engine: StateFlow<String?> = _engine.asStateFlow()

    public fun onEngine(label: String) {
        _engine.value = label
    }

    private val _startupMs = MutableStateFlow<Long?>(null)
    public val startupMs: StateFlow<Long?> = _startupMs.asStateFlow()

    public fun onStartup(ms: Long) {
        _startupMs.value = ms
    }
}
