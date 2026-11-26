package com.grayguava.legato.ui.home

import com.grayguava.legato.library.ExploreFeed
import com.grayguava.legato.source.innertube.InnertubeParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartsFeedTest {
    /** The welcome's starting grid: the charts' top artists, from a real answer for India. */
    @Test fun chartsGiveTheirTopArtists() {
        val response = Json.parseToJsonElement(javaClass.classLoader!!.getResource("ytm-charts-in.json")!!.readText()).jsonObject
        val artists = ExploreFeed.artistsOf(InnertubeParser.parseHome(response))
        assertEquals(listOf("Alka Yagnik", "Udit Narayan", "Arijit Singh", "Shreya Ghoshal", "Kumar Sanu", "Shilpi Raj"), artists.map { it.name })
        assertTrue(artists.all { it.browseId.startsWith("UC") && it.thumbnailUrl != null })
    }
}
