package com.rinwave.sakuro.core.player

import com.rinwave.sakuro.core.upscale.UpscaleProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * A single playback-engine abstraction (ARCHITECTURE.md §4).
 * The UI works only with this interface and does not know the concrete engine.
 */
interface PlayerEngine {

    val state: StateFlow<PlayerState>

    /** "Stats for nerds" — data for the debug overlay (FEATURES.md §4). */
    val debugStats: Flow<DebugStats>

    fun load(media: MediaSource)

    fun play()

    fun pause()

    fun seekTo(positionMs: Long)

    fun setSpeed(speed: Float)

    fun selectTrack(track: TrackSelection)

    /**
     * The single upscale application point: libmpv loads a `.glsl` chain,
     * Media3 builds a `GlEffect` list and calls `setVideoEffects()`.
     */
    fun applyUpscale(profile: UpscaleProfile)

    fun release()
}

enum class EngineType {
    MPV,
    MEDIA3,
    FAKE,
}
// Display names for engines live in the UI layer (localized).

/**
 * Each engine module provides a factory for its engine;
 * `composeApp` registers the target's available factories via DI.
 */
interface PlayerEngineFactory {
    val type: EngineType
    fun create(): PlayerEngine
}

/** Registry of engines available on the current target/flavor. */
class EngineRegistry(factories: List<PlayerEngineFactory>) {

    init {
        require(factories.isNotEmpty()) { "At least one PlayerEngineFactory is required" }
    }

    private val byType = factories.associateBy { it.type }

    val available: List<EngineType> = factories.map { it.type }

    fun isAvailable(type: EngineType): Boolean = type in byType

    /** The selected type if available, otherwise the first available engine. */
    fun resolve(preferred: EngineType): EngineType = if (isAvailable(preferred)) preferred else available.first()

    fun create(type: EngineType): PlayerEngine = requireNotNull(byType[resolve(type)]).create()
}
