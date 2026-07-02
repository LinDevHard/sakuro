package com.rinwave.sakuro.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.arkivanov.essenty.lifecycle.doOnPause
import com.rinwave.sakuro.core.player.EngineType
import com.rinwave.sakuro.core.player.MediaSource
import com.rinwave.sakuro.core.player.PlayerEngine
import com.rinwave.sakuro.core.player.EngineRegistry
import com.rinwave.sakuro.core.settings.SakuroSettings
import com.rinwave.sakuro.core.upscale.BuiltInPresets
import com.rinwave.sakuro.core.upscale.UpscaleProfile
import kotlinx.coroutines.flow.StateFlow

class PlayerComponent(
    componentContext: ComponentContext,
    val media: MediaSource,
    private val settings: SakuroSettings,
    engineRegistry: EngineRegistry,
    private val onFinished: () -> Unit,
) : ComponentContext by componentContext {

    /** Фактический движок (выбор пользователя, если доступен на таргете). */
    val activeEngineType: EngineType = engineRegistry.resolve(settings.engineType.value)

    val engine: PlayerEngine = engineRegistry.create(settings.engineType.value)

    val presets: List<UpscaleProfile> = BuiltInPresets.all

    val debugOverlay: StateFlow<Boolean> = settings.debugOverlay

    init {
        engine.applyUpscale(BuiltInPresets.byId(settings.presetId.value) ?: BuiltInPresets.OFF)
        engine.load(media)
        lifecycle.doOnPause { engine.pause() }
        lifecycle.doOnDestroy { engine.release() }
    }

    fun applyPreset(id: String) {
        val preset = BuiltInPresets.byId(id) ?: return
        engine.applyUpscale(preset)
        settings.setPresetId(id)
    }

    fun toggleDebugOverlay() = settings.setDebugOverlay(!settings.debugOverlay.value)

    fun onBack() = onFinished()
}
