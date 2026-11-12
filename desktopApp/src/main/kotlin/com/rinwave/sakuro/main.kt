package com.rinwave.sakuro

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.extensions.compose.lifecycle.LifecycleController
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.rinwave.sakuro.core.detect.CompositeContentClassifier
import com.rinwave.sakuro.core.detect.ContentClassifier
import com.rinwave.sakuro.core.detect.FfmpegFrameSampler
import com.rinwave.sakuro.core.detect.FilenameContentClassifier
import com.rinwave.sakuro.core.detect.FrameContentClassifier
import com.rinwave.sakuro.core.media.MediaLibrary
import com.rinwave.sakuro.core.media.SampleVideoLibrary
import com.rinwave.sakuro.core.model.EngineType
import com.rinwave.sakuro.core.model.GpuInfoProvider
import com.rinwave.sakuro.core.player.EngineRegistry
import com.rinwave.sakuro.core.settings.SakuroSettings
import com.rinwave.sakuro.core.ui.VideoSurfaceRenderer
import com.rinwave.sakuro.core.upscale.DeviceStatusMonitor
import com.rinwave.sakuro.core.upscale.NoopShaderInspector
import com.rinwave.sakuro.core.upscale.NoopUserShaderStore
import com.rinwave.sakuro.core.upscale.ShaderInspector
import com.rinwave.sakuro.core.upscale.StaticDeviceStatusMonitor
import com.rinwave.sakuro.core.upscale.UserShaderStore
import com.rinwave.sakuro.di.VideoSurfaceRenderers
import com.rinwave.sakuro.di.appModules
import com.rinwave.sakuro.engine.fake.FakeEngineFactory
import com.rinwave.sakuro.navigation.RootComponent
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

/**
 * Desktop target — a sandbox for fast UI iteration (ARCHITECTURE.md §3.2):
 * there is no real playback, the engine is FakePlayerEngine.
 */
fun main() {
    startKoin {
        modules(
            appModules(
                module {
                    single { SakuroSettings(defaultEngine = EngineType.FAKE) }
                    single<MediaLibrary> { SampleVideoLibrary() }
                    single { EngineRegistry(listOf(FakeEngineFactory())) }
                    single<DeviceStatusMonitor> { StaticDeviceStatusMonitor() }
                    // As on Android: the file name answers immediately, frames (system
                    // ffmpeg, if present) replace the result with a more confident one.
                    single<ContentClassifier> {
                        CompositeContentClassifier(
                            FilenameContentClassifier(),
                            FrameContentClassifier(FfmpegFrameSampler()),
                        )
                    }
                    single<UserShaderStore> { NoopUserShaderStore() }
                    single<ShaderInspector> { NoopShaderInspector() }
                    single { GpuInfoProvider { null } }
                    // No renderers: the fake engine falls back to the synthetic surface.
                    single<List<VideoSurfaceRenderer>>(VideoSurfaceRenderers) { emptyList() }
                },
            ),
        )
    }

    val lifecycle = LifecycleRegistry()
    val root = RootComponent(
        componentContext = DefaultComponentContext(lifecycle),
        factories = GlobalContext.get().get(),
    )

    application {
        val windowState = rememberWindowState(width = 1100.dp, height = 760.dp)
        LifecycleController(lifecycle, windowState)
        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = "Sakuro",
        ) {
            App(root)
        }
    }
}
