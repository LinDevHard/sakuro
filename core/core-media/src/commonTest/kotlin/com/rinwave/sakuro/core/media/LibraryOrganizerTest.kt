package com.rinwave.sakuro.core.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LibraryOrganizerTest {

    private val day = 86_400L
    private val now = 1_000L * day + 100 // a little past the start of day 1000

    @Suppress("LongParameterList")
    private fun item(
        id: String,
        title: String = id,
        date: Long = now,
        size: Long = 0,
        duration: Long = 0,
        width: Int = 0,
        height: Int = 0,
        folder: String = "",
    ) = VideoItem(
        id = id,
        title = title,
        uri = "u://$id",
        durationMs = duration,
        width = width,
        height = height,
        sizeBytes = size,
        dateAddedEpochSec = date,
        folderName = folder,
    )

    @Test
    fun sortsByNameAscendingIgnoringCase() {
        val items = listOf(item("1", "banana"), item("2", "Apple"), item("3", "cherry"))
        val sorted = items.sortedBy(SortOrder(LibrarySort.Name, descending = false))
        assertEquals(listOf("Apple", "banana", "cherry"), sorted.map { it.title })
    }

    @Test
    fun sortsBySizeDescending() {
        val items = listOf(item("s", size = 10), item("m", size = 30), item("l", size = 20))
        val sorted = items.sortedBy(SortOrder(LibrarySort.Size, descending = true))
        assertEquals(listOf("m", "l", "s"), sorted.map { it.id })
    }

    @Test
    fun sortsByResolutionUsingPixelCount() {
        val hd = item("hd", width = 1280, height = 720)
        val uhd = item("uhd", width = 3840, height = 2160)
        val sd = item("sd", width = 640, height = 480)
        val sorted = listOf(hd, uhd, sd).sortedBy(SortOrder(LibrarySort.Resolution, descending = true))
        assertEquals(listOf("uhd", "hd", "sd"), sorted.map { it.id })
    }

    @Test
    fun groupsIntoFoldersAlphabeticallyWithBlankAsOther() {
        val items = listOf(
            item("a", folder = "Anime"),
            item("b", folder = ""),
            item("c", folder = "Movies"),
            item("d", folder = "Anime"),
        )
        val folders = items.toFolders(SortOrder())
        assertEquals(listOf("Anime", "Movies", "Other"), folders.map { it.name })
        assertEquals(2, folders.first().count)
    }

    @Test
    fun dateSectionsUseRelativeBucketsForDateSort() {
        val items = listOf(
            item("today", date = now),
            item("yesterday", date = now - day),
            item("thisWeek", date = now - 3 * day),
            item("thisMonth", date = now - 15 * day),
            item("earlier", date = now - 90 * day),
        )
        val sections = items.toSections(SortOrder(LibrarySort.DateAdded, descending = true), nowEpochSec = now)
        assertEquals(
            listOf("Today", "Yesterday", "This week", "This month", "Earlier"),
            sections.map { it.title },
        )
    }

    @Test
    fun nonDateSortProducesSingleUntitledSection() {
        val items = listOf(item("b", "b"), item("a", "a"))
        val sections = items.toSections(SortOrder(LibrarySort.Name, descending = false), nowEpochSec = now)
        assertEquals(1, sections.size)
        assertTrue(sections.single().title.isEmpty())
        assertEquals(listOf("a", "b"), sections.single().items.map { it.id })
    }

    @Test
    fun emptyInputYieldsNoSectionsOrFolders() {
        assertTrue(emptyList<VideoItem>().toSections(SortOrder(), nowEpochSec = now).isEmpty())
        assertTrue(emptyList<VideoItem>().toFolders(SortOrder()).isEmpty())
    }
}
