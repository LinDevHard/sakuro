package com.rinwave.sakuro.core.upscale

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Pinning a preset to a specific file (FEATURES.md §1.3/§2.3):
 * uri → preset id. A pinned choice takes priority over the global default
 * and auto-detection; the consumer validates the id (the preset may have
 * been deleted) — the store keeps references as-is.
 *
 * Storage — multiplatform-settings, the whole map under one JSON key
 * (like [UserPresetStore]); insertion order is preserved, and on overflow
 * of [MAX_PINS] the oldest pin is evicted.
 */
class PinnedPresetStore(private val settings: Settings = Settings()) {

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), String.serializer())

    private val _pins = MutableStateFlow(load())

    /** uri → preset id; ordered from the oldest pin to the newest. */
    val pins: StateFlow<Map<String, String>> = _pins.asStateFlow()

    fun presetIdFor(uri: String): String? = _pins.value[uri]

    /** Pins a preset to a file; re-pinning updates the entry and makes it the newest. */
    fun pin(uri: String, presetId: String) {
        if (uri.isBlank() || presetId.isBlank()) return
        val updated = LinkedHashMap(_pins.value)
        updated.remove(uri)
        updated[uri] = presetId
        while (updated.size > MAX_PINS) updated.remove(updated.keys.first())
        persist(updated)
    }

    fun unpin(uri: String) {
        if (uri !in _pins.value) return
        persist(_pins.value.filterKeys { it != uri })
    }

    /** Cleanup on preset deletion: removes all pins referencing it. */
    fun removeAllFor(presetId: String) {
        if (presetId !in _pins.value.values) return
        persist(_pins.value.filterValues { it != presetId })
    }

    private fun persist(map: Map<String, String>) {
        settings.putString(KEY_PINS, json.encodeToString(serializer, map))
        _pins.value = map
    }

    private fun load(): Map<String, String> {
        val raw = settings.getStringOrNull(KEY_PINS) ?: return emptyMap()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyMap())
    }

    companion object {
        const val MAX_PINS = 200

        private const val KEY_PINS = "pinned_presets"
    }
}
