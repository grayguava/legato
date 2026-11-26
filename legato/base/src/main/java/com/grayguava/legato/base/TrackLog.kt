package com.grayguava.legato.base

import android.util.Log
import kotlinx.coroutines.CoroutineName
import kotlin.coroutines.CoroutineContext

/**
 * Logging for the stream-resolution path, tagged by the track it is about.
 *
 * [StreamResolver] resolves several tracks concurrently (the one playing, the
 * one queued next), so a plain `Log.d` interleaves their lines. [about] gives
 * each walk a [CoroutineName] carrying the video id, and every call here
 * reads it back off the current coroutine so a line always says which track
 * it belongs to, without every call site having to pass one explicitly.
 */
public object TrackLog {
    public fun about(videoId: String): CoroutineContext = CoroutineName(videoId)

    public fun d(tag: String, message: String) {
        AppLog.add('D', tag, message)
        Log.d(tag, message)
    }

    public fun w(tag: String, message: String) {
        AppLog.add('W', tag, message)
        Log.w(tag, message)
    }

    public fun w(tag: String, message: String, error: Throwable) {
        AppLog.add('W', tag, message, error)
        Log.w(tag, message, error)
    }

    public fun e(tag: String, message: String) {
        AppLog.add('E', tag, message)
        Log.e(tag, message)
    }
}
