package com.grayguava.legato.library

import com.grayguava.legato.base.model.HomeShelf
import com.grayguava.legato.base.model.MoodGenre
import com.grayguava.legato.base.model.MoodGenreSection
import com.grayguava.legato.base.model.ShelfItem
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Explore as rows: artists to jump to, then moods and genres to browse, then
 * new albums and chart playlists — each shelf its own horizontally scrolling
 * row of cards.
 */
public object ExploreFeed {
    public sealed interface Section {
        public val title: String

        /** A row of mood or genre buttons; [genre] only decides where the row sits. */
        public data class Buttons(
            override val title: String,
            val moods: List<MoodGenre>,
            val genre: Boolean,
        ) : Section

        /** A row of albums or playlists, taken from one shelf the server sent. */
        public data class Titles(override val title: String, val items: List<ShelfItem>) : Section
    }

    /** An artist pill: a page to open, a name and maybe a photo. */
    public data class Artist(val browseId: String, val name: String, val thumbnailUrl: String?)

    public data class Board(val artists: List<Artist>, val rows: List<Section>)

    public suspend fun load(): Board = coroutineScope {
        val moods = async { runCatching { MusicRepository.moodsAndGenres() }.getOrDefault(emptyList()) }
        val releases = async { runCatching { MusicRepository.shelves("FEmusic_new_releases", null) }.getOrDefault(emptyList()) }
        val charts = async { runCatching { MusicRepository.shelves("FEmusic_charts", null) }.getOrDefault(emptyList()) }

        val chartShelves = charts.await()
        val chartArtists = artistsOf(chartShelves)
        val mine = MusicRepository.personalArtists()
        val artists = mine.ifEmpty { chartArtists }.distinctBy { it.browseId }.take(24)

        Board(artists, rows(moods.await(), releases.await(), chartShelves))
    }

    /**
     * The page's rows in the order it shows them: moods, genres, new releases,
     * charts. Server sections keep their own titles and their own order; empty
     * shelves drop out rather than leaving a bare header.
     */
    public fun rows(
        moods: List<MoodGenreSection>,
        releases: List<HomeShelf>,
        charts: List<HomeShelf>,
    ): List<Section> {
        fun buttons(genre: Boolean): List<Section> = moods
            .filter { it.title.contains("genre", ignoreCase = true) == genre }
            .map { Section.Buttons(it.title, it.items, genre) }
            .filter { it.moods.isNotEmpty() }

        // A chart shelf also carries channels ("Top artists"); a card of those
        // is not a chart, so they stay out.
        fun titles(shelves: List<HomeShelf>, chart: Boolean = false): List<Section> = shelves.mapNotNull { shelf ->
            val items = shelf.items.filter {
                val id = it.browseId
                id != null && !(chart && id.startsWith("UC"))
            }
            if (items.isEmpty()) null else Section.Titles(shelf.title, items)
        }

        return buttons(genre = false) + buttons(genre = true) + titles(releases) + titles(charts, chart = true)
    }

    /** The artists on a page's shelves (the charts' "Top artists"), each once, in order. */
    public fun artistsOf(shelves: List<com.grayguava.legato.base.model.HomeShelf>): List<Artist> =
        shelves.flatMap { it.items }.filter { it.browseId?.startsWith("UC") == true }
            .map { Artist(it.browseId!!, it.title, it.thumbnailUrl) }
            .distinctBy { it.browseId }

    /**
     * Takes from each list in turn, so a set of shelves mixes them instead of
     * showing every item of the first before the first of the next.
     */
    public fun <T> weave(lists: List<List<T>>): List<T> {
        val out = ArrayList<T>(lists.sumOf { it.size })
        val iterators = lists.map { it.iterator() }
        while (iterators.any { it.hasNext() }) iterators.forEach { if (it.hasNext()) out += it.next() }
        return out
    }
}
