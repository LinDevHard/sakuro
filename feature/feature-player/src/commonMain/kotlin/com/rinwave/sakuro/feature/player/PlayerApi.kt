package com.rinwave.sakuro.feature.player

import com.arkivanov.decompose.ComponentContext
import com.rinwave.sakuro.core.detect.ContentClassifier
import com.rinwave.sakuro.core.media.MediaLibrary
import com.rinwave.sakuro.core.media.PlaybackHistoryStore
import com.rinwave.sakuro.core.model.MediaSource
import com.rinwave.sakuro.core.player.EngineRegistry
import com.rinwave.sakuro.core.settings.SakuroSettings
import com.rinwave.sakuro.core.upscale.DeviceStatusMonitor
import com.rinwave.sakuro.core.upscale.PresetStores
import com.rinwave.sakuro.core.upscale.ShaderInspector
import com.rinwave.sakuro.core.upscale.UserShaderStore
import org.koin.core.module.Module
import org.koin.dsl.module

/** What the player asks its host to do; the host owns navigation. */
public sealed interface PlayerOutput {
    /** Open another video (next/previous in folder) in place of the current one. */
    public data class OpenVideo(val uri: String, val title: String) : PlayerOutput

    /** Playback is done or the user left the player. */
    public data object Finished : PlayerOutput
}

/** Creates [PlayerComponent]s; the only way to build one outside this module. */
public fun interface PlayerComponentFactory {
    public fun create(
        context: ComponentContext,
        media: MediaSource,
        onOutput: (PlayerOutput) -> Unit,
    ): PlayerComponent
}

/** Everything a player session reads; grouped so the factory signature stays stable. */
@Suppress("LongParameterList") // a parameter object: grouping these is its whole purpose
public class PlayerDependencies(
    internal val settings: SakuroSettings,
    internal val engineRegistry: EngineRegistry,
    internal val deviceStatusMonitor: DeviceStatusMonitor,
    internal val contentClassifier: ContentClassifier,
    internal val presetStores: PresetStores,
    internal val userShaderStore: UserShaderStore,
    internal val shaderInspector: ShaderInspector,
    internal val history: PlaybackHistoryStore,
    internal val mediaLibrary: MediaLibrary,
)

public fun PlayerComponentFactory(deps: PlayerDependencies): PlayerComponentFactory =
    PlayerComponentFactory { context, media, onOutput ->
        PlayerComponent(
            componentContext = context,
            media = media,
            settings = deps.settings,
            engineRegistry = deps.engineRegistry,
            deviceStatusMonitor = deps.deviceStatusMonitor,
            contentClassifier = deps.contentClassifier,
            presetStores = deps.presetStores,
            userShaderStore = deps.userShaderStore,
            shaderInspector = deps.shaderInspector,
            history = deps.history,
            mediaLibrary = deps.mediaLibrary,
            onOutput = onOutput,
        )
    }

/** Registers [PlayerComponentFactory]; platform modules provide engines, stores and monitors. */
public val playerFeatureModule: Module = module {
    single { PlayerDependencies(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single { PlayerComponentFactory(get<PlayerDependencies>()) }
}
