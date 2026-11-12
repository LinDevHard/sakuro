package com.rinwave.sakuro.feature.bench

import com.arkivanov.decompose.ComponentContext
import com.rinwave.sakuro.core.model.GpuInfoProvider
import com.rinwave.sakuro.core.player.EngineRegistry
import com.rinwave.sakuro.core.upscale.PresetStores
import com.rinwave.sakuro.core.upscale.UserShaderStore
import org.koin.core.module.Module
import org.koin.dsl.module

/** What the benchmark asks its host to do; the host owns navigation. */
public sealed interface BenchOutput {
    public data object Back : BenchOutput
}

/** Creates [BenchComponent]s; the only way to build one outside this module. */
public fun interface BenchComponentFactory {
    public fun create(context: ComponentContext, onOutput: (BenchOutput) -> Unit): BenchComponent
}

public fun BenchComponentFactory(
    engineRegistry: EngineRegistry,
    presetStores: PresetStores,
    userShaderStore: UserShaderStore,
    gpuInfo: GpuInfoProvider,
): BenchComponentFactory = BenchComponentFactory { context, onOutput ->
    BenchComponent(context, engineRegistry, presetStores, userShaderStore, gpuInfo, onOutput)
}

/** Registers [BenchComponentFactory]; the app module provides [GpuInfoProvider]. */
public val benchFeatureModule: Module = module {
    single { BenchComponentFactory(get(), get(), get(), get()) }
}
