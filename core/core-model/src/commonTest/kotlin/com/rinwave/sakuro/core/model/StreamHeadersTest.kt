package com.rinwave.sakuro.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class StreamHeadersTest {
    @Test
    fun `blank values are dropped and values trimmed`() {
        assertEquals(
            mapOf(StreamHeaders.REFERER to "https://site.example/"),
            StreamHeaders.sanitize(
                mapOf(StreamHeaders.REFERER to "  https://site.example/ ", StreamHeaders.COOKIE to "  "),
            ),
        )
    }

    @Test
    fun `header injection through line breaks is rejected`() {
        assertEquals(
            emptyMap(),
            StreamHeaders.sanitize(mapOf(StreamHeaders.COOKIE to "a=b\r\nX-Evil: 1", "Bad\nName" to "x")),
        )
    }
}
