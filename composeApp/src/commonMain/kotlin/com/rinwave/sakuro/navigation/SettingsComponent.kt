package com.rinwave.sakuro.navigation

import com.arkivanov.decompose.ComponentContext
import com.rinwave.sakuro.core.player.EngineRegistry
import com.rinwave.sakuro.core.player.EngineType
import com.rinwave.sakuro.core.settings.SakuroSettings
import com.rinwave.sakuro.core.upscale.BuiltInPresets
import com.rinwave.sakuro.core.upscale.UpscaleProfile
import kotlinx.coroutines.flow.StateFlow

class SettingsComponent(
    componentContext: ComponentContext,
    private val settings: SakuroSettings,
    engineRegistry: EngineRegistry,
    val onBack: () -> Unit,
) : ComponentContext by componentContext {

    val availableEngines: List<EngineType> = engineRegistry.available

    val engineType: StateFlow<EngineType> = settings.engineType
    val presetId: StateFlow<String> = settings.presetId
    val debugOverlay: StateFlow<Boolean> = settings.debugOverlay

    val presets: List<UpscaleProfile> = BuiltInPresets.all

    fun selectEngine(type: EngineType) = settings.setEngineType(type)

    fun selectPreset(id: String) = settings.setPresetId(id)

    fun setDebugOverlay(enabled: Boolean) = settings.setDebugOverlay(enabled)
}
