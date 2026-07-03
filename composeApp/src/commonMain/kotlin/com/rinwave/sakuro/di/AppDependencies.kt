package com.rinwave.sakuro.di

import com.rinwave.sakuro.core.detect.ContentClassifier
import com.rinwave.sakuro.core.media.MediaLibrary
import com.rinwave.sakuro.core.player.EngineRegistry
import com.rinwave.sakuro.core.settings.SakuroSettings
import com.rinwave.sakuro.core.upscale.DeviceStatusMonitor
import com.rinwave.sakuro.core.upscale.PinnedPresetStore
import com.rinwave.sakuro.core.upscale.PresetStores
import com.rinwave.sakuro.core.upscale.UserPresetStore
import org.koin.core.module.Module
import org.koin.dsl.module

/** Явный набор зависимостей приложения; собирается Koin-модулем платформы. */
class AppDependencies(
    val settings: SakuroSettings,
    val mediaLibrary: MediaLibrary,
    val engineRegistry: EngineRegistry,
    val deviceStatusMonitor: DeviceStatusMonitor,
    val contentClassifier: ContentClassifier,
    val presetStores: PresetStores,
)

/** Общая часть DI: платформа поставляет реестр движков, библиотеку и настройки. */
fun appModule(
    settings: SakuroSettings,
    mediaLibrary: MediaLibrary,
    engineRegistry: EngineRegistry,
    deviceStatusMonitor: DeviceStatusMonitor,
    contentClassifier: ContentClassifier,
): Module = module {
    single { settings }
    single { mediaLibrary }
    single { engineRegistry }
    single { deviceStatusMonitor }
    single { contentClassifier }
    single { PresetStores(user = UserPresetStore(), pinned = PinnedPresetStore()) }
    single { AppDependencies(get(), get(), get(), get(), get(), get()) }
}
