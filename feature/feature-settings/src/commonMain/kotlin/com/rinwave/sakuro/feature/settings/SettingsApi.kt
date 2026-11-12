package com.rinwave.sakuro.feature.settings

import com.arkivanov.decompose.ComponentContext
import com.rinwave.sakuro.core.player.EngineRegistry
import com.rinwave.sakuro.core.settings.SakuroSettings
import com.rinwave.sakuro.core.upscale.PresetStores
import com.rinwave.sakuro.core.upscale.ShaderInspector
import com.rinwave.sakuro.core.upscale.UserShaderStore
import org.koin.core.module.Module
import org.koin.dsl.module

/** What settings asks its host to do; the host owns navigation. */
public sealed interface SettingsOutput {
    public data object Back : SettingsOutput

    public data object OpenBench : SettingsOutput
}

/** Creates [SettingsComponent]s; the only way to build one outside this module. */
public fun interface SettingsComponentFactory {
    public fun create(context: ComponentContext, onOutput: (SettingsOutput) -> Unit): SettingsComponent
}

public fun SettingsComponentFactory(
    settings: SakuroSettings,
    engineRegistry: EngineRegistry,
    presetStores: PresetStores,
    userShaderStore: UserShaderStore,
    shaderInspector: ShaderInspector,
): SettingsComponentFactory = SettingsComponentFactory { context, onOutput ->
    SettingsComponent(context, settings, engineRegistry, presetStores, userShaderStore, shaderInspector, onOutput)
}

/** Registers [SettingsComponentFactory]; platform modules provide the stores and registry. */
public val settingsFeatureModule: Module = module {
    single { SettingsComponentFactory(get(), get(), get(), get(), get()) }
}
