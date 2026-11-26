package com.grayguava.legato.ui.library

import com.grayguava.legato.ui.components.pinnedToTop
import com.grayguava.legato.ui.components.menuClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import com.grayguava.legato.ui.components.glass
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import com.grayguava.legato.settings.AppSettings
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import com.grayguava.legato.ui.browse.playlistSearchResults
import com.grayguava.legato.ui.browse.coverWash
import com.grayguava.legato.ui.browse.dismissPlaylistSearch
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grayguava.legato.library.MusicRepository
import com.grayguava.legato.library.account.AccountStore
import com.grayguava.legato.library.history.History
import com.grayguava.legato.library.LibraryStore
import com.grayguava.legato.library.PlaylistLinks
import com.grayguava.legato.library.PlaylistSync
import com.grayguava.legato.base.model.CARD_ART_PX
import com.grayguava.legato.base.model.PlaylistPrivacy
import com.grayguava.legato.base.model.ShelfItem
import com.grayguava.legato.base.model.artworkAt
import com.grayguava.legato.base.model.HEADER_ART_PX
import com.grayguava.legato.audio.playback.AudioCache
import com.grayguava.legato.ui.ScreenCache
import com.grayguava.legato.ui.browse.SongActions
import com.grayguava.legato.ui.components.AccountCopyOptions
import com.grayguava.legato.ui.components.Artwork
import com.grayguava.legato.ui.components.FloatingDialog
import com.grayguava.legato.ui.components.GlassIconButton
import com.grayguava.legato.ui.components.MessageState
import com.grayguava.legato.ui.components.PageHeader
import com.grayguava.legato.ui.components.PrivacySheet
import com.grayguava.legato.ui.components.PrivateShareDialog
import com.grayguava.legato.ui.components.SheetButton
import com.grayguava.legato.ui.components.SheetTone
import com.grayguava.legato.ui.components.SongListItem
import com.grayguava.legato.ui.components.meaning
import com.grayguava.legato.ui.icons.SfIcons
import com.grayguava.legato.ui.components.pressable
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where Library's tiles lead. */
public class LibraryNav(
    public val downloads: () -> Unit = {},
    public val local: () -> Unit,
    public val cached: () -> Unit = {},
    public val replay: () -> Unit,
    public val settings: () -> Unit,
    public val liked: () -> Unit,
    public val playlist: (String) -> Unit,
    public val browse: (String) -> Unit,
    public val wrapped: () -> Unit = {},
    public val history: () -> Unit = {},
    /** Every playlist, from Show all. */
    public val allPlaylists: () -> Unit = {},
    /** An album or playlist downloaded as a whole, by its id. */
    public val downloaded: (String) -> Unit = {},
)

private data class LibraryPlaylistRow(
    val key: String, val title: String, val subtitle: String, val url: String?,
    val local: com.grayguava.legato.library.LocalPlaylist? = null,
    /** What it's pinned by: a local playlist's id, or its YouTube Music browse id. */
    val pin: String,
    /** Where it lives: "YouTube Music" or "On this phone". */
    val source: String,
    /** How often its songs were played; only known for the ones kept here. */
    val plays: Int = 0,
    val onClick: () -> Unit,
)

/** Pinning playlists to the top of Library. */
internal object LibraryPins {
    fun pinned(key: String) = key in AppSettings.ui.value.pinnedPlaylists
    /** As many as the row on Library shows. */
    const val MAX = 5

    fun toggle(key: String) {
        val pins = AppSettings.ui.value.pinnedPlaylists
        if (key !in pins && pins.size >= MAX) {
            com.grayguava.legato.base.Pills.info("Up to $MAX playlists can be pinned. Unpin one first")
            return
        }
        AppSettings.updateUi { ui ->
            ui.copy(pinnedPlaylists = if (key in ui.pinnedPlaylists) ui.pinnedPlaylists - key else ui.pinnedPlaylists + key)
        }
    }
}

private const val YT_PLAYLISTS = "library:ytPlaylists"

/** Covers and the icon tiles all sit on this square, ten percent up from 48. */
private val ROW_ART = 53.dp
private val ROW_ART_SHAPE = RoundedCornerShape(10.dp)
private val CARD_SHAPE = RoundedCornerShape(14.dp)

/**
 * Every playlist in Library, the pinned ones first in the order they were pinned,
 * then those made on YouTube Music and those only on this phone.
 */
