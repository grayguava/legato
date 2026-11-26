package com.grayguava.legato.source.songinfo

import com.grayguava.legato.base.Http
import com.grayguava.legato.base.model.Song
import com.grayguava.legato.source.innertube.Innertube
import com.grayguava.legato.source.innertube.InnertubeParser
import java.time.LocalDate
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * YouTube Music's own account of an upload: plays, the release date it states,
 * and the credits page. Counts belong to this exact video, never a search
 * match, so nothing here is inferred from another row.
 */
public object YouTubeSongInfo {

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    /** The page's inline scripts, whose contents are raw JSON. */
    private val SCRIPT = Regex("""<script[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)

    /**
     * What the song's own page says, and whether that page could be read at
     * all: the credits can arrive while the page's own fields don't, and the
     * caller has to be able to tell the two apart to offer a retry.
     */
    public data class Loaded(val info: SongInfo, val watchFailed: Boolean)

    public suspend fun load(song: Song): Loaded {
        val watch = runCatching { watch(song) }
        // The credits page is separate and often absent; a miss there is not a
        // failure of the watch page's own fields, and vice versa.
        val credits = runCatching { credits(song.videoId) }.getOrDefault(emptyList())
        // A song reached from somewhere that carried no album still has one on
        // YouTube Music, linked from the video's own page.
        val album = runCatching { album(song) }.getOrNull()
        val found = watch.getOrNull()
        if (found == null && credits.isEmpty() && album == null) {
            throw watch.exceptionOrNull() ?: IllegalStateException("YouTube Music returned nothing")
        }
        return Loaded(
            info = SongInfo(
                album = album,
                lengthMs = found?.lengthMs ?: 0,
                releaseDate = found?.releaseDate,
                plays = found?.plays,
                credits = credits,
            ),
            watchFailed = watch.isFailure,
        )
    }

    internal data class Watch(val lengthMs: Long, val releaseDate: String?, val plays: String?)

    private suspend fun watch(song: Song): Watch {
        val url = "https://www.youtube.com/watch".toHttpUrl().newBuilder()
            .addQueryParameter("v", song.videoId)
            .addQueryParameter("hl", Innertube.currentLanguage)
            .build()
        // Without a consent answer an EU request is redirected to
        // consent.youtube.com, whose page carries no player response. SOCS=CAI
        // is "reject all": it answers the question and opts into nothing.
        val html = Http.text(
            url.toString(),
            headers = mapOf("User-Agent" to USER_AGENT, "Cookie" to "SOCS=CAI"),
            // A catalogue page with a long description runs well past a couple
            // of megabytes; too tight a bound failed the whole read and lost
            // the release date and views with it.
            limit = 16_000_000,
        )
        return parse(html, song.videoId)
    }

    internal fun parse(html: String, videoId: String): Watch {
        val scripts = SCRIPT.findAll(html).map { it.groupValues[1] }.toList()
        val player = scripts.firstNotNullOfOrNull { extract(it, "ytInitialPlayerResponse") }
            ?: error("Missing player metadata")
        val details = player.obj("videoDetails")
        check(details.str("videoId") == videoId) { "Metadata belongs to another track" }
        val seconds = details.str("lengthSeconds")?.toLongOrNull()?.takeIf { it > 0 } ?: 0L
        val views = details.str("viewCount")?.takeIf { it.toLongOrNull()?.let { n -> n >= 0 } == true }
        return Watch(seconds * 1_000, releaseDate(details.str("shortDescription")), views)
    }

    private suspend fun credits(videoId: String): List<SongCredit> {
        // Direct credits browse: reliable for catalogue tracks on YouTube Music.
        val direct = runCatching { parseCredits(Innertube.browse("MPTC$videoId")) }.getOrDefault(emptyList())
        if (direct.isNotEmpty()) return direct
        val next = runCatching { Innertube.next(videoId) }.getOrNull() ?: return emptyList()
        val endpoint = findCreditsEndpoint(next, videoId) ?: return emptyList()
        val browseId = endpoint.str("browseId") ?: return emptyList()
        return runCatching { parseCredits(Innertube.browse(browseId, endpoint.str("params"))) }
            .getOrDefault(emptyList())
    }

    /**
     * The album the video belongs to, when the caller arrived without one.
     * YouTube Music's `next` response links the release page (`MPRE…`), whose
     * header carries the name. One request pair, and only when it's needed.
     */
    private suspend fun album(song: Song): String? {
        if (!song.albumName.isNullOrBlank()) return null
        val id = objectsNamed(Innertube.next(song.videoId), "browseEndpoint")
            .mapNotNull { it.str("browseId") }
            .firstOrNull { it.startsWith("MPRE") }
            ?: return null
        return InnertubeParser.parseBrowseHeader(Innertube.browse(id))?.title
    }

    /**
     * Finds the credits browse endpoint in a `next` response. Prefers an
     * endpoint whose page type marks it as track credits, falling back to any
     * menu navigation whose text suggests credits.
     */
    internal fun findCreditsEndpoint(root: JsonElement, videoId: String): JsonObject? {
        fun JsonObject.pageType(): String =
            this["browseEndpointContextSupportedConfigs"]
                ?.let { it as? JsonObject }?.get("browseEndpointContextMusicConfig")
                ?.let { it as? JsonObject }?.get("pageType")
                ?.let { it as? JsonPrimitive }?.contentOrNull.orEmpty()

        fun isCreditsBrowse(browse: JsonObject): Boolean {
            val id = browse.str("browseId").orEmpty()
            return "CREDIT" in browse.pageType() || id.startsWith("MPTC")
        }

        val rows = objectsNamed(root, "playlistPanelVideoRenderer")
            .filter { it.str("videoId") == videoId }
            .toList()
            .ifEmpty { objectsNamed(root, "playlistPanelVideoRenderer").toList() }
        val menuScopes = (rows + listOfNotNull(root as? JsonObject))
        for (scope in menuScopes) {
            val items = objectsNamed(scope, "menuNavigationItemRenderer").toList()
            val endpoint = items.firstNotNullOfOrNull { nav ->
                val browse = (nav["navigationEndpoint"] as? JsonObject)?.get("browseEndpoint") as? JsonObject
                    ?: return@firstNotNullOfOrNull null
                if (isCreditsBrowse(browse)) browse else null
            } ?: items.firstNotNullOfOrNull { nav ->
                val browse = (nav["navigationEndpoint"] as? JsonObject)?.get("browseEndpoint") as? JsonObject
                    ?: return@firstNotNullOfOrNull null
                if (nav.runsText().contains("credit", ignoreCase = true)) browse else null
            }
            if (endpoint != null) return endpoint
        }
        return objectsNamed(root, "browseEndpoint").firstOrNull(::isCreditsBrowse)
    }

    internal fun parseCredits(root: JsonElement): List<SongCredit> {
        val credits = mutableListOf<SongCredit>()
        // dismissableDialogContentSectionRenderer: the song credits browse page (MPTC*)
        for (section in objectsNamed(root, "dismissableDialogContentSectionRenderer")) {
            val role = section["title"]?.runsText()?.trim().orEmpty()
            val names = splitNames(listOf(section["subtitle"]?.runsText().orEmpty()))
            if (role.isNotBlank() && names.isNotEmpty()) credits += SongCredit(role, names)
        }
        if (credits.isNotEmpty()) return credits.distinctBy { it.role.lowercase() to it.text.lowercase() }
        // musicResponsiveListItemRenderer rows: flex column 0 = role, 1+ = names
        for (item in objectsNamed(root, "musicResponsiveListItemRenderer")) {
            val flex = ((item["flexColumns"] as? JsonArray) ?: JsonArray(emptyList())).toList()
            val role = flex.getOrNull(0)?.rendererText()?.trim().orEmpty()
            val names = if (flex.size > 1) {
                flex.drop(1).flatMap { splitNames(listOf(it.rendererText())) }
            } else {
                splitNames(listOf(item["subtitle"]?.runsText().orEmpty()))
            }
            if (role.isNotBlank() && names.isNotEmpty()) credits += SongCredit(role, names)
        }
        if (credits.isNotEmpty()) return credits.distinctBy { it.role.lowercase() to it.text.lowercase() }
        // Fallback: shelf-based layouts
        for (shelf in objectsNamed(root, "musicShelfRenderer") + objectsNamed(root, "musicCarouselShelfRenderer")) {
            val role = (shelf["header"] as? JsonObject)?.runsText()?.trim().orEmpty()
            val contents = ((shelf["contents"] as? JsonArray) ?: JsonArray(emptyList())).toList()
            val names = contents.flatMap { c ->
                splitNames(listOf((c as? JsonObject)?.runsText().orEmpty()))
            }
            if (role.isNotBlank() && names.isNotEmpty()) credits += SongCredit(role, names)
        }
        return credits.distinctBy { it.role.lowercase() to it.text.lowercase() }
    }

    private fun JsonElement.rendererText(): String =
        objectsNamed(this, "text").firstNotNullOfOrNull { it.runsText().takeIf(String::isNotBlank) }
            ?: runsText()

    private fun JsonElement.runsText(): String = when (this) {
        is JsonPrimitive -> contentOrNull.orEmpty()
        is JsonArray -> joinToString("") { it.runsText() }
        is JsonObject -> (this["simpleText"] as? JsonPrimitive)?.contentOrNull
            ?: (this["runs"] as? JsonArray)?.joinToString("") { run ->
                ((run as? JsonObject)?.get("text") as? JsonPrimitive)?.contentOrNull.orEmpty()
            }
            ?: this["text"]?.runsText()
            ?: ""
    }

    private fun splitNames(strings: List<String>): List<String> {
        val joined = strings.joinToString("")
        if (joined.isBlank()) return emptyList()
        return joined.split(Regex("\\r?\\n+|\\s*[•·]\\s*|\\s*,\\s+(?=[\\p{Lu}\"'])"))
            .map { it.trim().trimEnd(',', '•', '·') }
            .filter { it.isNotBlank() }
            .ifEmpty { listOf(joined.trim()) }
    }

    private fun objectsNamed(root: JsonElement, name: String): Sequence<JsonObject> = sequence {
        when (root) {
            is JsonObject -> for ((key, value) in root) {
                if (key == name && value is JsonObject) yield(value)
                yieldAll(objectsNamed(value, name))
            }
            is JsonArray -> for (value in root) yieldAll(objectsNamed(value, name))
            else -> Unit
        }
    }

    /**
     * Catalogue uploads explicitly state this in their description. Upload and
     * publish dates describe the video, so they are never used in its place.
     */
    private fun releaseDate(description: String?): String? {
        val date = description?.lineSequence()?.firstNotNullOfOrNull { line ->
            Regex("Released on:\\s*(\\d{4}-\\d{2}-\\d{2})", RegexOption.IGNORE_CASE)
                .matchEntire(line.trim())?.groupValues?.get(1)
        } ?: return null
        return runCatching { LocalDate.parse(date).toString() }.getOrNull()
    }

    /** Balanced JSON extraction tolerates braces inside titles and trailing script statements. */
    private fun extract(script: String, name: String): JsonObject? {
        val assignment = Regex("(?:var\\s+)?${name}\\s*=\\s*").find(script) ?: return null
        val start = assignment.range.last + 1
        if (script.getOrNull(start) != '{') return null
        var depth = 0
        var quoted = false
        var escaped = false
        for (i in start until script.length) {
            val c = script[i]
            if (quoted) {
                if (escaped) escaped = false
                else if (c == '\\') escaped = true
                else if (c == '"') quoted = false
            } else when (c) {
                '"' -> quoted = true
                '{' -> depth++
                '}' -> if (--depth == 0) return runCatching {
                    Json.parseToJsonElement(script.substring(start, i + 1)) as? JsonObject
                }.getOrNull()
            }
        }
        return null
    }

    private fun JsonObject.obj(key: String) = this[key] as? JsonObject ?: JsonObject(emptyMap())
    private fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
}
