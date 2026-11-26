package com.grayguava.legato.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grayguava.legato.base.model.PlaylistPrivacy
import com.grayguava.legato.base.model.Song
import com.grayguava.legato.library.HiddenList
import com.grayguava.legato.library.LibraryStore
import com.grayguava.legato.library.PlaylistLinks
import com.grayguava.legato.library.PlaylistSync
import com.grayguava.legato.library.Ringtones
import com.grayguava.legato.source.isYouTubeId
import com.grayguava.legato.ui.icons.SfIcons
import kotlinx.coroutines.launch

/** What a song's menu can do beyond the library, wired up by the app shell. */
public class SongMenuActions(
    public val playNext: (Song) -> Unit,
    public val addToQueue: (Song) -> Unit,
    public val startRadio: (Song) -> Unit,
    public val openAlbum: (String) -> Unit,
    public val openArtist: (String) -> Unit,
    /** Marks a song as disliked; the player skips it if it's playing, and drops its queued copies. */
    public val dislike: (Song) -> Unit = LibraryStore::dislike,
    /** Opens the song's artist, finding the page when the row didn't say which. */
    public val viewArtist: ((Song) -> Unit)? = null,
    /** Opens one of the song's artists by name, when it has several. */
    public val viewArtistNamed: ((Song, String) -> Unit)? = null,
    /** Opens the song's album, finding the page when the row didn't say which. */
    public val viewAlbum: ((Song) -> Unit)? = null,
    /** Where the song is and how long it runs, when it's the one playing; for the share picture. */
    public val progressOf: (Song) -> Pair<Long, Long>? = { null },
    /** "Not interested": keeps the song out of suggestions; the shell offers Undo. */
    public val notInterested: (Song) -> Unit = { LibraryStore.hideSong(it) },
    /** "Don't play [artist]": keeps the song's artist out of suggestions. */
    public val dontPlayArtist: (Song) -> Unit = { LibraryStore.hideArtist(it) },
    /** Adds a song smart shuffle mixed in to the playlist the queue came from. */
    public val keepInPlaylist: (Song, String) -> Unit = { s, id -> LibraryStore.addToPlaylist(id, s); com.grayguava.legato.audio.playback.SmartShuffle.keep(s.videoId) },
)

public val LocalSongMenu: ProvidableCompositionLocal<SongMenuActions?> =
        staticCompositionLocalOf<SongMenuActions?> { null }

/**
 * The full song menu a row or a card opens, on long press or the ⋮: the rows
 * every screen can act on, plus the library's own (like, playlist, hide,
 * ringtone). The dialogs are the caller's: the row closes the panel first,
 * then asks for one.
 */
