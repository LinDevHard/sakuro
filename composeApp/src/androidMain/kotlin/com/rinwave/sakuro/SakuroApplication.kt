package com.rinwave.sakuro

import android.app.Application
import com.rinwave.sakuro.core.media.MediaStoreVideoLibrary
import com.rinwave.sakuro.core.player.EngineRegistry
import com.rinwave.sakuro.core.player.EngineType
import com.rinwave.sakuro.core.settings.SakuroSettings
import com.rinwave.sakuro.di.appModule
import com.rinwave.sakuro.engine.fake.FakeEngineFactory
import com.rinwave.sakuro.engine.media3.Media3EngineFactory
import org.koin.core.context.startKoin

class SakuroApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            modules(
                appModule(
                    settings = SakuroSettings(defaultEngine = EngineType.MEDIA3),
                    mediaLibrary = MediaStoreVideoLibrary(this@SakuroApplication),
                    // Media3 — основной движок Android; libmpv добавится своим модулем engine-mpv.
                    engineRegistry = EngineRegistry(
                        listOf(
                            Media3EngineFactory(this@SakuroApplication),
                            FakeEngineFactory(),
                        ),
                    ),
                ),
            )
        }
    }
}
