package com.grayguava.legato.library

import com.grayguava.legato.base.model.HomeShelf
import com.grayguava.legato.base.model.MoodGenre
import com.grayguava.legato.base.model.MoodGenreSection
import com.grayguava.legato.base.model.ShelfItem
import org.junit.Assert.assertEquals
import org.junit.Test

class ExploreFeedTest {
    @Test fun weavesListsInTurn() {
        assertEquals(listOf(1, "a", 2, "b", 3, 4), ExploreFeed.weave(listOf(listOf(1, 2, 3, 4), listOf("a", "b"), emptyList())))
    }

    private fun item(id: String) = ShelfItem(id, "", null, null, id)
    private fun shelf(title: String, vararg ids: String) = HomeShelf(title, ids.map(::item))
    private fun mood(title: String) = MoodGenre(title, "browse", null)

    @Test fun rowsComeInPageOrder() {
        val rows = ExploreFeed.rows(
            moods = listOf(
                MoodGenreSection("Genres", listOf(mood("Rock"))),
                MoodGenreSection("Moods", listOf(mood("Chill"))),
            ),
            releases = listOf(shelf("New albums", "a")),
            charts = listOf(shelf("Trending", "b")),
        )
        assertEquals(listOf("Moods", "Genres", "New albums", "Trending"), rows.map { it.title })
    }

    @Test fun emptyShelvesAndSongsWithoutAPageDropOut() {
        val rows = ExploreFeed.rows(
            moods = listOf(MoodGenreSection("Moods", emptyList())),
            releases = listOf(HomeShelf("New albums", listOf(ShelfItem("song", "", null, "v1", null)))),
            charts = emptyList(),
        )
        assertEquals(emptyList<ExploreFeed.Section>(), rows)
    }

    @Test fun chartsLeaveOutChannels() {
        val rows = ExploreFeed.rows(
            moods = emptyList(),
            releases = emptyList(),
            charts = listOf(shelf("Top artists", "UC1"), shelf("Trending", "VL1")),
        )
        assertEquals(listOf("Trending"), rows.map { it.title })
        assertEquals(listOf("VL1"), (rows.single() as ExploreFeed.Section.Titles).items.map { it.browseId })
    }
}
