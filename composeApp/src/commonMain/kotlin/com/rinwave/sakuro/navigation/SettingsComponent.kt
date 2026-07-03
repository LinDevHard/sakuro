package com.rinwave.sakuro.navigation

import com.arkivanov.decompose.ComponentContext
import com.rinwave.sakuro.core.player.EngineRegistry
import com.rinwave.sakuro.core.player.EngineType
import com.rinwave.sakuro.core.settings.SakuroSettings
import com.rinwave.sakuro.core.upscale.BuiltInPresets
import com.rinwave.sakuro.core.upscale.UpscaleProfile
import com.rinwave.sakuro.core.upscale.UserPresetStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class SettingsComponent(
    componentContext: ComponentContext,
    private val settings: SakuroSettings,
    engineRegistry: EngineRegistry,
    private val userPresets: UserPresetStore,
    val onBack: () -> Unit,
) : ComponentContext by componentContext {

    private val scope = componentScope()

    val availableEngines: List<EngineType> = engineRegistry.available

    val engineType: StateFlow<EngineType> = settings.engineType
    val presetId: StateFlow<String> = settings.presetId
    val debugOverlay: StateFlow<Boolean> = settings.debugOverlay
    val gesturesEnabled: StateFlow<Boolean> = settings.gesturesEnabled
    val gestureSensitivity: StateFlow<Float> = settings.gestureSensitivity

    /** Встроенные + пользовательские пресеты (FEATURES.md §2.2) — кандидаты в «по умолчанию». */
    val presets: StateFlow<List<UpscaleProfile>> = userPresets.presets
        .map { user -> BuiltInPresets.all + user }
        .stateIn(scope, SharingStarted.Eagerly, BuiltInPresets.all + userPresets.presets.value)

    /** Только пользовательские — для секции управления своими пресетами. */
    val userPresetList: StateFlow<List<UpscaleProfile>> = userPresets.presets

    fun selectEngine(type: EngineType) = settings.setEngineType(type)

    fun selectPreset(id: String) = settings.setPresetId(id)

    fun saveUserPreset(profile: UpscaleProfile): UpscaleProfile = userPresets.save(profile)

    fun deleteUserPreset(id: String) {
        userPresets.delete(id)
        // Удалённый пресет не должен оставаться «по умолчанию».
        if (settings.presetId.value == id) settings.setPresetId(BuiltInPresets.OFF.id)
    }

    fun exportUserPreset(profile: UpscaleProfile): String = userPresets.export(profile)

    fun importUserPreset(raw: String): Result<UpscaleProfile> = userPresets.import(raw)

    fun setDebugOverlay(enabled: Boolean) = settings.setDebugOverlay(enabled)

    fun setGesturesEnabled(enabled: Boolean) = settings.setGesturesEnabled(enabled)

    fun setGestureSensitivity(value: Float) = settings.setGestureSensitivity(value)
}
