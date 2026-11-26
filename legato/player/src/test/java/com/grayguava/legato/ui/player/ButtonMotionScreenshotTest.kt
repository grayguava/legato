package com.grayguava.legato.ui.player

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Frames of the play/pause fold and the skip roll, to build/screenshots/buttons, so the motion can be looked over. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class ButtonMotionScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun controlsOnLightAndDark() {
        var dark by mutableStateOf(true)
        var playing by mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            androidx.compose.material3.MaterialTheme(colorScheme = if (dark) androidx.compose.material3.darkColorScheme() else androidx.compose.material3.lightColorScheme()) {
                androidx.compose.foundation.layout.Box(Modifier.size(360.dp, 250.dp)) {
                    PlayerControls(playing, false, true, { playing = !playing }, {}, {},
                        Modifier.align(Alignment.Center), animate = false)
                }
            }
        }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithContentDescription("Play").performClick()
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithContentDescription("Pause").assertExists()
        save("capsule/dark-playing")
        dark = false
        compose.onNodeWithContentDescription("Pause").performClick()
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithContentDescription("Play").assertExists()
        save("capsule/light-paused")
    }

    @Test fun transport() {
        var playing by mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            androidx.compose.material3.MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme()) {
                androidx.compose.foundation.layout.Box(Modifier.size(360.dp, 250.dp).background(Color(0xFF15131A))) {
                    PlayerControls(playing, false, true, { playing = !playing }, {}, {},
                        Modifier.align(Alignment.Center).fillMaxWidth(), animate = false)
                }
            }
        }
        compose.mainClock.advanceTimeBy(100)
        save("buttons/paused")
        compose.onNodeWithContentDescription("Play").performClick()
        compose.mainClock.advanceTimeBy(100)
        save("buttons/playing")
        compose.onNodeWithContentDescription("Pause").assertExists()
    }

    private fun save(name: String) {
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        val file = File("build/screenshots/$name.png").apply { parentFile!!.mkdirs() }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
