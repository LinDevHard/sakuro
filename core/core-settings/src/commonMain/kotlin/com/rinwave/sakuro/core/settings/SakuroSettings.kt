package com.rinwave.sakuro.core.settings

import com.rinwave.sakuro.core.player.EngineType
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Пользовательские настройки: выбор движка, пресет апскейла, debug-оверлей.
 * Хранение — multiplatform-settings (SharedPreferences на Android, Preferences на JVM).
 */
class SakuroSettings(
    private val defaultEngine: EngineType,
    private val settings: Settings = Settings(),
) {

    private val _engineType = MutableStateFlow(
        settings.getStringOrNull(KEY_ENGINE)
            ?.let { stored -> EngineType.entries.firstOrNull { it.name == stored } }
            ?: defaultEngine,
    )
    val engineType: StateFlow<EngineType> = _engineType.asStateFlow()

    private val _presetId = MutableStateFlow(settings.getString(KEY_PRESET, DEFAULT_PRESET_ID))
    val presetId: StateFlow<String> = _presetId.asStateFlow()

    private val _debugOverlay = MutableStateFlow(settings.getBoolean(KEY_DEBUG_OVERLAY, false))
    val debugOverlay: StateFlow<Boolean> = _debugOverlay.asStateFlow()

    fun setEngineType(type: EngineType) {
        settings.putString(KEY_ENGINE, type.name)
        _engineType.value = type
    }

    fun setPresetId(id: String) {
        settings.putString(KEY_PRESET, id)
        _presetId.value = id
    }

    fun setDebugOverlay(enabled: Boolean) {
        settings.putBoolean(KEY_DEBUG_OVERLAY, enabled)
        _debugOverlay.value = enabled
    }

    private companion object {
        const val KEY_ENGINE = "engine_type"
        const val KEY_PRESET = "upscale_preset_id"
        const val KEY_DEBUG_OVERLAY = "debug_overlay"
        const val DEFAULT_PRESET_ID = "off"
    }
}
