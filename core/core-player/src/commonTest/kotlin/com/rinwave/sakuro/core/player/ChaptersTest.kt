package com.rinwave.sakuro.core.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChaptersTest {

    @Test
    fun `normalize sorts, dedupes, trims and drops chapters past the end`() {
        val chapters = Chapters.normalize(
            listOf(
                Chapter(90_000, " Part A "),
                Chapter(0, "Opening"),
                Chapter(90_000, "Duplicate"),
                Chapter(1_500_000, "Beyond"),
            ),
            durationMs = 1_440_000,
        )
        assertEquals(listOf(Chapter(0, "Opening"), Chapter(90_000, "Part A")), chapters)
    }

    @Test
    fun `a lone chapter is no chapter list`() {
        assertTrue(Chapters.normalize(listOf(Chapter(0, "Whole file"))).isEmpty())
    }

    @Test
    fun `index and end follow chapter boundaries`() {
        val chapters = listOf(Chapter(5_000), Chapter(90_000), Chapter(1_300_000))
        assertEquals(-1, Chapters.indexAt(chapters, 1_000))
        assertEquals(0, Chapters.indexAt(chapters, 5_000))
        assertEquals(1, Chapters.indexAt(chapters, 89_999 + 1))
        assertEquals(2, Chapters.indexAt(chapters, 2_000_000))
        assertEquals(90_000, Chapters.endOf(chapters, 0, 1_440_000))
        assertEquals(1_440_000, Chapters.endOf(chapters, 2, 1_440_000))
    }
}
