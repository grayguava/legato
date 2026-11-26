package com.grayguava.legato.source.innertube

import com.grayguava.legato.base.LoudnessStore
import com.grayguava.legato.settings.AppSettings
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Opus matches AAC at about two thirds the rate: YouTube's Opus is variable,
 * so its figure often reads under AAC 140's while sounding clearly better.
 */
internal const val OPUS_EFFICIENCY: Double = 1.5

/** One audio entry of a player response, before its URL has been unlocked. */
internal class Audio(
    val url: String?,
    val signatureCipher: String?,
    val kbps: Int,
    val mimeType: String,
) {
    /**
     * YouTube's Opus is always carried in WebM and its AAC always in MP4 —
     * there is no Opus-in-MP4 on this endpoint — so the container the mime
     * type names is enough to tell the two ladders apart, and the container
     * is the thing a download actually cares about.
     */
    val isAac: Boolean get() = "mp4" in mimeType.lowercase(Locale.ROOT)
    val isOpus: Boolean get() = "opus" in mimeType.lowercase(Locale.ROOT)

    /** Bitrate in AAC-equivalent terms: Opus matches AAC at about two thirds the rate. */
    val qualityScore: Double get() = if (isOpus) kbps * OPUS_EFFICIENCY else kbps.toDouble()
}

/**
 * The formats a player response offers, and the order they are worth
 * trying in.
 *
 * The formats arrive as one list of equally good entries and nothing says
 * which is best, so this is where that becomes an order: the codec that
 * sounds better first, the bitrate the connection can afford first, and
 * anything over the ceiling last but still ahead of nothing.
 */
internal object FormatRanking {


    private fun audioFormats(response: JsonObject): List<Audio> {
        rememberLoudness(response)
        return audioFormatList(response)
    }

    /**
     * YouTube's loudness figure for each track it answered, in dB relative to
     * its playback reference (positive is louder). Kept for the process's
     * lifetime; [com.grayguava.legato.audio.playback.PlaybackService] turns it into one fixed
     * gain per track, the way YouTube itself normalizes.
     */
    private val loudness = ConcurrentHashMap<String, Double>()

    internal fun loudnessDbFor(videoId: String): Double? = loudness[videoId] ?: LoudnessStore.get(videoId)

    internal fun keepLoudness(videoId: String, db: Double) {
        loudness[videoId] = db
        LoudnessStore.put(videoId, db)
    }

    private fun rememberLoudness(response: JsonObject) {
        val id = response["videoDetails"]?.jsonObject?.str("videoId") ?: return
        // Some clients leave playerConfig out; the audio formats carry the
        // same figure, which is where InnerTubeX looks next too.
        val db = response["playerConfig"]?.jsonObject?.get("audioConfig")?.jsonObject
            ?.get("loudnessDb")?.jsonPrimitive?.doubleOrNull
            ?: response["streamingData"]?.jsonObject?.get("adaptiveFormats")?.jsonArray
                ?.map { it.jsonObject }
                ?.firstOrNull { it.str("mimeType")?.startsWith("audio/") == true && it["loudnessDb"] != null }
                ?.get("loudnessDb")?.jsonPrimitive?.doubleOrNull
            ?: return
        keepLoudness(id, db)
    }

    private fun audioFormatList(response: JsonObject): List<Audio> =
        response["streamingData"]?.jsonObject
            ?.get("adaptiveFormats")?.jsonArray
            ?.map { it.jsonObject }
            ?.filter { it.str("mimeType")?.startsWith("audio/") == true }
            ?.map {
                Audio(
                    url = it.str("url"),
                    signatureCipher = it.str("signatureCipher") ?: it.str("cipher"),
                    kbps = ((it.str("bitrate")?.toLongOrNull() ?: 0L) / 1000).toInt(),
                    mimeType = it.str("mimeType").orEmpty(),
                )
            }
            ?.filter { it.url != null || it.signatureCipher != null }
            .orEmpty()

    /**
     * What playback wants, best first: the formats the connection's ceiling
     * allows, in the order they are worth trying.
     *
     * A list rather than a single pick because unlocking can fail per format —
     * see [playerStream] and [streamUrl].
     */
    internal fun rankForPlayback(response: JsonObject): List<Audio> =
        rankByQuality(audioFormats(response), AppSettings.effectiveAudioQuality.maxKbps)

    /**
     * [candidates] in the order they are worth attempting: the highest at or
     * under [maxKbps] first and the rest of the ladder descending from it, then
     * anything above the ceiling ascending — because a rung over budget still
     * beats no audio at all, and the cheapest such rung is the least wrong.
     *
     * Unciphered formats are preferred within a bitrate tie, and moved ahead of
     * ciphered ones outright once the signature solver has been found broken:
     * a ciphered format is then not merely more expensive, it is unplayable, and
     * ordering it first would spend the client's turn on a certainty. See
     * [onSignatureSolverBroken].
     */
    private fun rankByQuality(candidates: List<Audio>, maxKbps: Int): List<Audio> {
        val order = compareByDescending<Audio> { it.url != null }
        val (withinBudget, overBudget) = candidates.partition { it.kbps <= maxKbps }
        // Ranked by how good it sounds, not by the raw bitrate: Opus is the
        // better codec, and YouTube's Opus is variable-rate, so its figure
        // often reads a little under AAC 140's while sounding clearly better.
        // AAC 256 (Premium's 141) still outranks Opus 160.
        val ranked = withinBudget.sortedWith(compareByDescending<Audio> { it.qualityScore }.then(order)) +
            overBudget.sortedWith(compareBy<Audio> { it.kbps }.then(order))
        return if (SignatureSolver.broken) ranked.sortedWith(order) else ranked
    }

    /**
     * What an app-private download wants: Opus at the download setting's own
     * ceiling. Exported downloads use [pickAac] instead.
     *
     * The ceiling comes in as an argument rather than being read here, and it is
     * a different setting from the one [rankForPlayback] reads. The quality
     * ceilings budget a *stream* — bytes spent again on every replay of a track
     * being listened to — and a file saved to the device is the opposite case:
     * paid for once, kept, played from disk forever after. Capping a permanent
     * artefact at whichever network happened to be in hand would bake a
     * temporary decision into it, so a download is capped by a decision made
     * about downloads, or not at all.
     */
    internal fun pickOpus(response: JsonObject, maxKbps: Int): List<Audio> =
        rankByQuality(audioFormats(response).filter { it.isOpus }, maxKbps)

    /** AAC-in-MP4 is required when a download is exported through MediaStore.Audio. */
    internal fun pickAac(response: JsonObject, maxKbps: Int): List<Audio> =
        rankByQuality(audioFormats(response).filter { it.isAac }, maxKbps)

    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.content
}