@Composable
private fun rememberPlaylistRows(nav: LibraryNav): List<LibraryPlaylistRow> {
    val records by History.records.collectAsState()
    val playlists by LibraryStore.playlists.collectAsState()
    val hearted by LibraryStore.savedLists.collectAsState()
    val signedIn by AccountStore.signedIn.collectAsState()
    val pinned = AppSettings.ui.collectAsState().value.pinnedPlaylists
    val cachedPlaylists by com.grayguava.legato.library.LibraryCache.playlists.collectAsState()
    // One linked to a playlist here is listed once, as that one.
    val ytPlaylists = if (signedIn) remember(cachedPlaylists, playlists) { PlaylistLinks.unlinked(cachedPlaylists, playlists) } else emptyList()
    val playCounts = remember(records) { records.groupingBy { it.videoId }.eachCount() }
    val rows = mutableListOf<LibraryPlaylistRow>()
    for (p in playlists) {
        val source = if (p.synced) "YouTube Music" else "On this phone"
        rows += LibraryPlaylistRow(p.id, p.name,
            if (p.songs.size == 1) "1 song" else "${p.songs.size} songs",
            null, p, pin = p.id, source = source,
            plays = p.songs.sumOf { playCounts[it.videoId] ?: 0 }, onClick = { nav.playlist(p.id) })
    }
    for (h in hearted) {
        rows += LibraryPlaylistRow("saved:${h.browseId}", h.title, h.subtitle, h.thumbnailUrl, pin = h.browseId, source = "YouTube Music", onClick = { nav.browse(h.browseId) })
    }
    for (p in ytPlaylists.filter { p -> hearted.none { it.browseId == p.browseId } }) {
        val browseId = p.browseId ?: continue
        rows += LibraryPlaylistRow("yt:$browseId", p.title, p.subtitle, p.thumbnailUrl, pin = browseId, source = "YouTube Music", onClick = { nav.browse(browseId) })
    }
    val order = listOf("YouTube Music", "On this phone")
    val pinnedRows = pinned.mapNotNull { key -> rows.firstOrNull { it.pin == key } }
    return pinnedRows + rows.filter { it !in pinnedRows }.sortedBy { order.indexOf(it.source) }
}

