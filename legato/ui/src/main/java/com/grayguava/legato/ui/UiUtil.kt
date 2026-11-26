package com.grayguava.legato.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grayguava.legato.library.MusicRepository
import com.grayguava.legato.base.model.BrowseType
import com.grayguava.legato.base.model.ShelfItem
import com.grayguava.legato.base.model.Song
import com.grayguava.legato.base.model.UiState
import io.ktor.client.plugins.ResponseException
import java.io.IOException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Something a person can read, rather than a stack of HTML from a refused request. */
public fun friendlyError(e: Throwable): String = when (e) {
    is UnknownHostException -> "You're offline. Check your connection and try again."
    is ResponseException -> when (e.response.status.value) {
        403, 429 -> "YouTube Music turned the request away. Wait a moment and try again."
        in 500..599 -> "YouTube Music is having trouble right now."
        else -> "YouTube Music answered with an error (${e.response.status.value})."
    }
    is IOException -> "Network error. Check your connection and try again."
    else -> e.message?.take(160) ?: "Something went wrong."
}

public fun ShelfItem.toSong(): Song? = videoId?.let {
    // The credit, not the card's first word: "Song • Karan Aujla" played as by "Song", which then
    // looked its lyrics up under that name and found another song's.
    Song(videoId = it, title = title, artist = com.grayguava.legato.source.innertube.InnertubeParser.artistFromSubtitle(subtitle), thumbnailUrl = thumbnailUrl, isExplicit = explicit.takeIf { e -> e })
}

public fun ShelfItem.type(): BrowseType? = browseId?.let(MusicRepository::typeOf)

/** What a long press on this card opens: an album's or playlist's menu, or a song's. Artists have none. */
public fun ShelfItem.cardMenu(): com.grayguava.legato.ui.components.CardMenu? {
    val type = type()
    val id = browseId
    return when {
        id != null && (type == BrowseType.ALBUM || type == BrowseType.PLAYLIST) ->
            com.grayguava.legato.ui.components.CardMenu.ForList(com.grayguava.legato.ui.components.MenuList(id, title, subtitle, thumbnailUrl, type))
        id == null -> toSong()?.let { com.grayguava.legato.ui.components.CardMenu.ForSong(it) }
        else -> null
    }
}

/**
 * What screens loaded recently, by loader key, so going back to a page shows
 * it at once instead of a spinner. Entries are only a head start: anything
 * older than [FRESH_MS] is shown and then fetched again in the background.
 */
public object ScreenCache {
    @Synchronized public fun clear() { entries.clear() }
    private class Entry(val value: Any?, val at: Long)

    private val entries = object : LinkedHashMap<String, Entry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?) = size > MAX_ENTRIES
    }

    @Synchronized
    public fun get(key: String): Pair<Any?, Long>? = entries[key]?.let { it.value to it.at }

    @Synchronized
    public fun put(key: String, value: Any?, at: Long = System.currentTimeMillis()) {
        entries[key] = Entry(value, at)
    }

    @Synchronized
    public fun invalidate(key: String) { entries[key]?.let { entries[key] = Entry(it.value, 0L) } }

    public const val FRESH_MS: Long = 2 * 60 * 1000L
    private const val MAX_ENTRIES = 48
}

/**
 * Loads one thing for a screen, survives rotation, and can be retried or
 * refreshed. [refreshing] is a reload that keeps the old content on screen.
 * A page seen recently starts from [ScreenCache] and refreshes quietly.
 */
public class LoaderViewModel<T>(private val key: String, private val load: suspend () -> T, private val saved: suspend () -> T? = { null }) : ViewModel() {
    private val _state = MutableStateFlow<UiState<T>>(UiState.Loading)
    public val state: StateFlow<UiState<T>> = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    public val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    init {
        val cached = ScreenCache.get(key)
        if (cached != null) {
            @Suppress("UNCHECKED_CAST")
            _state.value = UiState.Success(cached.first as T)
            if (System.currentTimeMillis() - cached.second > ScreenCache.FRESH_MS) reload(keepContent = true, quiet = true)
        } else {
            viewModelScope.launch {
                saved()?.let { _state.value = UiState.Success(it) }
                reload(keepContent = true, quiet = true)
            }
        }
    }

    public fun reload(keepContent: Boolean = false, quiet: Boolean = false) {
        viewModelScope.launch {
            val hasContent = keepContent && _state.value is UiState.Success
            if (hasContent) {
                if (!quiet) _refreshing.value = true
            } else {
                _state.value = UiState.Loading
            }
            _state.value = try {
                UiState.Success(load().also { ScreenCache.put(key, it) })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (hasContent) _state.value else UiState.Error(friendlyError(e))
            }
            _refreshing.value = false
        }
    }
}

@Composable
public fun <T> rememberLoader(key: String, saved: suspend () -> T? = { null }, load: suspend () -> T): LoaderViewModel<T> =
    viewModel(key = key) { LoaderViewModel(key, load, saved) }
