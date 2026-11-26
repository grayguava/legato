package com.grayguava.legato.source.innertube.potoken

public class PoTokenException(message: String) : Exception(message)

// to be thrown if the WebView provided by the system is broken
public class BadWebViewException(message: String) : Exception(message)

public fun buildExceptionForJsError(error: String): Exception {
    return if (error.contains("SyntaxError"))
        BadWebViewException(error)
    else
        PoTokenException(error)
}
