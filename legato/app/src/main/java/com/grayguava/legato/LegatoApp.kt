package com.grayguava.legato

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.grayguava.legato.base.Http
import com.grayguava.legato.library.MusicRepository
import com.grayguava.legato.ui.ScreenCache
import okio.Path.Companion.toOkioPath

class LegatoApp : Application(), SingletonImageLoader.Factory {
    /**
     * Artwork on the app's shared HTTP client (warm connections to Google's
     * image hosts), a fifth of memory for decoded images and 100 MB on disk,
     * with a short cross-fade as each one lands.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { Http.client }))
                add(coil3.svg.SvgDecoder.Factory())
                add(coil3.map.Mapper<String, String> { data, _ -> com.grayguava.legato.library.LibraryArtwork.local(data) })
            }
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.2).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(100L * 1024 * 1024)
                    .build()
            }
            .crossfade(200)
            .build()

    override fun onCreate() {
        super.onCreate()
        // First, so a crash anywhere after this is kept for Export diagnostics.
        com.grayguava.legato.base.CrashLog.install(this)
        // The home-screen widget follows the shared player service.
        com.grayguava.legato.audio.AppHooks.onPlayerChanged = { context, player -> com.grayguava.legato.widget.AlbumArtWidget.refresh(context, player) }
        com.grayguava.legato.audio.AppHooks.onPlayerStopped = { context -> com.grayguava.legato.widget.AlbumArtWidget.stopped(context) }
        com.grayguava.legato.data.Core.init(this)
        // Last session's Home, shown at once and refreshed in the background.
        MusicRepository.init(this)?.let { ScreenCache.put("home:${com.grayguava.legato.settings.AppSettings.playback.value.recommender.name}", it, at = 0L) }
        // After history, library, downloads and account: they tell an existing user from a new one.
    }
}
