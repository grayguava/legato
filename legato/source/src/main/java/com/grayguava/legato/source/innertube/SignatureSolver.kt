package com.grayguava.legato.source.innertube

import com.grayguava.legato.base.TrackLog
import java.net.URLDecoder
import java.util.Locale
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager

/**
 * Unlocking a format: the signature cipher YouTube hands back, and the
 * `n` parameter that throttles a stream.
 *
 * Both need YouTube's player JavaScript, which only NewPipe can run, and
 * that library keeps its parsed script in static fields with no lock of
 * its own — so every call into it goes through one mutex here.
 */
internal object SignatureSolver {
    private const val TAG = "Legato"

    // ---- Unlocking ----------------------------------------------------------

    /** The playable URL behind a format, or null if it can't be unlocked. */
    internal suspend fun streamUrl(videoId: String, format: Audio): String? {
        val direct = format.url
        if (direct != null) return deobfuscate(videoId, direct)

        val cipher = format.signatureCipher ?: return null
        // Asked before the library is, because the library's answer is a cached
        // exception and its cost is a log line per format per walk rather than
        // any real work. See [signatureSolverBroken].
        if (broken) return null
        val params = cipher.split("&")
            .mapNotNull { part ->
                val i = part.indexOf('=').takeIf { it > 0 } ?: return@mapNotNull null
                URLDecoder.decode(part.substring(0, i), "UTF-8") to
                    URLDecoder.decode(part.substring(i + 1), "UTF-8")
            }
            .toMap()

        val base = params["url"] ?: return null
        val signature = params["s"] ?: return null
        // Which query parameter the solved signature belongs in; YouTube has
        // changed the name before, so it travels alongside rather than assumed.
        val into = params["sp"] ?: "signature"
        val solved = runCatching {
            jsPlayerManager { YoutubeJavaScriptPlayerManager.deobfuscateSignature(videoId, signature) }
        }.getOrElse {
            TrackLog.w(TAG, "signature cipher failed: ${it.message}")
            if (it.isUnparseablePlayer()) onSignatureSolverBroken(it)
            return null
        }
        val separator = if ("?" in base) "&" else "?"
        return deobfuscate(videoId, "$base$separator$into=$solved")
    }

    /**
     * Whether NewPipe cannot read the current `base.js` at all, as opposed to
     * having failed to solve one particular signature.
     *
     * The wording is the only thing that distinguishes them, which is
     * unsatisfying and still worth acting on: "Could not parse deobfuscation
     * function" is thrown before this app's input is looked at, so it says
     * nothing about the track and everything about the pair of (this extractor
     * release, whatever player YouTube is currently serving).
     *
     * Matched narrowly on purpose. A false positive here costs every ciphered
     * format for the rest of the process — the ANDROID client and WEB_REMIX
     * both — so a broad "could not parse" would trade one track's failure for a
     * session's, which is the wrong way round. Every player-JS parse failure
     * NewPipe raises names the function or the script it could not read.
     */
    private fun Throwable.isUnparseablePlayer(): Boolean {
        val text = generateSequence(this) { it.cause?.takeIf { c -> c !== it } }
            .mapNotNull { it.message }
            .joinToString(" ")
            .lowercase(Locale.ROOT)
        return "deobfuscation function" in text ||
            "player js" in text ||
            "player javascript" in text ||
            "javascript base url" in text
    }

    /**
     * NewPipe cannot solve signatures against the player YouTube is currently
     * serving, so nothing that depends on solving one is worth attempting again
     * this process.
     *
     * This is the actual root cause of the report, and the reason it looks like
     * an age-restriction bug when it isn't. Ordinary tracks never reach a
     * signature at all: the device clients at the top of [CLIENTS] hand back
     * plain `url` fields, so a broken solver is invisible on everything that
     * plays. An age-restricted track is the one case where every unciphered
     * client is refused and the *only* remaining route —
     * [authenticatedWebRemixStream] — is ciphered without exception, so the
     * broken solver is fatal exactly there and nowhere else. Hence "other songs
     * played fine", and hence clearing caches and restarting not helping: the
     * parse failure is deterministic, and NewPipe caches even the failed parse's
     * exception (see [jsPlayerMutex]).
     *
     * Recorded as a flag rather than acted on, because there is nothing to do
     * about it in this file beyond not paying for it repeatedly. What makes the
     * track play regardless is [playerStream]'s authenticated retry, which gets
     * an *unciphered* URL out of a device client and never touches this path.
     * Bumping NewPipeExtractor is not the fix: no release through v0.26.5
     * addresses this parse failure, and the last upstream fix in this area
     * shipped in 2025.
     */
    internal fun onSignatureSolverBroken(cause: Throwable) {
        if (broken) return
        broken = true
        TrackLog.w(
            TAG,
            "this NewPipe release cannot read YouTube's current player JavaScript " +
                "(${cause.message}); ciphered formats are unavailable for the rest of this session — " +
                "signed-in device clients are the only route to an age-restricted track",
        )
    }

