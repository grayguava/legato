package com.grayguava.legato.audio.playback

import com.grayguava.legato.settings.SoundSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When float output has to give way so Media3 can change the song's speed and pitch. */
class StretchedTest {
    @Test fun aPlainSongIsNotStretched() {
        assertFalse(isStretched(SoundSettings(), skipSilence = false))
        // A slider left a hair off normal by rounding still counts as normal.
        assertFalse(isStretched(SoundSettings(speed = 1.0004f, pitch = 0.9996f), skipSilence = false))
    }

    @Test fun speedPitchAndSilenceAllStretchIt() {
        assertTrue(isStretched(SoundSettings(enabled = true, speed = 1.25f), skipSilence = false))
        assertTrue(isStretched(SoundSettings(enabled = true, pitch = 0.9f), skipSilence = false))
        assertTrue(isStretched(SoundSettings(), skipSilence = true))
    }

    @Test fun aRemixThatIsSwitchedOffDoesNotStretchIt() {
        assertFalse(isStretched(SoundSettings(enabled = false, speed = 1.25f, pitch = 0.9f), skipSilence = false))
    }
}
