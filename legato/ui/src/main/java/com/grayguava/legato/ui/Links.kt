package com.grayguava.legato.ui

import android.app.SearchManager
import android.content.Intent
import android.provider.MediaStore
import java.net.URI
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Something a YouTube or YouTube Music link points at. */
public sealed interface LinkTarget {
    public data class Song(val videoId: String) : LinkTarget
    public data class Browse(val browseId: String) : LinkTarget
    /** "Play … on Legato" from the Assistant; blank means "play some music". */
    public data class PlaySearch(val query: String) : LinkTarget
    /** A song on another service (Apple Music, Deezer, Tidal…), to find and play here. */
    public data class Elsewhere(val url: String) : LinkTarget
    /** Open the app with the player up. */
    public data object Player : LinkTarget
}

/**
 * Reads music.youtube.com, youtube.com and youtu.be links: songs and videos,
 * playlists, albums and channels. Anything else is null.
 */
public object Links {
    private val HOSTS = setOf("music.youtube.com", "www.youtube.com", "youtube.com", "m.youtube.com", "youtu.be")
    private val ID = Regex("[A-Za-z0-9_-]+")

    /** Opens the app with the player up. */
    public const val ACTION_OPEN_PLAYER: String = "com.grayguava.legato.action.OPEN_PLAYER"

    public fun parse(text: String?): LinkTarget? {
        val raw = text?.let { Regex("""https?://\S+""").find(it)?.value } ?: return null
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (host !in HOSTS) return null
        val query = uri.rawQuery.orEmpty().split('&').mapNotNull {
            val (k, v) = it.split('=', limit = 2).takeIf { p -> p.size == 2 } ?: return@mapNotNull null
            k to v
        }.toMap()
        val path = uri.path.orEmpty().trim('/').split('/').filter { it.isNotEmpty() }
        fun valid(id: String?) = id?.takeIf { ID.matches(it) }
        return when {
            host == "youtu.be" -> valid(path.firstOrNull())?.let(LinkTarget::Song)
            path.firstOrNull() == "watch" -> valid(query["v"])?.let(LinkTarget::Song)
                ?: valid(query["list"])?.let { LinkTarget.Browse(playlistBrowseId(it)) }
            path.firstOrNull() == "shorts" -> valid(path.getOrNull(1))?.let(LinkTarget::Song)
            path.firstOrNull() == "playlist" -> valid(query["list"])?.let { LinkTarget.Browse(playlistBrowseId(it)) }
            path.firstOrNull() == "browse" -> valid(path.getOrNull(1))?.let(LinkTarget::Browse)
            path.firstOrNull() == "channel" -> valid(path.getOrNull(1))?.let(LinkTarget::Browse)
            else -> null
        }
    }

    /** Playlists are browsed with a "VL" in front of their id. */
    private fun playlistBrowseId(id: String) = if (id.startsWith("VL")) id else "VL$id"

    /** Links handed to the app from outside, waiting for the UI to open them. */
    private val _incoming = MutableStateFlow<LinkTarget?>(null)
    public val incoming: StateFlow<LinkTarget?> = _incoming.asStateFlow()

    public fun receive(intent: Intent?) {
        intent ?: return
        if (intent.action == ACTION_OPEN_PLAYER) {
            _incoming.value = LinkTarget.Player
            return
        }
        if (intent.action == MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) {
            _incoming.value = LinkTarget.PlaySearch(intent.getStringExtra(SearchManager.QUERY).orEmpty())
            return
        }
        val text = when (intent.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        }
        parse(text)?.let {
            _incoming.value = it
            return
        }
        com.grayguava.legato.source.links.MusicLinks.linkIn(text)?.let { _incoming.value = LinkTarget.Elsewhere(it) }
    }

    public fun consumed() {
        _incoming.value = null
    }
}
