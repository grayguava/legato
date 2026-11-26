package com.grayguava.legato.audio.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.grayguava.legato.base.DebugLog as Log
import com.grayguava.legato.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Where the sound goes: the phone's own speaker, headphones, Bluetooth, a USB
 * DAC, and the exclusive output the DAC wants.
 *
 * Android routes media to whatever appears, so the choice of device is made
 * here rather than left to the system: a DAC when one is plugged in and the
 * setting asks for it, and otherwise whatever is connected.
 */
internal class OutputDevices(
    private val context: Context,
    private val loudness: LoudnessControl,
    private val player: () -> ExoPlayer?,
    private val audioManager: () -> AudioManager?,
) {
    private var devicesListed = false

    /** Set while paused by "Pause at zero volume", so raising it again resumes. */
    private var pausedAtZero = false

    private var exclusiveWas: Boolean? = null

    /** Bumped when the volume keys move, so the bit-perfect software volume follows. */
    val volumeTick = MutableStateFlow(0)

    val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            applyPreferredDevice()
            // A DAC plugged in mid-song: the song moves onto it exclusively at once.
            if (devicesListed && BitPerfectUsb.wanted && addedDevices.orEmpty().any { it.type in USB_OUTPUTS }) {
                player()?.let { reopenOutput(it) }
            }
            // The callback reports every device already there as it's
            // registered; only one that turns up afterwards is a connection.
            if (!devicesListed) {
                devicesListed = true
                return
            }
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            applyPreferredDevice()
            if (removedDevices.orEmpty().any { it.type in USB_OUTPUTS }) BitPerfectUsb.devicesChanged()
        }
    }

    val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            volumeTick.value++
            if (intent?.getIntExtra(EXTRA_VOLUME_STREAM, AudioManager.STREAM_MUSIC) == AudioManager.STREAM_MUSIC) onMusicVolume()
        }
    }

    private fun onMusicVolume() {
        val player = player() ?: return
        val volume = audioManager()?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: return
        if (!AppSettings.playback.value.pauseAtZeroVolume) {
            pausedAtZero = false
            return
        }
        if (volume == 0 && player.playWhenReady) {
            pausedAtZero = true
            player.pause()
        } else if (volume > 0 && pausedAtZero) {
            pausedAtZero = false
            player.play()
        }
    }

    /**
     * Turns exclusive USB output on or off. With [reopen], a song already
     * playing has its output opened again where it is (a seek in place does
     * it), so the change is heard now and not from the next song.
     */
    fun applyBitPerfect(reopen: Boolean = false) {
        val pb = AppSettings.playback.value
        BitPerfectUsb.setWanted(pb.bitPerfectUsb)
        loudness.apply()
        val player = player() as? ExoPlayer ?: return
        loudness.openEffectSession(player.audioSessionId)
        val changed = exclusiveWas != null && exclusiveWas != pb.bitPerfectUsb
        exclusiveWas = pb.bitPerfectUsb
        if (reopen && (changed || pb.bitPerfectUsb)) reopenOutput(player)
    }

    fun stretching(): Boolean = isStretched(
        AppSettings.sound.value, AppSettings.playback.value.skipSilence,
    )

    /**
     * The player's output set up afresh at the same place, for a change in
     * whether the song is stretched: a seek keeps the output as it is, so the
     * player is stopped and prepared again. Only when float could be in use.
     */
    fun rebuildOutput() {
        val player = player() ?: return
        val pb = AppSettings.playback.value
        if (!pb.floatOutput && !pb.bitPerfectUsb) return
        if (player.currentMediaItem == null) return
        if (player.playbackState != Player.STATE_READY && player.playbackState != Player.STATE_BUFFERING) return
        val at = player.currentPosition
        player.stop()
        player.prepare()
        player.seekTo(at)
    }

    /** The song's output closed and opened again at the same place, so it opens on the current route. */
    private fun reopenOutput(player: ExoPlayer) {
        if (player.playbackState != Player.STATE_READY && player.playbackState != Player.STATE_BUFFERING) return
        if (player.currentMediaItem == null) return
        player.seekTo(player.currentPosition)
    }

    /**
     * Whether sound is going to the phone's own speaker: no headphones,
     * Bluetooth audio, USB, HDMI, dock or line output is connected. Android
     * routes media to any of those as soon as it appears.
     */
    fun onPhoneSpeaker(): Boolean {
        val outputs = audioManager()?.getDevices(AudioManager.GET_DEVICES_OUTPUTS) ?: return false
        return outputs.none { it.isSink && it.type in EXTERNAL_OUTPUTS }
    }

    /** Route to a USB DAC when one is plugged in and the setting asks for it. */
    fun applyPreferredDevice() {
        applyBitPerfect()
        val player = player() ?: return
        val usb = if (AppSettings.playback.value.preferUsbDac) {
            audioManager()?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET
            }
        } else {
            null
        }
        player.setPreferredAudioDevice(usb)
    }

    private companion object {
        const val TAG = "OutputDevices"

        const val EXTRA_VOLUME_STREAM = "android.media.EXTRA_VOLUME_STREAM_TYPE"

        /**
         * Outputs a person listens on privately, as opposed to the phone's
         * speaker. The BLE types are plain ints that older Android never reports.
         */
        @android.annotation.SuppressLint("InlinedApi")
        val LISTENING_DEVICES = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
        )

        /** A USB DAC or headset, the outputs exclusive mode takes. */
        val USB_OUTPUTS = setOf(AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET)

        /** Outputs that take media away from the phone's speaker. */
        val EXTERNAL_OUTPUTS = LISTENING_DEVICES + setOf(
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_ACCESSORY,
            AudioDeviceInfo.TYPE_HDMI,
            AudioDeviceInfo.TYPE_LINE_ANALOG,
            AudioDeviceInfo.TYPE_LINE_DIGITAL,
            AudioDeviceInfo.TYPE_AUX_LINE,
            AudioDeviceInfo.TYPE_DOCK,
        )
    }
}
