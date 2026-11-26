package com.grayguava.legato.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.grayguava.legato.settings.AudioQuality
import com.grayguava.legato.audio.playback.SleepTimer
import com.grayguava.legato.ui.components.ChoiceSheet
import com.grayguava.legato.ui.components.FloatingDialog
import com.grayguava.legato.ui.components.SheetButton
import com.grayguava.legato.ui.components.SheetTone
import com.grayguava.legato.ui.player.SleepTimerDialog
import androidx.compose.material3.Text
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

/** Draws the option picker to build/screenshots, and checks a pick lands and closes it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class ChoiceSheetScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun pick() {
        var quality by mutableStateOf(AudioQuality.entries.first())
        var open by mutableStateOf(true)
        compose.setContent {
            MaterialTheme(darkColorScheme(primary = Color(0xFFFF8A65))) {
                Surface(color = Color(0xFF0B0B0E)) {
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF3A2A4A), Color(0xFF101014)))))
                    if (open) {
                        ChoiceSheet(
                            title = "Quality on Wi-Fi",
                            options = AudioQuality.entries,
                            selected = quality,
                            label = { "${it.label} · ${it.summary}" },
                            onSelect = { quality = it },
                            onDismiss = { open = false },
                        )
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        save("choice_sheet")
        val target = AudioQuality.entries.last()
        compose.onNodeWithText(target.label).performClick()
        compose.waitForIdle()
        assertEquals(target, quality)
        assertTrue(!open)
    }

    @Test fun sleepTimer() {
        SleepTimer.startMinutes(30)
        SleepTimer.addMinutes(-12)
        show { SleepTimerDialog(onDismiss = {}) }
        save("sleep_timer")
        SleepTimer.cancel()
    }

    @Test fun confirm() {
        show {
            FloatingDialog(
                onDismissRequest = {},
                title = { Text("Delete playlist?") },
                text = { Text("Late night drive and its 42 songs go from this phone. Songs you saved stay saved.") },
                confirmButton = { SheetButton("Delete", tone = SheetTone.Danger, onClick = {}) },
                dismissButton = { SheetButton("Cancel", onClick = {}, closes = true) },
            )
        }
        save("confirm_dialog")
    }

    /** The player's BitChord menu: the panel must not nest a scroll container. */
    @Test fun anchoredSongMenu() {
        val song = com.grayguava.legato.base.model.Song("4NRXx6U8ABQ", "Blinding Lights", "The Weeknd", null, "3:20")
        val noop = {}
        show {
            com.grayguava.legato.ui.player.PlayerActionsMenu(
                song = song,
                visible = true,
                anchor = androidx.compose.ui.geometry.Rect(300f, 700f, 332f, 732f),
                onDismiss = noop, onFinished = noop, onShare = noop, onUpgradeQuality = noop, onRadio = noop,
                onPlayNext = noop, onAddToQueue = noop, onOpenAlbum = noop, onOpenArtist = noop,
                onSongInfo = noop, onSleepTimer = noop, onLyricsOffset = noop, onCopyLog = noop,
            )
        }
        compose.onNodeWithText("Play next").assertIsDisplayed()
    }

    /** With dynamic blur reduced, the player menu is a solid panel, not frosted glass. */
    @Test fun anchoredSongMenuStatic() {
        com.grayguava.legato.settings.AppSettings.updateUi { it.copy(reduceBlur = true) }
        try {
            val song = com.grayguava.legato.base.model.Song("4NRXx6U8ABQ", "Blinding Lights", "The Weeknd", null, "3:20")
            val noop = {}
            show {
                com.grayguava.legato.ui.player.PlayerActionsMenu(
                    song = song,
                    visible = true,
                    anchor = androidx.compose.ui.geometry.Rect(300f, 700f, 332f, 732f),
                    onDismiss = noop, onFinished = noop, onShare = noop, onUpgradeQuality = noop, onRadio = noop,
                    onPlayNext = noop, onAddToQueue = noop, onOpenAlbum = noop, onOpenArtist = noop,
                    onSongInfo = noop, onSleepTimer = noop, onLyricsOffset = noop, onCopyLog = noop,
                )
            }
            saveWindow("anchored_menu_static")
            compose.onNodeWithText("Play next").assertIsDisplayed()
        } finally {
            com.grayguava.legato.settings.AppSettings.updateUi { it.copy(reduceBlur = false) }
        }
    }

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            MaterialTheme(darkColorScheme(primary = Color(0xFFFF8A65), onPrimary = Color(0xFF3A1206))) {
                Surface(color = Color(0xFF0B0B0E)) {
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF3A2A4A), Color(0xFF101014)))))
                    content()
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
    }

    /** Draws the whole window, for content that isn't a Dialog. */
    private fun saveWindow(name: String) {
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val file = File("build/screenshots/$name.png").apply { parentFile!!.mkdirs() }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun save(name: String) {        val back = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(back.width, back.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        back.draw(canvas)
        canvas.drawColor(android.graphics.Color.argb(90, 0, 0, 0))
        val sheet = ShadowDialog.getLatestDialog().window!!.decorView
        canvas.save()
        canvas.translate(0f, (back.height - sheet.height).toFloat())
        sheet.draw(canvas)
        canvas.restore()
        val file = File("build/screenshots/$name.png").apply { parentFile!!.mkdirs() }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
