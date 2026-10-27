package com.rinwave.sakuro.core.media

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaybackHistoryStoreTest {
    @Test
    fun restoresDistinctUrisAndPreference() {
        val settings = MapSettings()
        val store = PlaybackHistoryStore(settings)
        assertFalse(store.autoPlayNext.value)
        store.record("content://video/a|b\n雪", 30_000, 100_000)
        store.record("content://video/a/b", 40_000, 100_000)
        store.setAutoPlayNext(true)
        val restored = PlaybackHistoryStore(settings)
        assertEquals(30_000L, restored.entry("content://video/a|b\n雪")?.resumePositionMs)
        assertEquals(40_000L, restored.entry("content://video/a/b")?.positionMs)
        assertTrue(restored.autoPlayNext.value)
    }

    @Test
    fun suppressesResumeNearStartAndCompletion() {
        assertEquals(0L, PlaybackProgress(2_000, 100_000).resumePositionMs)
        assertEquals(0L, PlaybackProgress(96_000, 100_000).resumePositionMs)
        assertEquals(30_000L, PlaybackProgress(30_000, 100_000).resumePositionMs)
        assertEquals(0.3f, PlaybackProgress(30_000, 100_000).fraction)
        assertEquals(0f, PlaybackProgress(100, 0).fraction)
    }

    @Test
    fun ignoresCorruptedStorageAndInvalidRecords() {
        val settings = MapSettings()
        val store = PlaybackHistoryStore(settings)
        store.record("one", 10_000, 100_000)
        settings.keys.forEach { settings.putString(it, "corrupt") }
        val restored = PlaybackHistoryStore(settings)
        assertTrue(restored.entries.value.isEmpty())
        restored.record("bad", -1, 100)
        restored.record("bad", 10, 0)
        assertNull(restored.entry("bad"))
    }

    @Test
    fun retainsTwoHundredMostRecentlyUpdatedEntriesAcrossRecreation() {
        val settings = MapSettings()
        val store = PlaybackHistoryStore(settings)
        repeat(200) { store.record("uri:$it", 10_000, 100_000) }
        store.record("uri:0", 20_000, 100_000)
        store.record("new", 10_000, 100_000)
        val restored = PlaybackHistoryStore(settings)
        assertEquals(200, restored.entries.value.size)
        assertNull(restored.entry("uri:1"))
        assertEquals(20_000L, restored.entry("uri:0")?.positionMs)
    }

    @Test
    fun clearingIsPersistentAndPreservesPreference() {
        val settings = MapSettings()
        val store = PlaybackHistoryStore(settings)
        store.setAutoPlayNext(true)
        store.record("one", 10_000, 100_000)
        store.record("two", 10_000, 100_000)
        store.clear("one")
        assertNull(PlaybackHistoryStore(settings).entry("one"))
        store.clearAll()
        assertTrue(PlaybackHistoryStore(settings).entries.value.isEmpty())
        assertTrue(PlaybackHistoryStore(settings).autoPlayNext.value)
    }

    @Test
    fun recordingUnchangedProgressStillRefreshesRetentionOrder() {
        val store = PlaybackHistoryStore(MapSettings())
        repeat(200) { store.record("uri:$it", 10_000, 100_000) }
        store.record("uri:0", 10_000, 100_000)
        store.record("new", 10_000, 100_000)
        assertEquals(10_000L, store.entry("uri:0")?.positionMs)
        assertNull(store.entry("uri:1"))
    }
}
