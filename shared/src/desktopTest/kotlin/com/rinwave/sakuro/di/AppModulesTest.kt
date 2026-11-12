package com.rinwave.sakuro.di

import com.rinwave.sakuro.core.detect.ContentClassifier
import com.rinwave.sakuro.core.detect.FilenameContentClassifier
import com.rinwave.sakuro.core.media.MediaLibrary
import com.rinwave.sakuro.core.media.SampleVideoLibrary
import com.rinwave.sakuro.core.model.EngineType
import com.rinwave.sakuro.core.model.GpuInfoProvider
import com.rinwave.sakuro.core.player.EngineRegistry
import com.rinwave.sakuro.core.player.PlayerEngine
import com.rinwave.sakuro.core.player.PlayerEngineFactory
import com.rinwave.sakuro.core.settings.SakuroSettings
import com.rinwave.sakuro.core.ui.VideoSurfaceRenderer
import com.rinwave.sakuro.core.upscale.DeviceStatusMonitor
import com.rinwave.sakuro.core.upscale.NoopShaderInspector
import com.rinwave.sakuro.core.upscale.NoopUserShaderStore
import com.rinwave.sakuro.core.upscale.ShaderInspector
import com.rinwave.sakuro.core.upscale.StaticDeviceStatusMonitor
import com.rinwave.sakuro.core.upscale.UserShaderStore
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * The DI graph is assembled at app start; a feature asking for something no
 * platform module provides would only crash there. This resolves the whole
 * graph with a platform module that provides exactly the documented contract.
 */
class AppModulesTest {

    /** Registry contents are irrelevant here; the factory is never invoked. */
    private object UnusedEngineFactory : PlayerEngineFactory {
        override val type = EngineType.FAKE
        override fun create(): PlayerEngine = error("not used")
    }

    private val platform: Module = module {
        single { SakuroSettings(defaultEngine = EngineType.FAKE) }
        single<MediaLibrary> { SampleVideoLibrary() }
        single { EngineRegistry(listOf(UnusedEngineFactory)) }
        single<DeviceStatusMonitor> { StaticDeviceStatusMonitor() }
        single<ContentClassifier> { FilenameContentClassifier() }
        single<UserShaderStore> { NoopUserShaderStore() }
        single<ShaderInspector> { NoopShaderInspector() }
        single { GpuInfoProvider { null } }
        single<List<VideoSurfaceRenderer>>(VideoSurfaceRenderers) { emptyList() }
    }

    @Test
    fun resolvesEveryFeatureFactoryFromTheDocumentedPlatformContract() {
        val koin = koinApplication { modules(appModules(platform)) }.koin

        val factories = koin.get<RootFactories>()

        assertNotNull(factories.library)
        assertNotNull(factories.player)
        assertNotNull(factories.settings)
        assertNotNull(factories.bench)
    }
}
