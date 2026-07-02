package com.rinwave.sakuro.core.upscale

/**
 * Кроссплатформенный тепловой статус.
 * Android: PowerManager.getThermalStatus; iOS: ProcessInfo.thermalState.
 */
enum class ThermalLevel {
    NONE,
    LIGHT,
    MODERATE,
    SEVERE,
    CRITICAL,
}

/** Снимок состояния устройства для адаптивного контроллера (ARCHITECTURE.md §5). */
data class DeviceStatus(
    val thermal: ThermalLevel = ThermalLevel.NONE,
    val powerSaveMode: Boolean = false,
    /** null — уровень заряда неизвестен (desktop, нет батареи). */
    val batteryPercent: Int? = null,
)

/** Снимок здоровья воспроизведения — считается поверх PlayerEngine.debugStats. */
data class PlaybackHealth(
    /** Доля дропнутых кадров за последнее окно наблюдения, 0..100. */
    val droppedFramePercent: Float = 0f,
)
