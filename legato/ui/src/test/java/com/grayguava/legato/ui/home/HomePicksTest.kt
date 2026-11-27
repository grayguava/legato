package com.grayguava.legato.ui.home

import android.graphics.Bitmap
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.grayguava.legato.base.model.HomeShelf
import com.grayguava.legato.base.model.ShelfItem
import com.grayguava.legato.ui.ScreenCache
import com.grayguava.legato.ui.browse.SongActions
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi", application = android.app.Application::class)
class HomePicksTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun picksLeadHome() {
        val picks = HomeShelf("Recommended for you", (1..8).map { ShelfItem("Song $it", "Artist", null, "id$it", null) })
        ScreenCache.put("home:${com.grayguava.legato.settings.AppSettings.playback.value.recommender.name}", listOf(picks))
        compose.setContent {
            MaterialTheme(darkColorScheme()) {
                Surface {
                    HomeScreen(PaddingValues(), SongActions(null, false, { _, _, _, _ -> }, {}, {}), {}, {})
                }
            }
        }
        compose.onNodeWithText("Quick picks").assertExists()
        compose.onNodeWithText("Song 1").assertExists()
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        File("build/screenshots/home/quick-picks.png").apply { parentFile!!.mkdirs() }.outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