@Composable
public fun LibraryScreen(contentPadding: PaddingValues, actions: SongActions, nav: LibraryNav) {
    val liked by LibraryStore.liked.collectAsState()
    val signedIn by AccountStore.signedIn.collectAsState()
    val account by AccountStore.account.collectAsState()
    val libraryUi by AppSettings.ui.collectAsState()
    val library by AppSettings.library.collectAsState()
    LaunchedEffect(signedIn, account?.email) {
        if (signedIn) runCatching { MusicRepository.libraryPlaylists() }
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    var cachedSongsCount by remember { mutableStateOf(0) }
    LaunchedEffect(library.showCachedSongs) {
        cachedSongsCount = if (library.showCachedSongs) withContext(Dispatchers.IO) { AudioCache.tracks(context).size } else 0
    }
    // The mark at the top shows only for a pull: started from the button further down the
    // page, it sat over the tiles while the button showed its own spinner.
    var pulled by remember { mutableStateOf(false) }
    // Playlists made on YouTube Music since, and changes to linked ones both ways.
    val refresh: () -> Unit = {
        if (!refreshing) {
            refreshing = true
            scope.launch {
                try {
                    val sync = PlaylistSync.refresh()
                    runCatching { MusicRepository.libraryPlaylists() }.onFailure {
                        com.grayguava.legato.base.Pills.error("Couldn't refresh your YouTube Music playlists", context)
                    }
                    sync.join()
                } finally {
                    refreshing = false
                    pulled = false
                }
            }
        }
    }
    var naming by remember { mutableStateOf(false) }
    if (naming) NewPlaylistDialog(signedIn, onDismiss = { naming = false }) { name, privacy -> naming = false; nav.playlist(LibraryStore.createPlaylist(name, privacy = privacy)) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val rows = rememberPlaylistRows(nav)
    // The pinned ones, then the most played; Show all has the rest.
    val top = remember(rows, libraryUi.pinnedPlaylists) {
        val pinned = rows.filter { it.pin in libraryUi.pinnedPlaylists }
        (pinned + rows.filter { it !in pinned }.sortedByDescending { it.plays }).take(5)
    }
    // The top playlists, as rows under the collections.

    val page: @Composable () -> Unit = { LazyColumn(state = listState, contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
        item {
            PageHeader("Library", inlineTitle = true, actions = {
                // The account in one glass pill.
                Row(
                    Modifier.height(54.dp).glass(androidx.compose.foundation.shape.CircleShape).padding(horizontal = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    com.grayguava.legato.ui.components.AccountButton(nav.settings, plain = true)
                }
            })
        }
        item {
            Column(Modifier.padding(vertical = 4.dp)) {
                CollectionRow(SfIcons.ThinHeartFill, "Liked Music", if (liked.isEmpty()) "Auto playlist" else "${liked.size} songs · plays first") { nav.liked() }
                CollectionRow(SfIcons.ThinChart, "Replay", "Your top songs") { nav.replay() }
                CollectionRow(SfIcons.ThinHistory, "Recents", "Recently played") { nav.history() }
                CollectionRow(SfIcons.ThinSmartphone, "On this phone", "Local files") { nav.local() }
                if (library.showCachedSongs) CollectionRow(SfIcons.ThinStorage, "Cached songs", if (cachedSongsCount == 0) "Nothing kept yet" else "$cachedSongsCount songs kept") { nav.cached() }
            }
        }
        item(key = "playlist-header") {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Playlists", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (signedIn) {
                    Box(Modifier.size(32.dp).pressable({ if (!refreshing) { pulled = false; refresh() } }, 0.9f), Alignment.Center) {
                        if (refreshing) androidx.compose.material3.CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Icon(SfIcons.ThinRestart, "Refresh YouTube Music playlists", Modifier.size(18.dp))
                    }
                }
                Text(
                    "See all",
                    Modifier.pressable(nav.allPlaylists, 0.96f).padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1,
                )
                Row(
                    Modifier.height(34.dp).glass(androidx.compose.foundation.shape.CircleShape)
                        .pressable({ naming = true }, 0.96f).padding(start = 12.dp, end = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(SfIcons.ThinPlus, null, Modifier.size(13.dp))
                    Text("New playlist", Modifier.padding(start = 5.dp), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }
        // Liked Music already sits with the collections; these are the playlists under it.
        itemsIndexed(top) { _, row -> LibraryPlaylistRowItem(row) }
    } }

    // Pull to refresh only when there's an account to refresh from.
    if (signedIn) {
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(isRefreshing = refreshing && pulled, onRefresh = { pulled = true; refresh() }, modifier = Modifier.fillMaxSize()) { page() }
    } else {
        page()
    }
}

/** The orders "Show all" can put playlists in. Pinned ones stay first in each. */
private enum class PlaylistOrder(val label: String) {
    LIBRARY("Where they're from"), PLAYED("Most played"), NEWEST("Newest first"), NAME("A to Z"), SONGS("Most songs");

    fun sort(rows: List<LibraryPlaylistRow>): List<LibraryPlaylistRow> = when (this) {
        LIBRARY -> rows
        PLAYED -> rows.sortedByDescending { it.plays }
        NEWEST -> rows.sortedByDescending { it.local?.createdAt ?: 0L }
        NAME -> rows.sortedBy { it.title.lowercase() }
        SONGS -> rows.sortedByDescending { it.local?.songs?.size ?: -1 }
    }
}

/** Every playlist, two to a row, from "Show all" on Library. */
@Composable
public fun AllPlaylistsScreen(contentPadding: PaddingValues, nav: LibraryNav, onBack: () -> Unit) {
    val liked by LibraryStore.liked.collectAsState()
    val signedIn by AccountStore.signedIn.collectAsState()
    val pinned = AppSettings.ui.collectAsState().value.pinnedPlaylists
    var order by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(PlaylistOrder.LIBRARY) }
    var sorting by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    if (naming) NewPlaylistDialog(signedIn, onDismiss = { naming = false }) { name, privacy -> naming = false; nav.playlist(LibraryStore.createPlaylist(name, privacy = privacy)) }
    val rows = rememberPlaylistRows(nav)
    val shown = remember(rows, order, pinned) {
        val top = rows.filter { it.pin in pinned }
        top + order.sort(rows.filter { it !in top })
    }
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    // Behind it all, the cover of the first playlist in view, blurred across the top and running
    // down into its colour; as the page scrolls, both follow the playlists on screen.
    val byKey = remember(shown) { shown.associateBy { it.key } }
    val coverUrl by remember(byKey) {
        androidx.compose.runtime.derivedStateOf {
            gridState.layoutInfo.visibleItemsInfo.firstNotNullOfOrNull { item ->
                byKey[item.key]?.let { it.url ?: it.local?.songs?.firstOrNull()?.thumbnailUrl }
            }?.artworkAt(CARD_ART_PX)
        }
    }
    val seed = com.grayguava.legato.ui.theme.rememberArtworkSeed(coverUrl)
    val background = MaterialTheme.colorScheme.background
    val accent by androidx.compose.animation.animateColorAsState(
        seed?.let { androidx.compose.ui.graphics.lerp(background, it, 0.24f) } ?: background,
        androidx.compose.animation.core.tween(800), label = "playlistsAccent",
    )
    com.grayguava.legato.ui.components.GlassBackground(
        background = {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to accent, 0.6f to accent,
                1f to androidx.compose.ui.graphics.lerp(background, accent, 0.45f))))
            coverUrl?.let { CoverWash(it, accent, Modifier.fillMaxWidth().height(440.dp)) }
        },
    ) {
    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
        state = gridState,
        // Two cards to a row, one playlist to a card.
        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
    ) {
        item(key = "header", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            PageHeader("Playlists", onBack = onBack, actions = {
                // Sorting and the account in one glass pill, as on Library.
                Row(
                    Modifier.height(54.dp).glass(androidx.compose.foundation.shape.CircleShape).padding(horizontal = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        IconButton(onClick = { sorting = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Sort playlists") }
                        androidx.compose.material3.DropdownMenu(sorting, { sorting = false }, shape = RoundedCornerShape(20.dp)) {
                            PlaylistOrder.entries.forEach { o ->
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(o.label, fontWeight = if (o == order) FontWeight.SemiBold else null) },
                                    trailingIcon = if (o == order) ({ Icon(Icons.Rounded.Check, null) }) else null,
                                    onClick = { order = o; sorting = false },
                                )
                            }
                        }
                    }
                    com.grayguava.legato.ui.components.AccountButton(nav.settings, plain = true)
                }
            })
        }
        item(key = "new") { NewPlaylistCard(signedIn) { naming = true } }
        item(key = "liked") { LikedCard(liked.size, nav.liked) }
        items(shown.size, key = { shown[it].key }) { i -> PlaylistCard(shown[i], Modifier.animateItem()) }
    }
    }
}

