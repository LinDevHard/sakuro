package com.rinwave.sakuro.feature.player

/**
 * "Frieren 05 00-12-34.567": the video title (without its file extension and characters that
 * file systems reject) and the frame's position, so several shots of one scene sort in order.
 */
internal fun screenshotBaseName(title: String, positionMs: Long): String {
    val withoutExtension = title.replace(VIDEO_EXTENSION, "")
    val safe = withoutExtension.replace(FORBIDDEN, "_").replace(WHITESPACE, " ").trim().take(MAX_TITLE_LENGTH).trim()
    val ms = positionMs.coerceAtLeast(0)
    val hours = ms / MS_PER_HOUR
    val minutes = ms / MS_PER_MINUTE % MINUTES_PER_HOUR
    val seconds = ms / MS_PER_SECOND % SECONDS_PER_MINUTE
    val millis = ms % MS_PER_SECOND
    val time = "${hours.pad(2)}-${minutes.pad(2)}-${seconds.pad(2)}.${millis.pad(3)}"
    return "${safe.ifEmpty { "Sakuro" }} $time"
}

private fun Long.pad(width: Int) = toString().padStart(width, '0')

private val VIDEO_EXTENSION = Regex("""\.(mkv|mp4|m4v|webm|mov|avi|ts|m2ts|wmv|flv|3gp)$""", RegexOption.IGNORE_CASE)
private val FORBIDDEN = Regex("""[\\/:*?"<>|\x00-\x1F]""")
private val WHITESPACE = Regex("""\s+""")
private const val MAX_TITLE_LENGTH = 80
private const val MS_PER_SECOND = 1000L
private const val MS_PER_MINUTE = 60_000L
private const val MS_PER_HOUR = 3_600_000L
private const val MINUTES_PER_HOUR = 60
private const val SECONDS_PER_MINUTE = 60
