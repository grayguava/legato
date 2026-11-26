package com.grayguava.legato.source.songinfo

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeSongInfoTest {

    private fun page(data: String = "{}", id: String = "abcdefghijk", views: String = "12345") = """
        <script>var ytInitialPlayerResponse = {"videoDetails":{"videoId":"$id","viewCount":"$views","lengthSeconds":"253","title":"A } title"},
        "microformat":{"playerMicroformatRenderer":{"uploadDate":"2024-02-03"}}}; window.after = true;</script>
        <script>var ytInitialData = $data;</script>
    """.trimIndent()

    @Test fun extractsCountsWithoutTreatingUploadDateAsReleaseYear() {
        val watch = YouTubeSongInfo.parse(page(), "abcdefghijk")
        assertEquals("12345", watch.plays)
        assertNull(watch.releaseDate)
        assertEquals(253_000, watch.lengthMs)
    }

    @Test fun readsTheReleaseDateTheDescriptionStates() {
        val html = """<script>var ytInitialPlayerResponse = {"videoDetails":{"videoId":"abcdefghijk",
            "shortDescription":"Provided to YouTube by X\n\nReleased on: 2025-01-04\n"}};</script>"""
        assertEquals("2025-01-04", YouTubeSongInfo.parse(html, "abcdefghijk").releaseDate)
    }

    @Test(expected = IllegalStateException::class)
    fun rejectsAnotherTracksMetadata() {
        YouTubeSongInfo.parse(page(), "zzzzzzzzzzz")
    }

    @Test fun parsesCreditsFromTheCreditsPage() {
        val root = Json.parseToJsonElement(
            """
            {"contents":[{"dismissableDialogContentSectionRenderer":{
              "title":{"runs":[{"text":"Performed by"}]},
              "subtitle":{"runs":[{"text":"Eminem"}]}}},
             {"dismissableDialogContentSectionRenderer":{
              "title":{"runs":[{"text":"Written by"}]},
              "subtitle":{"runs":[{"text":"Jeff Bass, Luis Resto"}]}}}]}
            """.trimIndent(),
        )
        val credits = YouTubeSongInfo.parseCredits(root)
        assertEquals(listOf("Performed by", "Written by"), credits.map { it.role })
        assertEquals(listOf("Eminem"), credits[0].names)
        assertEquals(listOf("Jeff Bass", "Luis Resto"), credits[1].names)
    }

    @Test fun findsTheCreditsEndpointInANextResponse() {
        val root = Json.parseToJsonElement(
            """
            {"playlistPanelVideoRenderer":{"videoId":"abcdefghijk","menu":{"menuRenderer":{"items":[
              {"menuNavigationItemRenderer":{"text":{"runs":[{"text":"View song credits"}]},
               "navigationEndpoint":{"browseEndpoint":{"browseId":"MPTCabcdefghijk"}}}}]}}}}
            """.trimIndent(),
        )
        assertEquals("MPTCabcdefghijk", YouTubeSongInfo.findCreditsEndpoint(root, "abcdefghijk")?.get("browseId")?.toString()?.trim('"'))
    }

    @Test fun compactsCountsTheWayYouTubeDoes() {
        assertEquals("950", compactCount("950"))
        assertEquals("1.2K", compactCount("1234"))
        assertEquals("100K", compactCount("100000"))
        assertEquals("2.3M", compactCount("2345678"))
    }
}
