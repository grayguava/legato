package com.grayguava.legato.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.asImage
import coil3.test.FakeImageLoaderEngine
import com.grayguava.legato.library.ExploreFeed
import com.grayguava.legato.library.LibraryStore
import com.grayguava.legato.lyrics.LyricLine
import com.grayguava.legato.lyrics.LyricWord
import com.grayguava.legato.lyrics.Lyrics
import com.grayguava.legato.base.model.MoodGenre
import com.grayguava.legato.base.model.ShelfItem
import com.grayguava.legato.base.model.Song
import com.grayguava.legato.settings.AppSettings
import com.grayguava.legato.settings.SoundSettings
import com.grayguava.legato.ui.browse.SongActions
import com.grayguava.legato.ui.components.SectionHeader
import com.grayguava.legato.ui.explore.ExploreBoard
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
 * The README's screenshots: the app's own screens, drawn with real covers.
 * Covers aren't kept in the repository, so this runs only when
 * LEGATO_SHOT_ART points at a folder of them, and writes to
 * build/screenshots/readme.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class ReadmeScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val art = System.getenv("LEGATO_SHOT_ART")?.let(::File)

    /** Cover "name" is served from art/name.jpg for any URL ending in /name.jpg. */
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

    private fun shoot(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            LegatoTheme() {
                Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(top = 28.dp)) { content() }
                }
            }
        }
        capture(name)
    }

    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(3_000)
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        val dir = File("build/screenshots/readme").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val blinding = Song("4NRXx6U8ABQ", "Blinding Lights", "The Weeknd", cover("00hd"), "3:20", albumName = "After Hours")
    private val queue = listOf(
        blinding,
        Song("TUVcZfQe-Kw", "Levitating", "Dua Lipa", cover("01"), "3:23"),
        Song("H5v3kku4y6Q", "As It Was", "Harry Styles", cover("16"), "2:47"),
        Song("4m1EFMoRFvY", "Do I Wanna Know?", "Arctic Monkeys", cover("08"), "4:32"),
    )
    private val noActions = PlayerActions({}, {}, {}, {}, {}, {}, {}, {}, {})

    private fun player(
        song: Song,
        lyrics: com.grayguava.legato.ui.player.LyricsState,
        initialLyrics: Boolean,
        position: Long,
        playing: Boolean = true,
        format: com.grayguava.legato.audio.playback.AudioFormatInfo? = null,
    ) = PlayerUiState(
        song = song, isPlaying = playing, isBuffering = false, hasNext = true, shuffle = false, repeatMode = Player.REPEAT_MODE_OFF,
        durationMs = 200_000, lyrics = lyrics, queue = queue, currentIndex = 0, upNext = listOf(1, 2, 3),
        sound = SoundSettings(), source = song.albumName,
        // Liquid Glass needs the GPU's shaders, which this software drawing can't run; the smoke glass stands in.
        // The player moves every frame while playing; a still needs that held, or the test never goes idle.
        ui = com.grayguava.legato.settings.InterfaceSettings(liquidGlass = false, reduceAnimation = true, statsForNerds = format != null),
        audioFormat = format,
    ).let { state -> @Composable { PlayerLayout(state, position = { position }, buffered = { position + 30_000 }, actions = noActions, initialLyrics = initialLyrics) } }

    @Test fun nowPlaying() = shoot("player", content = player(blinding, com.grayguava.legato.ui.player.LyricsState.NotFound, false, 64_000))

    @Test fun nowPlayingLyricPreview() = shoot(
        "player-lyrics-preview",
        content = player(
            blinding,
            com.grayguava.legato.ui.player.LyricsState.Found(
                Lyrics.Synced(
                    listOf(
                        LyricLine(60_000, 70_000, "I'm blinded by the lights", emptyList(), false),
                        LyricLine(70_000, 80_000, "No, I can't sleep until I feel your touch", emptyList(), false),
                    ),
                    "test",
                ),
            ),
            false,
            64_000,
            format = com.grayguava.legato.audio.playback.AudioFormatInfo("AAC", 256, 44_100, 2),
        ),
    )

    @Test fun buttons() = shoot("buttons-capsule", content = player(blinding, com.grayguava.legato.ui.player.LyricsState.NotFound, false, 64_000))

    @Test fun lyrics() = lyricsShot("lyrics")

    private fun lyricsShot(name: String) {
        // Words by Frederic Weatherly, 1913: in the public domain.
        val text = listOf(
            "Oh Danny boy, the pipes, the pipes are calling",
            "From glen to glen, and down the mountain side",
            "The summer's gone, and all the roses falling",
            "'Tis you, 'tis you must go and I must bide",
            "But come ye back when summer's in the meadow",
            "Or when the valley's hushed and white with snow",
        )
        val lines = text.mapIndexed { i, t ->
            val start = 10_000L + i * 7_000L
            val words = t.split(' ')
            LyricLine(start, start + 7_000, t, words.mapIndexed { j, w -> LyricWord("$w ", start + j * 7_000L / words.size, start + (j + 1) * 7_000L / words.size) }, wordSynced = true)
        }
        val danny = Song("dannyboycash", "Danny Boy", "Johnny Cash", cover("danny"), "3:20", albumName = "American IV: The Man Comes Around")
        shoot(name, content = player(danny, com.grayguava.legato.ui.player.LyricsState.Found(Lyrics.Synced(lines, "LRCLIB")), true, 25_500))
    }

    @Test fun explore() {
        val artists = listOf("The Weeknd" to "00", "Dua Lipa" to "01", "Taylor Swift" to "05", "SZA" to "06", "Billie Eilish" to "07")
            .mapIndexed { i, (n, c) -> ExploreFeed.Artist("UC$i", n, cover(c)) }
        val canvas = ExploreFeed.Board(
            artists = artists,
            rows = ExploreFeed.rows(
                moods = listOf(
                    com.grayguava.legato.base.model.MoodGenreSection(
                        "Moods",
                        listOf("Chill", "Workout", "Focus", "Party", "Sleep", "Romance")
                            .mapIndexed { i, t -> MoodGenre(t, "FEmusic_moods_and_genres_category", t, cover("0$i")) },
                    ),
                ),
                releases = listOf(
                    com.grayguava.legato.base.model.HomeShelf(
                        "New albums",
                        listOf("SOS" to "06", "Midnights" to "22", "Short n' Sweet" to "19", "HIT ME HARD AND SOFT" to "20")
                            .map { (n, c) -> ShelfItem(n, "Album", cover(c), null, "MPRE$n") },
                    ),
                ),
                charts = listOf(
                    com.grayguava.legato.base.model.HomeShelf(
                        "Charts",
                        listOf("After Hours" to "00", "Harry's House" to "16")
                            .map { (n, c) -> ShelfItem(n, "Album", cover(c), null, "MPRE$n") },
                    ),
                ),
            ),
        )
        shoot("explore") { ExploreBoard(canvas, PaddingValues(), {}, {}) }
    }

    @Test fun library() {
        val actions = SongActions(null, false, { _, _, _, _ -> }, {}, {})
        val nav = com.grayguava.legato.ui.library.LibraryNav({}, {}, {}, {}, {}, {}, {}, {}, {})
        shoot("library") { com.grayguava.legato.ui.library.LibraryScreen(androidx.compose.foundation.layout.PaddingValues(), actions, nav) }
    }
}
