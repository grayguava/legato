package com.grayguava.legato.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.test.core.app.ApplicationProvider
import com.grayguava.legato.library.LibraryStore
import com.grayguava.legato.settings.AppSettings
import com.grayguava.legato.ui.settings.SettingsScreen
import com.grayguava.legato.ui.theme.LegatoTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Settings: the main page of categories, one category's page, and search across them. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class SettingsScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        AppSettings.init(context)
        LibraryStore.init(context)
        compose.setContent {
            LegatoTheme() {
                Surface(color = MaterialTheme.colorScheme.background) {
                    SettingsScreen(PaddingValues(), onBack = {}, onOpenEqualizer = {}, onOpenReplay = {})
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun categoriesThenAPage() {
        save("settings-home")
        list().performScrollToNode(hasText("Look and feel"))
        compose.onNodeWithText("Look and feel").performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        compose.onNodeWithText("Appearance").performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        compose.onNodeWithText("GLASS").assertIsDisplayed()
        compose.onNodeWithText("Liquid").assertExists()
        save("settings-look")
    }

    @Test fun lookAndFeelUnfoldsIntoItsTwoPages() {
        list().performScrollToNode(hasText("Look and feel"))
        compose.onNodeWithText("Look and feel").performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        compose.onNodeWithText("Appearance").assertIsDisplayed()
        compose.onNodeWithText("Motion").assertIsDisplayed()
        save("settings-look-and-feel")
    }

    @Test fun accountsUnfold() {
        compose.onNodeWithText("Accounts and services").performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        compose.onNodeWithText("Manage accounts").assertIsDisplayed()
        compose.onNodeWithText("Listening history").assertIsDisplayed()
        save("settings-accounts")
        compose.onNodeWithText("Accounts and services").performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
    }

    @Test fun clearingHistoryOpensInPlace() {
        compose.onNodeWithText("Accounts and services").performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        compose.onNodeWithText("Listening history").performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        list().performScrollToNode(hasText("Clear listening history"))
        // The lone destructive card has no group label over it.
        assertTrue(compose.onAllNodesWithText("CLEAR").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("Clear listening history").performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        compose.onNodeWithText("Clear listening history?").assertIsDisplayed()
        compose.onNodeWithText("Clear history").performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        compose.onNodeWithText("Listening history cleared.").assertIsDisplayed()
        compose.onNodeWithText("OK").performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        save("settings-clear-history")
    }

    @Test fun playbackUnfoldsIntoItsPages() {
        compose.onNodeWithText("Playback").performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        compose.onNodeWithText("Between songs").assertIsDisplayed()
        compose.onNodeWithText("Autoplay and suggestions").assertIsDisplayed()
        compose.onNodeWithText("App behavior").assertIsDisplayed()
        save("settings-playback")
    }

    @Test fun playerAndLyricsUnfold() {
        compose.onNodeWithText("Player and Lyrics").performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        compose.onNodeWithText("Lyrics").assertIsDisplayed()
        save("settings-playerlyrics")
    }

    @Test fun searchFindsAcrossPages() {
        compose.onNodeWithContentDescription("Search settings").performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        compose.onNodeWithText("Search settings").performTextInput("equalizer")
        compose.waitForIdle()
        assertTrue(compose.onAllNodesWithText("Equalizer").fetchSemanticsNodes().isNotEmpty())
        // Each setting lives on one page only.
        assertTrue(compose.onAllNodesWithText("Equalizer").fetchSemanticsNodes().size == 1)
        save("settings-search")
    }

    @Test fun theSeekBarSettingsShowInFull() {
        list().performScrollToNode(hasText("Player and Lyrics"))
        compose.onNodeWithText("Player and Lyrics").performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        compose.onNodeWithText("Lyrics").performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        list().performScrollToNode(hasText("Lyric preview while seeking"))
        compose.onNodeWithText("Lyric preview while seeking").assertIsDisplayed()
        save("settings-seek-bar")
    }

    @Test fun lyricsSourcesHaveTheirOwnPage() {
        openLyricsPage()
        // The two switches that used to sit in the sources sheet are on the page now.
        list().performScrollToNode(hasText("Prioritize word-by-word lyrics"))
        compose.onNodeWithText("Prioritize word-by-word lyrics").assertIsDisplayed()
        openLyricsSourcesFromLyricsPage()
        compose.onNodeWithText("lrc.red").assertIsDisplayed()
        compose.onNodeWithText("Paxsenix").assertIsDisplayed()
        save("settings-lyrics-sources")
    }

    @Test fun resettingTheOrderLeavesTheOtherChoices() {
        val saved = AppSettings.lyrics.value
        val id = "4NRXx6U8ABQ"
        val source = com.grayguava.legato.settings.LyricsSource.SIMPMUSIC
        try {
            AppSettings.updateLyrics { it.copy(
                sources = listOf(com.grayguava.legato.settings.LyricsSourceEntry(source, enabled = false)),
                preferWordSynced = false,
                romanize = false,
                offsets = mapOf(id to 500L),
                chosen = mapOf(id to source),
            ) }
            openLyricsSources()
            compose.onNodeWithText("Reset the order").performScrollTo().performClick()
            compose.waitForIdle()
            val reset = AppSettings.lyrics.value
            assertEquals(com.grayguava.legato.settings.DEFAULT_LYRICS_SOURCES, reset.sources)
            // The switches on the Lyrics page and every per-song choice stay as they were.
            assertTrue(!reset.preferWordSynced)
            assertTrue(!reset.romanize)
            assertEquals(mapOf(id to 500L), reset.offsets)
            assertEquals(mapOf(id to source), reset.chosen)
        } finally {
            AppSettings.updateLyrics { saved }
        }
    }

    private fun openLyricsPage() {
        list().performScrollToNode(hasText("Player and Lyrics"))
        compose.onNodeWithText("Player and Lyrics").performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        compose.onNodeWithText("Lyrics").performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
    }

    private fun openLyricsSources() {
        openLyricsPage()
        openLyricsSourcesFromLyricsPage()
    }

    private fun openLyricsSourcesFromLyricsPage() {
        list().performScrollToNode(hasText("Lyrics sources"))
        compose.onNodeWithText("Lyrics sources").performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
    }

    @Test fun backFromAPageReturnsToTheList() {
        list().performScrollToNode(hasText("About"))
        compose.onNodeWithText("About").assertIsDisplayed()
        compose.onNodeWithText("About").performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        // Back on the list of categories, not on the page it opened.
        compose.onNodeWithContentDescription("Search settings").assertIsDisplayed()
        compose.onNodeWithText("Check for updates").assertIsNotDisplayed()
    }

    private fun list() = compose.onNode(hasScrollToNodeAction())

    private fun save(name: String) {
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        val file = File("build/screenshots/$name.png").apply { parentFile!!.mkdirs() }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
