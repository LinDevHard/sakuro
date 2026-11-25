package com.rinwave.sakuro.engine.mpv

import com.rinwave.sakuro.core.player.Chapter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MpvChapterListTest {

    @Test
    fun `parses chapter titles and start times`() {
        val json = """[{"title":"Opening","time":0.0},{"title":"Part A","time":90.5},{"time":1300}]"""
        assertEquals(
            listOf(Chapter(0, "Opening"), Chapter(90_500, "Part A"), Chapter(1_300_000, "")),
            parseMpvChapterList(json),
        )
    }

    @Test
    fun `malformed or empty lists yield no chapters`() {
        assertTrue(parseMpvChapterList("not json").isEmpty())
        assertTrue(parseMpvChapterList("[]").isEmpty())
    }
}
