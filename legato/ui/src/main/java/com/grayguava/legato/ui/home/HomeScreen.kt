package com.grayguava.legato.ui.home

import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.grayguava.legato.library.account.AccountStore
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grayguava.legato.library.MusicRepository
import com.grayguava.legato.settings.ContentFilter
import com.grayguava.legato.library.history.History
import com.grayguava.legato.base.model.HomeShelf
import com.grayguava.legato.base.model.ROW_ART_PX
import com.grayguava.legato.base.model.ShelfItem
import com.grayguava.legato.base.model.Song
import com.grayguava.legato.base.model.UiState
import com.grayguava.legato.base.model.artworkAt
import com.grayguava.legato.settings.AppSettings
import com.grayguava.legato.uicore.R
import com.grayguava.legato.ui.browse.SongActions
import com.grayguava.legato.ui.components.SongRowPlaceholder
import com.grayguava.legato.ui.components.Artwork
import com.grayguava.legato.ui.components.ErrorState
import com.grayguava.legato.ui.components.GlassIconButton
import com.grayguava.legato.ui.components.ItemCard
import com.grayguava.legato.ui.components.RefreshBarSlot
import com.grayguava.legato.ui.components.SectionHeader
import com.grayguava.legato.ui.components.Shelf
import com.grayguava.legato.ui.components.ShelfPlaceholder
import com.grayguava.legato.ui.components.SongListItem
import com.grayguava.legato.ui.rememberLoader
import com.grayguava.legato.ui.theme.rememberArtworkSeed
import com.grayguava.legato.ui.toSong
import com.grayguava.legato.ui.type
import com.grayguava.legato.ui.cardMenu

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
public fun HomeScreen(
    contentPadding: PaddingValues,
    actions: SongActions,
    onItemClick: (ShelfItem) -> Unit,
    onOpenSettings: () -> Unit,
    /** The full player is open over the page, so nothing on it needs to move. */
    covered: Boolean = false,
) {
    val platform = "android"
    val playback by AppSettings.playback.collectAsState()
    val loader = rememberLoader("home:${playback.recommender.name}") { MusicRepository.home() }
    val state by loader.state.collectAsState()
    val refreshing by loader.refreshing.collectAsState()
    // New picks shown as soon as they're made, while the rest of the page reloads. They stay
    // until the next refresh: YouTube Music's own Quick picks, the same every time, showed
    // first and were swapped for these a few seconds later.
    var freshPicks by remember { mutableStateOf<HomeShelf?>(null) }
    var picksPending by remember { mutableStateOf(false) }
    val pickScope = androidx.compose.runtime.rememberCoroutineScope()
    val refreshHome: () -> Unit = {
        freshPicks = null
        picksPending = true
        val picks = MusicRepository.refreshPicks()
        pickScope.launch {
            try { picks.await().firstOrNull()?.let { freshPicks = it } } finally { picksPending = false }
        }
        loader.reload(keepContent = true)
    }
    val moreAll by MusicRepository.homeMore.collectAsState()
    val library by AppSettings.library.collectAsState()
    val hideExplicit = library.hideExplicit
    // What the user hid is taken off every song shelf and out of the mixes.
    val hidden by com.grayguava.legato.library.LibraryStore.hidden.collectAsState()
    val more = remember(moreAll, hideExplicit, hidden) { hidden.shelves(ContentFilter.shelves(moreAll, hideExplicit)) }
    val records by History.records.collectAsState()
    val newestListen = records.firstOrNull()?.playedAt
    var shownListen by remember { mutableStateOf(newestListen) }
    var lastListeningRefresh by remember { mutableStateOf(0L) }
    LaunchedEffect(newestListen, covered) {
        if (!covered && newestListen != shownListen) {
            val wait = (60_000L - (android.os.SystemClock.elapsedRealtime() - lastListeningRefresh)).coerceAtLeast(0)
            kotlinx.coroutines.delay(wait)
            shownListen = newestListen
            lastListeningRefresh = android.os.SystemClock.elapsedRealtime()
            refreshHome()
        }
    }
    val first = (state as? UiState.Success<List<HomeShelf>>)?.data?.let { hidden.shelves(ContentFilter.shelves(it, hideExplicit)) }.orEmpty()
    val allShelves = first + more
    val feed = remember(first, more) {
        val firstPage = arrangeHomeShelves(first)
        val rest = arrangeHomeShelves(allShelves)
        rest.copy(quickPicks = firstPage.quickPicks ?: rest.quickPicks)
    }
    // What the user hid is taken off every song shelf and out of the mixes.
    val mixes = remember(records, hidden) {
        com.grayguava.legato.library.history.DailyMixes.build(records, hidden = hidden)
    }
    val signedIn by AccountStore.signedIn.collectAsState()
    val account by AccountStore.account.collectAsState()
    val savedAccounts by AccountStore.accounts.collectAsState()
    val homeAccount = signedIn to savedAccounts.firstOrNull { it.active }?.id
    // Switching between two signed-in accounts must refresh recommendations too.
    var seenAccount by remember { mutableStateOf(homeAccount) }
    LaunchedEffect(homeAccount) {
        if (homeAccount != seenAccount) {
            seenAccount = homeAccount
            MusicRepository.refreshHomeRecommendations()
            loader.reload(keepContent = true)
        }
    }

    val listState = rememberLazyListState()
    // Home tapped again: back up to the top, and the page refreshes with its pull mark.
    LaunchedEffect(listState) {
        com.grayguava.legato.ui.TabEvents.reselected.collect { r ->
            if (r != "home") return@collect
            if (listState.firstVisibleItemIndex > 12) listState.scrollToItem(6)
            listState.animateScrollToItem(0)
            refreshHome()
        }
    }

    val refreshState = rememberPullToRefreshState()
    // The shell draws the bar above the top frost; inside the page the frost's
    // blur would smear its stripes.
    androidx.compose.runtime.SideEffect {
        RefreshBarSlot.state.value = refreshState
        RefreshBarSlot.refreshing.value = refreshing
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { RefreshBarSlot.state.value = null } }
    Box(
        Modifier.fillMaxSize().pullToRefresh(
            isRefreshing = refreshing,
            state = refreshState,
            threshold = 81.6.dp,
            onRefresh = refreshHome,
        ),
    ) {
        LazyColumn(state = listState, contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
            item(key = "top") {
                // The app's own mark at the top left, with the name when there
                // is one, and the account (and settings) one tap away on the right.
                val firstName = (account?.name.orEmpty())
                    .substringBefore(' ').trim().takeIf { it.isNotBlank() }
                Column(
                    Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)
                        .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
                ) {
                    // The mark sits where the greeting used to, with the name beside it.
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(
                            painter = painterResource(R.drawable.ic_stat_legato),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp),
                        )
                        if (firstName != null) {
                            Text(
                                firstName,
                                style = MaterialTheme.typography.displaySmall,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                        com.grayguava.legato.ui.components.AccountButton(onOpenSettings)
                    }
                }
            }
            for (section in HOME_SECTIONS) when (section) {
                "quick-picks" -> {
                    val title = "Quick picks"
                    val picks = freshPicks ?: feed.quickPicks
                    item(key = "quick-picks") {
                        if ((refreshing || picksPending) && freshPicks == null) SongRowPlaceholder()
                        else if (picks != null) QuickPicks(picks.copy(title = title, subtitle = ""), onItemClick)
                        else SectionHeader(title)
                    }
                    if (picks != null) item(key = "line:quick-picks") { HomeLine() }
                }
                "mixes" -> if (mixes.isNotEmpty()) {
                    item(key = "mixes") {
                        MixesShelf(mixes, actions, MIXES_LINE, title = "Made for you today")
                    }
                    item(key = "line:mixes") { HomeLine() }
                }
                "recommendations" -> {
                    when (val s = state) {
                        is UiState.Loading -> items(3) { ShelfPlaceholder() }
                        is UiState.Error -> item(key = "error") {
                            ErrorState(s.message, onRetry = { loader.reload() }, modifier = Modifier.padding(top = 24.dp))
                        }
                        is UiState.Success -> {
                            itemsIndexed(feed.cards, key = { i, shelf -> "shelf:$i:${shelf.title}" }, contentType = { _, _ -> "recommendations" }) { i, shelf ->
                                Column {
                                    HomeShelfView(shelf, onItemClick, songRows = false)
                                    HomeLine()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A hairline between home's rows, with air above and below it. */
@Composable
private fun HomeLine() {
    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(Dp.Hairline)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        )
        Spacer(Modifier.height(14.dp))
    }
}

/** Songs-only shelves of four or more are laid out as quick picks. */
private fun HomeShelf.isQuickPicks(): Boolean =
    items.size >= 4 && items.all { it.videoId != null && it.browseId == null }

/** Home uses a single paged track list followed by collection and track cards. */
internal data class HomeSections(val quickPicks: HomeShelf?, val cards: List<HomeShelf>)

/** The shelves Home shows, in order. */
private val HOME_SECTIONS = listOf("quick-picks", "mixes", "recommendations")

/**
 * One track list, then the collection shelves: playlists and mixes first, then
 * albums, artists and the rest, in the provider's order within each kind.
 * The picks are the radio off a recent song, else the first songs shelf.
 */
internal fun arrangeHomeShelves(shelves: List<HomeShelf>): HomeSections {
    // YouTube Music sends its own Speed dial carousel, marked personal: it isn't
    // shown any more, here or anywhere else.
    val all = shelves.filter { it.items.isNotEmpty() && !it.personal }
    val available = all
    val pickIndex = available.indexOfFirst { it.isQuickPicks() && it.title.startsWith("Because you played", ignoreCase = true) }
        .takeIf { it >= 0 } ?: available.indexOfFirst { it.isQuickPicks() }
    val remaining = available.filterIndexed { index, _ -> index != pickIndex }
    // Collections only: a second songs-only shelf would repeat the track list above.
    val collections = remaining.filter { shelf -> shelf.items.any { it.browseId != null } }
    // Playlist/mix collections first, retaining the provider's order within each kind.
    val cards = collections.sortedBy { shelf ->
        if (shelf.items.any { it.browseId?.startsWith("VL") == true }) 0 else 1
    }
    return HomeSections(available.getOrNull(pickIndex), cards)
}

/**
 * A shelf of cards, or quick picks for a shelf of songs.
 */
@Composable
public fun HomeShelfView(shelf: HomeShelf, onItemClick: (ShelfItem) -> Unit, modifier: Modifier = Modifier, songRows: Boolean = true) {
    Box(modifier) {
        if (songRows && shelf.isQuickPicks()) {
            QuickPicks(shelf, onItemClick)
        } else {
            Shelf(title = shelf.title, subtitle = shelf.subtitle, items = shelf.items) { item ->
                ItemCard(item.title, item.subtitle, item.thumbnailUrl, item.type(), onClick = { onItemClick(item) }, explicit = item.explicit, menu = item.cardMenu(), homeMenu = true)
            }
        }
    }
}

/** A shelf of single tracks, laid out four rows deep like YouTube Music's quick picks. */
@Composable
private fun QuickPicks(shelf: HomeShelf, onItemClick: (ShelfItem) -> Unit) {
    Column {
        SectionHeader(shelf.title, subtitle = shelf.subtitle)
        val pages = rememberPagerState(pageCount = { (shelf.items.size + 3) / 4 })
        HorizontalPager(
            state = pages,
            modifier = Modifier.fillMaxWidth().height(64.dp * 4),
            verticalAlignment = Alignment.Top,
            // The next column's covers peek at the edge, so there's clearly more.
            contentPadding = PaddingValues(end = 40.dp),
        ) { page ->
            Column(Modifier.fillMaxWidth()) {
                for (item in shelf.items.drop(page * 4).take(4)) {
                    val song = item.toSong()
                    if (song != null) {
                        // The same row as Recents, so each gets the three-dot menu.
                        SongListItem(
                            song = song,
                            onClick = { onItemClick(item) },
                            modifier = Modifier.fillMaxWidth().height(64.dp),
                        )
                    } else {
                        Row(
                            Modifier.fillMaxWidth().height(64.dp).clickable { onItemClick(item) }.padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Artwork(item.thumbnailUrl.artworkAt(ROW_ART_PX), Modifier.size(48.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                                com.grayguava.legato.ui.components.SubtitleLine(item.subtitle, explicit = item.explicit)
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val MIXES_LINE = "Daily mixes from what you play, new each day"

/** Today's mixes from this phone's listening, each a card of four covers. */
@Composable
internal fun MixesShelf(
    mixes: List<com.grayguava.legato.library.history.DailyMixes.Mix>,
    actions: SongActions,
    line: String = MIXES_LINE,
    title: String = "Made for you today",
) {
    Column(Modifier.padding(top = 18.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Text(
            line,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        androidx.compose.foundation.lazy.LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        ) {
            items(mixes.size, key = { mixes[it].id }) { i ->
                val mix = mixes[i]
                MixCard(mix) { actions.playAll(mix.songs, 0, false, mix.title) }
            }
        }
    }
}

@Composable
private fun MixCard(mix: com.grayguava.legato.library.history.DailyMixes.Mix, onClick: () -> Unit) {
    // A tall chip: the cover square on top, its name and artists below on a band
    // of the cover's own colour, darkened so the words stay readable.
    val seed = rememberArtworkSeed(mix.covers.firstOrNull()) ?: MaterialTheme.colorScheme.primary
    val band = androidx.compose.ui.graphics.Brush.verticalGradient(
        listOf(
            androidx.compose.ui.graphics.lerp(seed, androidx.compose.ui.graphics.Color.Black, 0.12f),
            androidx.compose.ui.graphics.lerp(seed, androidx.compose.ui.graphics.Color.Black, 0.62f),
        ),
    )
    Column(
        Modifier.width(50.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
    ) {
        Artwork(mix.covers.firstOrNull(), Modifier.size(50.dp), androidx.compose.ui.graphics.RectangleShape)
        Column(
            Modifier.fillMaxWidth().height(20.dp).background(band).padding(horizontal = 3.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                mix.title,
                fontSize = 8.sp,
                lineHeight = 9.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                color = androidx.compose.ui.graphics.Color.White,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                mix.subtitle,
                fontSize = 7.sp,
                lineHeight = 8.sp,
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.82f),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}


/** The activity's own lifecycle, apart from the page's (which also stops when another page covers it). */
@Composable
private fun LocalActivityLifecycle(): androidx.lifecycle.Lifecycle? {
    var c: android.content.Context? = androidx.compose.ui.platform.LocalContext.current
    while (c is android.content.ContextWrapper && c !is androidx.lifecycle.LifecycleOwner) c = c.baseContext
    return (c as? androidx.lifecycle.LifecycleOwner)?.lifecycle
}
