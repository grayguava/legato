package com.grayguava.legato.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.grayguava.legato.R
import com.grayguava.legato.library.LibraryStore
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AlbumArtWidgetTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val manager = shadowOf(AppWidgetManager.getInstance(context))

    @Before
    fun setUp() {
        LibraryStore.init(context)
    }

    @Test
    fun placedWidgetInflatesAndShowsTheEmptyState() {
        val id = manager.createWidget(AlbumArtWidget::class.java, R.layout.widget_album_art)
        AlbumArtWidget().onUpdate(context, AppWidgetManager.getInstance(context), intArrayOf(id))
        val view = manager.getViewFor(id)
        assertEquals("Nothing playing", view.findViewById<TextView>(R.id.widget_title).text.toString())
        assertEquals(View.GONE, view.findViewById<View>(R.id.widget_like).visibility)
        assertEquals("Play", view.findViewById<ImageButton>(R.id.widget_play).contentDescription)
    }

    @Test fun theWidgetKeepsTheSameSongAndAccessibleControls() {
        context.getSharedPreferences("widget", 0).edit().putString("id", "aaaaaaaaaa1")
            .putString("title", "A saved song").putString("artist", "An artist").commit()
        val id = manager.createWidget(AlbumArtWidget::class.java, R.layout.widget_album_art)
        AlbumArtWidget().onUpdate(context, AppWidgetManager.getInstance(context), intArrayOf(id))
        val view = manager.getViewFor(id)
        assertEquals("A saved song", view.findViewById<TextView>(R.id.widget_title).text.toString())
        assertEquals("Play", view.findViewById<ImageButton>(R.id.widget_play).contentDescription)
    }

    @Test
    fun stoppedPlayerStillShowsTheLastSong() {
        context.getSharedPreferences("widget", 0).edit()
            .putString("id", "aaaaaaaaaa1").putString("title", "Blinding Lights").putString("artist", "The Weeknd").commit()
        val id = manager.createWidget(AlbumArtWidget::class.java, R.layout.widget_album_art)
        AlbumArtWidget.stopped(context)
        val view = manager.getViewFor(id)
        assertEquals("Blinding Lights", view.findViewById<TextView>(R.id.widget_title).text.toString())
        assertEquals("The Weeknd", view.findViewById<TextView>(R.id.widget_artist).text.toString())
        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.widget_like).visibility)
        assertEquals("Play", view.findViewById<ImageButton>(R.id.widget_play).contentDescription)
    }

}
