package com.rinwave.sakuro.core.upscale

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Закрепление пресета за конкретным файлом (FEATURES.md §1.3/§2.3):
 * uri → id пресета. Закреплённый выбор приоритетнее общего дефолта
 * и авто-детекции; валидность id проверяет потребитель (пресет могли
 * удалить) — стор хранит ссылки как есть.
 *
 * Хранение — multiplatform-settings, вся карта одним JSON-ключом
 * (как [UserPresetStore]); порядок вставки сохраняется, при переполнении
 * [MAX_PINS] вытесняется самый старый пин.
 */
class PinnedPresetStore(private val settings: Settings = Settings()) {

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), String.serializer())

    private val _pins = MutableStateFlow(load())

    /** uri → id пресета; порядок — от самого старого пина к самому свежему. */
    val pins: StateFlow<Map<String, String>> = _pins.asStateFlow()

    fun presetIdFor(uri: String): String? = _pins.value[uri]

    /** Закрепляет пресет за файлом; повторный пин обновляет запись и делает её самой свежей. */
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

    /** Чистка при удалении пресета: снимает все пины, ссылающиеся на него. */
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