@Composable
public fun homeSongMenu(
    song: Song,
    onDismiss: () -> Unit,
    onPlayNext: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onRingtone: (() -> Unit)? = null,
    onSongInfo: (() -> Unit)? = null,
): List<MenuItem> {
    val actions = LocalSongMenu.current
    val context = LocalContext.current
    val liked by LibraryStore.liked.collectAsState()
    val hidden by LibraryStore.hidden.collectAsState()
    val playlists by LibraryStore.playlists.collectAsState()
    val origin by com.grayguava.legato.audio.playback.SmartShuffle.origin.collectAsState()
    val isLiked = liked.any { it.videoId == song.videoId }
    // Files on this phone and songs on your server: no YouTube actions.
    val local = !isYouTubeId(song.videoId)
    // A song smart shuffle mixed into one of the app's own playlists can be kept there.
    val keepIn = origin?.playlistId?.takeIf { song.smartShuffle }
        ?.let { id -> playlists.firstOrNull { it.id == id } }
        ?.takeIf { p -> p.songs.none { it.videoId == song.videoId } }
    val playNext: (() -> Unit)? = onPlayNext ?: actions?.let { a -> { a.playNext(song) } }
    val addToQueue: (() -> Unit)? = onAddToQueue ?: actions?.let { a -> { a.addToQueue(song) } }
    return buildList {
        add(MenuItem.Tile(
            if (isLiked) SfIcons.HeartFill else SfIcons.Heart,
            if (isLiked) "Liked" else "Like",
            iconOnly = true,
            // Liked, it burns the player's red instead of the menu's white.
            tint = if (isLiked) Color(0xFFFF4D6D) else null,
        ) {
            LibraryStore.setLiked(song, !isLiked)
        })
        if (!local) {
            add(MenuItem.Tile(SfIcons.Share, "Share", iconOnly = true) {
                onDismiss()
                val (at, length) = actions?.progressOf?.invoke(song) ?: (0L to 0L)
                com.grayguava.legato.ui.share.ShareCard.share(context, song, at, length)
            })
        }
        add(MenuItem.Separator)
        if (onAddToPlaylist != null) add(MenuItem.Row(SfIcons.Plus, "Add to playlist") { onDismiss(); onAddToPlaylist() })
        if (!local && actions != null) add(MenuItem.Row(SfIcons.Radio, "Start radio") { onDismiss(); actions.startRadio(song) })
        playNext?.let { pick -> add(MenuItem.Row(SfIcons.PlayNext, "Play next") { onDismiss(); pick() }) }
        addToQueue?.let { pick -> add(MenuItem.Row(SfIcons.DragHandle, "Add to queue") { onDismiss(); pick() }) }
        onRemoveFromPlaylist?.let { remove -> add(MenuItem.Row(SfIcons.Trash, "Remove from playlist") { onDismiss(); remove() }) }
        keepIn?.let { p -> add(MenuItem.Row(SfIcons.Plus, "Keep in playlist") { onDismiss(); actions?.keepInPlaylist?.invoke(song, p.id) }) }
        add(MenuItem.Separator)
        val artist = song.artist.takeIf { it.isNotBlank() }
        when {
            actions?.viewArtist != null && !local -> add(MenuItem.Row(SfIcons.Person, "Open artist", artist) { onDismiss(); actions.viewArtist.invoke(song) })
            song.artistId != null -> add(MenuItem.Row(SfIcons.Person, "Open artist", artist) { onDismiss(); actions?.openArtist?.invoke(song.artistId!!) })
        }
        when {
            song.albumId != null -> add(MenuItem.Row(SfIcons.Album, "Open album", song.albumName) { onDismiss(); actions?.openAlbum?.invoke(song.albumId!!) })
            actions?.viewAlbum != null && !local -> add(MenuItem.Row(SfIcons.Album, "Open album", song.albumName) { onDismiss(); actions.viewAlbum.invoke(song) })
        }
        if (onSongInfo != null) add(MenuItem.Row(SfIcons.Info, "Song info") { onDismiss(); onSongInfo() })
        if (local && onRingtone != null && Ringtones.canUse(song)) add(MenuItem.Row(SfIcons.Phone, "Set as ringtone") { onDismiss(); onRingtone() })
        if (actions != null) {
            add(MenuItem.Separator)
            add(MenuItem.Row(SfIcons.MusicOff, "Not for me (dislike)") { onDismiss(); actions.dislike(song) })
            if (hidden.hidesSong(song.videoId)) {
                add(MenuItem.Row(SfIcons.Hidden, "Suggest this song again") { LibraryStore.unhideSong(song.videoId); onDismiss() })
            } else {
                add(MenuItem.Row(SfIcons.Hidden, "Not interested") { onDismiss(); actions.notInterested(song) })
            }
            val hiddenArtist = HiddenList.mainArtist(song.artist)
            if (hiddenArtist.isNotBlank()) {
                val entry = hidden.artists.firstOrNull { it.key == HiddenList.normalize(hiddenArtist) }
                if (entry != null) {
                    add(MenuItem.Row(SfIcons.PersonAdd, "Suggest $hiddenArtist again") { LibraryStore.unhideArtist(entry.key); onDismiss() })
                } else {
                    add(MenuItem.Row(SfIcons.PersonAdd, "Don't play $hiddenArtist") { onDismiss(); actions.dontPlayArtist(song) })
                }
            }
        }
    }
}

@Composable
public fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Icon(icon, null, Modifier.size(24.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** One playlist to pick in [AddToPlaylistDialog]: its cover, its name, how many songs, and a tick if the song is in it. */
@Composable
private fun PlaylistPickRow(
    title: String,
    subtitle: String,
    art: @Composable () -> Unit,
    added: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp))) { art() }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (added) Icon(Icons.Rounded.CheckCircle, "Already in this playlist", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
    }
}

/**
 * Picks one of the playlists made in the app, or makes a new one with [song]
 * in it. Signed in, the account's own YouTube Music playlists are listed too,
 * and a song picked for one of those goes straight there; one linked to a
 * playlist here is listed once, as that one.
 */
