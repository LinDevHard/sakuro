package com.rinwave.sakuro.navigation

import com.arkivanov.decompose.ComponentContext
import com.rinwave.sakuro.bench.BenchConfigItem
import com.rinwave.sakuro.core.player.EngineRegistry
import com.rinwave.sakuro.core.player.EngineType
import com.rinwave.sakuro.core.player.MediaSource
import com.rinwave.sakuro.core.upscale.BuiltInPresets
import com.rinwave.sakuro.core.upscale.BundledShader
import com.rinwave.sakuro.core.upscale.BundledShaders
import com.rinwave.sakuro.core.upscale.PresetStores
import com.rinwave.sakuro.core.upscale.UpscaleProfile
import com.rinwave.sakuro.core.upscale.UserShaderStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** One shader the bench can measure on its own, as a single-shader chain. */
data class BenchShaderOption(
    /** Chain file name — what a profile's `shaderChain` holds. */
    val fileName: String,
    /** Registry entry when the shader is vendored; null — an imported file. */
    val bundled: BundledShader?,
)

/**
 * On-device benchmark setup: the video, and the matrix of engines × things to
 * measure. Two kinds of subject can be measured side by side:
 * presets (their passes and any chain they carry) and individual shaders, each
 * benched as a one-shader chain so the registry can be swept without building
 * a preset for every entry by hand.
 *
 * The run itself lives in the platform `BenchRunner`; this component only
 * assembles the configuration list.
 */
class BenchComponent(
    componentContext: ComponentContext,
    val engineRegistry: EngineRegistry,
    presetStores: PresetStores,
    userShaderStore: UserShaderStore,
    val onBack: () -> Unit,
) : ComponentContext by componentContext {

    private val scope = componentScope()

    /** Engines worth benchmarking on this build (the fake engine renders nothing). */
    val engines: List<EngineType> = engineRegistry.available.filterNot { it == EngineType.FAKE }

    /** Built-in + user presets, same list the default-preset picker shows. */
    val presets: StateFlow<List<UpscaleProfile>> = presetStores.user.presets
        .map { user -> BuiltInPresets.all + user }
        .stateIn(scope, SharingStarted.Eagerly, BuiltInPresets.all + presetStores.user.presets.value)

    /** Imported shaders first (an import shadows a bundled name), then the registry, in its order. */
    val shaders: StateFlow<List<BenchShaderOption>> = userShaderStore.shaders
        .map { imported -> shaderOptions(imported) }
        .stateIn(scope, SharingStarted.Eagerly, shaderOptions(userShaderStore.shaders.value))

    private val _video = MutableStateFlow<MediaSource?>(null)
    val video: StateFlow<MediaSource?> = _video

    private val _selectedEngines = MutableStateFlow(engines.toSet())
    val selectedEngines: StateFlow<Set<EngineType>> = _selectedEngines

    /** Off is the mandatory baseline for the report; the rest of the built-ins start selected. */
    private val _selectedPresets = MutableStateFlow(BuiltInPresets.all.map { it.id }.toSet())
    val selectedPresets: StateFlow<Set<String>> = _selectedPresets

    /** Shaders start unselected: sweeping the whole registry is opt-in, it is a long run. */
    private val _selectedShaders = MutableStateFlow(emptySet<String>())
    val selectedShaders: StateFlow<Set<String>> = _selectedShaders

    /** True when every available shader is selected — drives the "all shaders" toggle. */
    val allShadersSelected: StateFlow<Boolean> = combine(shaders, selectedShaders) { all, selected ->
        all.isNotEmpty() && selected.containsAll(all.map { it.fileName })
    }.stateIn(scope, SharingStarted.Eagerly, false)

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

    fun toggleShader(fileName: String) {
        _selectedShaders.update { if (fileName in it) it - fileName else it + fileName }
    }

    /** Selects every shader for a full sweep, or clears the selection. */
    fun toggleAllShaders() {
        val all = shaders.value.map { it.fileName }.toSet()
        _selectedShaders.value = if (_selectedShaders.value.containsAll(all)) emptySet() else all
    }

    /**
     * The run matrix: for each selected engine, the selected presets (baseline
     * Off first) followed by the selected shaders in list order.
     */
    fun buildConfigs(): List<BenchConfigItem> {
        val chosenPresets = presets.value.filter { it.id in _selectedPresets.value }
        val chosenShaders = shaders.value
            .filter { it.fileName in _selectedShaders.value }
            .map { it.asProfile() }
        val subjects = chosenPresets + chosenShaders
        return _selectedEngines.value
            .sortedBy { engines.indexOf(it) }
            .flatMap { engine -> subjects.map { BenchConfigItem(engine, it) } }
    }

    private fun shaderOptions(imported: List<String>): List<BenchShaderOption> =
        imported.map { BenchShaderOption(it, bundled = null) } +
            BundledShaders.all
                .filterNot { it.fileName in imported }
                .map { BenchShaderOption(it.fileName, bundled = it) }
}

/** A synthetic profile that runs just this shader, so the bench can measure it alone. */
internal fun BenchShaderOption.asProfile(): UpscaleProfile = UpscaleProfile(
    id = "shader-${fileName.substringBeforeLast('.')}",
    name = bundled?.displayName ?: fileName,
    shaderChain = listOf(fileName),
)
