package com.rinwave.sakuro.core.upscale

sealed interface AdaptiveReason {
    data class Thermal(val level: ThermalLevel) : AdaptiveReason
    data class FrameDrops(val percent: Float) : AdaptiveReason
    data object PowerSave : AdaptiveReason
    data class LowBattery(val percent: Int) : AdaptiveReason
}

/**
 * Adaptive upscale controller (ARCHITECTURE.md §5): degrades the user-selected
 * preset on heat/FPS drops/power-saving and returns
 * to full when there is headroom. The user's choice never changes — only the applied
 * chain does (FEATURES.md §2.3).
 *
 * A pure deterministic state machine: platform sources (thermal,
 * battery, frame statistics) feed snapshots into [update].
 */
class AdaptiveController(private val config: Config = Config()) {

    data class Config(
        /** % of dropped frames after which we degrade by one step. */
        val degradeDropPercent: Float = 5f,
        /** % of drops after which we degrade by two steps at once. */
        val severeDropPercent: Float = 12f,
        /** How many consecutive healthy snapshots are needed for a recovery step. */
        val recoverySamples: Int = 6,
        /** Battery level below which we keep at least one degradation step. */
        val lowBatteryPercent: Int = 15,
    )

    data class Decision(
        /** The profile selected by the user before adaptive changes. */
        val requested: UpscaleProfile,
        /** What to actually apply to the engine. */
        val effective: UpscaleProfile,
        /** 0 — full preset … [MAX_LEVEL] — processing off. */
        val level: Int,
        /** Structured degradation reason for UI localization and transition logs. */
        val reason: AdaptiveReason? = null,
    )

    private var perfLevel = 0
    private var healthyStreak = 0
    private var lastDropPercent = 0f

    /** Accept fresh state snapshots and decide which preset to apply. */
    fun update(user: UpscaleProfile, device: DeviceStatus, health: PlaybackHealth): Decision {
        val thermalFloor = when (device.thermal) {
            ThermalLevel.CRITICAL -> MAX_LEVEL
            ThermalLevel.SEVERE -> 2
            ThermalLevel.MODERATE -> 1
            ThermalLevel.LIGHT, ThermalLevel.NONE -> 0
        }
        val lowBattery = device.batteryPercent?.let { it <= config.lowBatteryPercent } == true
        val powerFloor = if (device.powerSaveMode || lowBattery) 1 else 0

        updatePerfLevel(health)

        val level = maxOf(thermalFloor, powerFloor, perfLevel).coerceAtMost(MAX_LEVEL)
        val reason = degradationReason(level, thermalFloor, device, health, lowBattery)
        return Decision(requested = user, effective = user.degradedTo(level), level = level, reason = reason)
    }

    private fun degradationReason(
        level: Int,
        thermalFloor: Int,
        device: DeviceStatus,
        health: PlaybackHealth,
        lowBattery: Boolean,
    ): AdaptiveReason? = when {
        level == 0 -> null
        thermalFloor >= level -> AdaptiveReason.Thermal(device.thermal)
        health.droppedFramePercent >= config.degradeDropPercent && perfLevel >= level -> {
            AdaptiveReason.FrameDrops(health.droppedFramePercent)
        }
        device.powerSaveMode -> AdaptiveReason.PowerSave
        lowBattery -> AdaptiveReason.LowBattery(checkNotNull(device.batteryPercent))
        perfLevel >= level -> AdaptiveReason.FrameDrops(lastDropPercent)
        else -> null
    }

    private fun updatePerfLevel(health: PlaybackHealth) {
        when {
            health.droppedFramePercent >= config.severeDropPercent -> {
                lastDropPercent = health.droppedFramePercent
                perfLevel = (perfLevel + 2).coerceAtMost(MAX_LEVEL)
                healthyStreak = 0
            }
            health.droppedFramePercent >= config.degradeDropPercent -> {
                lastDropPercent = health.droppedFramePercent
                perfLevel = (perfLevel + 1).coerceAtMost(MAX_LEVEL)
                healthyStreak = 0
            }
            perfLevel > 0 -> {
                healthyStreak++
                if (healthyStreak >= config.recoverySamples) {
                    perfLevel--
                    if (perfLevel == 0) lastDropPercent = 0f
                    healthyStreak = 0
                }
            }
            else -> healthyStreak = 0
        }
    }

    fun reset() {
        perfLevel = 0
        healthyStreak = 0
        lastDropPercent = 0f
    }

    companion object {
        /** The level at which processing is fully disabled. */
        const val MAX_LEVEL = 3
    }
}

/**
 * A lightened version of the preset for a degradation level:
 * 1 — no denoise; 2 — no denoise/sharpen, upscale no higher than 1.5×; 3 — everything off.
 */
fun UpscaleProfile.degradedTo(level: Int): UpscaleProfile = when {
    level <= 0 || !isEnabled -> this
    level >= AdaptiveController.MAX_LEVEL -> processingOff()
    shaderChain.isNotEmpty() -> degradeExplicitShaderChain(level)
    else -> copy(passes = degradedPasses(level))
}

private fun UpscaleProfile.degradeExplicitShaderChain(level: Int): UpscaleProfile {
    if (level >= 2) return processingOff()

    val retained = shaderChain.filter { fileName ->
        val shader = BundledShaders.byFileName(fileName)
        shader == null || (shader.role != BundledShaderRole.DENOISE && shader.cost != ShaderCost.HIGH)
    }
    if (retained.isEmpty()) return processingOff()

    return copy(
        passes = degradedPasses(level),
        shaderChain = retained,
        shaderParams = shaderParams.filterKeys { it in retained },
    )
}

private fun UpscaleProfile.degradedPasses(level: Int): List<UpscalePass> = passes.mapNotNull { pass ->
    when (pass) {
        is UpscalePass.Denoise -> null
        is UpscalePass.Sharpen -> if (level >= 2) null else pass
        is UpscalePass.Upscale -> if (level >= 2) pass.copy(factor = minOf(pass.factor, 1.5f)) else pass
    }
}

private fun UpscaleProfile.processingOff(): UpscaleProfile = copy(
    passes = emptyList(),
    shaderChain = emptyList(),
    shaderParams = emptyMap(),
)
