package com.grayguava.legato.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.asImage
import coil3.test.FakeImageLoaderEngine
import com.grayguava.legato.base.model.Song
import com.grayguava.legato.library.LibraryStore
import com.grayguava.legato.lyrics.LyricLine
import com.grayguava.legato.lyrics.Lyrics
import com.grayguava.legato.settings.AppSettings
import com.grayguava.legato.settings.SoundSettings
import com.grayguava.legato.ui.player.LyricsState
import com.grayguava.legato.ui.player.PlayerActions
import com.grayguava.legato.ui.player.PlayerLayout
import com.grayguava.legato.ui.player.PlayerUiState
import com.grayguava.legato.ui.theme.LegatoTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The player at every shape the layout has to survive. Each test runs on a
 * device of that exact size, so the layout is the real thing rather than a
 * box clipped by the test window. A layout that pools a gap under the cover
 * shows up here before it ships.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class PlayerMatrixScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val art = System.getenv("LEGATO_SHOT_ART")?.let(::File)

    private fun cover(name: String) = "https://covers.invalid/$name.jpg"

    @Before fun setUp() {
        assumeTrue(art?.isDirectory == true)
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        AppSettings.init(context)
        LibraryStore.init(context)
        AppSettings.updateUi { it.copy(liquidGlass = false) }
        val engine = FakeImageLoaderEngine.Builder().apply {
            art!!.listFiles { f -> f.extension == "jpg" }!!.forEach { f ->
                val bmp = BitmapFactory.decodeFile(f.path)
                intercept({ it is String && it.substringBefore('?').endsWith("/${f.name}") }, android.graphics.drawable.BitmapDrawable(context.resources, bmp).asImage())
            }
        }.build()
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(context).components { add(engine) }.coroutineContext(Dispatchers.Unconfined).build(),
        )
    }

    private val song = Song("4NRXx6U8ABQ", "Blinding Lights", "The Weeknd", cover("00hd"), "3:20", albumName = "After Hours")
    private val noActions = PlayerActions({}, {}, {}, {}, {}, {}, {}, {}, {})

    private fun player(lyrics: LyricsState) = PlayerUiState(
        song = song, isPlaying = true, isBuffering = false, hasNext = true, shuffle = false, repeatMode = Player.REPEAT_MODE_OFF,
        durationMs = 200_000, lyrics = lyrics, queue = listOf(song), currentIndex = 0, upNext = emptyList(),
        sound = SoundSettings(), source = song.albumName,
        ui = com.grayguava.legato.settings.InterfaceSettings(liquidGlass = false, reduceAnimation = true, statsForNerds = true),
        audioFormat = com.grayguava.legato.audio.playback.AudioFormatInfo("OPUS", 137, 48_000, 2),
    )

    private val synced = LyricsState.Found(
        Lyrics.Synced(
            listOf(LyricLine(60_000, 70_000, "I'm blinded by the lights", emptyList(), false)),
            "test",
        ),
    )

    private fun shoot(name: String, fontScale: Float = 1f, lyrics: Boolean = true) {
        val density = compose.activity.resources.displayMetrics.density
        val state = player(if (lyrics) synced else LyricsState.NotFound)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                LegatoTheme {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            PlayerLayout(state, position = { 64_000 }, buffered = { 94_000 }, actions = noActions)
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(3_000)
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        val dir = File("build/screenshots/player-matrix").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test @Config(qualifiers = "w360dp-h640dp-xxhdpi") fun smallPhone() = shoot("360x640")
    @Test fun samplePhone() = shoot("411x891")
    @Test @Config(qualifiers = "w411dp-h1118dp-xxhdpi") fun tallPhone() = shoot("411x1118")
    @Test @Config(qualifiers = "w600dp-h960dp-xxhdpi") fun tablet() = shoot("600x960")
    @Test @Config(qualifiers = "w840dp-h2200dp-xxhdpi") fun unfolded() = shoot("840x2200")
    @Test @Config(qualifiers = "w411dp-h1118dp-xxhdpi") fun tallPhoneLargeText() = shoot("411x1118-font1.3", fontScale = 1.3f)
    @Test @Config(qualifiers = "w411dp-h1118dp-xxhdpi") fun tallPhoneNoLyrics() = shoot("411x1118-nolyrics", lyrics = false)
}