@Composable
public fun AddToPlaylistDialog(song: Song, onDone: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val allPlaylists by LibraryStore.playlists.collectAsState()
    val playlists = remember(allPlaylists) { allPlaylists }
    val signedIn by com.grayguava.legato.library.account.AccountStore.signedIn.collectAsState()
    val account by com.grayguava.legato.library.LibraryCache.playlists.collectAsState()
    val owned by com.grayguava.legato.library.LibraryCache.owned.collectAsState()
    // A file on this phone can't go on YouTube Music.
    val online = signedIn && isYouTubeId(song.videoId)
    val accountPlaylists = if (online) remember(account, owned, playlists) { PlaylistLinks.pickable(account, owned, playlists) } else emptyList()
    // Which of the account's playlists already have the song, looked up as the sheet opens.
    val inAccount = remember(song.videoId) { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
    LaunchedEffect(accountPlaylists, song.videoId) {
        val gate = kotlinx.coroutines.sync.Semaphore(ACCOUNT_LOOKUPS)
        kotlinx.coroutines.coroutineScope {
            accountPlaylists.take(MAX_ACCOUNT_CHECKED).filter { it.playlistId !in inAccount }.forEach { p ->
                launch {
                    gate.acquire()
                    try {
                        runCatching { com.grayguava.legato.library.MusicRepository.collection(p.browseId) }.getOrNull()
                            ?.let { c -> inAccount[p.playlistId] = c.songs.any { it.videoId == song.videoId } }
                    } finally {
                        gate.release()
                    }
                }
            }
        }
    }
    var naming by remember { mutableStateOf(playlists.isEmpty() && accountPlaylists.isEmpty()) }
    var name by remember { mutableStateOf("") }
    var saveOnline by remember { mutableStateOf(true) }
    var privacy by remember { mutableStateOf(PlaylistPrivacy.PRIVATE) }
    FloatingDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (naming) "New playlist" else "Add to playlist") },
        text = {
            if (naming) {
                Column {
                    OutlinedTextField(name, { name = it }, singleLine = true, placeholder = { Text("Name") })
                    if (online) AccountCopyOptions(saveOnline, { saveOnline = it }, privacy, { privacy = it }, Modifier.padding(top = 12.dp))
                }
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    item {
                        PlaylistPickRow(
                            title = "New playlist",
                            subtitle = "With this song in it",
                            art = {
                                Box(
                                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp)) }
                            },
                            onClick = { naming = true },
                        )
                    }
                    items(playlists, key = { it.id }) { p ->
                        val has = p.songs.any { it.videoId == song.videoId }
                        PlaylistPickRow(
                            title = p.name,
                            subtitle = if (has) "Already in it" else if (p.songs.size == 1) "1 song" else "${p.songs.size} songs",
                            art = { PlaylistCover(p.name, p.songs.map { it.thumbnailUrl }, Modifier.fillMaxSize(), RectangleShape) },
                            added = has,
                            onClick = {
                                if (has) {
                                    com.grayguava.legato.base.Pills.info("Already in ${p.name}", context)
                                } else {
                                    LibraryStore.addToPlaylist(p.id, song)
                                    com.grayguava.legato.base.Pills.done("Added to ${p.name}", context)
                                }
                                onDone()
                            },
                        )
                    }
                    if (accountPlaylists.isNotEmpty()) {
                        item {
                            Text(
                                "On YouTube Music",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 4.dp),
                            )
                        }
                        items(accountPlaylists, key = { "yt:${it.playlistId}" }) { p ->
                            val has = inAccount[p.playlistId] == true
                            PlaylistPickRow(
                                title = p.title,
                                subtitle = if (has) "Already in it" else p.subtitle.ifBlank { "YouTube Music" },
                                art = { Artwork(p.thumbnailUrl, Modifier.fillMaxSize(), RectangleShape, placeholder = Icons.AutoMirrored.Rounded.QueueMusic) },
                                added = has,
                            ) {
                                if (has) {
                                    com.grayguava.legato.base.Pills.info("Already in ${p.title}", context)
                                    onDone()
                                    return@PlaylistPickRow
                                }
                                PlaylistSync.addToAccountPlaylist(p.playlistId, song) { ok ->
                                    if (ok) com.grayguava.legato.base.Pills.done("Added to ${p.title}", context)
                                    else com.grayguava.legato.base.Pills.error("Couldn't add it to ${p.title}", context)
                                }
                                onDone()
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (naming) {
                SheetButton("Create", onClick = {
                    LibraryStore.createPlaylist(name, song, privacy.takeIf { online && saveOnline })
                    com.grayguava.legato.base.Pills.done("Added to ${name.trim().ifEmpty { "New playlist" }}", context)
                    onDone()
                })
            } else {
                // A pick adds and closes on its own; all the alert needs is a way out.
                SheetButton("Cancel", onClick = onDismiss, closes = true)
            }
        },
        dismissButton = if (naming) {
            { SheetButton("Cancel", onClick = onDismiss, closes = true) }
        } else null,
    )
}

/** Account playlists looked up at once, and at most, to mark the ones that already have the song. */
private const val ACCOUNT_LOOKUPS = 4
private const val MAX_ACCOUNT_CHECKED = 30
