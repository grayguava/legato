package com.grayguava.legato.source.covers.sources

import com.grayguava.legato.base.Http
import com.grayguava.legato.base.model.TrackNameCleaner
import com.grayguava.legato.source.covers.CoverAnswer
import com.grayguava.legato.source.covers.CoverRequest
import com.grayguava.legato.source.covers.CoverResolver
import com.grayguava.legato.source.covers.normalizeForMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

/**
 * Deezer's cover for the same recording: title, credited artist and length
 * within 3 s, and the same album when the song names one. Its covers go to
 * 1200px, and its catalogue carries extended mixes and compilations that
 * Apple's doesn't, which is why the length check is the whole point.
 */
internal object DeezerCover : CoverResolver {
    override val name: String = "Deezer"

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun find(request: CoverRequest): CoverAnswer {
        // Matched on the length, so it is only asked once that is known.
        if (request.durationMs <= 0) return CoverAnswer.None
        return withContext(Dispatchers.IO) {
            val url = "https://api.deezer.com/search".toHttpUrl().newBuilder()
                // Plain words: the field syntax (artist:"…" track:"…") answered nothing when tested on 2026-10-24.
                .addQueryParameter("q", "${request.title} ${request.artist}")
                .addQueryParameter("limit", "15")
                .build()
            val body = Http.client.newCall(Request.Builder().url(url).build()).execute()
                .use { r -> if (r.isSuccessful) r.body?.string() else throw java.io.IOException("Deezer answered ${r.code}") }
                ?: return@withContext CoverAnswer.None
            val cover = cover(json.parseToJsonElement(body).jsonObject, request.title, request.artist, request.album, request.durationMs)
            if (cover != null) CoverAnswer.Found(cover) else CoverAnswer.None
        }
    }

    /** The cover among Deezer's search [response] rows, or null when none is this recording. */
    internal fun cover(response: JsonObject, title: String, artist: String, album: String?, durationMs: Long): String? {
        // Over quota Deezer answers 200 with an error object: no answer, not a "no".
        if (response["error"] != null) throw java.io.IOException("Deezer: ${response["error"]}")
        val wanted = normalizeForMatch(artist)
        val matches = response["data"].array().map { it.obj() }.filter { row ->
            val name = row["title_short"].string() ?: row["title"].string() ?: return@filter false
            val credited = row["artist"].obj()["name"].string()?.let(::normalizeForMatch) ?: return@filter false
            val seconds = (row["duration"] as? JsonPrimitive)?.intOrNull ?: return@filter false
            normalizeForMatch(TrackNameCleaner.clean(name, artist).title) == normalizeForMatch(title) &&
                (credited == wanted || credited.contains(wanted) || wanted.contains(credited)) &&
                kotlin.math.abs(seconds * 1000L - durationMs) <= 3_000
        }
        val sameAlbum = album?.takeIf { it.isNotBlank() }?.let { a -> matches.filter { normalizeForMatch(it["album"].obj()["title"].string().orEmpty()) == normalizeForMatch(a) } }
        // With an album named, only that album's sleeve; without one, the top match's.
        val pick = (if (album.isNullOrBlank()) matches else sameAlbum.orEmpty()).firstOrNull() ?: return null
        return pick["album"].obj()["cover_xl"].string()?.takeIf { it.startsWith("https://") }
    }

    private fun JsonElement?.obj(): JsonObject = this as? JsonObject ?: JsonObject(emptyMap())
    private fun JsonElement?.array(): List<JsonElement> = (this as? JsonArray).orEmpty()
    private fun JsonElement?.string(): String? = (this as? JsonPrimitive)?.contentOrNull
}
