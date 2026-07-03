package com.rinwave.sakuro.engine.media3

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
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
import com.rinwave.sakuro.core.upscale.UpscalePass
import com.rinwave.sakuro.core.upscale.UpscaleProfile
import com.rinwave.sakuro.core.upscale.describe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Движок №1 (ARCHITECTURE.md §2): Media3/ExoPlayer.
 * Апскейл — через `setVideoEffects()` с цепочкой `GlEffect` (см. [UpscaleEffectChain]).
 *
 * Ограничение Media3: эффекты применяются надёжно, если выставлены до `prepare()`,
 * поэтому смена пресета на лету выполняется быстрым re-prepare с восстановлением позиции.
 */
@UnstableApi
class Media3PlayerEngine(context: Context) : PlayerEngine {

    val player: ExoPlayer = ExoPlayer.Builder(context).build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(PlayerState())
    override val state = _state.asStateFlow()

    private var currentMedia: MediaSource? = null
    private var currentProfile: UpscaleProfile = BuiltInPresets.OFF

    /** Высота источника, с которой собрана текущая цепочка эффектов (0 — размер ещё неизвестен). */
    private var effectsBuiltForHeight = -1

    private var videoDecoderName: String? = null
    private var audioDecoderName: String? = null
    private var droppedFrames = 0

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            _state.update { it.copy(status = mapStatus(playbackState)) }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width > 0 && videoSize.height > 0) {
                onSourceSizeKnown(videoSize.width, videoSize.height)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.update { it.copy(status = PlaybackStatus.ERROR, errorMessage = error.errorCodeName) }
        }

        override fun onTracksChanged(tracks: Tracks) {
            _state.update { it.copy(tracks = mapTracks(tracks)) }
        }

        override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
            _state.update { it.copy(speed = playbackParameters.speed) }
        }
    }

    private val analyticsListener = object : AnalyticsListener {
        override fun onDroppedVideoFrames(
            eventTime: AnalyticsListener.EventTime,
            droppedFrameCount: Int,
            elapsedMs: Long,
        ) {
            droppedFrames += droppedFrameCount
        }

        override fun onVideoDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            videoDecoderName = decoderName
        }

        override fun onAudioDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            audioDecoderName = decoderName
        }
    }

    init {
        player.addListener(listener)
        player.addAnalyticsListener(analyticsListener)
        scope.launch {
            while (isActive) {
                _state.update {
                    it.copy(
                        positionMs = player.currentPosition.coerceAtLeast(0),
                        durationMs = player.duration.takeIf { d -> d != C.TIME_UNSET } ?: 0L,
                        bufferedMs = player.bufferedPosition.coerceAtLeast(0),
                    )
                }
                // При включённых видеоэффектах onVideoSizeChanged может не приходить —
                // размер источника достаём из формата дорожки.
                val format = player.videoFormat
                if (format != null && format.width > 0 && format.height > 0) {
                    onSourceSizeKnown(format.width, format.height)
                }
                delay(POSITION_POLL_MS)
            }
        }
    }

    private fun onSourceSizeKnown(width: Int, height: Int) {
        val changed = _state.value.videoWidth != width || _state.value.videoHeight != height
        if (changed) {
            _state.update { it.copy(videoWidth = width, videoHeight = height) }
        }
        // Цепочка с Upscale-проходом, собранная до того, как стал известен размер
        // источника, не содержит Presentation — пересобираем её один раз.
        val needsRebuild = effectsBuiltForHeight == 0 &&
            currentProfile.passes.any { it is UpscalePass.Upscale }
        if (needsRebuild) {
            applyUpscale(currentProfile)
        }
    }

    override val debugStats: Flow<DebugStats> = flow {
        while (currentCoroutineContext().isActive) {
            emit(buildStats())
            delay(500)
        }
    }

    override fun load(media: MediaSource) {
        currentMedia = media
        droppedFrames = 0
        _state.update { it.copy(status = PlaybackStatus.BUFFERING, errorMessage = null) }
        setEffectsInternal()
        player.setMediaItem(media.toMediaItem())
        player.prepare()
        player.playWhenReady = true
    }

    override fun play() {
        if (player.playbackState == Player.STATE_ENDED) {
            player.seekTo(0)
        }
        player.play()
    }

    override fun pause() {
        player.pause()
    }

    override fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    override fun setSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
    }

    override fun selectTrack(track: TrackSelection) {
        val parts = track.trackId.split(":")
        if (parts.size != 2) return
        val groupIndex = parts[0].toIntOrNull() ?: return
        val trackIndex = parts[1].toIntOrNull() ?: return
        val group = player.currentTracks.groups.getOrNull(groupIndex) ?: return
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
            .build()
    }

    override fun applyUpscale(profile: UpscaleProfile) {
        currentProfile = profile
        _state.update { it.copy(activeUpscaleProfileId = profile.id) }
        if (player.playbackState == Player.STATE_IDLE) {
            setEffectsInternal()
            return
        }
        // Смена эффектов на подготовленном плеере: быстрый re-prepare с той же позиции.
        val media = currentMedia ?: return
        val resumePosition = player.currentPosition
        val wasPlaying = player.playWhenReady
        player.stop()
        setEffectsInternal()
        player.setMediaItem(media.toMediaItem(), resumePosition)
        player.prepare()
        player.playWhenReady = wasPlaying
    }

    override fun release() {
        scope.cancel()
        player.release()
    }

    private fun setEffectsInternal() {
        val sourceHeight = _state.value.videoHeight
        effectsBuiltForHeight = if (currentProfile.passes.any { it is UpscalePass.Upscale }) sourceHeight else -1
        runCatching {
            player.setVideoEffects(UpscaleEffectChain.build(currentProfile, sourceHeight))
        }.onFailure {
            _state.update { s -> s.copy(errorMessage = "Не удалось применить эффекты: ${it.message}") }
        }
    }

    private fun buildStats(): DebugStats {
        val videoFormat = player.videoFormat
        val audioFormat = player.audioFormat
        val sourceHeight = _state.value.videoHeight
        val sourceWidth = _state.value.videoWidth
        val targetHeight = UpscaleEffectChain.targetHeight(currentProfile, sourceHeight)
        val outputWidth = if (sourceHeight > 0) sourceWidth * targetHeight / sourceHeight else 0
        return DebugStats(
            engine = "Media3/ExoPlayer",
            videoCodec = videoFormat?.codecs ?: videoFormat?.sampleMimeType,
            videoDecoder = videoDecoderName,
            sourceResolution = videoFormat?.let { "${it.width}x${it.height}" },
            outputResolution = if (sourceHeight > 0) "${outputWidth}x$targetHeight" else null,
            videoFps = videoFormat?.frameRate?.takeIf { it > 0 },
            droppedFrames = droppedFrames,
            bitrateKbps = videoFormat?.bitrateKbps(),
            colorInfo = videoFormat?.colorInfo?.toLogString(),
            audioCodec = audioFormat?.codecs ?: audioFormat?.sampleMimeType,
            audioChannels = audioFormat?.channelCount?.takeIf { it != Format.NO_VALUE },
            audioSampleRateHz = audioFormat?.sampleRate?.takeIf { it != Format.NO_VALUE },
            upscaleProfile = currentProfile.name,
            upscalePasses = currentProfile.passes.map { it.describe() },
            extras = buildMap {
                audioDecoderName?.let { put("audio decoder", it) }
            },
        )
    }

    private fun Format.bitrateKbps(): Int? {
        val bitrate = when {
            averageBitrate != Format.NO_VALUE -> averageBitrate
            peakBitrate != Format.NO_VALUE -> peakBitrate
            else -> return null
        }
        return bitrate / 1000
    }

    private fun mapStatus(playbackState: Int): PlaybackStatus = when (playbackState) {
        Player.STATE_IDLE -> PlaybackStatus.IDLE
        Player.STATE_BUFFERING -> PlaybackStatus.BUFFERING
        Player.STATE_READY -> PlaybackStatus.READY
        Player.STATE_ENDED -> PlaybackStatus.ENDED
        else -> PlaybackStatus.IDLE
    }

    private fun mapTracks(tracks: Tracks): List<TrackInfo> =
        tracks.groups.flatMapIndexed { groupIndex: Int, group: Tracks.Group ->
            val type = when (group.type) {
                C.TRACK_TYPE_VIDEO -> TrackType.VIDEO
                C.TRACK_TYPE_AUDIO -> TrackType.AUDIO
                C.TRACK_TYPE_TEXT -> TrackType.SUBTITLE
                else -> null
            } ?: return@flatMapIndexed emptyList()
            (0 until group.length).map { trackIndex ->
                val format = group.getTrackFormat(trackIndex)
                TrackInfo(
                    id = "$groupIndex:$trackIndex",
                    type = type,
                    label = format.label ?: format.defaultLabel(type),
                    language = format.language,
                    selected = group.isTrackSelected(trackIndex),
                )
            }
        }

    private fun Format.defaultLabel(type: TrackType): String = when (type) {
        TrackType.VIDEO -> "${width}x$height ${sampleMimeType.orEmpty()}".trim()
        TrackType.AUDIO -> "${language ?: "audio"} (${sampleMimeType.orEmpty()} ${channelCount}ch)"
        TrackType.SUBTITLE -> language ?: sampleMimeType.orEmpty()
    }

    private fun MediaSource.toMediaItem(): MediaItem = MediaItem.Builder()
        .setUri(uri)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
        .build()

    private companion object {
        const val POSITION_POLL_MS = 250L
    }
}

@UnstableApi
class Media3EngineFactory(private val context: Context) : PlayerEngineFactory {
    override val type = EngineType.MEDIA3
    override fun create(): PlayerEngine = Media3PlayerEngine(context.applicationContext)
}
