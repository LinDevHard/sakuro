package com.rinwave.sakuro.core.model

/** Container/manifest hint for a network stream; [PROGRESSIVE] lets the engine sniff the container. */
enum class StreamFormat {
    HLS,
    DASH,
    PROGRESSIVE,
}

/** A validated direct http(s) link to a stream (HLS playlist, DASH manifest or a plain MP4/MKV/WebM file). */
data class StreamUrl(
    val value: String,
    val format: StreamFormat,
) {
    /** A readable fallback title: the last path segment without the query, or the host. */
    val title: String
        get() {
            val afterScheme = value.substringAfter("://")
            val host = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
                .substringAfterLast('@')
                .let { if (it.startsWith("[")) it.substringBefore(']') + "]" else it.substringBefore(':') }
            val path = afterScheme.substringAfter('/', "").substringBefore('?').substringBefore('#')
            val segment = path.trimEnd('/').substringAfterLast('/')
            val decoded = percentDecode(segment)
            // Playlist names like "master.m3u8"/"index.m3u8" say nothing; the host is more useful.
            return if (decoded.isBlank() || decoded.substringBeforeLast('.').lowercase() in genericNames) {
                host
            } else {
                decoded
            }
        }

    sealed interface ParseResult {
        data class Valid(val url: StreamUrl) : ParseResult
        data object Empty : ParseResult
        data object UnsupportedScheme : ParseResult
        data object Malformed : ParseResult
    }

    companion object {
        fun parse(input: String): ParseResult {
            val text = input.trim()
            if (text.isEmpty()) return ParseResult.Empty
            if (text.any { it.isWhitespace() || it.isISOControl() }) return ParseResult.Malformed
            val scheme = text.substringBefore("://", "").lowercase()
            if (scheme.isEmpty()) {
                // "example.com/video.mp4" without a scheme: most users mean https.
                return if (text.contains('.') && !text.contains(':')) parse("https://$text") else ParseResult.Malformed
            }
            if (scheme != "http" && scheme != "https") return ParseResult.UnsupportedScheme
            val authority = text.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')
            val host = authority.substringAfterLast('@').let { hostPort ->
                if (hostPort.startsWith("[")) hostPort.substringBefore(']') + "]" else hostPort.substringBefore(':')
            }
            if (host.isEmpty() || host == "[]") return ParseResult.Malformed
            val port = authority.substringAfterLast('@').substringAfter("]").substringAfter(':', "")
            if (port.isNotEmpty() && (port.toIntOrNull() == null || port.toInt() !in 1..MAX_PORT)) {
                return ParseResult.Malformed
            }
            val normalized = scheme + text.substring(scheme.length)
            return ParseResult.Valid(StreamUrl(normalized, detectFormat(normalized)))
        }

        /** True for links the network stack handles (as opposed to content:// and file:// media). */
        fun isRemote(uri: String): Boolean {
            val scheme = uri.substringBefore("://", "").lowercase()
            return scheme == "http" || scheme == "https"
        }

        /**
         * Extension first, then a hint anywhere in the URL: tokenized CDN links often hide the
         * playlist name in the query (`?type=m3u8`, `/playlist/m3u8/...`).
         */
        fun detectFormat(url: String): StreamFormat {
            val lower = url.lowercase()
            val path = lower.substringAfter("://").substringBefore('?').substringBefore('#')
            return when {
                path.endsWith(".m3u8") || path.endsWith(".m3u") -> StreamFormat.HLS
                path.endsWith(".mpd") -> StreamFormat.DASH
                progressiveExtensions.any { path.endsWith(it) } -> StreamFormat.PROGRESSIVE
                "m3u8" in lower -> StreamFormat.HLS
                ".mpd" in lower || "format=mpd" in lower -> StreamFormat.DASH
                else -> StreamFormat.PROGRESSIVE
            }
        }

        private const val MAX_PORT = 65535
        private val genericNames = setOf("master", "index", "playlist", "manifest", "stream", "chunklist", "prog_index")
        private val progressiveExtensions = listOf(".mp4", ".m4v", ".mkv", ".webm", ".mov", ".ts", ".3gp")

        private fun percentDecode(text: String): String {
            if ('%' !in text) return text
            val bytes = ArrayList<Byte>(text.length)
            var i = 0
            while (i < text.length) {
                val c = text[i]
                val hex = if (c == '%' && i + 2 <= text.lastIndex) {
                    text.substring(i + 1, i + 3).toIntOrNull(HEX_RADIX)
                } else {
                    null
                }
                if (hex != null) {
                    bytes += hex.toByte()
                    i += PERCENT_ESCAPE_LENGTH
                } else {
                    c.toString().encodeToByteArray().forEach { bytes += it }
                    i++
                }
            }
            return bytes.toByteArray().decodeToString()
        }

        private const val HEX_RADIX = 16
        private const val PERCENT_ESCAPE_LENGTH = 3
    }
}
