package com.rinwave.sakuro.di

import com.rinwave.sakuro.core.media.LibraryPreferencesStore
import com.rinwave.sakuro.core.media.PlaybackHistoryStore
import com.rinwave.sakuro.core.upscale.PinnedPresetStore
import com.rinwave.sakuro.core.upscale.PresetStores
import com.rinwave.sakuro.core.upscale.UserPresetStore
import com.rinwave.sakuro.feature.bench.BenchComponentFactory
import com.rinwave.sakuro.feature.bench.benchFeatureModule
import com.rinwave.sakuro.feature.library.LibraryComponentFactory
import com.rinwave.sakuro.feature.library.libraryFeatureModule
import com.rinwave.sakuro.feature.player.PlayerComponentFactory
import com.rinwave.sakuro.feature.player.playerFeatureModule
import com.rinwave.sakuro.feature.settings.SettingsComponentFactory
import com.rinwave.sakuro.feature.settings.settingsFeatureModule
import org.koin.core.module.Module
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** The video-surface renderers the app registers, one per shipped engine (`List<VideoSurfaceRenderer>`). */
val VideoSurfaceRenderers: Qualifier = named("videoSurfaceRenderers")

/** Everything RootComponent needs to build its children. */
data class RootFactories(
    val library: LibraryComponentFactory,
    val player: PlayerComponentFactory,
    val settings: SettingsComponentFactory,
    val bench: BenchComponentFactory,
)

/** Stores that common code can build itself (multiplatform-settings). */
private val storesModule = module {
    single { LibraryPreferencesStore() }
    single { PlaybackHistoryStore() }
    single { PresetStores(user = UserPresetStore(), pinned = PinnedPresetStore()) }
    single { RootFactories(get(), get(), get(), get()) }
}

/**
 * The whole DI graph: shared stores, every feature module and [platform].
 *
 * [platform] must provide: `SakuroSettings`, `MediaLibrary`, `EngineRegistry`,
 * `DeviceStatusMonitor`, `ContentClassifier`, `UserShaderStore`,
 * `ShaderInspector`, `GpuInfoProvider` and `List<VideoSurfaceRenderer>`
 * qualified by [VideoSurfaceRenderers].
 */
fun appModules(platform: Module): List<Module> = listOf(
    storesModule,
    libraryFeatureModule,
    playerFeatureModule,
    settingsFeatureModule,
    benchFeatureModule,
    platform,
)
