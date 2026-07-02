package com.rinwave.sakuro

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.extensions.compose.lifecycle.LifecycleController
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.rinwave.sakuro.core.media.SampleVideoLibrary
import com.rinwave.sakuro.core.player.EngineRegistry
import com.rinwave.sakuro.core.player.EngineType
import com.rinwave.sakuro.core.settings.SakuroSettings
import com.rinwave.sakuro.di.AppDependencies
import com.rinwave.sakuro.di.appModule
import com.rinwave.sakuro.engine.fake.FakeEngineFactory
import com.rinwave.sakuro.navigation.RootComponent
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

/**
 * Desktop-таргет — полигон для быстрой итерации по UI (ARCHITECTURE.md §3.2):
 * реального воспроизведения нет, движок — FakePlayerEngine.
 */
fun main() {
    startKoin {
        modules(
            appModule(
                settings = SakuroSettings(defaultEngine = EngineType.FAKE),
                mediaLibrary = SampleVideoLibrary(),
                engineRegistry = EngineRegistry(listOf(FakeEngineFactory())),
            ),
        )
    }

    val lifecycle = LifecycleRegistry()
    val root = RootComponent(
        componentContext = DefaultComponentContext(lifecycle),
        deps = GlobalContext.get().get<AppDependencies>(),
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
