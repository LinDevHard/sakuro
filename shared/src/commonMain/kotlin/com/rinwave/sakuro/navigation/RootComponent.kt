package com.rinwave.sakuro.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.push
import com.arkivanov.decompose.router.stack.replaceCurrent
import com.arkivanov.decompose.value.Value
import com.rinwave.sakuro.core.model.MediaSource
import com.rinwave.sakuro.di.AppDependencies
import com.rinwave.sakuro.feature.library.LibraryComponent
import com.rinwave.sakuro.feature.library.LibraryComponentFactory
import com.rinwave.sakuro.feature.library.LibraryOutput
import com.rinwave.sakuro.feature.settings.SettingsComponent
import com.rinwave.sakuro.feature.settings.SettingsComponentFactory
import com.rinwave.sakuro.feature.settings.SettingsOutput
import kotlinx.serialization.Serializable

/** The Decompose navigation root: Library ↔ Player / Settings. */
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

        @Serializable
        data object Bench : Config
    }

    sealed interface Child {
        class Library(val component: LibraryComponent) : Child
        class Player(val component: PlayerComponent) : Child
        class Settings(val component: SettingsComponent) : Child
        class Bench(val component: BenchComponent) : Child
    }

    private val navigation = StackNavigation<Config>()

    private val settingsFactory = SettingsComponentFactory(
        deps.settings,
        deps.engineRegistry,
        deps.presetStores,
        deps.userShaderStore,
        deps.shaderInspector,
    )

    private val libraryFactory =
        LibraryComponentFactory(deps.mediaLibrary, deps.libraryPreferences, deps.playbackHistory)

    val stack: Value<ChildStack<*, Child>> = childStack(
        source = navigation,
        serializer = Config.serializer(),
        initialConfiguration = Config.Library,
        handleBackButton = true,
        childFactory = ::createChild,
    )

    /** Entry point shared by the library, external intents and folder navigation. */
    fun openVideo(uri: String, title: String) {
        val current = stack.value.active.instance
        if (current is Child.Player) {
            if (current.component.media.uri != uri) navigation.replaceCurrent(Config.Player(uri, title))
        } else {
            navigation.push(Config.Player(uri, title))
        }
    }

    private fun createChild(config: Config, componentContext: ComponentContext): Child = when (config) {
        is Config.Library -> Child.Library(
            libraryFactory.create(componentContext) { output ->
                when (output) {
                    is LibraryOutput.OpenVideo -> openVideo(output.uri, output.title)
                    LibraryOutput.OpenSettings -> navigation.push(Config.Settings)
                }
            },
        )

        is Config.Player -> Child.Player(
            PlayerComponent(
                componentContext = componentContext,
                media = MediaSource(uri = config.uri, title = config.title),
                settings = deps.settings,
                engineRegistry = deps.engineRegistry,
                deviceStatusMonitor = deps.deviceStatusMonitor,
                contentClassifier = deps.contentClassifier,
                presetStores = deps.presetStores,
                userShaderStore = deps.userShaderStore,
                shaderInspector = deps.shaderInspector,
                history = deps.playbackHistory,
                mediaLibrary = deps.mediaLibrary,
                onOpenVideo = ::openVideo,
                onFinished = navigation::pop,
            ),
        )

        is Config.Settings -> Child.Settings(
            settingsFactory.create(componentContext) { output ->
                when (output) {
                    SettingsOutput.Back -> navigation.pop()
                    SettingsOutput.OpenBench -> navigation.push(Config.Bench)
                }
            },
        )

        is Config.Bench -> Child.Bench(
            BenchComponent(
                componentContext = componentContext,
                engineRegistry = deps.engineRegistry,
                presetStores = deps.presetStores,
                userShaderStore = deps.userShaderStore,
                onBack = navigation::pop,
            ),
        )
    }
}
