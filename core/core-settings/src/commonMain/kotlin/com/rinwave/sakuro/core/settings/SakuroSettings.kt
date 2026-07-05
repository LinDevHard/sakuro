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

    /**
     * Адаптивная деградация пресета (ARCHITECTURE.md §5): термалка/дропы/энергосбережение
     * упрощают применяемую цепочку. Выключение фиксирует выбранный пресет как есть —
     * нужно для чистоты замеров качества (sakuro-bench).
     */
    private val _adaptiveEnabled = MutableStateFlow(settings.getBoolean(KEY_ADAPTIVE, true))
    val adaptiveEnabled: StateFlow<Boolean> = _adaptiveEnabled.asStateFlow()

    /** Свайпы/пинч в плеере (FEATURES.md §3.2); тапы и long-press работают всегда. */
    private val _gesturesEnabled = MutableStateFlow(settings.getBoolean(KEY_GESTURES, true))
    val gesturesEnabled: StateFlow<Boolean> = _gesturesEnabled.asStateFlow()

    /** Множитель чувствительности свайпов (FEATURES.md §3.2), [SENSITIVITY_MIN]..[SENSITIVITY_MAX]. */
    private val _gestureSensitivity = MutableStateFlow(
        settings.getFloat(KEY_GESTURE_SENSITIVITY, SENSITIVITY_DEFAULT)
            .coerceIn(SENSITIVITY_MIN, SENSITIVITY_MAX),
    )
    val gestureSensitivity: StateFlow<Float> = _gestureSensitivity.asStateFlow()

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

    fun setAdaptiveEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_ADAPTIVE, enabled)
        _adaptiveEnabled.value = enabled
    }

    fun setGesturesEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_GESTURES, enabled)
        _gesturesEnabled.value = enabled
    }

    fun setGestureSensitivity(value: Float) {
        val clamped = value.coerceIn(SENSITIVITY_MIN, SENSITIVITY_MAX)
        settings.putFloat(KEY_GESTURE_SENSITIVITY, clamped)
        _gestureSensitivity.value = clamped
    }

    companion object {
        const val SENSITIVITY_MIN = 0.5f
        const val SENSITIVITY_MAX = 2f
        const val SENSITIVITY_DEFAULT = 1f

        private const val KEY_ENGINE = "engine_type"
        private const val KEY_PRESET = "upscale_preset_id"
        private const val KEY_DEBUG_OVERLAY = "debug_overlay"
        private const val KEY_ADAPTIVE = "adaptive_enabled"
        private const val KEY_GESTURES = "player_gestures"
        private const val KEY_GESTURE_SENSITIVITY = "player_gesture_sensitivity"
        private const val DEFAULT_PRESET_ID = "off"
    }
}