    @Volatile
    internal var broken = false

    /**
     * Transform the `n` parameter when present. If deobfuscation itself fails
     * we still return the original URL — a throttled stream beats no stream,
     * and [probe] gets the final say on whether it plays at all.
     */
    private suspend fun deobfuscate(videoId: String, url: String): String {
        val needsWork = url.toHttpUrlOrNull()?.queryParameter("n")?.isNotBlank() == true
        if (!needsWork) return url
        return runCatching {
            jsPlayerManager { YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(videoId, url) }
        }.getOrElse {
            TrackLog.w(TAG, "n-param deobfuscation failed: ${it.message}")
            url
        }
    }

    /**
     * Guards every call into [YoutubeJavaScriptPlayerManager].
     *
     * Its player-JS cache — the parsed code, the deobfuscation function, and
     * even a *failed* parse's exception — lives in static fields with no
     * synchronization, shared by the whole process. This app resolves more
     * than one track at once by design (a track playing while its successor
     * pre-caches — see [AudioCache][com.grayguava.legato.audio.playback.AudioCache]),
     * so two resolves can enter these calls together; the library was never
     * written for that, and serializing access is what keeps concurrent
     * resolves from corrupting that shared state.
     *
     * A failure here is passed straight out, and deliberately so. An earlier
     * version answered one by calling [clearAllCaches] and running the block
     * again, on the reasoning that the library caches a *failed* parse's
     * exception and replays it forever, so one bad parse would otherwise be
     * permanent for the process. Measured, that cure was far worse than the
     * disease, for two reasons that only show up on a device:
     *
     *  - The failure this app actually sees — "Could not parse deobfuscation
     *    function" against a `base.js` this NewPipe release doesn't understand
     *    — is deterministic. Re-fetching the script and parsing it again cannot
     *    end differently, so the retry only ever bought a second failure, at
     *    5s to 55s a time, holding this mutex while every concurrent resolve
     *    queued behind it.
     *  - [clearAllCaches] is all-or-nothing. It throws away the parsed player
     *    JS that the *working* paths depend on — the `n` parameter transform
     *    that [newPipeStream] runs for every format it extracts — along with
     *    the one broken function. So the price of the retry was charged twice:
     *    once here, and again on the fallback that was about to succeed, which
     *    went from 2.7s warm to 49.8s against the cache this had just emptied.
     *
     * Left alone, the cached exception makes the failure free — it is what
     * turns a broken signature solve into the ~0ms no it should always have
     * been, and what lets the walk move on to something that works. The cost
     * of not clearing is that a parse which failed for a reason that has since
     * passed stays failed until the process restarts; that is a real loss, and
     * a much smaller one than a fifty-second track.
     */
    private val jsPlayerMutex = Mutex()

    private suspend fun <T> jsPlayerManager(block: () -> T): T = jsPlayerMutex.withLock { block() }

    /**
     * YouTube's current player revision, as the number a client quotes to prove
     * it is running that player.
     *
     * Named and shared rather than fetched at each of the three call sites,
     * because there is now a fourth kind of caller that is nothing to do with
     * streaming: [PlaybackTracker] needs one to be issued a tracking block at
     * all, and it has no business knowing that the value comes from parsing
     * YouTube's player JavaScript.
     *
     * Memoised for the process. It is a property of YouTube's deployment, not of
     * a track — the videoId is passed only because NewPipe's API takes one, to
     * decide which player script to fetch — and it changes on the order of days,
     * against a first parse that costs seconds.
     */
    internal suspend fun signatureTimestamp(videoId: String): Int? {
        cachedSignatureTimestamp?.let { return it }
        return runCatching {
            jsPlayerManager { YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId) }
        }
            .onFailure { TrackLog.w(TAG, "no signature timestamp: ${it.message}") }
            .getOrNull()
            ?.also { cachedSignatureTimestamp = it }
    }

    @Volatile
    private var cachedSignatureTimestamp: Int? = null

    /**
     * Align the URL's `cver` with the client that actually asked.
     *
     * The player response fills it in from the request, but a signature or `n`
     * transform can be solved against player JavaScript of a different vintage,
     * and googlevideo answers a version it doesn't expect with a 403.
     */
    internal fun patchClientVersion(url: String, clientVersion: String): String =
        if ("cver=" in url) url.replace(Regex("cver=[^&]+"), "cver=$clientVersion") else url
}
