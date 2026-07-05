package com.rinwave.sakuro.core.media

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class LibraryPreferencesStoreTest {

    @Test
    fun defaultsToDateAddedDescending() {
        val store = LibraryPreferencesStore(MapSettings())
        assertEquals(SortOrder(LibrarySort.DateAdded, descending = true), store.sortOrder.value)
    }

    @Test
    fun persistsAndReloadsSortOrder() {
        val settings = MapSettings()
        LibraryPreferencesStore(settings).setSortOrder(SortOrder(LibrarySort.Size, descending = false))

        val reloaded = LibraryPreferencesStore(settings)
        assertEquals(SortOrder(LibrarySort.Size, descending = false), reloaded.sortOrder.value)
    }

    @Test
    fun setSortOrderUpdatesFlow() {
        val store = LibraryPreferencesStore(MapSettings())
        store.setSortOrder(SortOrder(LibrarySort.Name, descending = false))
        assertEquals(LibrarySort.Name, store.sortOrder.value.field)
        assertEquals(false, store.sortOrder.value.descending)
    }
}
