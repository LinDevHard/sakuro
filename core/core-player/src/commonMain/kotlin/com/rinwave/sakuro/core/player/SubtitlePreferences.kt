package com.rinwave.sakuro.core.player

/** Positive delay displays subtitles later; all positions remain relative to the media clock. */
data class SubtitlePreferences(
    val fontSizeSp: Float = 20f,
    val backgroundEnabled: Boolean = true,
    val bottomPaddingFraction: Float = 0.08f,
    val delayMs: Long = 0L,
    val preferredLanguage: String = "",
) {
    fun normalized(): SubtitlePreferences = copy(
        fontSizeSp = if (fontSizeSp.isFinite()) {
            fontSizeSp.coerceIn(12f, 48f)
        } else {
            20f
        },
        bottomPaddingFraction = if (bottomPaddingFraction.isFinite()) {
            bottomPaddingFraction.coerceIn(0f, 0.4f)
        } else {
            0.08f
        },
        delayMs = delayMs.coerceIn(-30_000L, 30_000L),
        preferredLanguage = preferredLanguage.trim().replace('_', '-'),
    )

    fun subtitlePositionUs(mediaPositionUs: Long): Long = mediaPositionUs - delayMs.coerceIn(-30_000L, 30_000L) * 1000L
}
