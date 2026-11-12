package com.rinwave.sakuro.navigation

import com.arkivanov.decompose.ComponentContext
import com.rinwave.sakuro.core.settings.SakuroSettings
import com.rinwave.sakuro.core.upscale.BuiltInPresets
import com.rinwave.sakuro.core.upscale.PresetStores
import com.rinwave.sakuro.core.upscale.ShaderInspector
import com.rinwave.sakuro.core.upscale.UpscaleProfile
import com.rinwave.sakuro.core.upscale.UserShaderStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Upscale tab: the default preset plus management of user presets and shaders. */
class UpscaleSettingsComponent(
    componentContext: ComponentContext,
    private val settings: SakuroSettings,
    presetStores: PresetStores,
    private val userShaderStore: UserShaderStore,
    val shaderInspector: ShaderInspector,
) : ComponentContext by componentContext {

    private val userPresets = presetStores.user
    private val pinnedPresets = presetStores.pinned
    private val scope = componentScope()

    /** Imported `.glsl`/`.hook` files usable as preset shader chains. */
    val userShaders: StateFlow<List<String>> = userShaderStore.shaders

    fun importShader(fileName: String, content: String): Result<String> =
        userShaderStore.import(fileName, content)

    fun deleteShader(name: String) = userShaderStore.delete(name)

    val presetId: StateFlow<String> = settings.presetId

    /** Built-in + user presets (FEATURES.md §2.2) — candidates for the default. */
    val presets: StateFlow<List<UpscaleProfile>> = userPresets.presets
        .map { user -> BuiltInPresets.all + user }
        .stateIn(scope, SharingStarted.Eagerly, BuiltInPresets.all + userPresets.presets.value)

    /** User presets only — for the section that manages your own presets. */
    val userPresetList: StateFlow<List<UpscaleProfile>> = userPresets.presets

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
}
