package com.grayguava.legato.ui.explore

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grayguava.legato.base.model.BrowseType
import com.grayguava.legato.base.model.CARD_ART_PX
import com.grayguava.legato.base.model.MoodGenre
import com.grayguava.legato.base.model.ShelfItem
import com.grayguava.legato.base.model.UiState
import com.grayguava.legato.base.model.artworkAt
import com.grayguava.legato.library.ExploreFeed
import com.grayguava.legato.library.MusicRepository
import com.grayguava.legato.ui.components.Artwork
import com.grayguava.legato.ui.components.ErrorState
import com.grayguava.legato.ui.components.ItemCard
import com.grayguava.legato.ui.components.MessageState
import com.grayguava.legato.ui.components.PageHeader
import com.grayguava.legato.ui.components.Shelf
import com.grayguava.legato.ui.components.ShelfPlaceholder
import com.grayguava.legato.ui.components.pressable
import com.grayguava.legato.ui.home.HomeShelfView
import com.grayguava.legato.ui.rememberLoader
import com.grayguava.legato.ui.theme.rememberArtworkSeed
import com.grayguava.legato.ui.type
import kotlinx.coroutines.sync.withPermit

/**
 * Explore as rows: artists to jump straight to, then moods, genres, new
 * releases and charts, each shelf its own row of cards scrolling sideways.
 */
@Composable
public fun ExploreScreen(
    contentPadding: PaddingValues,
    onMoodClick: (MoodGenre) -> Unit,
    onItemClick: (ShelfItem) -> Unit,
    onOpenSettings: (() -> Unit)? = null,
    /** False inside the Search page, which has its own bar at the top. */
    showHeader: Boolean = true,
) {
    val header: (@Composable () -> Unit)? = if (!showHeader) null else ({
        PageHeader("Explore", actions = onOpenSettings?.let { open -> { com.grayguava.legato.ui.components.AccountButton(open) } }, inlineTitle = true)
    })
    val loader = rememberLoader("explore:canvas") { ExploreFeed.load() }
    val state by loader.state.collectAsState()

    when (val s = state) {
        is UiState.Error -> Column(Modifier.fillMaxSize()) {
            header?.invoke()
            Box(Modifier.fillMaxSize().padding(contentPadding), Alignment.Center) { ErrorState(s.message, onRetry = { loader.reload() }) }
        }
        else -> ExploreBoard((s as? UiState.Success)?.data, contentPadding, onMoodClick, onItemClick, header)
    }
}

/** The rows themselves, from a loaded [board] (null while it loads). */
@Composable
internal fun ExploreBoard(
    board: ExploreFeed.Board?,
    contentPadding: PaddingValues,
    onMoodClick: (MoodGenre) -> Unit,
    onItemClick: (ShelfItem) -> Unit,
    header: (@Composable () -> Unit)? = { PageHeader("Explore", inlineTitle = true) },
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (header != null) item(key = "header") { header() }
        if (board == null) {
            items(4) { ShelfPlaceholder() }
            return@LazyColumn
        }
        if (board.artists.isNotEmpty()) {
            item(key = "artists") {
                Shelf("Your artists", board.artists) { artist ->
                    ItemCard(
                        title = artist.name,
                        subtitle = "",
                        thumbnailUrl = artist.thumbnailUrl,
                        type = BrowseType.ARTIST,
                        onClick = { onItemClick(ShelfItem(artist.name, "", artist.thumbnailUrl, null, artist.browseId)) },
                        width = ARTIST_CARD,
                    )
                }
            }
        }
        if (board.rows.isEmpty()) {
            item(key = "empty") {
                MessageState(Icons.Filled.Explore, "Nothing to explore", message = "YouTube Music didn't send anything for this.")
            }
        }
        itemsIndexed(board.rows, key = { i, _ -> "row$i" }) { _, row ->
            when (row) {
                is ExploreFeed.Section.Buttons -> Shelf(row.title, row.moods) { mood ->
                    MoodCard(mood) { onMoodClick(mood) }
                }
                is ExploreFeed.Section.Titles -> Shelf(row.title, row.items) { item ->
                    ItemCard(
                        title = item.title,
                        subtitle = item.subtitle,
                        thumbnailUrl = item.thumbnailUrl,
                        type = item.type(),
                        onClick = { onItemClick(item) },
                        explicit = item.explicit,
                    )
                }
            }
        }
    }
}

