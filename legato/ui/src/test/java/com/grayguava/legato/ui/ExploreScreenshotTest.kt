package com.grayguava.legato.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.asImage
import coil3.test.FakeImageLoaderEngine
import com.grayguava.legato.base.model.HomeShelf
import com.grayguava.legato.base.model.MoodGenre
import com.grayguava.legato.base.model.MoodGenreSection
import com.grayguava.legato.base.model.ShelfItem
import com.grayguava.legato.library.ExploreFeed
import com.grayguava.legato.ui.explore.ExploreBoard
import java.io.File
import kotlinx.coroutines.Dispatchers
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Draws Explore's rows to build/screenshots. Covers are solid colours served
 * from a fake image loader, so the mood cards' gradients (which are mixed from
 * the cover's own colour) show as they would with real art.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class ExploreScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    /** Mood and genre names to the colour their fake cover is painted. */
    private val colours = mapOf(
        "Chill" to 0xFF17395C, "Workout" to 0xFFB4461F, "Focus" to 0xFF14554A,
        "Party" to 0xFF7A1B4E, "Sleep" to 0xFF2A2B63, "Romance" to 0xFF8C2D3E,
        "Pop" to 0xFF8A6A12, "Hip-Hop" to 0xFF4B2166, "Rock" to 0xFF3A3A3A,
        "Indie" to 0xFF2F5A2A, "Jazz" to 0xFF5B3A22, "Electronic" to 0xFF0E5561,
    )

    @Before fun fakeCovers() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val engine = FakeImageLoaderEngine.Builder().apply {
            colours.forEach { (name, argb) ->
                val bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(argb.toInt()) }
                intercept(
                    { it is String && it.substringBefore('?').endsWith("/${name.lowercase()}.png") },
                    android.graphics.drawable.BitmapDrawable(context.resources, bmp).asImage(),
                )
            }
        }.build()
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(context).components { add(engine) }.coroutineContext(Dispatchers.Unconfined).build(),
        )
    }

    private fun mood(title: String) =
        MoodGenre(title, "FEmusic_moods_and_genres_category", title, "https://covers.invalid/${title.lowercase()}.png")

    private val board = ExploreFeed.Board(
        artists = listOf("Taylor Swift", "The Weeknd", "Arijit Singh", "Billie Eilish", "Drake")
            .mapIndexed { i, n -> ExploreFeed.Artist("UC$i", n, null) },
        rows = ExploreFeed.rows(
            moods = listOf(
                MoodGenreSection("Moods", listOf("Chill", "Workout", "Focus", "Party", "Sleep", "Romance").map(::mood)),
                MoodGenreSection("Genres", listOf("Pop", "Hip-Hop", "Rock", "Indie", "Jazz", "Electronic").map(::mood)),
            ),
            releases = listOf(
                HomeShelf(
                    "New albums",
                    listOf("Midnights", "Hurry Up Tomorrow", "GNX", "Short n' Sweet")
                        .map { ShelfItem(it, "Album • 2025", null, null, "MPRE$it") },
                ),
            ),
            charts = listOf(
                HomeShelf("Trending", listOf("Top 100 Global", "Trending India").map { ShelfItem(it, "Chart", null, null, "VL$it") }),
                HomeShelf("Top 100", listOf("Top 100 Japan", "Top 100 Brazil").map { ShelfItem(it, "Chart", null, null, "VL$it") }),
            ),
        ),
    )

    @Test fun board() = shoot("explore")

    @Test @Config(qualifiers = "w600dp-h960dp-xxhdpi") fun boardOnATablet() = shoot("explore-tablet")

    /** A viewport tall enough to show every row without scrolling. */
    @Test @Config(qualifiers = "w411dp-h1600dp-xxhdpi") fun boardAllRows() = shoot("explore-full")

    private fun shoot(name: String) {
        compose.setContent {
            com.grayguava.legato.ui.theme.LegatoTheme {
                androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        ExploreBoard(board, PaddingValues(), {}, {})
                    }
                }
            }
        }
        // The gradient colour is read off the cover on a background thread, so
        // let that land and repaint before the shot.
        repeat(6) { compose.waitForIdle(); Thread.sleep(120) }
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
