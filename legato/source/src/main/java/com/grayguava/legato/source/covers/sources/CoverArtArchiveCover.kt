package com.grayguava.legato.source.covers.sources

import com.grayguava.legato.base.Http
import com.grayguava.legato.base.model.TrackNameCleaner
import com.grayguava.legato.source.BuildConfig
import com.grayguava.legato.source.covers.CoverAnswer
import com.grayguava.legato.source.covers.CoverRequest
import com.grayguava.legato.source.covers.CoverResolver
import com.grayguava.legato.source.covers.normalizeForMatch
import com.grayguava.legato.source.songinfo.MusicBrainzRequests
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
 * The Cover Art Archive's front cover, reached through MusicBrainz.
 *
 * MusicBrainz needs a recording scoring [MIN_SCORE] or more whose credited
 * artist matches and, when the length is known, is within 3 s of it. Release
 * kinds whose cover isn't the song's own album's are skipped, official
 * albums, singles and EPs lead, and the archive is then asked whether it
 * actually holds an image before its URL is taken.
 *
 * It asks for at most one request a second and a User-Agent naming the app;
 * both are MusicBrainz's rules, not preferences.
 */
internal object CoverArtArchiveCover : CoverResolver {
    override val name: String = "MusicBrainz"

    private const val MIN_SCORE = 90
    /** Release kinds whose cover isn't the song's own album's. */
    private val SKIPPED_TYPES = setOf("Compilation", "DJ-mix", "Live", "Remix", "Mixtape/Street", "Demo")
    private val USER_AGENT = "Legato/${BuildConfig.VERSION_NAME} ( https://github.com/grayguava/legato )"

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun find(request: CoverRequest): CoverAnswer = withContext(Dispatchers.IO) {
        val query = "recording:\"${escape(request.title)}\" AND artist:\"${escape(request.artist)}\""
        val url = "https://musicbrainz.org/ws/2/recording".toHttpUrl().newBuilder()
            .addQueryParameter("query", query)
            .addQueryParameter("fmt", "json")
            .addQueryParameter("limit", "5")
            .build()
        val body = politely { Http.client.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).build()).execute() }
            // "Busy" (503) and the like are no answer at all, so they throw and aren't cached.
            .use { r -> if (r.isSuccessful) r.body?.string() else throw java.io.IOException("MusicBrainz answered ${r.code}") }
            ?: return@withContext CoverAnswer.None
        val groups = releaseGroups(json.parseToJsonElement(body).jsonObject, request.artist, request.album, request.durationMs)
        for (group in groups.take(3)) {
            // The archive answers 404 for an album it has no front cover for; older covers may lack the 1200px copy.
            for (size in listOf(1200, 500)) {
                val cover = "https://coverartarchive.org/release-group/$group/front-$size"
                val exists = Http.client.newCall(Request.Builder().url(cover).head().header("User-Agent", USER_AGENT).build()).execute()
                    .use { it.isSuccessful }
                if (exists) return@withContext CoverAnswer.Found(cover)
            }
        }
        CoverAnswer.None
    }

    /**
     * Release groups of confident matches, best first: recordings scoring
     * [MIN_SCORE]+ whose credited artist matches and, when the length is
     * known, within 3 s of it. Compilations, DJ mixes, live and remix
     * releases are left out: a song's copy on "World Music Day" carries that
     * compilation's cover, not its own. With an [album] named, only release
     * groups of that name count. Official albums, singles and EPs lead.
     */
    internal fun releaseGroups(response: JsonObject, artist: String, album: String? = null, durationMs: Long = 0): List<String> {
        val wanted = normalizeForMatch(artist)
        val wantAlbum = album?.let { normalizeForMatch(TrackNameCleaner.clean(it, artist).title) }?.takeIf { it.isNotEmpty() }
        val ranked = mutableListOf<Pair<Int, String>>()
        response["recordings"].array().map { it.obj() }
            .filter { ((it["score"] as? JsonPrimitive)?.intOrNull ?: 0) >= MIN_SCORE }
            .filter { rec ->
                val credited = rec["artist-credit"].array().mapNotNull { it.obj()["name"].string() ?: it.obj()["artist"].obj()["name"].string() }
                credited.any { normalizeForMatch(it) == wanted || normalizeForMatch(it).contains(wanted) || wanted.contains(normalizeForMatch(it)) }
            }
            .filter { rec ->
                val length = (rec["length"] as? JsonPrimitive)?.intOrNull ?: return@filter true
                durationMs <= 0 || kotlin.math.abs(length - durationMs) <= 3_000
            }
            .forEach { rec ->
                rec["releases"].array().map { it.obj() }.forEach { rel ->
                    val group = rel["release-group"].obj()
                    val id = group["id"].string() ?: return@forEach
                    val secondary = group["secondary-types"].array().mapNotNull { it.string() }
                    if (secondary.any { it in SKIPPED_TYPES }) return@forEach
                    val title = normalizeForMatch(TrackNameCleaner.clean(group["title"].string() ?: rel["title"].string().orEmpty(), artist).title)
                    if (wantAlbum != null && title != wantAlbum && !title.contains(wantAlbum) && !wantAlbum.contains(title)) return@forEach
                    val primary = group["primary-type"].string()
                    val official = rel["status"].string() == "Official"
                    val rank = when {
                        official && secondary.isEmpty() && (primary == "Album" || primary == "Single" || primary == "EP") -> 0
                        official && secondary.isEmpty() -> 1
                        official -> 2
                        else -> 3
                    }
                    ranked += rank to id
                }
            }
        return ranked.sortedBy { it.first }.map { it.second }.distinct()
    }

    /** MusicBrainz's one-request-a-second rule, across the app. */
    private suspend fun <T> politely(block: () -> T): T = MusicBrainzRequests.politely { block() }

    /** Lucene's special characters, escaped for a quoted phrase. */
    private fun escape(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun JsonElement?.obj(): JsonObject = this as? JsonObject ?: JsonObject(emptyMap())
    private fun JsonElement?.array(): List<JsonElement> = (this as? JsonArray).orEmpty()
    private fun JsonElement?.string(): String? = (this as? JsonPrimitive)?.contentOrNull
}
