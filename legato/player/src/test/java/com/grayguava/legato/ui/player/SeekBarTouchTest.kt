package com.grayguava.legato.ui.player

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.grayguava.legato.settings.AppSettings
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class SeekBarTouchTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun theTrackTapsAndDragsSeekWhateverSitsBelowIt() {
        AppSettings.init(compose.activity)
        var sought = -1L
        compose.setContent {
            MaterialTheme {
                SeekBar({ 20_000L }, { 60_000L }, 100_000L, onSeek = { sought = it }, info = {
                    Text("OPUS · 165 kbps · 48 kHz · Stereo")
                })
            }
        }
        compose.runOnIdle { sought = -1L }
        val bar = compose.onNodeWithContentDescription("Playback position. Drag to seek.")
        bar.assertHeightIsAtLeast(48.dp)
        bar.performTouchInput { click(Offset(centerX, bottom - 1f)) }
        compose.runOnIdle { assertTrue(sought in 49_000L..51_000L) }
        bar.performTouchInput { swipe(Offset(width * 0.2f, centerY), Offset(width * 0.8f, centerY)) }
        compose.runOnIdle { assertTrue(sought > 60_000L) }
    }
}
