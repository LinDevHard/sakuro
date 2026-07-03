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
import com.rinwave.sakuro.core.upscale.DeviceStatusMonitor
import com.rinwave.sakuro.core.upscale.UpscaleProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class PlayerComponent(
    componentContext: ComponentContext,
    val media: MediaSource,
    private val settings: SakuroSettings,
    engineRegistry: EngineRegistry,
    deviceStatusMonitor: DeviceStatusMonitor,
    contentClassifier: ContentClassifier,
    private val onFinished: () -> Unit,
) : ComponentContext by componentContext {

    private val scope = componentScope()

    /** Фактический движок (выбор пользователя, если доступен на таргете). */
    val activeEngineType: EngineType = engineRegistry.resolve(settings.engineType.value)

    val engine: PlayerEngine = engineRegistry.create(settings.engineType.value)

    /** «Авто» + встроенные пресеты. */
    val presets: List<UpscaleProfile> = listOf(AUTO_PRESET) + BuiltInPresets.all

    val debugOverlay: StateFlow<Boolean> = settings.debugOverlay

    /** Свайпы/пинч в плеере (FEATURES.md §3.2). */
    val gesturesEnabled: StateFlow<Boolean> = settings.gesturesEnabled

    /** Выбор пользователя (включая «auto»); фактически применённая цепочка может отличаться. */
    private val _selectedPresetId = MutableStateFlow(settings.presetId.value)
    val selectedPresetId: StateFlow<String> = _selectedPresetId.asStateFlow()

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
        lifecycle.doOnPause { engine.pause() }
        lifecycle.doOnDestroy { engine.release() }

        scope.launch {
            contentClassifier.classify(ClassificationRequest(uri = media.uri, title = media.title))
                .collect { _detection.value = it }
        }
        // Адаптивный контур (ARCHITECTURE.md §5): решение пересчитывается на каждом
        // снимке debug-статистики и при смене пресета/класса/состояния устройства.
        scope.launch {
            combine(
                engine.debugStats,
                deviceStatusMonitor.status,
                _selectedPresetId,
                _detection,
            ) { stats, device, selectedId, detection ->
                adaptiveController.update(
                    user = resolveUserProfile(selectedId, detection),
                    device = device,
                    health = healthTracker.update(stats),
                )
            }.collect { decision ->
                _adaptiveDecision.value = decision
                // applyUpscale на подготовленном плеере = re-prepare,
                // поэтому дёргаем движок только при реальной смене цепочки.
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
            else -> BuiltInPresets.byId(selectedId) ?: BuiltInPresets.OFF
        }

    fun applyPreset(id: String) {
        if (id != AUTO_PRESET_ID && BuiltInPresets.byId(id) == null) return
        settings.setPresetId(id)
        _selectedPresetId.value = id
    }

    fun toggleDebugOverlay() = settings.setDebugOverlay(!settings.debugOverlay.value)

    fun onBack() = onFinished()

    companion object {
        const val AUTO_PRESET_ID = "auto"

        /** Псевдо-пресет: реальная цепочка выбирается по классу контента (core-detect). */
        val AUTO_PRESET = UpscaleProfile(
            id = AUTO_PRESET_ID,
            name = "Авто",
            description = "Пресет подбирается по классу контента",
            builtIn = true,
        )
    }
}
