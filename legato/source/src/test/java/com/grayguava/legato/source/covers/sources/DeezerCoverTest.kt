package com.grayguava.legato.source.covers.sources

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class DeezerCoverTest {
    private fun deezer(rows: String) = Json.parseToJsonElement("""{"data":[$rows]}""").jsonObject
    private fun row(title: String, artist: String, seconds: Int, album: String, cover: String) =
        """{"title":"$title","title_short":"$title","duration":$seconds,"artist":{"name":"$artist"},"album":{"title":"$album","cover_xl":"$cover"}}"""

    @Test
    fun takesOnlyTheSameRecording() {
        val response = deezer(listOf(
            row("Blinding Lights", "The Weeknd", 200, "After Hours", "https://cdn-images.dzcdn.net/images/cover/a/1000x1000-000000-80-0-0.jpg"),
            row("Blinding Lights", "The Weeknd", 262, "The Highlights", "https://cdn-images.dzcdn.net/images/cover/b/1000x1000-000000-80-0-0.jpg"),
        ).joinToString(","))
        assertEquals("https://cdn-images.dzcdn.net/images/cover/a/1000x1000-000000-80-0-0.jpg",
            DeezerCover.cover(response, "Blinding Lights", "The Weeknd", null, 200_400))
        // The extended mix's length matches only the compilation.
        assertEquals("https://cdn-images.dzcdn.net/images/cover/b/1000x1000-000000-80-0-0.jpg",
            DeezerCover.cover(response, "Blinding Lights", "The Weeknd", null, 262_000))
        // Another artist's song of the same name, or a named album Deezer hasn't got, is no match.
        assertEquals(null, DeezerCover.cover(response, "Blinding Lights", "Someone Else", null, 200_000))
        assertEquals(null, DeezerCover.cover(response, "Blinding Lights", "The Weeknd", "Live at SoFi Stadium", 200_000))
    }

    @Test(expected = java.io.IOException::class)
    fun aQuotaErrorIsNoAnswerRatherThanNoCover() {
        DeezerCover.cover(Json.parseToJsonElement("""{"error":{"code":4,"message":"Quota limit exceeded"}}""").jsonObject, "t", "a", null, 1_000)
    }
}