/**
 * A cover blurred and spread wide, fading down into [into]. Before Android 12, which
 * can't blur, a tiny copy of it stretched to fill gives the same soft wash.
 */
@Composable
private fun CoverWash(url: String, into: Color, modifier: Modifier) {
    Box(modifier.clipToBounds()) {
        val blurs = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
        androidx.compose.animation.Crossfade(url, animationSpec = androidx.compose.animation.core.tween(800), label = "coverWash") { u ->
            Artwork(
                if (blurs) u else u.artworkAt(24),
                Modifier.fillMaxSize().graphicsLayer { scaleX = 1.5f; scaleY = 1.5f; alpha = 0.8f }
                    .then(if (blurs) Modifier.blur(70.dp) else Modifier),
                shape = androidx.compose.ui.graphics.RectangleShape,
            )
        }
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to into.copy(alpha = 0.1f), 0.6f to into.copy(alpha = 0.55f), 1f to into)))
    }
}

/** A square picture, icon or cover, that starts every library row. */
@Composable
private fun RowArt(background: Brush? = null, content: @Composable () -> Unit = {}) {
    Box(
        Modifier.size(ROW_ART).clip(ROW_ART_SHAPE)
            .then(if (background != null) Modifier.background(background) else Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)),
        Alignment.Center,
    ) { content() }
}

/** The text half of every library row: a name, and the line that explains it. */
@Composable
private fun RowText(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A card in Playlists: a square picture, then the name and the line that explains it. */
@Composable
private fun LibraryCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    art: @Composable () -> Unit,
) {
    Column(modifier.fillMaxWidth().then(Modifier.menuClickable(onClick = onClick, onLongClick = onLongClick))) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(CARD_SHAPE), Alignment.Center) { art() }
        Text(
            title, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(
            subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun NewPlaylistCard(signedIn: Boolean, onClick: () -> Unit) {
    LibraryCard("New playlist", if (signedIn) "Saved to YouTube Music" else "On this phone", onClick) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh))
        Icon(SfIcons.ThinPlus, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Liked Music as a card, in the purple running into pink. */
@Composable
private fun LikedCard(count: Int, onClick: () -> Unit) {
    LibraryCard("Liked Music", if (count == 0) "Auto playlist" else "Auto playlist · $count songs", onClick) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF8C6AF0), Color(0xFFF95AAE)))))
        Icon(SfIcons.ThinHeartFill, null, Modifier.size(56.dp), tint = Color.White)
    }
}

/** One playlist as a card; held down, it can be pinned to the top. */
@Composable
private fun PlaylistCard(row: LibraryPlaylistRow, modifier: Modifier = Modifier) {
    PinnableRow(row.pin, modifier.fillMaxWidth()) { onLongClick ->
        LibraryCard(
            row.title,
            if (row.local != null) "${row.source} · ${row.subtitle}" else row.subtitle,
            row.onClick,
            modifier,
            onLongClick = onLongClick,
        ) {
            if (row.local != null && row.url == null) {
                com.grayguava.legato.ui.components.PlaylistCover(
                    row.title, row.local.songs.map { it.thumbnailUrl },
                    Modifier.fillMaxSize(), androidx.compose.ui.graphics.RectangleShape, nameOnCover = false,
                )
            } else {
                Artwork(row.url.artworkAt(CARD_ART_PX), Modifier.fillMaxSize(), androidx.compose.ui.graphics.RectangleShape)
            }
        }
    }
}

/** One playlist as a row; held down, it can be pinned to the top. */
@Composable
private fun LibraryPlaylistRowItem(row: LibraryPlaylistRow, modifier: Modifier = Modifier) {
    PinnableRow(row.pin, Modifier.fillMaxWidth()) { onLongClick ->
        PlaylistRow(
            art = if (row.local != null && row.url == null) null else row.url,
            title = row.title,
            subtitle = if (row.local != null) "${row.source} · ${row.subtitle}" else row.subtitle,
            onClick = row.onClick,
            modifier = modifier,
            onLongClick = onLongClick,
            cover = if (row.local != null && row.url == null) {
                {
                    com.grayguava.legato.ui.components.PlaylistCover(
                        row.title, row.local.songs.map { it.thumbnailUrl }, Modifier.size(ROW_ART), ROW_ART_SHAPE,
                    )
                }
            } else null,
        )
    }
}

