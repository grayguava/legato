package com.grayguava.legato.base

import android.util.Log
import com.grayguava.legato.base.BuildConfig

/**
 * `android.util.Log`, minus the release build. Every line also goes to
 * [AppLog], debug or not, so "Copy log" has it.
 *
 * For call sites outside the playback/resolve path that [TrackLog] covers —
 * feeds, artwork, library scans, scrobbling — where there's no Copy Log
 * reader depending on the output, so there's nothing to preserve in prod.
 * Import as `import com.grayguava.legato.base.DebugLog as Log` to drop in
 * without touching call sites.
 */
public object DebugLog {
    public fun d(tag: String, message: String) {
        AppLog.add('D', tag, message)
        if (BuildConfig.DEBUG) Log.d(tag, message)
    }

    public fun i(tag: String, message: String) {
        AppLog.add('I', tag, message)
        if (BuildConfig.DEBUG) Log.i(tag, message)
    }

    public fun w(tag: String, message: String) {
        AppLog.add('W', tag, message)
        if (BuildConfig.DEBUG) Log.w(tag, message)
    }

    public fun w(tag: String, message: String, error: Throwable) {
        AppLog.add('W', tag, message, error)
        if (BuildConfig.DEBUG) Log.w(tag, message, error)
    }

    public fun e(tag: String, message: String) {
        AppLog.add('E', tag, message)
        if (BuildConfig.DEBUG) Log.e(tag, message)
    }

    public fun e(tag: String, message: String, error: Throwable) {
        AppLog.add('E', tag, message, error)
        if (BuildConfig.DEBUG) Log.e(tag, message, error)
    }
}
