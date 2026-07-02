package com.rinwave.sakuro.core.upscale

/**
 * Адаптивный контроллер апскейла (ARCHITECTURE.md §5): деградирует выбранный
 * пользователем пресет при нагреве/просадке FPS/энергосбережении и возвращается
 * к полному при запасе. Выбор пользователя не меняется — только применяемая
 * цепочка (FEATURES.md §2.3).
 *
 * Чистая детерминированная state-machine: платформенные источники (термал,
 * батарея, статистика кадров) скармливают снимки в [update].
 */
class AdaptiveController(private val config: Config = Config()) {

    data class Config(
        /** % дропнутых кадров, после которого деградируем на шаг. */
        val degradeDropPercent: Float = 5f,
        /** % дропов, после которого деградируем сразу на два шага. */
        val severeDropPercent: Float = 12f,
        /** Сколько подряд здоровых снимков нужно для шага восстановления. */
        val recoverySamples: Int = 6,
        /** Заряд, ниже которого держим минимум один шаг деградации. */
        val lowBatteryPercent: Int = 15,
    )

    data class Decision(
        /** Что реально применять к движку. */
        val effective: UpscaleProfile,
        /** 0 — полный пресет … [MAX_LEVEL] — обработка выключена. */
        val level: Int,
        /** Человекочитаемая причина деградации для debug-оверлея; null при level 0. */
        val reason: String? = null,
    )

    private var perfLevel = 0
    private var healthyStreak = 0

    /** Принять свежие снимки состояния и решить, какой пресет применять. */
    fun update(user: UpscaleProfile, device: DeviceStatus, health: PlaybackHealth): Decision {
        val thermalFloor = when (device.thermal) {
            ThermalLevel.CRITICAL -> MAX_LEVEL
            ThermalLevel.SEVERE -> 2
            ThermalLevel.MODERATE -> 1
            ThermalLevel.LIGHT, ThermalLevel.NONE -> 0
        }
        val powerFloor = if (device.powerSaveMode || device.batteryPercent?.let { it <= config.lowBatteryPercent } == true) 1 else 0

        updatePerfLevel(health)

        val level = maxOf(thermalFloor, powerFloor, perfLevel).coerceAtMost(MAX_LEVEL)
        val reason = when {
            level == 0 -> null
            thermalFloor >= level -> "термал: ${device.thermal.name.lowercase()}"
            perfLevel >= level -> "дропы кадров: ${health.droppedFramePercent}%"
            else -> "энергосбережение"
        }
        return Decision(effective = user.degradedTo(level), level = level, reason = reason)
    }

    private fun updatePerfLevel(health: PlaybackHealth) {
        when {
            health.droppedFramePercent >= config.severeDropPercent -> {
                perfLevel = (perfLevel + 2).coerceAtMost(MAX_LEVEL)
                healthyStreak = 0
            }
            health.droppedFramePercent >= config.degradeDropPercent -> {
                perfLevel = (perfLevel + 1).coerceAtMost(MAX_LEVEL)
                healthyStreak = 0
            }
            perfLevel > 0 -> {
                healthyStreak++
                if (healthyStreak >= config.recoverySamples) {
                    perfLevel--
                    healthyStreak = 0
                }
            }
            else -> healthyStreak = 0
        }
    }

    fun reset() {
        perfLevel = 0
        healthyStreak = 0
    }

    companion object {
        /** Уровень полного отключения обработки. */
        const val MAX_LEVEL = 3
    }
}

/**
 * Облегчённая версия пресета для уровня деградации:
 * 1 — без деноиза; 2 — без деноиза/шарпена, апскейл не выше 1.5×; 3 — всё выключено.
 */
fun UpscaleProfile.degradedTo(level: Int): UpscaleProfile = when {
    level <= 0 || passes.isEmpty() -> this
    level >= AdaptiveController.MAX_LEVEL -> copy(passes = emptyList())
    else -> copy(
        passes = passes.mapNotNull { pass ->
            when (pass) {
                is UpscalePass.Denoise -> null
                is UpscalePass.Sharpen -> if (level >= 2) null else pass
                is UpscalePass.Upscale -> if (level >= 2) pass.copy(factor = minOf(pass.factor, 1.5f)) else pass
            }
        },
    )
}
