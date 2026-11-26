package com.grayguava.legato.ui.home

import com.grayguava.legato.base.model.HomeShelf
import com.grayguava.legato.base.model.ShelfItem
import org.junit.Assert.*
import org.junit.Test

class HomeSectionsTest {
    private fun songs(title: String) = HomeShelf(title, (1..4).map {
        ShelfItem("$title $it", "Artist", null, "$title-$it", null)
    })
    private fun collection(title: String, id: String) = HomeShelf(title,
        listOf(ShelfItem(title, "For you", null, null, id)))

    @Test fun historyPicksLeadAndOtherSongShelvesDoNotRepeatAsCards() {
        val generic = songs("Quick picks")
        val related = songs("Because you played Millionaire")
        val more = songs("Your favourites")
        val album = collection("New albums", "MPRE123")
        val playlist = collection("Playlists for you", "VLPL123")
        val input = listOf(generic, related, more, album, playlist)
        val result = arrangeHomeShelves(input)
        assertEquals(related, result.quickPicks)
        assertEquals(listOf(playlist, album), result.cards)
    }

    @Test fun songOnlyFeedKeepsJustOneListAndEmptyShelvesAreOmitted() {
        val first = songs("Quick picks")
        val second = songs("Discover")
        val result = arrangeHomeShelves(listOf(HomeShelf("Empty", emptyList()), first, second))
        assertEquals(first, result.quickPicks)
        assertEquals(emptyList<HomeShelf>(), result.cards)
        assertNull(arrangeHomeShelves(emptyList()).quickPicks)
    }

    @Test fun collectionsStillAppearWithoutSongRecommendations() {
        val playlist = collection("Playlists", "VLPL123")
        val result = arrangeHomeShelves(listOf(playlist))
        assertNull(result.quickPicks)
        assertEquals(listOf(playlist), result.cards)
    }

    /** YouTube Music's own Speed dial carousel, marked personal, isn't shown as a card. */
    @Test fun theAccountShelfIsLeftOut() {
        val dial = HomeShelf("Speed dial", songs("Dial").items, personal = true)
        val picks = songs("Quick picks")
        val result = arrangeHomeShelves(listOf(dial, picks))
        assertEquals(listOf(picks), listOfNotNull(result.quickPicks) + result.cards)
    }
}
