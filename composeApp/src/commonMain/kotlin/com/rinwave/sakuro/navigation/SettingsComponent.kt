package com.rinwave.sakuro.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.DelicateDecomposeApi
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.push
import com.arkivanov.decompose.value.Value
import com.rinwave.sakuro.AppInfo
import com.rinwave.sakuro.core.player.EngineRegistry
import com.rinwave.sakuro.core.settings.SakuroSettings
import com.rinwave.sakuro.core.upscale.PresetStores
import kotlinx.serialization.Serializable

/**
 * Parent of the settings feature. The root is a menu that lists sections; tapping a
 * section pushes its own screen onto an inner stack (drill-down navigation). Each
 * section is a dedicated Decompose child with its own state and lifecycle.
 */
class SettingsComponent(
    componentContext: ComponentContext,
    private val settings: SakuroSettings,
    private val engineRegistry: EngineRegistry,
    private val presetStores: PresetStores,
    val onBack: () -> Unit,
) : ComponentContext by componentContext {

    @Serializable
    private sealed interface Config {
        @Serializable
        data object Menu : Config

        @Serializable
        data object Playback : Config

        @Serializable
        data object Upscale : Config

        @Serializable
        data object Controls : Config

        @Serializable
        data object Advanced : Config

        @Serializable
        data object About : Config
    }

    sealed interface Child {
        class Menu(val component: SettingsMenuComponent) : Child
        class Playback(val component: PlaybackSettingsComponent) : Child
        class Upscale(val component: UpscaleSettingsComponent) : Child
        class Controls(val component: ControlsSettingsComponent) : Child
        class Advanced(val component: AdvancedSettingsComponent) : Child
        class About(val component: AboutSettingsComponent) : Child
    }

    private val navigation = StackNavigation<Config>()

    val stack: Value<ChildStack<*, Child>> = childStack(
        source = navigation,
        serializer = Config.serializer(),
        initialConfiguration = Config.Menu,
        handleBackButton = true,
        childFactory = ::createChild,
    )

    @OptIn(DelicateDecomposeApi::class)
    private fun createChild(config: Config, componentContext: ComponentContext): Child = when (config) {
        is Config.Menu -> Child.Menu(
            SettingsMenuComponent(
                componentContext = componentContext,
                version = AppInfo.VERSION,
                onBack = onBack,
                onOpenPlayback = { navigation.push(Config.Playback) },
                onOpenUpscale = { navigation.push(Config.Upscale) },
                onOpenControls = { navigation.push(Config.Controls) },
                onOpenAdvanced = { navigation.push(Config.Advanced) },
                onOpenAbout = { navigation.push(Config.About) },
            ),
        )

        is Config.Playback -> Child.Playback(PlaybackSettingsComponent(componentContext, settings, engineRegistry))
        is Config.Upscale -> Child.Upscale(UpscaleSettingsComponent(componentContext, settings, presetStores))
        is Config.Controls -> Child.Controls(ControlsSettingsComponent(componentContext, settings))
        is Config.Advanced -> Child.Advanced(AdvancedSettingsComponent(componentContext, settings))
        is Config.About -> Child.About(AboutSettingsComponent(componentContext))
    }

    fun pop() {
        navigation.pop()
    }
}
