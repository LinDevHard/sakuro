package com.rinwave.sakuro.core.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SkipSegmentsTest {

    @Test
    fun `recognizes common opening and ending chapter names`() {
        listOf("Opening", "OP", "OP2", "op 1", "Intro", "オープニング", "Опенинг", "[OP] Song").forEach {
            assertEquals(SkipKind.OPENING, SkipSegments.kindOf(it), it)
        }
        listOf("Ending", "ED", "ED1", "Credits", "エンディング", "Эндинг", "Титры").forEach {
            assertEquals(SkipKind.ENDING, SkipSegments.kindOf(it), it)
        }
    }

    @Test
    fun `ordinary chapter names are not skip segments`() {
        listOf("Part A", "Prologue", "Operation", "Edge of the world", "Episode", "Chapter 1", "").forEach {
            assertNull(SkipSegments.kindOf(it), it)
        }
    }

    @Test
    fun `the active segment ends at the next chapter or the end of the media`() {
        val chapters = listOf(
            Chapter(0, "Prologue"),
            Chapter(60_000, "Opening"),
            Chapter(150_000, "Part A"),
            Chapter(1_320_000, "Ending"),
        )
        assertNull(SkipSegments.at(chapters, 30_000, 1_420_000))
        assertEquals(SkipSegment(SkipKind.OPENING, 60_000, 150_000), SkipSegments.at(chapters, 61_000, 1_420_000))
        assertNull(SkipSegments.at(chapters, 149_000, 1_420_000))
        assertEquals(SkipSegment(SkipKind.ENDING, 1_320_000, 1_420_000), SkipSegments.at(chapters, 1_330_000, 1_420_000))
    }
}
