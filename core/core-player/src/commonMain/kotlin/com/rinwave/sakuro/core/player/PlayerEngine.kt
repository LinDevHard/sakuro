package com.rinwave.sakuro.core.player

import com.rinwave.sakuro.core.upscale.UpscaleProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Единая абстракция движка воспроизведения (ARCHITECTURE.md §4).
 * UI работает только с этим интерфейсом и не знает, какой движок под ним.
 */
interface PlayerEngine {

    val state: StateFlow<PlayerState>

    /** «Stats for nerds» — данные для debug-оверлея (FEATURES.md §4). */
    val debugStats: Flow<DebugStats>

    fun load(media: MediaSource)

    fun play()

    fun pause()

    fun seekTo(positionMs: Long)

    fun setSpeed(speed: Float)

    fun selectTrack(track: TrackSelection)

    /**
     * Единая точка применения апскейла: libmpv грузит `.glsl`-цепочку,
     * Media3 собирает список `GlEffect` и зовёт `setVideoEffects()`.
     */
    fun applyUpscale(profile: UpscaleProfile)

    fun release()
}

enum class EngineType {
    MPV,
    MEDIA3,
    FAKE,
}

val EngineType.displayName: String
    get() = when (this) {
        EngineType.MPV -> "libmpv"
        EngineType.MEDIA3 -> "Media3 (ExoPlayer)"
        EngineType.FAKE -> "Fake (UI-отладка)"
    }

/**
 * Каждый engine-модуль предоставляет фабрику своего движка;
 * `composeApp` регистрирует доступные на таргете фабрики через DI.
 */
interface PlayerEngineFactory {
    val type: EngineType
    fun create(): PlayerEngine
}

/** Реестр движков, доступных на текущем таргете/флейворе. */
class EngineRegistry(factories: List<PlayerEngineFactory>) {

    init {
        require(factories.isNotEmpty()) { "At least one PlayerEngineFactory is required" }
    }

    private val byType = factories.associateBy { it.type }

    val available: List<EngineType> = factories.map { it.type }

    fun isAvailable(type: EngineType): Boolean = type in byType

    /** Выбранный тип, если доступен, иначе первый доступный движок. */
    fun resolve(preferred: EngineType): EngineType = if (isAvailable(preferred)) preferred else available.first()

    fun create(type: EngineType): PlayerEngine = requireNotNull(byType[resolve(type)]).create()
}
