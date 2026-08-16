package com.rinwave.sakuro.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.arkivanov.essenty.lifecycle.doOnPause
import com.rinwave.sakuro.core.detect.ClassificationRequest
import com.rinwave.sakuro.core.detect.ContentClassifier
import com.rinwave.sakuro.core.detect.ContentDetection
import com.rinwave.sakuro.core.player.EngineRegistry
import com.rinwave.sakuro.core.player.EngineType
import com.rinwave.sakuro.core.player.MediaSource
import com.rinwave.sakuro.core.player.PlaybackHealthTracker
import com.rinwave.sakuro.core.player.PlayerEngine
import com.rinwave.sakuro.core.settings.SakuroSettings
import com.rinwave.sakuro.core.upscale.AdaptiveController
import com.rinwave.sakuro.core.upscale.BuiltInPresets
import com.rinwave.sakuro.core.upscale.BundledShaders
import com.rinwave.sakuro.core.upscale.DeviceStatus
import com.rinwave.sakuro.core.upscale.DeviceStatusMonitor
import com.rinwave.sakuro.core.upscale.PlaybackHealth
import com.rinwave.sakuro.core.upscale.PresetStores
import com.rinwave.sakuro.core.upscale.ShaderInspector
import com.rinwave.sakuro.core.upscale.UpscaleProfile
import com.rinwave.sakuro.core.upscale.UserShaderStore
import com.rinwave.sakuro.ui.isInPipNow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Suppress("LongParameterList")
class PlayerComponent(
    componentContext: ComponentContext,
    val media: MediaSource,
    private val settings: SakuroSettings,
    engineRegistry: EngineRegistry,
    deviceStatusMonitor: DeviceStatusMonitor,
    contentClassifier: ContentClassifier,
    presetStores: PresetStores,
    userShaderStore: UserShaderStore,
    val shaderInspector: ShaderInspector,
    private val onFinished: () -> Unit,
) : ComponentContext by componentContext {

    private val userPresets = presetStores.user
    private val pinnedPresets = presetStores.pinned

    private val scope = componentScope()

    /** The actual engine (the user's choice if available on the target). */
    val activeEngineType: EngineType = engineRegistry.resolve(settings.engineType.value)

    val engine: PlayerEngine = engineRegistry.create(settings.engineType.value)

    /** "Auto" + built-in + user presets (FEATURES.md §2.2). */
    val presets: StateFlow<List<UpscaleProfile>> = userPresets.presets
        .map { user -> listOf(AUTO_PRESET) + BuiltInPresets.all + user }
        .stateIn(scope, SharingStarted.Eagerly, listOf(AUTO_PRESET) + BuiltInPresets.all + userPresets.presets.value)

    val debugOverlay: StateFlow<Boolean> = settings.debugOverlay

    /** Whether adaptive preset degradation is enabled (for the debug overlay indicator). */
    val adaptiveEnabled: StateFlow<Boolean> = settings.adaptiveEnabled

    /** Master switch for swipes/pinch in the player (FEATURES.md §3.2). */
    val gesturesEnabled: StateFlow<Boolean> = settings.gesturesEnabled

    /** Per-gesture switches, honoured only while [gesturesEnabled] is on. */
    val gestureBrightness: StateFlow<Boolean> = settings.gestureBrightness
    val gestureVolume: StateFlow<Boolean> = settings.gestureVolume
    val gestureSeek: StateFlow<Boolean> = settings.gestureSeek
    val gestureZoom: StateFlow<Boolean> = settings.gestureZoom

    /** Swipe sensitivity multiplier (FEATURES.md §3.2). */
    val gestureSensitivity: StateFlow<Float> = settings.gestureSensitivity

    /** The user's choice (including "auto"); the actually applied chain may differ. */
    private val _selectedPresetId = MutableStateFlow(initialPresetId())
    val selectedPresetId: StateFlow<String> = _selectedPresetId.asStateFlow()

    /**
     * A shader applied straight from the player, without saving a preset:
     * the chain is a single file and its `//!PARAM` values can be moved while
     * the video plays. Null — the selected preset is in charge.
     */
    private val _liveShader = MutableStateFlow<LiveShader?>(null)
    val liveShader: StateFlow<LiveShader?> = _liveShader.asStateFlow()

    /** What the user picked: a live shader wins over the preset while it is set. */
    private data class Selection(val presetId: String, val live: LiveShader?)

    private val selection = combine(_selectedPresetId, _liveShader) { id, live -> Selection(id, live) }

    /** Shaders that can be applied live: imported files plus the bundled registry. */
    val shaderFiles: StateFlow<List<String>> = userShaderStore.shaders
        .map { imported -> imported + BundledShaders.all.map { it.fileName }.filterNot { it in imported } }
        .stateIn(
            scope,
            SharingStarted.Eagerly,
            userShaderStore.shaders.value +
                BundledShaders.all.map { it.fileName }.filterNot { it in userShaderStore.shaders.value },
        )

    /**
     * The preset is pinned to this file (FEATURES.md §1.3): the sheet's choice changes the pin,
     * not the global default.
     */
    val isPinned: StateFlow<Boolean> = pinnedPresets.pins
        .map { media.uri in it }
        .stateIn(scope, SharingStarted.Eagerly, media.uri in pinnedPresets.pins.value)

    private val _detection = MutableStateFlow(ContentDetection.UNKNOWN)
    val detection: StateFlow<ContentDetection> = _detection.asStateFlow()

    private val _adaptiveDecision = MutableStateFlow<AdaptiveController.Decision?>(null)
    val adaptiveDecision: StateFlow<AdaptiveController.Decision?> = _adaptiveDecision.asStateFlow()

    private val adaptiveController = AdaptiveController()
    private val healthTracker = PlaybackHealthTracker()
    private var appliedProfile: UpscaleProfile
    private var viewportWidth = 0
    private var viewportHeight = 0

    init {
        appliedProfile = resolveUserProfile(Selection(_selectedPresetId.value, null), _detection.value)
        engine.applyUpscale(appliedProfile)
        engine.load(media)
        // In PiP the activity is "paused", but the video must keep playing.
        lifecycle.doOnPause { if (!isInPipNow()) engine.pause() }
        lifecycle.doOnDestroy { engine.release() }

        scope.launch {
            contentClassifier.classify(ClassificationRequest(uri = media.uri, title = media.title))
                .collect { _detection.value = it }
        }
        // The adaptive loop (ARCHITECTURE.md §5): the decision is recomputed on every
        // debug-stats snapshot and on any change of preset/class/device state.
        scope.launch {
            combine(
                engine.debugStats,
                deviceStatusMonitor.status,
                selection,
                _detection,
                userPresets.presets,
            ) { stats, device, current, detection, _ ->
                AdaptiveInputs(resolveUserProfile(current, detection), device, healthTracker.update(stats))
            }.combine(settings.adaptiveEnabled) { inputs, adaptive ->
                if (adaptive) {
                    adaptiveController.update(inputs.user, inputs.device, inputs.health)
                } else {
                    // The toggle is off — apply the selected preset without degradation
                    // (clean quality measurements). We reset the controller so that on
                    // re-enable the streak/level start over.
                    adaptiveController.reset()
                    AdaptiveController.Decision(effective = inputs.user, level = 0, reason = null)
                }
            }.collect { decision ->
                _adaptiveDecision.value = decision
                // applyUpscale on a prepared player = re-prepare,
                // so we poke the engine only on a real chain change.
                if (decision.effective != appliedProfile) {
                    appliedProfile = decision.effective
                    engine.applyUpscale(decision.effective)
                }
            }
        }
    }

    private fun resolveUserProfile(selection: Selection, detection: ContentDetection): UpscaleProfile {
        selection.live?.let { return it.toProfile() }
        return when (val selectedId = selection.presetId) {
            AUTO_PRESET_ID -> BuiltInPresets.forContentClass(detection.contentClass)
            else -> BuiltInPresets.byId(selectedId) ?: userPresets.byId(selectedId) ?: BuiltInPresets.OFF
        }
    }

    /** A file-pinned preset takes priority over the global default; a broken pin is cleared. */
    private fun initialPresetId(): String {
        val pinned = pinnedPresets.presetIdFor(media.uri) ?: return settings.presetId.value
        if (isKnownPreset(pinned)) return pinned
        pinnedPresets.unpin(media.uri)
        return settings.presetId.value
    }

    private fun isKnownPreset(id: String): Boolean =
        id == AUTO_PRESET_ID || BuiltInPresets.byId(id) != null || userPresets.byId(id) != null

    fun applyPreset(id: String) {
        if (!isKnownPreset(id)) return
        // Choosing a preset ends the live-shader experiment.
        _liveShader.value = null
        if (isPinned.value) pinnedPresets.pin(media.uri, id) else settings.setPresetId(id)
        _selectedPresetId.value = id
    }

    /**
     * Applies a single shader right away, without saving a preset. Null returns
     * control to the selected preset. Values start at the shader's defaults;
     * [setShaderParam] moves them while playing.
     */
    fun applyShader(fileName: String?) {
        _liveShader.value = fileName?.let { LiveShader(it) }
    }

    /** Live `//!PARAM` change; null restores that param's default. */
    fun setShaderParam(name: String, value: Float?) {
        _liveShader.update { current ->
            if (current == null) {
                null
            } else {
                val params = if (value == null) current.params - name else current.params + (name to value)
                current.copy(params = params)
            }
        }
    }

    /** Unpinning does not touch the current choice — the global default returns on next open. */
    fun togglePinned() {
        if (isPinned.value) {
            pinnedPresets.unpin(media.uri)
        } else {
            pinnedPresets.pin(media.uri, _selectedPresetId.value)
        }
    }

    /**
     * Entering/leaving PiP or rotating the player viewport: the Media3 videoEffects
     * GL pipeline is bound to the surface size at prepare time; after a window resize
     * the frame is drawn with the old geometry — so we restart the chain.
     */
    fun onPipModeChanged() {
        refreshMedia3OutputGeometry()
    }

    fun onViewportSizeChanged(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        if (width == viewportWidth && height == viewportHeight) return

        val hadViewport = viewportWidth > 0 && viewportHeight > 0
        viewportWidth = width
        viewportHeight = height

        if (hadViewport) {
            refreshMedia3OutputGeometry()
        }
    }

    private fun refreshMedia3OutputGeometry() {
        if (activeEngineType == EngineType.MEDIA3) {
            engine.applyUpscale(appliedProfile)
        }
    }

    fun toggleDebugOverlay() = settings.setDebugOverlay(!settings.debugOverlay.value)

    fun onBack() = onFinished()

    companion object {
        const val AUTO_PRESET_ID = "auto"

        /** A pseudo-preset: the real chain is chosen by content class (core-detect). */
        val AUTO_PRESET = UpscaleProfile(
            id = AUTO_PRESET_ID,
            name = "Auto",
            description = "The preset is chosen by content class",
            builtIn = true,
        )
    }
}

/** A snapshot of the adaptive loop's inputs — to combine with the adaptiveEnabled toggle. */
private data class AdaptiveInputs(
    val user: UpscaleProfile,
    val device: DeviceStatus,
    val health: PlaybackHealth,
)

/**
 * A shader applied straight from the player: one-file chain plus the
 * `//!PARAM` values being tuned. It never reaches the preset store — closing
 * the player forgets it.
 */
data class LiveShader(
    val fileName: String,
    val params: Map<String, Float> = emptyMap(),
) {
    fun toProfile(): UpscaleProfile = UpscaleProfile(
        id = "live-$fileName",
        name = BundledShaders.byFileName(fileName)?.displayName ?: fileName,
        shaderChain = listOf(fileName),
        shaderParams = if (params.isEmpty()) emptyMap() else mapOf(fileName to params),
    )
}
