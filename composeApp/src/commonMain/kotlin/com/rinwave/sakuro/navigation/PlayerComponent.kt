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

    /** Фактический движок (выбор пользователя, если доступен на таргете). */
    val activeEngineType: EngineType = engineRegistry.resolve(settings.engineType.value)

    val engine: PlayerEngine = engineRegistry.create(settings.engineType.value)

    /** «Авто» + встроенные + пользовательские пресеты (FEATURES.md §2.2). */
    val presets: StateFlow<List<UpscaleProfile>> = userPresets.presets
        .map { user -> listOf(AUTO_PRESET) + BuiltInPresets.all + user }
        .stateIn(scope, SharingStarted.Eagerly, listOf(AUTO_PRESET) + BuiltInPresets.all + userPresets.presets.value)

    val debugOverlay: StateFlow<Boolean> = settings.debugOverlay

    /** Включена ли адаптивная деградация пресета (для индикации в debug-оверлее). */
    val adaptiveEnabled: StateFlow<Boolean> = settings.adaptiveEnabled

    /** Свайпы/пинч в плеере (FEATURES.md §3.2). */
    val gesturesEnabled: StateFlow<Boolean> = settings.gesturesEnabled

    /** Множитель чувствительности свайпов (FEATURES.md §3.2). */
    val gestureSensitivity: StateFlow<Float> = settings.gestureSensitivity

    /** Выбор пользователя (включая «auto»); фактически применённая цепочка может отличаться. */
    private val _selectedPresetId = MutableStateFlow(initialPresetId())
    val selectedPresetId: StateFlow<String> = _selectedPresetId.asStateFlow()

    /** Пресет закреплён за этим файлом (FEATURES.md §1.3): выбор в шторке меняет пин, а не общий дефолт. */
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
        // В PiP активити «на паузе», но видео должно продолжать играть.
        lifecycle.doOnPause { if (!isInPipNow()) engine.pause() }
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
                userPresets.presets,
            ) { stats, device, selectedId, detection, _ ->
                AdaptiveInputs(resolveUserProfile(selectedId, detection), device, healthTracker.update(stats))
            }.combine(settings.adaptiveEnabled) { inputs, adaptive ->
                if (adaptive) {
                    adaptiveController.update(inputs.user, inputs.device, inputs.health)
                } else {
                    // Тумблер выключен — применяем выбранный пресет без деградации
                    // (чистота замеров качества). Контроллер сбрасываем, чтобы при
                    // повторном включении стрик/уровень стартовали заново.
                    adaptiveController.reset()
                    AdaptiveController.Decision(effective = inputs.user, level = 0, reason = null)
                }
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
            else -> BuiltInPresets.byId(selectedId) ?: userPresets.byId(selectedId) ?: BuiltInPresets.OFF
        }

    /** Закреплённый за файлом пресет приоритетнее общего дефолта; битый пин снимается. */
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

    /** Снятие пина не трогает текущий выбор — общий дефолт вернётся при следующем открытии. */
    fun togglePinned() {
        if (isPinned.value) {
            pinnedPresets.unpin(media.uri)
        } else {
            pinnedPresets.pin(media.uri, _selectedPresetId.value)
        }
    }

    /**
     * Вход/выход PiP: GL-конвейер videoEffects в Media3 привязан к размеру
     * surface на момент prepare, после ресайза окна кадр рисуется со старой
     * геометрией — перезапускаем цепочку (быстрый re-prepare с той же позиции).
     */
    fun onPipModeChanged() {
        engine.applyUpscale(appliedProfile)
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

/** Снимок входов адаптивного контура — чтобы соединить с тумблером adaptiveEnabled. */
private data class AdaptiveInputs(
    val user: UpscaleProfile,
    val device: DeviceStatus,
    val health: PlaybackHealth,
)
