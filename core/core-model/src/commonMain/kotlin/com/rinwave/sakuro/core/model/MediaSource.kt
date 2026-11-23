package com.rinwave.sakuro.core.model

/** A minimal reference to playable media; the rich library model lives in core-media. */
data class MediaSource(
    val uri: String,
    val title: String,
    /** Extra HTTP request headers for network streams (User-Agent, Referer, Cookie); ignored locally. */
    val headers: Map<String, String> = emptyMap(),
)

/** Request headers a user may set for a stream link; everything else comes from the engine's defaults. */
object StreamHeaders {
    const val USER_AGENT = "User-Agent"
    const val REFERER = "Referer"
    const val COOKIE = "Cookie"

    /**
     * Drops blank values and anything that could split a header (CR/LF): a pasted cookie
     * must not be able to smuggle extra request lines.
     */
    fun sanitize(headers: Map<String, String>): Map<String, String> = headers
        .mapValues { (_, value) -> value.trim() }
        .filter { (name, value) ->
            name.isNotBlank() && value.isNotEmpty() && (name + value).none { it == '\r' || it == '\n' || it == '\u0000' }
        }
}
