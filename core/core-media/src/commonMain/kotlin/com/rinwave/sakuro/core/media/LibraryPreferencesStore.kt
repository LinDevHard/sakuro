package com.rinwave.sakuro.core.media

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persisted library sorting preferences (field + direction) —
 * stored via multiplatform-settings under a couple of simple keys (no JSON).
 */
class LibraryPreferencesStore(private val settings: Settings = Settings()) {

    private val _sortOrder = MutableStateFlow(load())
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    fun setSortOrder(order: SortOrder) {
        settings.putString(KEY_FIELD, order.field.name)
        settings.putBoolean(KEY_DESC, order.descending)
        _sortOrder.value = order
    }

    private fun load(): SortOrder {
        val field = settings.getStringOrNull(KEY_FIELD)
            ?.let { name -> LibrarySort.entries.firstOrNull { it.name == name } }
            ?: return SortOrder()
        return SortOrder(field, settings.getBoolean(KEY_DESC, field.defaultDescending))
    }

    private companion object {
        const val KEY_FIELD = "library_sort_field"
        const val KEY_DESC = "library_sort_desc"
    }
}
