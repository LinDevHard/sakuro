package com.rinwave.sakuro.core.player

import kotlin.test.Test
import kotlin.test.assertEquals

class SubtitlePreferencesTest {
    @Test
    fun `preferences sanitize persisted values and language tags`() {
        val preferences = SubtitlePreferences(
            fontSizeSp = Float.NaN, bottomPaddingFraction = 5f,
            delayMs = Long.MAX_VALUE, preferredLanguage = "  pt_BR  ",
        ).normalized()
        assertEquals(20f, preferences.fontSizeSp)
        assertEquals(0.4f, preferences.bottomPaddingFraction)
        assertEquals(30_000L, preferences.delayMs)
        assertEquals("pt-BR", preferences.preferredLanguage)
    }

    @Test
    fun `positive delay holds subtitles back without altering video time`() {
        assertEquals(800_000L, SubtitlePreferences(delayMs = 200).subtitlePositionUs(1_000_000))
        assertEquals(1_200_000L, SubtitlePreferences(delayMs = -200).subtitlePositionUs(1_000_000))
    }
}
