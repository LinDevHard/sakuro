package com.rinwave.sakuro

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.video.VideoFrameDecoder
import com.rinwave.sakuro.core.detect.CompositeContentClassifier
import com.rinwave.sakuro.core.detect.FilenameContentClassifier
import com.rinwave.sakuro.core.detect.FrameContentClassifier
import com.rinwave.sakuro.core.detect.RetrieverFrameSampler
import com.rinwave.sakuro.core.media.MediaStoreVideoLibrary
import com.rinwave.sakuro.core.player.EngineRegistry
import com.rinwave.sakuro.core.player.EngineType
import com.rinwave.sakuro.core.settings.SakuroSettings
import com.rinwave.sakuro.core.upscale.AndroidDeviceStatusMonitor
import com.rinwave.sakuro.di.appModule
import com.rinwave.sakuro.engine.fake.FakeEngineFactory
import com.rinwave.sakuro.engine.media3.Media3EngineFactory
import com.rinwave.sakuro.engine.mpv.MpvEngineFactory
import org.koin.core.context.startKoin

class SakuroApplication : Application(), SingletonImageLoader.Factory {

    // Без VideoFrameDecoder Coil не умеет доставать превью-кадры из видеофайлов.
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()

    override fun onCreate() {
        super.onCreate()
        startKoin {
            modules(
                appModule(
                    settings = SakuroSettings(defaultEngine = EngineType.MEDIA3),
                    mediaLibrary = MediaStoreVideoLibrary(this@SakuroApplication),
                    // Media3 первым — он же fallback при недоступном предпочтении.
                    engineRegistry = EngineRegistry(
                        listOf(
                            Media3EngineFactory(this@SakuroApplication),
                            MpvEngineFactory(this@SakuroApplication),
                            FakeEngineFactory(),
                        ),
                    ),
                    deviceStatusMonitor = AndroidDeviceStatusMonitor(this@SakuroApplication),
                    // Имя файла отвечает мгновенно, анализ кадров подтягивается
                    // следом и замещает результат более уверенным.
                    contentClassifier = CompositeContentClassifier(
                        FilenameContentClassifier(),
                        FrameContentClassifier(RetrieverFrameSampler(this@SakuroApplication)),
                    ),
                ),
            )
        }
    }
}
