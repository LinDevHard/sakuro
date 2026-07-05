package com.rinwave.sakuro.navigation

import com.arkivanov.decompose.ComponentContext
import com.rinwave.sakuro.core.player.EngineRegistry
import com.rinwave.sakuro.core.player.EngineType
import com.rinwave.sakuro.core.settings.SakuroSettings
import com.rinwave.sakuro.core.upscale.BuiltInPresets
import com.rinwave.sakuro.core.upscale.PresetStores
import com.rinwave.sakuro.core.upscale.UpscaleProfile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class SettingsComponent(
    componentContext: ComponentContext,
    private val settings: SakuroSettings,
    engineRegistry: EngineRegistry,
    presetStores: PresetStores,
    val onBack: () -> Unit,
) : ComponentContext by componentContext {

    private val userPresets = presetStores.user
    private val pinnedPresets = presetStores.pinned

    private val scope = componentScope()

    val availableEngines: List<EngineType> = engineRegistry.available

    val engineType: StateFlow<EngineType> = settings.engineType
    val presetId: StateFlow<String> = settings.presetId
    val debugOverlay: StateFlow<Boolean> = settings.debugOverlay
    val adaptiveEnabled: StateFlow<Boolean> = settings.adaptiveEnabled
    val gesturesEnabled: StateFlow<Boolean> = settings.gesturesEnabled
    val gestureSensitivity: StateFlow<Float> = settings.gestureSensitivity

    /** Built-in + user presets (FEATURES.md §2.2) — candidates for the default. */
    val presets: StateFlow<List<UpscaleProfile>> = userPresets.presets
        .map { user -> BuiltInPresets.all + user }
        .stateIn(scope, SharingStarted.Eagerly, BuiltInPresets.all + userPresets.presets.value)

    /** User presets only — for the section that manages your own presets. */
    val userPresetList: StateFlow<List<UpscaleProfile>> = userPresets.presets

    fun selectEngine(type: EngineType) = settings.setEngineType(type)

    fun selectPreset(id: String) = settings.setPresetId(id)

    fun saveUserPreset(profile: UpscaleProfile): UpscaleProfile = userPresets.save(profile)

    fun deleteUserPreset(id: String) {
        userPresets.delete(id)
        // A deleted preset must remain neither the default nor in the file pins.
        if (settings.presetId.value == id) settings.setPresetId(BuiltInPresets.OFF.id)
        pinnedPresets.removeAllFor(id)
    }

    fun exportUserPreset(profile: UpscaleProfile): String = userPresets.export(profile)

    fun importUserPreset(raw: String): Result<UpscaleProfile> = userPresets.import(raw)

    fun setDebugOverlay(enabled: Boolean) = settings.setDebugOverlay(enabled)

    fun setAdaptiveEnabled(enabled: Boolean) = settings.setAdaptiveEnabled(enabled)

    fun setGesturesEnabled(enabled: Boolean) = settings.setGesturesEnabled(enabled)

    fun setGestureSensitivity(value: Float) = settings.setGestureSensitivity(value)
}