@Composable
private fun PlaylistRow(
    art: String?,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cover: (@Composable () -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().then(Modifier.menuClickable(onClick = onClick, onLongClick = onLongClick)).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (cover != null) {
            cover()
        } else if (art != null) {
            Artwork(art.artworkAt(CARD_ART_PX), Modifier.size(ROW_ART), ROW_ART_SHAPE)
        } else {
            RowArt { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        RowText(title, subtitle, Modifier.padding(start = 14.dp).weight(1f))
    }
}

/** One collection as a row: icon, title, the count that explains it. */
@Composable
private fun CollectionRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowArt { Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary) }
        RowText(title, subtitle, Modifier.padding(start = 14.dp).weight(1f))
        Icon(
            imageVector = SfIcons.ChevronForward,
            contentDescription = null,
            modifier = Modifier.size(16.5.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
        )
    }
}

/** [content] with a long press that offers to pin playlist [pin] to the top of Library, or unpin it. */
@Composable
private fun PinnableRow(pin: String, modifier: Modifier = Modifier, content: @Composable (onLongClick: () -> Unit) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val pinned = AppSettings.ui.collectAsState().value.pinnedPlaylists.contains(pin)
    Box(modifier) {
        content { menu = true }
        androidx.compose.material3.DropdownMenu(menu, { menu = false }, shape = RoundedCornerShape(20.dp)) {
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(if (pinned) "Unpin" else "Pin to the top") },
                leadingIcon = { Icon(Icons.Rounded.PushPin, null) },
                onClick = { menu = false; LibraryPins.toggle(pin) },
            )
        }
    }
}

@Composable
internal fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    FloatingDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, placeholder = { Text("Name") }) },
        confirmButton = { SheetButton("Save", onClick = { onConfirm(name) }) },
        dismissButton = { SheetButton("Cancel", onClick = onDismiss, closes = true) },
    )
}

/**
 * Names a new playlist. Signed in, it can go on YouTube Music too (on unless
 * turned off), with a choice of who can see it there; [onCreate] gets that
 * privacy, or null for a playlist on this phone only.
 */
@Composable
internal fun NewPlaylistDialog(signedIn: Boolean, onDismiss: () -> Unit, onCreate: (String, PlaylistPrivacy?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var online by remember { mutableStateOf(true) }
    var privacy by remember { mutableStateOf(PlaylistPrivacy.PRIVATE) }
    FloatingDialog(
        onDismissRequest = onDismiss,
        title = { Text("New playlist") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, singleLine = true, placeholder = { Text("Name") })
                if (signedIn) AccountCopyOptions(online, { online = it }, privacy, { privacy = it }, Modifier.padding(top = 12.dp))
            }
        },
        confirmButton = { SheetButton("Save", onClick = { onCreate(name, privacy.takeIf { signedIn && online }) }) },
        dismissButton = { SheetButton("Cancel", onClick = onDismiss, closes = true) },
    )
}

