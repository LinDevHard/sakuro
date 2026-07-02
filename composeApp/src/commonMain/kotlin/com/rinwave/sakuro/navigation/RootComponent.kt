package com.rinwave.sakuro.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.push
import com.arkivanov.decompose.value.Value
import com.rinwave.sakuro.core.player.MediaSource
import com.rinwave.sakuro.di.AppDependencies
import kotlinx.serialization.Serializable

/** Корень дерева компонентов Decompose: Library → Player / Settings. */
class RootComponent(
    componentContext: ComponentContext,
    private val deps: AppDependencies,
) : ComponentContext by componentContext {

    @Serializable
    private sealed interface Config {
        @Serializable
        data object Library : Config

        @Serializable
        data class Player(val uri: String, val title: String) : Config

        @Serializable
        data object Settings : Config
    }

    sealed interface Child {
        class Library(val component: LibraryComponent) : Child
        class Player(val component: PlayerComponent) : Child
        class Settings(val component: SettingsComponent) : Child
    }

    private val navigation = StackNavigation<Config>()

    val stack: Value<ChildStack<*, Child>> = childStack(
        source = navigation,
        serializer = Config.serializer(),
        initialConfiguration = Config.Library,
        handleBackButton = true,
        childFactory = ::createChild,
    )

    private fun createChild(config: Config, componentContext: ComponentContext): Child = when (config) {
        is Config.Library -> Child.Library(
            LibraryComponent(
                componentContext = componentContext,
                mediaLibrary = deps.mediaLibrary,
                onOpenVideo = { uri, title -> navigation.push(Config.Player(uri, title)) },
                onOpenSettings = { navigation.push(Config.Settings) },
            ),
        )

        is Config.Player -> Child.Player(
            PlayerComponent(
                componentContext = componentContext,
                media = MediaSource(uri = config.uri, title = config.title),
                settings = deps.settings,
                engineRegistry = deps.engineRegistry,
                onFinished = navigation::pop,
            ),
        )

        is Config.Settings -> Child.Settings(
            SettingsComponent(
                componentContext = componentContext,
                settings = deps.settings,
                engineRegistry = deps.engineRegistry,
                onBack = navigation::pop,
            ),
        )
    }
}
