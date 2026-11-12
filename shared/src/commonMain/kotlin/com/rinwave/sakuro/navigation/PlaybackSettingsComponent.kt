package com.rinwave.sakuro.navigation

import com.arkivanov.decompose.ComponentContext
import com.rinwave.sakuro.core.player.EngineRegistry
import com.rinwave.sakuro.core.player.EngineType
import com.rinwave.sakuro.core.player.SubtitlePreferences
import com.rinwave.sakuro.core.settings.SakuroSettings
import kotlinx.coroutines.flow.StateFlow

/** Playback tab: which engine drives decoding and rendering. */
class PlaybackSettingsComponent(
    componentContext: ComponentContext,
    private val settings: SakuroSettings,
    engineRegistry: EngineRegistry,
) : ComponentContext by componentContext {

    val availableEngines: List<EngineType> = engineRegistry.available
    val engineType: StateFlow<EngineType> = settings.engineType

    val subtitlePreferences = settings.subtitlePreferences

    fun setSubtitlePreferences(value: SubtitlePreferences) = settings.setSubtitlePreferences(value)

    fun selectEngine(type: EngineType) = settings.setEngineType(type)
}