/** Artists are narrower than the albums and playlists under them. */
private val ARTIST_CARD = 120.dp

/** Cards keep the album card's frame: 156dp wide with its 6dp breathing room. */
private val MOOD_CARD = 156.dp
private val MOOD_PAD = 6.dp

/**
 * A mood or genre: its cover fills the card and the name sits in a gradient
 * mixed from the cover's own colour, so the white label always has a floor.
 * The first playlist on the mood's page supplies the cover.
 */
@Composable
private fun MoodCard(mood: MoodGenre, onClick: () -> Unit) {
    var cover by remember(mood) { mutableStateOf(MoodCovers.cached(mood)) }
    LaunchedEffect(mood) { if (cover == null) cover = MoodCovers.load(mood) }
    val url = cover?.artworkAt(CARD_ART_PX)
    val seed = rememberArtworkSeed(cover)
    val base = seed ?: MaterialTheme.colorScheme.surfaceContainerHigh
    // A pale cover needs more shade than a dark one before white text is safe.
    val shade = if (base.luminance() > 0.5f) 0.7f else 0.45f
    // Reading the cover takes a moment; the gradient settles into its colour.
    val mid by animateColorAsState(base.copy(alpha = 0.35f), tween(400), label = "moodMid")
    val foot by animateColorAsState(lerp(base, Color.Black, shade).copy(alpha = 0.94f), tween(400), label = "moodFoot")
    Box(Modifier.width(MOOD_CARD).padding(MOOD_PAD)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f)
                .pressable(onClick, pressedScale = 0.97f)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            AnimatedVisibility(cover != null, enter = fadeIn(tween(500)), modifier = Modifier.matchParentSize()) {
                Artwork(url, Modifier.fillMaxSize(), RectangleShape)
            }
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(0f to Color.Transparent, 0.55f to mid, 1f to foot),
                ),
            )
            Text(
                mood.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
            )
        }
    }
}

/**
 * Covers for mood cards, fetched lazily (the first playlist on each mood's
 * page), a few at a time, and kept for the session.
 */
private object MoodCovers {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val gate = kotlinx.coroutines.sync.Semaphore(3)

    fun cached(mood: MoodGenre): String? = mood.thumbnailUrl ?: cache[mood.browseId + mood.params]

    suspend fun load(mood: MoodGenre): String? {
        cached(mood)?.let { return it }
        return gate.withPermit {
            runCatching {
                MusicRepository.shelves(mood.browseId, mood.params)
                    .firstNotNullOfOrNull { shelf -> shelf.items.firstNotNullOfOrNull { it.thumbnailUrl } }
            }.getOrNull()?.also { cache[mood.browseId + mood.params] = it }
        }
    }
}

/** A mood or genre's own page: shelves of playlists. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun MoodScreen(
    title: String,
    browseId: String,
    params: String?,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onItemClick: (ShelfItem) -> Unit,
) {
    val loader = rememberLoader("mood:$browseId:$params") { MusicRepository.shelves(browseId, params) }
    val state by loader.state.collectAsState()
    Column(Modifier.fillMaxSize()) {
        PageHeader(title, onBack = onBack)
        when (val s = state) {
            is UiState.Loading -> LazyColumn { items(3) { ShelfPlaceholder() } }
            is UiState.Error -> Box(Modifier.fillMaxSize().padding(contentPadding), Alignment.Center) {
                ErrorState(s.message, onRetry = { loader.reload() })
            }
            is UiState.Success -> LazyColumn(contentPadding = contentPadding) {
                items(s.data) { HomeShelfView(it, onItemClick) }
            }
        }
    }
}
