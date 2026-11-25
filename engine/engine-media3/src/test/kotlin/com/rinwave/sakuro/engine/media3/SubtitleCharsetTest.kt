package com.rinwave.sakuro.engine.media3

import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubtitleCharsetTest {

    private val russian = "1\n00:00:01,000 --> 00:00:02,000\nПривет, мир!\n"

    @Test
    fun `utf-8 text and bom-marked files are left alone`() {
        assertTrue(SubtitleCharset.isValidUtf8(russian.toByteArray(Charsets.UTF_8)))
        assertTrue(SubtitleCharset.hasUnicodeBom(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte(), 0x31)))
        assertTrue(SubtitleCharset.hasUnicodeBom(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x31, 0)))
    }

    @Test
    fun `windows-1251 text is not valid utf-8`() {
        assertFalse(SubtitleCharset.isValidUtf8(russian.toByteArray(Charset.forName("windows-1251"))))
    }

    @Test
    fun `a confident detector guess wins`() {
        assertEquals("Shift_JIS", SubtitleCharset.chooseCharset(listOf("Shift_JIS" to 90, "windows-1251" to 20), "ru"))
    }

    @Test
    fun `a weak latin guess yields to the device language's code page`() {
        val candidates = listOf("ISO-8859-1" to 35, "windows-1251" to 25, "KOI8-R" to 12)
        assertEquals("windows-1251", SubtitleCharset.chooseCharset(candidates, "ru"))
        assertEquals("ISO-8859-1", SubtitleCharset.chooseCharset(candidates, "en"))
    }

    @Test
    fun `a lone weak latin pick on a russian device becomes windows-1251`() {
        val pick = "WINDOWS-1252"
        assertEquals(
            "windows-1251",
            SubtitleCharset.chooseCharset(listOf(pick to SubtitleCharset.confidenceOf(pick)), "ru"),
        )
        assertEquals(pick, SubtitleCharset.chooseCharset(listOf(pick to SubtitleCharset.confidenceOf(pick)), "en"))
    }

    @Test
    fun `no candidates fall back to the language default or nothing`() {
        assertEquals("windows-1251", SubtitleCharset.chooseCharset(emptyList(), "ru"))
        assertNull(SubtitleCharset.chooseCharset(emptyList(), "en"))
        assertNull(SubtitleCharset.chooseCharset(listOf("UTF-8" to 80), "en"))
    }
}
