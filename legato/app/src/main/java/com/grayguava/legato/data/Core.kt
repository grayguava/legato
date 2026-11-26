package com.grayguava.legato.data

import android.app.Application
import com.grayguava.legato.base.LoudnessStore
import com.grayguava.legato.audio.autoeq.AutoEq
import com.grayguava.legato.audio.playback.QueueStore
import com.grayguava.legato.base.AppLog
import com.grayguava.legato.library.LibraryStore
import com.grayguava.legato.library.account.AccountStore
import com.grayguava.legato.library.history.History
import com.grayguava.legato.settings.AppSettings
import com.grayguava.legato.source.covers.AlbumCovers
import com.grayguava.legato.source.innertube.InnerTubeXResolver
import com.grayguava.legato.source.innertube.UpgradedTracks

/** Starts the shared services, in the order they depend on each other. */
object Core {
    /**
     * Starts everything the phone and TV app share. New-release alerts, update
     * checks and usage counts belong to it too.
     */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun init(app: Application) {
        // Media3's warnings and errors (a failed load, a decoder that won't
        // start) into the app's own log as well as logcat, for "Copy log".
        androidx.media3.common.util.Log.setLogger(object : androidx.media3.common.util.Log.Logger {
            private val system = androidx.media3.common.util.Log.Logger.DEFAULT
            override fun d(tag: String, message: String, throwable: Throwable?) = system.d(tag, message, throwable)
            override fun i(tag: String, message: String, throwable: Throwable?) = system.i(tag, message, throwable)
            override fun w(tag: String, message: String, throwable: Throwable?) {
                AppLog.add('W', tag, message, throwable)
                system.w(tag, message, throwable)
            }
            override fun e(tag: String, message: String, throwable: Throwable?) {
                AppLog.add('E', tag, message, throwable)
                system.e(tag, message, throwable)
            }
        })
        AppSettings.init(app)
        History.init(app)
        com.grayguava.legato.source.covers.ArtistPhotos.init(app)
        LoudnessStore.init(app)
        UpgradedTracks.init(app)
        QueueStore.init(app)
        // Spotify sign-in is gone; drop any tokens an earlier version kept.
        app.deleteSharedPreferences("spotify")
        // The waveform seek bar is gone; drop the waveforms an earlier version saved.
        java.io.File(app.filesDir, "waveforms").takeIf { it.exists() }?.let { dir -> Thread { dir.deleteRecursively() }.start() }
        com.grayguava.legato.library.ResumePoints.init(app)
        AlbumCovers.init(app)
        AutoEq.init(app)
        com.grayguava.legato.library.LibraryArtwork.init(app)
        LibraryStore.init(app)
        AccountStore.init(app)
        com.grayguava.legato.library.LibraryCache.init(app)
        // After library and account: it sends playlist changes still waiting, when signed in.
        com.grayguava.legato.library.PlaylistSync.init(app)
        InnerTubeXResolver.init(app)
    }

    /**
     * Saves at once what's still waiting for its save (each store waits a moment
     * after a change, so a run of changes is written once): the app is leaving
     * the screen or the player is closing, and the process may go any moment.
     */
    fun flush() {
        LibraryStore.flush()
        History.flush()
    }
}
