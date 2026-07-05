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
import com.rinwave.sakuro.core.upscale.DeviceStatus
import com.rinwave.sakuro.core.upscale.DeviceStatusMonitor
import com.rinwave.sakuro.core.upscale.PlaybackHealth
import com.rinwave.sakuro.core.upscale.PresetStores
import com.rinwave.sakuro.core.upscale.UpscaleProfile
import com.rinwave.sakuro.ui.isInPipNow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlayerComponent(
    componentContext: ComponentContext,
    val media: MediaSource,
    private val settings: SakuroSettings,
    engineRegistry: EngineRegistry,
    deviceStatusMonitor: DeviceStatusMonitor,
    contentClassifier: ContentClassifier,
    presetStores: PresetStores,
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

    /** Swipes/pinch in the player (FEATURES.md §3.2). */
    val gesturesEnabled: StateFlow<Boolean> = settings.gesturesEnabled

    /** Swipe sensitivity multiplier (FEATURES.md §3.2). */
    val gestureSensitivity: StateFlow<Float> = settings.gestureSensitivity

    /** The user's choice (including "auto"); the actually applied chain may differ. */
    private val _selectedPresetId = MutableStateFlow(initialPresetId())
    val selectedPresetId: StateFlow<String> = _selectedPresetId.asStateFlow()

    /** The preset is pinned to this file (FEATURES.md §1.3): the sheet's choice changes the pin, not the global default. */
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

    init {
        appliedProfile = resolveUserProfile(_selectedPresetId.value, _detection.value)
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
                _selectedPresetId,
                _detection,
                userPresets.presets,
            ) { stats, device, selectedId, detection, _ ->
                AdaptiveInputs(resolveUserProfile(selectedId, detection), device, healthTracker.update(stats))
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

    private fun resolveUserProfile(selectedId: String, detection: ContentDetection): UpscaleProfile =
        when (selectedId) {
            AUTO_PRESET_ID -> BuiltInPresets.forContentClass(detection.contentClass)
            else -> BuiltInPresets.byId(selectedId) ?: userPresets.byId(selectedId) ?: BuiltInPresets.OFF
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
        if (isPinned.value) pinnedPresets.pin(media.uri, id) else settings.setPresetId(id)
        _selectedPresetId.value = id
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
     * Entering/leaving PiP: the Media3 videoEffects GL pipeline is bound to the surface
     * size at prepare time; after a window resize the frame is drawn with the old
     * geometry — so we restart the chain (a fast re-prepare from the same position).
     */
    fun onPipModeChanged() {
        engine.applyUpscale(appliedProfile)
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
