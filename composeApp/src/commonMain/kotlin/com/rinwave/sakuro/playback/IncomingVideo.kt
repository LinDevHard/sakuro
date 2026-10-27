package com.rinwave.sakuro.playback

/** Restricts external playback requests to local video URIs, before touching a provider. */
internal fun incomingVideoUri(action: String?, mimeType: String?, data: String?, stream: String?): String? {
    val uri = when (action) {
        "android.intent.action.VIEW" -> data
        "android.intent.action.SEND" -> stream.takeIf { mimeType != null }
        else -> null
    } ?: return null
    if (mimeType != null && !mimeType.startsWith("video/", ignoreCase = true)) return null
    if (uri.any { it.isWhitespace() || it.isISOControl() } || malformedEscape.containsMatchIn(uri)) return null
    return uri.takeIf { contentUri.matches(it) || fileUri.matches(it) }
}

private val contentUri = Regex("content://[^/?#]+(?:/[^\\s]*)?")
private val fileUri = Regex("file:///[^\\s]+")
private val malformedEscape = Regex("%(?![0-9a-fA-F]{2})")
