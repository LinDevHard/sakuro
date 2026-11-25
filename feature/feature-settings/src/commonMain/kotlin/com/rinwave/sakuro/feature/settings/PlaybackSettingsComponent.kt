package com.rinwave.sakuro.feature.settings

import com.arkivanov.decompose.ComponentContext
import com.rinwave.sakuro.core.model.EngineType
import com.rinwave.sakuro.core.model.FrameRateMatching
import com.rinwave.sakuro.core.model.SubtitlePreferences
import com.rinwave.sakuro.core.player.EngineRegistry
import com.rinwave.sakuro.core.player.MediaCache
import com.rinwave.sakuro.core.settings.SakuroSettings
import kotlinx.coroutines.flow.StateFlow

/** Playback tab: engine, background playback, the stream cache and subtitles. */
internal class PlaybackSettingsComponent(
    componentContext: ComponentContext,
    private val settings: SakuroSettings,
    engineRegistry: EngineRegistry,
    private val mediaCache: MediaCache,
) : ComponentContext by componentContext {

    val availableEngines: List<EngineType> = engineRegistry.available
    val engineType: StateFlow<EngineType> = settings.engineType

    val subtitlePreferences = settings.subtitlePreferences
    val assRenderer: StateFlow<Boolean> = settings.assRenderer

    fun setAssRenderer(enabled: Boolean) = settings.setAssRenderer(enabled)

    val backgroundPlayback: StateFlow<Boolean> = settings.backgroundPlayback
    val frameRateMatching: StateFlow<FrameRateMatching> = settings.frameRateMatching
    val audioDelayMs: StateFlow<Int> = settings.audioDelayMs
    val cacheLimitMb: StateFlow<Int> = settings.streamCacheLimitMb
    val cacheUsedBytes: StateFlow<Long> = mediaCache.usedBytes

    init {
        mediaCache.refreshUsage()
    }

    fun setSubtitlePreferences(value: SubtitlePreferences) = settings.setSubtitlePreferences(value)

    fun selectEngine(type: EngineType) = settings.setEngineType(type)

    fun setBackgroundPlayback(enabled: Boolean) = settings.setBackgroundPlayback(enabled)

    fun setFrameRateMatching(value: FrameRateMatching) = settings.setFrameRateMatching(value)

    fun setAudioDelayMs(value: Int) = settings.setAudioDelayMs(value)

    fun setCacheLimitMb(value: Int) {
        settings.setStreamCacheLimitMb(value)
        mediaCache.setLimitBytes(value * MediaCache.MB)
    }

    fun clearCache() = mediaCache.clear()
}
