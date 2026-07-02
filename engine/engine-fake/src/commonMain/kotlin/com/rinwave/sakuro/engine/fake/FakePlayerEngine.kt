package com.rinwave.sakuro.engine.fake

import com.rinwave.sakuro.core.player.DebugStats
import com.rinwave.sakuro.core.player.EngineType
import com.rinwave.sakuro.core.player.MediaSource
import com.rinwave.sakuro.core.player.PlaybackStatus
import com.rinwave.sakuro.core.player.PlayerEngine
import com.rinwave.sakuro.core.player.PlayerEngineFactory
import com.rinwave.sakuro.core.player.PlayerState
import com.rinwave.sakuro.core.player.TrackInfo
import com.rinwave.sakuro.core.player.TrackSelection
import com.rinwave.sakuro.core.player.TrackType
import com.rinwave.sakuro.core.upscale.BuiltInPresets
import com.rinwave.sakuro.core.upscale.UpscaleProfile
import com.rinwave.sakuro.core.upscale.describe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Мок движка для desktop-таргета и тестов (ARCHITECTURE.md §3.2):
 * симулирует состояние, прогресс и дорожки без нативных зависимостей.
 */
class FakePlayerEngine : PlayerEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(PlayerState())
    override val state = _state.asStateFlow()

    private var profile: UpscaleProfile = BuiltInPresets.OFF

    init {
        scope.launch {
            while (isActive) {
                delay(TICK_MS)
                _state.update { s ->
                    if (!s.isPlaying || s.status != PlaybackStatus.READY) return@update s
                    val next = s.positionMs + (TICK_MS * s.speed).toLong()
                    if (next >= s.durationMs) {
                        s.copy(positionMs = s.durationMs, isPlaying = false, status = PlaybackStatus.ENDED)
                    } else {
                        s.copy(positionMs = next, bufferedMs = minOf(next + 30_000, s.durationMs))
                    }
                }
            }
        }
    }

    override val debugStats: Flow<DebugStats> = flow {
        while (true) {
            val s = _state.value
            emit(
                DebugStats(
                    engine = "FakePlayerEngine",
                    videoCodec = "fake/av1",
                    videoDecoder = "fake.decoder.simulated",
                    sourceResolution = "${s.videoWidth}x${s.videoHeight}",
                    outputResolution = outputResolution(s),
                    videoFps = 23.976f,
                    droppedFrames = 0,
                    bitrateKbps = 4200,
                    audioCodec = "fake/opus",
                    audioChannels = 2,
                    audioSampleRateHz = 48_000,
                    upscaleProfile = profile.name,
                    upscalePasses = profile.passes.map { it.describe() },
                    extras = mapOf("container" to "fake-mkv"),
                ),
            )
            delay(500)
        }
    }

    private fun outputResolution(s: PlayerState): String {
        val factor = profile.passes
            .filterIsInstance<com.rinwave.sakuro.core.upscale.UpscalePass.Upscale>()
            .fold(1f) { acc, pass -> acc * pass.factor }
        return "${(s.videoWidth * factor).toInt()}x${(s.videoHeight * factor).toInt()}"
    }

    override fun load(media: MediaSource) {
        _state.value = PlayerState(
            status = PlaybackStatus.READY,
            durationMs = 24 * 60_000L + 12_000L,
            videoWidth = 854,
            videoHeight = 480,
            activeUpscaleProfileId = profile.id,
            tracks = listOf(
                TrackInfo("v:0", TrackType.VIDEO, "854x480 fake/av1", selected = true),
                TrackInfo("a:0", TrackType.AUDIO, "Японский (opus 2.0)", language = "ja", selected = true),
                TrackInfo("a:1", TrackType.AUDIO, "Русский (opus 2.0)", language = "ru"),
                TrackInfo("s:0", TrackType.SUBTITLE, "Русские субтитры", language = "ru", selected = true),
            ),
        )
    }

    override fun play() {
        _state.update {
            if (it.status == PlaybackStatus.ENDED) {
                it.copy(positionMs = 0, status = PlaybackStatus.READY, isPlaying = true)
            } else {
                it.copy(isPlaying = true)
            }
        }
    }

    override fun pause() {
        _state.update { it.copy(isPlaying = false) }
    }

    override fun seekTo(positionMs: Long) {
        _state.update {
            val clamped = positionMs.coerceIn(0, it.durationMs)
            it.copy(
                positionMs = clamped,
                status = if (it.status == PlaybackStatus.ENDED) PlaybackStatus.READY else it.status,
            )
        }
    }

    override fun setSpeed(speed: Float) {
        _state.update { it.copy(speed = speed) }
    }

    override fun selectTrack(track: TrackSelection) {
        _state.update { s ->
            s.copy(
                tracks = s.tracks.map {
                    when {
                        it.type != track.type -> it
                        else -> it.copy(selected = it.id == track.trackId)
                    }
                },
            )
        }
    }

    override fun applyUpscale(profile: UpscaleProfile) {
        this.profile = profile
        _state.update { it.copy(activeUpscaleProfileId = profile.id) }
    }

    override fun release() {
        scope.cancel()
    }

    private companion object {
        const val TICK_MS = 250L
    }
}

class FakeEngineFactory : PlayerEngineFactory {
    override val type = EngineType.FAKE
    override fun create(): PlayerEngine = FakePlayerEngine()
}
