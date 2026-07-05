package com.rinwave.sakuro.core.upscale

/**
 * Cross-platform thermal status.
 * Android: PowerManager.getThermalStatus; iOS: ProcessInfo.thermalState.
 */
enum class ThermalLevel {
    NONE,
    LIGHT,
    MODERATE,
    SEVERE,
    CRITICAL,
}

/** Device-status snapshot for the adaptive controller (ARCHITECTURE.md §5). */
data class DeviceStatus(
    val thermal: ThermalLevel = ThermalLevel.NONE,
    val powerSaveMode: Boolean = false,
    /** null — battery level unknown (desktop, no battery). */
    val batteryPercent: Int? = null,
)

/** Playback-health snapshot — computed on top of PlayerEngine.debugStats. */
data class PlaybackHealth(
    /** Share of dropped frames over the last observation window, 0..100. */
    val droppedFramePercent: Float = 0f,
)
