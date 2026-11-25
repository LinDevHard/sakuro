package com.rinwave.sakuro.feature.player

import kotlin.test.Test
import kotlin.test.assertEquals

class ScreenshotNamesTest {

    @Test
    fun `title without extension plus a sortable timestamp`() {
        assertEquals("[Sub] Frieren - 05 00-12-34.567", screenshotBaseName("[Sub] Frieren - 05.mkv", 754_567))
        assertEquals("Movie 01-02-03.004", screenshotBaseName("Movie", 3_723_004))
    }

    @Test
    fun `characters file systems reject become underscores`() {
        assertEquals("a_b_c__ d 00-00-00.000", screenshotBaseName("a/b:c?\n d", 0))
        assertEquals("Sakuro 00-00-01.000", screenshotBaseName("  .mp4", 1_000))
    }
}
