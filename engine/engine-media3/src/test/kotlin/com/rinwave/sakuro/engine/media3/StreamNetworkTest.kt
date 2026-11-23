package com.rinwave.sakuro.engine.media3

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class StreamNetworkTest {
    @Test
    fun `accept language prefers the device locale and falls back to english`() {
        assertEquals("ru-RU,ru;q=0.9,en;q=0.8", StreamNetwork.acceptLanguage(Locale.forLanguageTag("ru-RU")))
        assertEquals("en-US,en;q=0.9", StreamNetwork.acceptLanguage(Locale.forLanguageTag("en-US")))
        assertEquals("ja,en;q=0.8", StreamNetwork.acceptLanguage(Locale.forLanguageTag("ja")))
    }
}
