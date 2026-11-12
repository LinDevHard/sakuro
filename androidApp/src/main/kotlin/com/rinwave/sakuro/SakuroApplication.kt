package com.rinwave.sakuro

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.video.VideoFrameDecoder
import com.rinwave.sakuro.core.detect.CompositeContentClassifier
import com.rinwave.sakuro.core.detect.ContentClassifier
import com.rinwave.sakuro.core.detect.FilenameContentClassifier
import com.rinwave.sakuro.core.detect.FrameContentClassifier
import com.rinwave.sakuro.core.detect.RetrieverFrameSampler
import com.rinwave.sakuro.core.media.MediaLibrary
import com.rinwave.sakuro.core.media.MediaStoreVideoLibrary
import com.rinwave.sakuro.core.model.EngineType
import com.rinwave.sakuro.core.model.GpuInfo
import com.rinwave.sakuro.core.model.GpuInfoProvider
import com.rinwave.sakuro.core.player.EngineRegistry
import com.rinwave.sakuro.core.settings.SakuroSettings
import com.rinwave.sakuro.core.ui.VideoSurfaceRenderer
import com.rinwave.sakuro.core.upscale.AndroidDeviceStatusMonitor
import com.rinwave.sakuro.core.upscale.DeviceStatusMonitor
import com.rinwave.sakuro.core.upscale.ShaderInspector
import com.rinwave.sakuro.core.upscale.UserShaderStore
import com.rinwave.sakuro.di.AndroidUserShaderStore
import com.rinwave.sakuro.di.VideoSurfaceRenderers
import com.rinwave.sakuro.di.appModules
import com.rinwave.sakuro.engine.fake.FakeEngineFactory
import com.rinwave.sakuro.engine.media3.DeviceGpu
import com.rinwave.sakuro.engine.media3.Media3EngineFactory
import com.rinwave.sakuro.engine.media3.Media3ShaderInspector
import com.rinwave.sakuro.ui.Media3SurfaceRenderer
import org.koin.core.context.startKoin
import org.koin.dsl.module

class SakuroApplication : Application(), SingletonImageLoader.Factory {

    // Without VideoFrameDecoder, Coil cannot pull preview frames from video files.
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()

    override fun onCreate() {
        super.onCreate()
        // Shader pickers and import need the GPU capabilities; probe them off the main thread.
        DeviceGpu.warmUp()
        val app = this
        startKoin {
            modules(
                appModules(
                    module {
                        single { SakuroSettings(defaultEngine = EngineType.MEDIA3) }
                        single<MediaLibrary> { MediaStoreVideoLibrary(app) }
                        // Media3 first — it is also the fallback when the preference is unavailable.
                        single {
                            EngineRegistry(
                                buildList {
                                    add(Media3EngineFactory(app))
                                    addAll(referenceEngineFactories(app))
                                    add(FakeEngineFactory())
                                },
                            )
                        }
                        single<DeviceStatusMonitor> { AndroidDeviceStatusMonitor(app) }
                        // The file name answers instantly, frame analysis follows
                        // and replaces the result with a more confident one.
                        single<ContentClassifier> {
                            CompositeContentClassifier(
                                FilenameContentClassifier(),
                                FrameContentClassifier(RetrieverFrameSampler(app)),
                            )
                        }
                        single<UserShaderStore> { AndroidUserShaderStore(app) }
                        single<ShaderInspector> { Media3ShaderInspector(app) }
                        single { GpuInfoProvider(::probeGpu) }
                        // Same order as the engines: Media3 first, then the flavor's reference engines.
                        single<List<VideoSurfaceRenderer>>(VideoSurfaceRenderers) {
                            listOf(Media3SurfaceRenderer()) + referenceSurfaceRenderers()
                        }
                    },
                ),
            )
        }
    }
}

/** GPU details from the Media3 engine's shader-runtime probe (bench reports). */
private fun probeGpu(): GpuInfo? = DeviceGpu.capabilities?.let {
    GpuInfo(
        renderer = it.renderer,
        vendor = it.vendor,
        glVersion = it.glVersion,
        glesMajor = it.glesMajor,
        glesMinor = it.glesMinor,
        fp16Renderable = it.fp16Renderable,
        compute = it.es31,
    )
}
