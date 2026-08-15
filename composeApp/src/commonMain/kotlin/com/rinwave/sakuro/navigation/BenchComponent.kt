package com.rinwave.sakuro.navigation

import com.arkivanov.decompose.ComponentContext
import com.rinwave.sakuro.bench.BenchConfigItem
import com.rinwave.sakuro.core.player.EngineRegistry
import com.rinwave.sakuro.core.player.EngineType
import com.rinwave.sakuro.core.player.MediaSource
import com.rinwave.sakuro.core.upscale.BuiltInPresets
import com.rinwave.sakuro.core.upscale.PresetStores
import com.rinwave.sakuro.core.upscale.UpscaleProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * On-device benchmark setup: video, engines and presets to iterate. The run
 * itself lives in the platform `BenchRunner`; this component only assembles
 * the configuration matrix.
 */
class BenchComponent(
    componentContext: ComponentContext,
    val engineRegistry: EngineRegistry,
    presetStores: PresetStores,
    val onBack: () -> Unit,
) : ComponentContext by componentContext {

    private val scope = componentScope()

    /** Engines worth benchmarking on this build (the fake engine renders nothing). */
    val engines: List<EngineType> = engineRegistry.available.filterNot { it == EngineType.FAKE }

    /** Built-in + user presets, same list the default-preset picker shows. */
    val presets: StateFlow<List<UpscaleProfile>> = presetStores.user.presets
        .map { user -> BuiltInPresets.all + user }
        .stateIn(scope, SharingStarted.Eagerly, BuiltInPresets.all + presetStores.user.presets.value)

    private val _video = MutableStateFlow<MediaSource?>(null)
    val video: StateFlow<MediaSource?> = _video

    private val _selectedEngines = MutableStateFlow(engines.toSet())
    val selectedEngines: StateFlow<Set<EngineType>> = _selectedEngines

    /** Off is the mandatory baseline for the report; the rest of the built-ins start selected. */
    private val _selectedPresets = MutableStateFlow(BuiltInPresets.all.map { it.id }.toSet())
    val selectedPresets: StateFlow<Set<String>> = _selectedPresets

    fun setVideo(uri: String, title: String) {
        _video.value = MediaSource(uri, title)
    }

    fun toggleEngine(engine: EngineType) {
        _selectedEngines.update { if (engine in it) it - engine else it + engine }
    }

    fun togglePreset(id: String) {
        if (id == BuiltInPresets.OFF.id) return // the baseline stays
        _selectedPresets.update { if (id in it) it - id else it + id }
    }

    /** The run matrix: engines × presets, baseline Off first within each engine. */
    fun buildConfigs(): List<BenchConfigItem> {
        val chosen = presets.value.filter { it.id in _selectedPresets.value }
        return _selectedEngines.value
            .sortedBy { engines.indexOf(it) }
            .flatMap { engine -> chosen.map { BenchConfigItem(engine, it) } }
    }
}
