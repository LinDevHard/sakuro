package com.rinwave.sakuro.core.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FolderPlaybackQueueTest {
    private fun video(name: String, folder: String = "Season", uri: String = name) =
        VideoItem(uri, name, uri, 100_000, folderName = folder)

    @Test
    fun navigatesNaturalTitleOrderWithinFolderWithoutWrapping() {
        val one = video("Episode1")
        val two = video("episode2")
        val ten = video("Episode10")
        val items = listOf(ten, video("episode3", "Other season"), two, one)
        assertEquals(two, nextVideoInFolder(items, one.uri))
        assertEquals(ten, nextVideoInFolder(items, two.uri))
        assertEquals(two, previousVideoInFolder(items, ten.uri))
        assertNull(nextVideoInFolder(items, ten.uri))
        assertNull(previousVideoInFolder(items, one.uri))
        assertNull(nextVideoInFolder(items, "missing"))
    }

    @Test
    fun unknownFoldersDoNotFormAQueue() {
        val items = listOf(video("a", ""), video("b", ""))
        assertNull(nextVideoInFolder(items, "a"))
    }

    @Test
    fun folderIdentitySeparatesEqualLabels() {
        val first = video("Episode1").copy(folderId = "bucket:1")
        val different = video("Episode2").copy(folderId = "bucket:2")
        val next = video("Episode3").copy(folderId = "bucket:1")
        assertEquals(next, nextVideoInFolder(listOf(first, different, next), first.uri))
        assertNull(previousVideoInFolder(listOf(first, different, next), different.uri))
    }

    @Test
    fun resolvesTiesDeterministicallyAndHandlesLargeNumbers() {
        val a = video("Episode2", uri = "a")
        val b = video("Episode2", uri = "b")
        val huge = video("Episode999999999999999999999999")
        val items = listOf(huge, b, a)
        assertEquals(b, nextVideoInFolder(items, "a"))
        assertEquals(b, nextVideoInFolder(items.reversed(), "a"))
        assertEquals(huge, nextVideoInFolder(items, "b"))
    }
}
