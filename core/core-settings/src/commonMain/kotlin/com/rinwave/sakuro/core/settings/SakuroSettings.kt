package com.rinwave.sakuro.core.settings

import com.rinwave.sakuro.core.player.EngineType
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * User settings: engine selection, upscale preset, debug overlay.
 * Storage — multiplatform-settings (SharedPreferences on Android, Preferences on JVM).
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
     * Adaptive preset degradation (ARCHITECTURE.md §5): thermals/drops/power-saving
     * simplify the applied chain. Turning it off pins the selected preset as-is —
     * needed for clean quality measurements (sakuro-bench).
     */
    private val _adaptiveEnabled = MutableStateFlow(settings.getBoolean(KEY_ADAPTIVE, true))
    val adaptiveEnabled: StateFlow<Boolean> = _adaptiveEnabled.asStateFlow()

    /** Master switch for swipes/pinch in the player (FEATURES.md §3.2); taps and long-press always work. */
    private val _gesturesEnabled = MutableStateFlow(settings.getBoolean(KEY_GESTURES, true))
    val gesturesEnabled: StateFlow<Boolean> = _gesturesEnabled.asStateFlow()

    /** Per-gesture switches, each honoured only while [gesturesEnabled] is on. */
    private val _gestureBrightness = MutableStateFlow(settings.getBoolean(KEY_GESTURE_BRIGHTNESS, true))
    val gestureBrightness: StateFlow<Boolean> = _gestureBrightness.asStateFlow()

    private val _gestureVolume = MutableStateFlow(settings.getBoolean(KEY_GESTURE_VOLUME, true))
    val gestureVolume: StateFlow<Boolean> = _gestureVolume.asStateFlow()

    private val _gestureSeek = MutableStateFlow(settings.getBoolean(KEY_GESTURE_SEEK, true))
    val gestureSeek: StateFlow<Boolean> = _gestureSeek.asStateFlow()

    private val _gestureZoom = MutableStateFlow(settings.getBoolean(KEY_GESTURE_ZOOM, true))
    val gestureZoom: StateFlow<Boolean> = _gestureZoom.asStateFlow()

    /** Swipe sensitivity multiplier (FEATURES.md §3.2), [SENSITIVITY_MIN]..[SENSITIVITY_MAX]. */
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

    fun setGestureBrightness(enabled: Boolean) {
        settings.putBoolean(KEY_GESTURE_BRIGHTNESS, enabled)
        _gestureBrightness.value = enabled
    }

    fun setGestureVolume(enabled: Boolean) {
        settings.putBoolean(KEY_GESTURE_VOLUME, enabled)
        _gestureVolume.value = enabled
    }

    fun setGestureSeek(enabled: Boolean) {
        settings.putBoolean(KEY_GESTURE_SEEK, enabled)
        _gestureSeek.value = enabled
    }

    fun setGestureZoom(enabled: Boolean) {
        settings.putBoolean(KEY_GESTURE_ZOOM, enabled)
        _gestureZoom.value = enabled
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
        private const val KEY_GESTURE_BRIGHTNESS = "player_gesture_brightness"
        private const val KEY_GESTURE_VOLUME = "player_gesture_volume"
        private const val KEY_GESTURE_SEEK = "player_gesture_seek"
        private const val KEY_GESTURE_ZOOM = "player_gesture_zoom"
        private const val KEY_GESTURE_SENSITIVITY = "player_gesture_sensitivity"
        private const val DEFAULT_PRESET_ID = "off"
    }
}