/** Puts a playlist that so far lives only on this phone on YouTube Music too, with who can see it there. */
@Composable
private fun SaveToAccountDialog(name: String, onDismiss: () -> Unit, onSave: (PlaylistPrivacy) -> Unit) {
    var privacy by remember { mutableStateOf(PlaylistPrivacy.PRIVATE) }
    FloatingDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save to YouTube Music") },
        text = {
            Column {
                Text("Makes a copy of $name on YouTube Music, songs and all. Changes you make here go there too.")
                com.grayguava.legato.ui.components.PillSegmented(PlaylistPrivacy.entries, privacy, { it.label }, { privacy = it }, Modifier.padding(top = 16.dp))
                Text(privacy.meaning, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { SheetButton("Save", onClick = { onSave(privacy) }) },
        dismissButton = { SheetButton("Cancel", onClick = onDismiss, closes = true) },
    )
}

/** Songs liked in the app (and on YouTube Music, when signed in). */
@Composable
public fun LikedScreen(contentPadding: PaddingValues, actions: SongActions, onBack: () -> Unit, onOpenYouTubeLikes: () -> Unit) {
    val liked by LibraryStore.liked.collectAsState()
    val signedIn by AccountStore.signedIn.collectAsState()
    val songs = remember(liked) { liked.map { it.toSong() } }
    LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
        item { PageHeader("Liked Music", onBack = onBack) }
        if (signedIn) {
            item {
                TextButton(onClick = onOpenYouTubeLikes, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Liked on YouTube Music") }
            }
        }
        if (songs.isEmpty()) {
            item {
                MessageState(Icons.Rounded.Favorite, "No liked songs", Modifier.padding(top = 32.dp), message = "Tap the heart in the player, or Like in a song's menu.")
            }
            return@LazyColumn
        }
        item {
            PlayButtons(
                onPlay = { actions.playAll(songs, 0, false, "Liked Music") },
                onShuffle = { actions.playAll(songs, 0, true, "Liked Music") },
                onSmartShuffle = { actions.smartShuffle(songs, "Liked Music") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        itemsIndexed(songs, key = { _, s -> s.videoId }) { i, song ->
            SongListItem(
                song = song,
                onClick = { actions.playAll(songs, i, false, "Liked Music") },
                modifier = Modifier.animateItem(),
                isCurrent = song.videoId == actions.currentVideoId,
                isPlaying = actions.isPlaying,
                onPlayNext = { actions.playNext(song) },
                onAddToQueue = { actions.addToQueue(song) },
            )
        }
    }
}

/**
 * A playlist made in the app: play, rename, delete, remove songs. Signed in,
 * it can also be saved to YouTube Music, after which it's kept in step there
 * and has a privacy and a link to share.
 */
@Composable
public fun LocalPlaylistScreen(id: String, contentPadding: PaddingValues, actions: SongActions, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playlists by LibraryStore.playlists.collectAsState()
    val signedIn by AccountStore.signedIn.collectAsState()
    val pending by PlaylistSync.pending.collectAsState()
    val playlist = playlists.firstOrNull { it.id == id }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var savingOnline by remember { mutableStateOf(false) }
    var choosingPrivacy by remember { mutableStateOf(false) }
    var privateShare by remember { mutableStateOf(false) }
    if (adding && playlist != null) AddSongsSheet(id, onDismiss = { adding = false })
    // Changes made on YouTube Music since, once this page's own have gone.
    val remoteId = playlist?.remoteId
    LaunchedEffect(id, signedIn, remoteId) {
        if (signedIn && remoteId != null) PlaylistSync.refresh(id)
    }
    if (playlist == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    // Tagged with the playlist, so smart shuffle can offer to keep a song it mixed in.
    val songs = remember(playlist) { playlist.songs.map { it.toSong().copy(playbackSourceId = playlist.id) } }
    val search = remember(id) { com.grayguava.legato.ui.browse.PlaylistSearch() }
    val query = search.query
    val searching = search.open
    var order by remember(id) { mutableStateOf(com.grayguava.legato.ui.browse.ListOrder.DEFAULT) }
    var sortOpen by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(searching, query) {
        if (searching) listState.scrollToItem(0)
    }
    val matching = remember(songs, query, order) {
        val found = songs.indices.filter { songs[it].title.contains(query.trim(), true) || songs[it].artist.contains(query.trim(), true) }
        when (order) {
            com.grayguava.legato.ui.browse.ListOrder.DEFAULT -> found
            com.grayguava.legato.ui.browse.ListOrder.A_TO_Z -> found.sortedBy { songs[it].title.lowercase() }
            com.grayguava.legato.ui.browse.ListOrder.Z_TO_A -> found.sortedByDescending { songs[it].title.lowercase() }
        }
    }
    val shown = matching.map(songs::get)
    val online = signedIn && playlist.synced
    if (renaming) NameDialog("Rename playlist", playlist.name, onDismiss = { renaming = false }) { LibraryStore.renamePlaylist(id, it); renaming = false }
    if (deleting) {
        // Signed out, the copy on YouTube Music can't be reached, so it's left as it is.
        val linked = signedIn && remoteId != null
        var everywhere by remember { mutableStateOf(false) }
        FloatingDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Delete \"${playlist.name}\"?") },
            text = {
                Column {
                    Text("The songs stay where they are; only this list goes.")
                    if (linked) {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 12.dp).clip(MaterialTheme.shapes.medium).clickable { everywhere = !everywhere }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Also delete it on YouTube Music", color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                            com.grayguava.legato.ui.components.AppSwitch(everywhere, { everywhere = it })
                        }
                    }
                }
            },
            confirmButton = { SheetButton("Delete", tone = SheetTone.Danger, onClick = { deleting = false; LibraryStore.deletePlaylist(id, alsoOnAccount = linked && everywhere) }) },
            dismissButton = { SheetButton("Cancel", onClick = { deleting = false }, closes = true) },
        )
    }
    if (savingOnline) SaveToAccountDialog(playlist.name, onDismiss = { savingOnline = false }) { privacy ->
        savingOnline = false
        LibraryStore.saveToAccount(id, privacy)
    }
    if (choosingPrivacy) PrivacySheet(playlist.privacy, { LibraryStore.setPlaylistPrivacy(id, it) }, onDismiss = { choosingPrivacy = false })
    if (privateShare && remoteId != null) PrivateShareDialog(
        onMakePublic = {
            privateShare = false
            LibraryStore.setPlaylistPrivacy(id, PlaylistPrivacy.PUBLIC)
            com.grayguava.legato.ui.share.ListShare.sharePlaylist(context, playlist.name, remoteId, com.grayguava.legato.ui.share.ListShare.coversFor(null, songs), com.grayguava.legato.ui.share.ListShare.countLine(songs.size))
        },
        onShareAnyway = {
            privateShare = false
            com.grayguava.legato.ui.share.ListShare.sharePlaylist(context, playlist.name, remoteId, com.grayguava.legato.ui.share.ListShare.coversFor(null, songs), com.grayguava.legato.ui.share.ListShare.countLine(songs.size))
        },
        onDismiss = { privateShare = false },
    )
    // On YouTube Music, that link; otherwise (or signed out, or not made there yet) the songs themselves.
    val share: () -> Unit = {
        when {
            !signedIn || remoteId == null -> com.grayguava.legato.ui.share.ListShare.shareLocal(context, playlist.name, songs)
            playlist.privacy?.linkWorks == true -> com.grayguava.legato.ui.share.ListShare.sharePlaylist(context, playlist.name, remoteId, com.grayguava.legato.ui.share.ListShare.coversFor(null, songs), com.grayguava.legato.ui.share.ListShare.countLine(songs.size))
            else -> privateShare = true
        }
    }
    val tools: @Composable (Modifier) -> Unit = { modifier ->
        ListTools(
            songs,
            onAddSongs = { adding = true },
            onShare = share,
            modifier = modifier,
            privacy = playlist.privacy,
            onPrivacy = if (online) ({ choosingPrivacy = true }) else null,
        )
    }
    if (moreOpen) LocalListMoreSheet(playlist, songs, actions,
        onRefresh = {
            if (signedIn && remoteId != null) PlaylistSync.refresh(id)
        },
        onAddSongs = { adding = true }, onRename = { renaming = true }, onDelete = { deleting = true },
        onShare = share, onDismiss = { moreOpen = false })
    // Where the playlist stands with YouTube Music, under its cover.
    val status: (@Composable () -> Unit)? = when {
        !signedIn -> null
        !playlist.synced -> ({
            TextButton(onClick = { savingOnline = true }) {
                Icon(Icons.Rounded.CloudUpload, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Save to YouTube Music")
            }
        })
        else -> ({
            val waiting = pending.count { it.playlist == id }
            Text(
                when {
                    pending.any { it.playlist == id && it.kind == com.grayguava.legato.library.PlaylistEdit.Kind.CREATE } -> "Saving to YouTube Music…"
                    waiting > 0 -> "On YouTube Music · ${if (waiting == 1) "1 change" else "$waiting changes"} to send"
                    else -> "On YouTube Music"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        })
    }
    com.grayguava.legato.ui.components.GlassPage(Modifier.fillMaxSize().dismissPlaylistSearch(search), overlay = {
        com.grayguava.legato.ui.browse.PlaylistSearchHeader(search) {
        com.grayguava.legato.ui.browse.CollapsingBar(playlist.name, listState, onBack, forceCollapsed = searching,
            artworkBehind = songs.any { it.thumbnailUrl != null }) {
            Row(Modifier.height(48.dp).glass(androidx.compose.foundation.shape.CircleShape).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (searching) search.close() else search.open = true }) {
                    Icon(if (searching) Icons.Rounded.Close else Icons.Rounded.Search, "Search this list")
                }
                Box {
                    IconButton(onClick = { sortOpen = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Sort") }
                    androidx.compose.material3.DropdownMenu(sortOpen, { sortOpen = false }) {
                        com.grayguava.legato.ui.browse.ListOrder.entries.forEach { value ->
                            androidx.compose.material3.DropdownMenuItem(text = { Text(value.label) }, onClick = { order = value; sortOpen = false })
                        }
                    }
                }
                IconButton(onClick = { moreOpen = true }) { Icon(Icons.Rounded.MoreHoriz, "More") }
            }
        }
        }
    }) {
    val tint = com.grayguava.legato.ui.browse.rememberCoverTint(songs.firstOrNull()?.thumbnailUrl.artworkAt(CARD_ART_PX))
    val band = com.grayguava.legato.ui.components.rememberRubberBand()
    com.grayguava.legato.ui.components.GlassBackground(
        background = { Box(Modifier.fillMaxSize().coverWash(tint)) },
    ) {
    LazyColumn(state = listState, contentPadding = if (searching) PaddingValues(bottom = 16.dp) else contentPadding,
        modifier = Modifier.fillMaxSize().playlistSearchResults(search), overscrollEffect = band) {
        if (!searching) item(key = "header") {
            Box {
                com.grayguava.legato.ui.browse.HeaderBackdrop(songs.firstOrNull()?.thumbnailUrl.artworkAt(HEADER_ART_PX), Modifier.matchParentSize().pinnedToTop(band), into = tint)
                Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(top = 64.dp, start = 24.dp, end = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    com.grayguava.legato.ui.browse.PlaylistHeading(playlist.name,
                        if (playlist.synced) "YouTube Music" else "On this phone",
                        null, songs, cover = {
                            com.grayguava.legato.ui.components.PlaylistCover(playlist.name, songs.map { it.thumbnailUrl }, Modifier.fillMaxSize(), MaterialTheme.shapes.large)
                        })
                    com.grayguava.legato.ui.browse.ListPlayRow(shown.isNotEmpty(), false,
                        onPlay = { actions.playAll(shown, 0, false, playlist.name) },
                        onShuffle = { actions.playAll(shown, 0, true, playlist.name) },
                        onSmartShuffle = { actions.smartShuffle(shown, playlist.name) },
                        onSave = null,
                        modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 12.dp))
                    // The same row of tools as a YouTube Music playlist's page.
                    tools(Modifier.fillMaxWidth().padding(bottom = 12.dp))
                    if (status != null) status()
                }
            }
        }
        if (songs.isEmpty()) {
            item {
                MessageState(Icons.AutoMirrored.Rounded.QueueMusic, "Nothing here yet", Modifier.padding(top = 32.dp), message = "Add songs here, or use Add to playlist in any song's menu.")
            }
            return@LazyColumn
        }
        // Keyed by the song (and which copy of it), not its place, so a row taken out pops
        // alone and the rows below slide up rather than every row after it being redrawn.
        if (shown.isEmpty()) item { Text("No matching songs", Modifier.padding(24.dp)) }
        val keys = songKeys(songs)
        itemsIndexed(shown, key = { i, _ -> keys[matching[i]] }) { i, song ->
            SongListItem(
                song = song,
                onClick = { actions.playAll(shown, i, false, playlist.name) },
                modifier = Modifier.animateItem(),
                isCurrent = song.videoId == actions.currentVideoId,
                isPlaying = actions.isPlaying,
                onPlayNext = { actions.playNext(song) },
                onAddToQueue = { actions.addToQueue(song) },
                onRemoveFromPlaylist = { removeWithUndo(id, matching[i]) },
            )
        }
    }
    }
}
}

/**
 * A playlist page's own menu, as a YouTube Music playlist's has it: the list's cover and
 * name, then what can be done with it.
 */
@Composable
private fun LocalListMoreSheet(
    playlist: com.grayguava.legato.library.LocalPlaylist,
    songs: List<com.grayguava.legato.base.model.Song>,
    actions: SongActions,
    onRefresh: () -> Unit,
    onAddSongs: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val pinned = AppSettings.ui.collectAsState().value.pinnedPlaylists.contains(playlist.id)
    com.grayguava.legato.ui.components.FloatingCard(
        onDismiss = onDismiss,
        contentPadding = PaddingValues(0.dp),
        startExpanded = true,
        header = {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                com.grayguava.legato.ui.components.PlaylistCover(playlist.name, songs.map { it.thumbnailUrl }, Modifier.size(56.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(playlist.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(com.grayguava.legato.ui.share.ListShare.countLine(songs.size),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        },
    ) {
        val card = com.grayguava.legato.ui.components.LocalFloatingCard.current
        val close: () -> Unit = { card?.close() ?: onDismiss() }
        Column {
            com.grayguava.legato.ui.components.MenuRow(Icons.Rounded.Refresh, "Refresh") { close(); onRefresh() }
            com.grayguava.legato.ui.components.MenuRow(Icons.AutoMirrored.Rounded.PlaylistPlay, "Play next") { close(); songs.asReversed().forEach(actions.playNext) }
            com.grayguava.legato.ui.components.MenuRow(Icons.AutoMirrored.Rounded.QueueMusic, "Add to queue") { close(); songs.forEach(actions.addToQueue) }

            com.grayguava.legato.ui.components.MenuRow(Icons.Rounded.Add, "Add songs") { close(); onAddSongs() }
            com.grayguava.legato.ui.components.MenuRow(Icons.Rounded.Edit, "Rename") { close(); onRename() }
            com.grayguava.legato.ui.components.MenuRow(Icons.Rounded.Share, "Share") { close(); onShare() }
            com.grayguava.legato.ui.components.MenuRow(Icons.Rounded.PushPin, if (pinned) "Unpin from Library" else "Pin to the top of Library") { close(); LibraryPins.toggle(playlist.id) }
            com.grayguava.legato.ui.components.MenuRow(Icons.Rounded.DeleteOutline, "Delete") { close(); onDelete() }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** A key for each of [songs] that stays with the song when others are taken out or put back. */
internal fun songKeys(songs: List<com.grayguava.legato.base.model.Song>): List<String> {
    val seen = HashMap<String, Int>()
    return songs.map { s ->
        val copy = seen.merge(s.videoId, 1, Int::plus)!!
        "${s.videoId}#$copy"
    }
}

/** Takes the song at [index] out of playlist [id], with a few seconds to Undo it. */
private fun removeWithUndo(id: String, index: Int) {
    val removed = LibraryStore.removeUndoably(id, index) ?: return
    com.grayguava.legato.base.Pills.withAction("Removed \"${removed.song.title}\"", "Undo") { LibraryStore.undoRemove(removed) }
}
