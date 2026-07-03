package com.rinwave.sakuro.core.upscale

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PinnedPresetStoreTest {

    @Test
    fun pinPersistsAcrossInstances() {
        val settings = MapSettings()
        PinnedPresetStore(settings).pin("content://video/1", "anime-hd")

        assertEquals("anime-hd", PinnedPresetStore(settings).presetIdFor("content://video/1"))
    }

    @Test
    fun rePinReplacesPresetForSameUri() {
        val store = PinnedPresetStore(MapSettings())
        store.pin("content://video/1", "anime-hd")

        store.pin("content://video/1", "off")

        assertEquals("off", store.presetIdFor("content://video/1"))
        assertEquals(1, store.pins.value.size)
    }

    @Test
    fun unpinRemovesOnlyRequestedUri() {
        val store = PinnedPresetStore(MapSettings())
        store.pin("content://video/1", "anime-hd")
        store.pin("content://video/2", "auto")

        store.unpin("content://video/1")

        assertNull(store.presetIdFor("content://video/1"))
        assertEquals("auto", store.presetIdFor("content://video/2"))
    }

    @Test
    fun removeAllForDropsOnlyPinsOfDeletedPreset() {
        val store = PinnedPresetStore(MapSettings())
        store.pin("content://video/1", "user-abc123")
        store.pin("content://video/2", "anime-hd")
        store.pin("content://video/3", "user-abc123")

        store.removeAllFor("user-abc123")

        assertEquals(mapOf("content://video/2" to "anime-hd"), store.pins.value)
    }

    @Test
    fun blankUriOrPresetIdIsIgnored() {
        val store = PinnedPresetStore(MapSettings())

        store.pin("  ", "anime-hd")
        store.pin("content://video/1", "")

        assertTrue(store.pins.value.isEmpty())
    }

    @Test
    fun corruptPayloadDegradesToEmpty() {
        val settings = MapSettings()
        settings.putString("pinned_presets", "{not json")

        assertTrue(PinnedPresetStore(settings).pins.value.isEmpty())
    }

    @Test
    fun overflowEvictsOldestPin() {
        val store = PinnedPresetStore(MapSettings())
        repeat(PinnedPresetStore.MAX_PINS + 1) { store.pin("content://video/$it", "off") }

        assertEquals(PinnedPresetStore.MAX_PINS, store.pins.value.size)
        assertNull(store.presetIdFor("content://video/0"))
        assertEquals("off", store.presetIdFor("content://video/${PinnedPresetStore.MAX_PINS}"))
    }

    @Test
    fun rePinRefreshesEvictionOrder() {
        val store = PinnedPresetStore(MapSettings())
        repeat(PinnedPresetStore.MAX_PINS) { store.pin("content://video/$it", "off") }

        store.pin("content://video/0", "anime-hd")
        store.pin("content://video/new", "off")

        assertEquals("anime-hd", store.presetIdFor("content://video/0"))
        assertNull(store.presetIdFor("content://video/1"))
    }
}
