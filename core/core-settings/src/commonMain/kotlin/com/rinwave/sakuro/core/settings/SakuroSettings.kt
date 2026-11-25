package com.rinwave.sakuro.core.settings

import com.rinwave.sakuro.core.model.EngineType
import com.rinwave.sakuro.core.model.FrameRateMatching
import com.rinwave.sakuro.core.model.SubtitlePreferences
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
     * Adaptive preset degradation (docs/architecture.md §5): thermals/drops/power-saving
     * simplify the applied chain. Turning it off pins the selected preset as-is —
     * needed for clean quality measurements (sakuro-bench).
     */
    private val _adaptiveEnabled = MutableStateFlow(settings.getBoolean(KEY_ADAPTIVE, true))
    val adaptiveEnabled: StateFlow<Boolean> = _adaptiveEnabled.asStateFlow()

    /**
     * Cap upscaling at the physical screen resolution (DisplayUpscaleLimit).
     * Off = unlimited, as the preset asks — for comparisons and benchmarks.
     */
    private val _upscaleDisplayLimit = MutableStateFlow(settings.getBoolean(KEY_UPSCALE_DISPLAY_LIMIT, true))
    val upscaleDisplayLimit: StateFlow<Boolean> = _upscaleDisplayLimit.asStateFlow()

    /**
     * Master switch for swipes/pinch in the player (docs/product/features.md §3.2);
     * taps and long-press always work.
     */
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

    /** Swipe sensitivity multiplier (docs/product/features.md §3.2), [SENSITIVITY_MIN]..[SENSITIVITY_MAX]. */
    private val _gestureSensitivity = MutableStateFlow(
        settings.getFloat(KEY_GESTURE_SENSITIVITY, SENSITIVITY_DEFAULT)
            .coerceIn(SENSITIVITY_MIN, SENSITIVITY_MAX),
    )
    val gestureSensitivity: StateFlow<Float> = _gestureSensitivity.asStateFlow()

    private val _subtitlePreferences = MutableStateFlow(
        SubtitlePreferences(
            fontSizeSp = settings.getFloat("subtitle_font_size", 20f),
            backgroundEnabled = settings.getBoolean("subtitle_background", true),
            bottomPaddingFraction = settings.getFloat("subtitle_bottom_padding", 0.08f),
            delayMs = settings.getLong("subtitle_delay_ms", 0L),
            preferredLanguage = settings.getString("subtitle_language", ""),
        ).normalized(),
    )
    val subtitlePreferences: StateFlow<SubtitlePreferences> = _subtitlePreferences.asStateFlow()

    fun setSubtitlePreferences(value: SubtitlePreferences) {
        val normalized = value.normalized()
        settings.putFloat("subtitle_font_size", normalized.fontSizeSp)
        settings.putBoolean("subtitle_background", normalized.backgroundEnabled)
        settings.putFloat("subtitle_bottom_padding", normalized.bottomPaddingFraction)
        settings.putLong("subtitle_delay_ms", normalized.delayMs)
        settings.putString("subtitle_language", normalized.preferredLanguage)
        _subtitlePreferences.value = normalized
    }

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

    private val _backgroundPlayback = MutableStateFlow(settings.getBoolean(KEY_BACKGROUND_PLAYBACK, true))

    /** Keep playing (audio) with a media notification when the app leaves the screen. */
    val backgroundPlayback: StateFlow<Boolean> = _backgroundPlayback.asStateFlow()

    fun setBackgroundPlayback(enabled: Boolean) {
        settings.putBoolean(KEY_BACKGROUND_PLAYBACK, enabled)
        _backgroundPlayback.value = enabled
    }

    private val _streamCacheLimitMb = MutableStateFlow(
        settings.getInt(KEY_STREAM_CACHE_MB, DEFAULT_STREAM_CACHE_MB).coerceIn(0, MAX_STREAM_CACHE_MB),
    )

    /** Disk cache for network streams in MiB; 0 turns caching off. */
    val streamCacheLimitMb: StateFlow<Int> = _streamCacheLimitMb.asStateFlow()

    fun setStreamCacheLimitMb(value: Int) {
        val clamped = value.coerceIn(0, MAX_STREAM_CACHE_MB)
        settings.putInt(KEY_STREAM_CACHE_MB, clamped)
        _streamCacheLimitMb.value = clamped
    }

    private val _assRenderer = MutableStateFlow(settings.getBoolean(KEY_ASS_RENDERER, true))

    /** Full ASS/SSA rendering with libass (Media3 engine); applies to the next video. */
    val assRenderer: StateFlow<Boolean> = _assRenderer.asStateFlow()

    fun setAssRenderer(enabled: Boolean) {
        settings.putBoolean(KEY_ASS_RENDERER, enabled)
        _assRenderer.value = enabled
    }

    private val _frameRateMatching = MutableStateFlow(
        settings.getStringOrNull(KEY_FRAME_RATE_MATCHING)
            ?.let { stored -> FrameRateMatching.entries.firstOrNull { it.name == stored } }
            ?: FrameRateMatching.SEAMLESS,
    )

    /** Display refresh rate follows the video frame rate; seamless-only by default. */
    val frameRateMatching: StateFlow<FrameRateMatching> = _frameRateMatching.asStateFlow()

    fun setFrameRateMatching(value: FrameRateMatching) {
        settings.putString(KEY_FRAME_RATE_MATCHING, value.name)
        _frameRateMatching.value = value
    }

    fun setUpscaleDisplayLimit(enabled: Boolean) {
        settings.putBoolean(KEY_UPSCALE_DISPLAY_LIMIT, enabled)
        _upscaleDisplayLimit.value = enabled
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
        private const val KEY_UPSCALE_DISPLAY_LIMIT = "upscale_display_limit"
        private const val KEY_GESTURES = "player_gestures"
        private const val KEY_GESTURE_BRIGHTNESS = "player_gesture_brightness"
        private const val KEY_GESTURE_VOLUME = "player_gesture_volume"
        private const val KEY_GESTURE_SEEK = "player_gesture_seek"
        private const val KEY_GESTURE_ZOOM = "player_gesture_zoom"
        private const val KEY_GESTURE_SENSITIVITY = "player_gesture_sensitivity"
        private const val KEY_ASS_RENDERER = "subtitles_ass_renderer"
        private const val KEY_FRAME_RATE_MATCHING = "player_frame_rate_matching"
        private const val KEY_BACKGROUND_PLAYBACK = "player_background_playback"
        private const val KEY_STREAM_CACHE_MB = "stream_cache_limit_mb"
        private const val DEFAULT_STREAM_CACHE_MB = 1024
        private const val MAX_STREAM_CACHE_MB = 16 * 1024
        private const val DEFAULT_PRESET_ID = "off"
    }
}
