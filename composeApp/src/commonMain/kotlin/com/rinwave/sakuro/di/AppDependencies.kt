package com.rinwave.sakuro.di

import com.rinwave.sakuro.core.media.MediaLibrary
import com.rinwave.sakuro.core.player.EngineRegistry
import com.rinwave.sakuro.core.settings.SakuroSettings
import org.koin.core.module.Module
import org.koin.dsl.module

/** Явный набор зависимостей приложения; собирается Koin-модулем платформы. */
class AppDependencies(
    val settings: SakuroSettings,
    val mediaLibrary: MediaLibrary,
    val engineRegistry: EngineRegistry,
)

/** Общая часть DI: платформа поставляет реестр движков, библиотеку и настройки. */
fun appModule(
    settings: SakuroSettings,
    mediaLibrary: MediaLibrary,
    engineRegistry: EngineRegistry,
): Module = module {
    single { settings }
    single { mediaLibrary }
    single { engineRegistry }
    single { AppDependencies(get(), get(), get()) }
}
