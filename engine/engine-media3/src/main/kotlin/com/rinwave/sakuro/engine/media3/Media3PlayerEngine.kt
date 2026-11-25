package com.rinwave.sakuro.engine.media3

import android.content.Context
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.SystemClock
import android.view.Display
import android.view.SurfaceHolder
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.session.MediaController
import androidx.media3.ui.SubtitleView
import com.google.common.util.concurrent.ListenableFuture
import com.rinwave.sakuro.core.model.DisplayLimitStatus
import com.rinwave.sakuro.core.model.DisplaySize
import com.rinwave.sakuro.core.model.EngineType
import com.rinwave.sakuro.core.model.MediaSource
import com.rinwave.sakuro.core.model.StreamFormat
import com.rinwave.sakuro.core.model.StreamHeaders
import com.rinwave.sakuro.core.model.StreamUrl
import com.rinwave.sakuro.core.model.SubtitlePreferences
import com.rinwave.sakuro.core.model.UpscaleProfile
import com.rinwave.sakuro.core.player.DebugStats
import com.rinwave.sakuro.core.player.ExternalSubtitle
import com.rinwave.sakuro.core.player.PlaybackError
import com.rinwave.sakuro.core.player.PlaybackErrorKind
import com.rinwave.sakuro.core.player.PlaybackStatus
import com.rinwave.sakuro.core.player.PlayerEngine
import com.rinwave.sakuro.core.player.PlayerEngineFactory
import com.rinwave.sakuro.core.player.PlayerState
import com.rinwave.sakuro.core.player.SurfaceVideoOutput
import com.rinwave.sakuro.core.player.TrackInfo
import com.rinwave.sakuro.core.player.TrackSelection
import com.rinwave.sakuro.core.player.TrackType
import com.rinwave.sakuro.core.player.VideoQuality
import com.rinwave.sakuro.core.upscale.BuiltInPresets
import com.rinwave.sakuro.core.upscale.DisplayUpscaleLimit
import io.github.peerless2012.ass.media.AssHandler
import io.github.peerless2012.ass.media.AssHandlerConfig
import io.github.peerless2012.ass.media.kt.withAssMkvSupport
import io.github.peerless2012.ass.media.kt.withAssSupport
import io.github.peerless2012.ass.media.parser.AssSubtitleParserFactory
import io.github.peerless2012.ass.media.type.AssRenderType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Media3/ExoPlayer implementation of [PlayerEngine].
 *
 * Upscaling is applied through `setVideoEffects()` using the effect list produced
 * by [UpscaleEffectChain]. Media3 only applies a changed effect chain after
 * preparation, so live preset changes re-prepare the current item while preserving
 * playback position and play/pause state.
 *
 * Network streams (HLS, DASH, progressive http(s)) share the same pipeline. Transient load
 * failures are retried inside Media3 by [StreamLoadErrorPolicy]; a player-level failure on a
 * remote item is then retried with backoff (waiting for connectivity first), and only after
 * that surfaces as a classified [PlaybackError]. Protected content is reported as
 * [PlaybackErrorKind.DRM] — Sakuro configures no DRM sessions.
 */
@OptIn(UnstableApi::class)
class Media3PlayerEngine(
    context: Context,
    /** Render ASS/SSA with libass (fonts, positioning, karaoke, effects) instead of Media3's subset. */
    assSubtitles: Boolean = false,
) : PlayerEngine, SurfaceVideoOutput {

    private val appContext: Context = context.applicationContext

    @Volatile
    private var subtitlePreferences = SubtitlePreferences()

    private val network = NetworkAvailability(appContext)

    /**
     * The current item is (or was, before an event stream ended) live. Read on loader threads
     * to keep live segments out of the cache, so it is updated from the timeline at once.
     */
    @Volatile
    private var liveStream = false

    /** Per-item request headers (User-Agent/Referer/Cookie from the link dialog); read per request. */
    @Volatile
    private var requestHeaders: Map<String, String> = emptyMap()

    private val streamNetwork = StreamNetwork.get(appContext)

    // Tuning from Reddit's production ExoPlayer experiments (Alexey Bykov, "Improving video
    // playback with ExoPlayer"): decoder fallback and a remembered bandwidth estimate
    // ([StreamNetwork.bandwidthMeter]). Their shorter start buffer (1 s, 2 s after a stall,
    // min = max 50 s) is Media3's own DefaultLoadControl default since 1.9, which also keeps
    // smaller, separate buffers for local files, so no custom LoadControl is set.
    /**
     * libass in overlay mode: subtitles are drawn by a view above the video, so they stay out of
     * the upscale effect chain (Media3's effect-based ASS modes would replace our effects).
     */
    private val assHandler: AssHandler? =
        if (assSubtitles && LibassNative.available) {
            AssHandler(AssRenderType.OVERLAY_OPEN_GL, AssHandlerConfig())
        } else {
            null
        }
    private val assParserFactory = assHandler?.let(::AssSubtitleParserFactory)

    val player: ExoPlayer = ExoPlayer.Builder(
        context,
        SubtitleRenderersFactory(context) { subtitlePreferences.subtitlePositionUs(it) }
            // A busy or missing hardware decoder (error 4001, often held by another app) falls
            // back to the next decoder, software if need be: slower, but it plays (−0.9% errors).
            .setEnableDecoderFallback(true)
            .let { factory -> assHandler?.let(factory::withAssSupport) ?: factory },
    ).setMediaSourceFactory(
        DefaultMediaSourceFactory(
            streamNetwork.dataSourceFactory(headers = { requestHeaders }, cacheAllowed = { !liveStream }),
            if (assHandler != null && assParserFactory != null) {
                // MKV: ASS tracks and font attachments go to libass.
                DefaultExtractorsFactory().withAssMkvSupport(assParserFactory, assHandler)
            } else {
                DefaultExtractorsFactory()
            },
        )
            .apply { assParserFactory?.let(::setSubtitleParserFactory) } // external .ass files
            .setLoadErrorHandlingPolicy(StreamLoadErrorPolicy()),
    ).setBandwidthMeter(
        streamNetwork.bandwidthMeter,
    ).setAudioAttributes(
        AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build(),
        true,
    ).setHandleAudioBecomingNoisy(true).build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(PlayerState())

    private val _videoFrameRate = MutableStateFlow(0f)

    /**
     * Source frame rate (0 when unknown), for the surface renderer's display-rate matching.
     * Media3's own frame-rate hint is off: with video effects the output surface is the
     * effect graph's, so the renderer sets the rate on the visible surface itself.
     */
    val videoFrameRate: StateFlow<Float> = _videoFrameRate.asStateFlow()
    override val state = _state.asStateFlow()

    private var currentMedia: MediaSource? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var inBackground = false

    /** A chain change arrived while hidden; rebuilt (re-prepared) on return to the foreground. */
    private var effectsPendingForForeground = false
    private var selectedQualityId: String? = null
    private val timelineWindow = Timeline.Window()
    private var reconnectJob: Job? = null
    private var reconnectAttempt = 0

    /**
     * For adaptive streams the chain is sized from the best variant, not the one currently
     * decoding: ABR switches would otherwise rebuild the chain (= re-prepare and rebuffer),
     * and the rebuffer would push ABR down again.
     */
    private var adaptiveSourceSize: Pair<Int, Int>? = null
    private var currentProfile: UpscaleProfile = BuiltInPresets.OFF
    private val externalSubtitles = mutableListOf<ExternalSubtitle>()

    /** Source height used when the current effect chain was built; 0 means unknown. */
    private val effectSourceSize = EffectSourceSize()
    private var effectChainTelemetry = EffectChainTelemetry()

    /** Off until the player opts in, so benchmarks stay unlimited. */
    private var displayLimitEnabled = false
    private var displayLimitStatus: DisplayLimitStatus? = null

    private var videoDecoderName: String? = null
    private var audioDecoderName: String? = null
    private var droppedFrames = 0

    /** (rendered buffers, elapsedRealtime) of the previous stats poll, for the FPS delta. */
    private var lastRenderSample: Pair<Int, Long>? = null

    private val listener = object : Player.Listener {
        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (player.isCurrentMediaItemLive) liveStream = true
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) reconnectAttempt = 0
            // An event stream that published its end (EXT-X-ENDLIST) stops being "live" and ends.
            if (playbackState == Player.STATE_ENDED && liveStream) {
                _state.update { it.copy(liveEnded = true) }
            }
            _state.update {
                // IDLE after a failure must not hide the error or a pending reconnect.
                if (playbackState == Player.STATE_IDLE && (it.error != null || it.reconnectAttempt > 0)) {
                    it
                } else if (playbackState == Player.STATE_READY) {
                    it.copy(status = PlaybackStatus.READY, reconnectAttempt = 0, waitingForNetwork = false)
                } else {
                    it.copy(status = mapStatus(playbackState))
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            // VideoSize may describe the processed output. Only decoded format sizes
            // can drive source-relative scaling without causing a rebuild feedback loop.
            val format = player.videoFormat
            if (format != null && format.width > 0 && format.height > 0) {
                onSourceSizeKnown(format.width, format.height)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            handlePlayerError(error)
        }

        override fun onTracksChanged(tracks: Tracks) {
            _state.update { it.copy(tracks = mapTracks(tracks)) }
            if (checkPlayableTracks(tracks)) {
                updateAdaptiveSourceSize(tracks)
                updateQualities(tracks)
            }
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
        assHandler?.init(player)
        player.videoChangeFrameRateStrategy = C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
        player.addListener(listener)
        player.addAnalyticsListener(analyticsListener)
        scope.launch {
            while (isActive) {
                _state.update {
                    it.copy(
                        positionMs = player.currentPosition.coerceAtLeast(0),
                        durationMs = player.duration.takeIf { d -> d != C.TIME_UNSET } ?: 0L,
                        bufferedMs = player.bufferedPosition.coerceAtLeast(0),
                        isLive = player.isCurrentMediaItemLive,
                        liveBehindMs = liveBehindMs(),
                        liveWindowStartEpochMs = liveWindowStartEpochMs(),
                        isSeekable = player.isCurrentMediaItemSeekable || player.playbackState == Player.STATE_IDLE,
                        playingQualityId = playingQualityId(it.videoQualities),
                    )
                }
                // With active video effects, Media3 may not always dispatch
                // onVideoSizeChanged, so poll the current video format as a fallback.
                val format = player.videoFormat
                if (format != null && format.width > 0 && format.height > 0) {
                    onSourceSizeKnown(format.width, format.height)
                }
                _videoFrameRate.value = format?.frameRate?.takeIf { it > 0f } ?: 0f
                delay(POSITION_POLL_MS)
            }
        }
    }

    private fun onSourceSizeKnown(width: Int, height: Int) {
        val changed = _state.value.videoWidth != width || _state.value.videoHeight != height
        if (changed) {
            _state.update { it.copy(videoWidth = width, videoHeight = height) }
        }
        // Rebuild both the first unknown-size chain and chains for a changed source. Live streams
        // keep their first chain: a rebuild re-prepares (seconds of rebuffer, a jump in the
        // window), and ad breaks or discontinuities can change the resolution again and again.
        // The chain's Presentation scales any input to its target, so it still renders correctly.
        if (liveStream && effectSourceSize.hasKnownSize) return
        if (effectSourceSize.needsRebuild(adaptiveSourceSize?.second ?: height)) {
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
        externalSubtitles.clear()
        droppedFrames = 0
        lastRenderSample = null
        adaptiveSourceSize = null
        selectedQualityId = null
        effectsPendingForForeground = false
        liveStream = false
        // Network items hold a Wi‑Fi lock while playing so a screen-off background stream does
        // not lose the radio; local files only need the CPU awake.
        player.setWakeMode(if (StreamUrl.isRemote(media.uri)) C.WAKE_MODE_NETWORK else C.WAKE_MODE_LOCAL)
        requestHeaders = if (StreamUrl.isRemote(media.uri)) StreamHeaders.sanitize(media.headers) else emptyMap()
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            .build()
        cancelReconnect()
        _state.update {
            it.copy(
                status = PlaybackStatus.BUFFERING,
                errorMessage = null,
                error = null,
                videoWidth = 0,
                videoHeight = 0,
                isLive = false,
                liveBehindMs = 0L,
                liveEnded = false,
                liveWindowStartEpochMs = null,
                videoQualities = emptyList(),
                selectedQualityId = null,
                playingQualityId = null,
            )
        }
        setEffectsInternal()
        player.setMediaItem(media.toMediaItem())
        player.prepare()
        player.playWhenReady = true
    }

    override fun play() {
        when {
            player.playbackState == Player.STATE_ENDED -> {
                _state.update { it.copy(liveEnded = false) }
                player.seekTo(0)
            }
            // A live stream without a DVR window cannot resume where it paused: the data is
            // gone. Resume at the live edge instead of waiting for a BEHIND_LIVE_WINDOW error.
            player.isCurrentMediaItemLive && !player.isCurrentMediaItemSeekable -> player.seekToDefaultPosition()
        }
        player.play()
    }

    override fun pause() {
        player.pause()
    }

    override fun seekTo(positionMs: Long) {
        if (player.isCurrentMediaItemLive) {
            if (!player.isCurrentMediaItemSeekable) return
            // Inside the DVR window only; past its end is the live edge.
            val windowEnd = player.duration.takeIf { it != C.TIME_UNSET } ?: return
            if (positionMs >= windowEnd) {
                player.seekToDefaultPosition()
                return
            }
            player.seekTo(positionMs.coerceAtLeast(0))
            return
        }
        player.seekTo(positionMs)
    }

    /**
     * Live streams keep Media3's own speed control, which nudges the speed (about ±3%) to hold
     * the target latency; a fixed user speed would fight it and drift off the edge.
     */
    override fun setSpeed(speed: Float) {
        if (player.isCurrentMediaItemLive) return
        player.setPlaybackSpeed(speed)
    }

    /** Adds the libass overlay to the player view's subtitle layer (no-op when libass is off). */
    fun attachAssOverlay(subtitleView: SubtitleView) {
        assHandler?.let(subtitleView::withAssSupport)
    }

    override fun attachMediaSession() {
        PlaybackSessionRegistry.publish(player)
        // Created by autoplay while the app is hidden: start audio-only like the previous engine.
        if (PlaybackSessionRegistry.uiInBackground) setBackground(true)
        if (controllerFuture == null) controllerFuture = SakuroPlaybackService.connect(appContext)
    }

    /**
     * Media3's advice for audio-only background playback: drop the video renderer, so nothing
     * is decoded and the upscale chain gets no frames (no GPU work). The chain itself and any
     * pinned quality override stay in the player and apply again when video is re-enabled.
     */
    override fun setBackground(background: Boolean) {
        PlaybackSessionRegistry.uiInBackground = background
        if (background == inBackground) return
        inBackground = background
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, background)
            .build()
        if (!background && effectsPendingForForeground) {
            effectsPendingForForeground = false
            applyUpscale(currentProfile)
        }
    }

    override fun selectVideoQuality(qualityId: String?) {
        val builder = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        val target = qualityId?.let(::parseTrackId)
        if (target != null) {
            val group = player.currentTracks.groups.getOrNull(target.first) ?: return
            builder.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, target.second))
        }
        selectedQualityId = qualityId.takeIf { target != null }
        player.trackSelectionParameters = builder.build()
        _state.update { it.copy(selectedQualityId = selectedQualityId) }
    }

    private var scrubbing = false

    /**
     * Media3 1.8+ scrubbing mode: decode-only frames and dynamic scheduling make rapid seeks
     * show frames while the finger moves. Local files only — on a network stream every preview
     * seek would request new segments.
     */
    override fun setScrubbing(scrubbing: Boolean) {
        val enabled = scrubbing && currentMedia?.let { !StreamUrl.isRemote(it.uri) } == true && !inBackground
        if (enabled == this.scrubbing) return
        this.scrubbing = enabled
        player.setScrubbingModeEnabled(enabled)
    }

    override fun scrubTo(positionMs: Long) {
        if (scrubbing) player.seekTo(positionMs.coerceAtLeast(0))
    }

    override fun seekToLiveEdge() {
        if (!player.isCurrentMediaItemLive) return
        player.seekToDefaultPosition()
        player.play()
    }

    /** Wall-clock time of the window start (HLS PROGRAM-DATE-TIME / DASH availability), if known. */
    private fun liveWindowStartEpochMs(): Long? {
        if (!player.isCurrentMediaItemLive) return null
        val timeline = player.currentTimeline
        if (timeline.isEmpty) return null
        return timeline.getWindow(player.currentMediaItemIndex, timelineWindow)
            .windowStartTimeMs.takeIf { it != C.TIME_UNSET }
    }

    private fun liveTargetOffsetMs(): Long? {
        val timeline = player.currentTimeline
        if (timeline.isEmpty) return null
        return timeline.getWindow(player.currentMediaItemIndex, timelineWindow)
            .liveConfiguration?.targetOffsetMs?.takeIf { it != C.TIME_UNSET }
    }

    /** How far playback trails the live edge beyond the stream's own target latency. */
    private fun liveBehindMs(): Long {
        if (!player.isCurrentMediaItemLive) return 0L
        val offset = player.currentLiveOffset.takeIf { it != C.TIME_UNSET } ?: return 0L
        return (offset - (liveTargetOffsetMs() ?: 0L)).coerceAtLeast(0L)
    }


    /** Lists the variants of the selected adaptive video group, best first. */
    private fun updateQualities(tracks: Tracks) {
        val groupIndex = tracks.groups.indexOfFirst { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
            .takeIf { it >= 0 }
            ?: tracks.groups.indexOfFirst { it.type == C.TRACK_TYPE_VIDEO }
        val group = tracks.groups.getOrNull(groupIndex)
        val qualities = if (group == null || group.length < 2) {
            emptyList()
        } else {
            (0 until group.length)
                .filter { group.isTrackSupported(it) }
                .map { index -> index to group.getTrackFormat(index) }
                .filter { (_, format) -> format.width > 0 && format.height > 0 }
                .map { (index, format) ->
                    VideoQuality(
                        id = "$groupIndex:$index",
                        width = format.width,
                        height = format.height,
                        bitrate = format.peakBitrate.takeIf { it > 0 } ?: format.averageBitrate.coerceAtLeast(0),
                        frameRate = format.frameRate.takeIf { it > 0 } ?: 0f,
                    )
                }
                .sortedWith(compareByDescending<VideoQuality> { it.height * it.width }.thenByDescending { it.bitrate })
        }
        // A pinned variant that vanished (new item, re-prepare with another manifest) means Auto.
        if (selectedQualityId != null && qualities.none { it.id == selectedQualityId }) selectedQualityId = null
        _state.update { it.copy(videoQualities = qualities, selectedQualityId = selectedQualityId) }
    }

    private fun playingQualityId(qualities: List<VideoQuality>): String? {
        if (qualities.isEmpty()) return null
        val format = player.videoFormat ?: return null
        val byId = qualities.firstOrNull { quality ->
            val (groupIndex, trackIndex) = parseTrackId(quality.id) ?: return@firstOrNull false
            val group = player.currentTracks.groups.getOrNull(groupIndex) ?: return@firstOrNull false
            trackIndex < group.length && group.getTrackFormat(trackIndex).id.let { it != null && it == format.id }
        }
        return (byId ?: qualities.firstOrNull { it.width == format.width && it.height == format.height })?.id
    }

    private fun parseTrackId(id: String): Pair<Int, Int>? {
        val parts = id.split(":")
        if (parts.size != 2) return null
        val group = parts[0].toIntOrNull() ?: return null
        val track = parts[1].toIntOrNull() ?: return null
        return group to track
    }

    override fun retry() {
        if (currentMedia == null) return
        cancelReconnect()
        _state.update { it.copy(status = PlaybackStatus.BUFFERING, error = null, errorMessage = null) }
        if (player.mediaItemCount == 0) {
            currentMedia?.let(::load)
            return
        }
        player.prepare()
        player.playWhenReady = true
    }

    private fun handlePlayerError(error: PlaybackException) {
        val classified = StreamErrorClassifier.classify(error)
        val media = currentMedia
        if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            // Fell behind the live window (long pause or a stall): jump back to the live edge.
            player.seekToDefaultPosition()
            player.prepare()
            return
        }
        val autoRetry = media != null &&
            StreamUrl.isRemote(media.uri) &&
            StreamErrorClassifier.isAutoRetryable(classified.kind) &&
            reconnectAttempt < StreamErrorClassifier.MAX_RECONNECT_ATTEMPTS
        if (autoRetry) {
            scheduleReconnect()
        } else {
            fail(classified)
        }
    }

    private fun scheduleReconnect() {
        reconnectAttempt++
        val attempt = reconnectAttempt
        _state.update {
            it.copy(
                status = PlaybackStatus.BUFFERING,
                error = null,
                reconnectAttempt = attempt,
                maxReconnectAttempts = StreamErrorClassifier.MAX_RECONNECT_ATTEMPTS,
                waitingForNetwork = !network.online.value,
            )
        }
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(StreamErrorClassifier.reconnectDelayMs(attempt))
            if (!network.online.value) {
                // Offline time does not count against the attempts: wait for any network.
                _state.update { it.copy(waitingForNetwork = true) }
                network.online.first { it }
                _state.update { it.copy(waitingForNetwork = false) }
            }
            // After an outage a live position is usually outside the window: go to the edge,
            // unless the viewer was deliberately watching back in a DVR window.
            if (liveStream && !watchingDvr()) player.seekToDefaultPosition()
            player.prepare()
        }
    }

    /** Watching back in a live DVR window (more than the badge's tolerance behind the edge). */
    private fun watchingDvr(): Boolean =
        player.isCurrentMediaItemSeekable && liveBehindMs() > DVR_BEHIND_THRESHOLD_MS

    private fun cancelReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempt = 0
        _state.update { it.copy(reconnectAttempt = 0, waitingForNetwork = false) }
    }

    private fun fail(error: PlaybackError) {
        cancelReconnect()
        _state.update { it.copy(status = PlaybackStatus.ERROR, error = error, errorMessage = error.code) }
    }

    /**
     * Without a DRM session manager, encrypted tracks are reported as unsupported instead of
     * failing, which would leave a silent black screen. Returns false when playback was stopped.
     */
    private fun checkPlayableTracks(tracks: Tracks): Boolean {
        val media = currentMedia ?: return true
        val groups = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO || it.type == C.TRACK_TYPE_AUDIO }
        if (groups.isEmpty()) return true
        val video = groups.filter { it.type == C.TRACK_TYPE_VIDEO }
        val encrypted = groups.any { group ->
            !group.isSupported && (0 until group.length).any { group.getTrackFormat(it).drmInitData != null }
        }
        val reason = when {
            encrypted && (video.none { it.isSupported } || groups.none { it.isSupported }) ->
                PlaybackError(PlaybackErrorKind.DRM, DRM_PROTECTED_CODE)
            // Local files keep the old audio-only fallback; a stream that plays nothing is an error.
            StreamUrl.isRemote(media.uri) && video.isNotEmpty() && video.none { it.isSupported } ->
                PlaybackError(PlaybackErrorKind.UNSUPPORTED, VIDEO_UNSUPPORTED_CODE)
            else -> return true
        }
        player.stop()
        fail(reason)
        return false
    }

    private fun updateAdaptiveSourceSize(tracks: Tracks) {
        val group = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
            ?: tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_VIDEO }
        val best = group?.takeIf { it.length > 1 }?.let { g ->
            (0 until g.length)
                .filter { g.isTrackSupported(it) }
                .map { g.getTrackFormat(it) }
                .filter { it.width > 0 && it.height > 0 }
                .maxByOrNull { it.height }
        }
        val size = best?.let { it.width to it.height }
        if (size == adaptiveSourceSize) return
        adaptiveSourceSize = size
        if (size != null && effectSourceSize.needsRebuild(size.second)) applyUpscale(currentProfile)
    }

    override fun selectTrack(track: TrackSelection) {
        val trackType = track.toMedia3TrackType()
        val trackId = track.trackId
        if (trackId == null) {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(trackType)
                .setTrackTypeDisabled(trackType, true)
                .build()
            return
        }
        val parts = trackId.split(":")
        if (parts.size != 2) return
        val groupIndex = parts[0].toIntOrNull() ?: return
        val trackIndex = parts[1].toIntOrNull() ?: return
        val group = player.currentTracks.groups.getOrNull(groupIndex) ?: return
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(trackType, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
            .build()
    }

    override fun addExternalSubtitle(subtitle: ExternalSubtitle) {
        val media = currentMedia ?: return
        scope.launch {
            // Legacy code pages (Windows-1251, Shift_JIS …) become a UTF-8 copy first.
            val utf8 = withContext(Dispatchers.IO) {
                SubtitleCharset.normalizedCopy(appContext, subtitle.uri, subtitle.title)
            }
            if (currentMedia !== media) return@launch
            addPreparedSubtitle(media, utf8?.let { subtitle.copy(uri = it) } ?: subtitle)
        }
    }

    private fun addPreparedSubtitle(media: MediaSource, subtitle: ExternalSubtitle) {
        externalSubtitles += subtitle
        val resumePosition = player.currentPosition.coerceAtLeast(0)
        val wasPlaying = player.playWhenReady
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .build()
        player.stop()
        setMediaItemAt(media, resumePosition)
        player.prepare()
        player.playWhenReady = wasPlaying
    }

    override fun setSubtitlePreferences(preferences: SubtitlePreferences) {
        val value = preferences.normalized()
        val delayChanged = value.delayMs != subtitlePreferences.delayMs
        val languageChanged = value.preferredLanguage != subtitlePreferences.preferredLanguage
        subtitlePreferences = value
        if (languageChanged) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setPreferredTextLanguage(value.preferredLanguage.ifEmpty { null })
                .build()
        }
        if (delayChanged && player.playbackState != Player.STATE_IDLE) {
            // Re-read cues already consumed by the text decoder, keeping the media position unchanged.
            val media = currentMedia ?: return
            val position = player.currentPosition
            val wasPlaying = player.playWhenReady
            player.stop()
            setMediaItemAt(media, position)
            player.prepare()
            player.playWhenReady = wasPlaying
        }
    }

    override fun applyUpscale(profile: UpscaleProfile) {
        currentProfile = profile
        _state.update { it.copy(activeUpscaleProfileId = profile.id, errorMessage = null) }
        if (player.playbackState == Player.STATE_IDLE && currentMedia == null) {
            setEffectsInternal()
            return
        }
        // Media3 applies changed effects on prepare, so rebuild around the current item.
        val media = currentMedia ?: return
        // Hidden: no frame reaches the chain, and a re-prepare would cut the audio. Defer it.
        if (inBackground) {
            effectsPendingForForeground = true
            return
        }
        val resumePosition = player.currentPosition
        val wasPlaying = player.playWhenReady
        // Re-preparing doubles as a retry (e.g. "retry without processing" after a failure).
        reconnectJob?.cancel()
        if (_state.value.error != null) {
            _state.update { it.copy(status = PlaybackStatus.BUFFERING, error = null) }
        }
        player.stop()
        setEffectsInternal()
        setMediaItemAt(media, resumePosition)
        player.prepare()
        player.playWhenReady = wasPlaying
    }

    /** Live items restart at the live edge; a stale position would land outside the window. */
    private fun setMediaItemAt(media: MediaSource, positionMs: Long) {
        if (player.isCurrentMediaItemLive) {
            // At the edge: rejoin the edge. Back in a DVR window: keep the window position (the
            // window moves a little during the re-prepare; a position that fell out is caught
            // by the BEHIND_LIVE_WINDOW recovery).
            if (watchingDvr()) {
                player.setMediaItem(media.toMediaItem(), positionMs.coerceAtLeast(0))
            } else {
                player.setMediaItem(media.toMediaItem())
            }
        } else {
            player.setMediaItem(media.toMediaItem(), positionMs.coerceAtLeast(0))
        }
    }

    override fun setUpscaleDisplayLimit(enabled: Boolean) {
        if (enabled == displayLimitEnabled) return
        displayLimitEnabled = enabled
        applyUpscale(currentProfile)
    }

    override fun release() {
        streamNetwork.saveBandwidthEstimate()
        scope.cancel()
        network.release()
        PlaybackSessionRegistry.withdraw(player)
        controllerFuture?.let(MediaController::releaseFuture)
        controllerFuture = null
        assHandler?.release()
        player.release()
    }

    // SurfaceVideoOutput: raw-surface binding for the benchmark; playback uses PlayerView.
    override fun attachSurface(holder: SurfaceHolder) = player.setVideoSurfaceHolder(holder)

    override fun resizeSurface(width: Int, height: Int) = Unit

    override fun detachSurface() = player.clearVideoSurface()

    private fun setEffectsInternal() {
        val sourceWidth = adaptiveSourceSize?.first ?: _state.value.videoWidth
        val sourceHeight = adaptiveSourceSize?.second ?: _state.value.videoHeight
        val limit = if (displayLimitEnabled) {
            DisplayUpscaleLimit.apply(currentProfile, sourceWidth, sourceHeight, physicalDisplaySize())
        } else {
            null
        }
        displayLimitStatus = limit?.status
        val built = UpscaleEffectChain.build(
            appContext,
            limit?.profile ?: currentProfile,
            sourceHeight,
            chainTargetSize = limit?.let { Size(it.screenWidth, it.screenHeight) },
        )
        // Until the source size is known the limit cannot be decided, so a limited
        // chain must be rebuilt once the first frame size arrives.
        val sourceDependent = built.sourceDependent || (displayLimitEnabled && currentProfile.isEnabled)
        effectSourceSize.onEffectsBuilt(if (sourceDependent) sourceHeight else -1)
        effectChainTelemetry = built.telemetry
        runCatching {
            player.setVideoEffects(built.effects)
        }.onFailure {
            built.telemetry.fail(it.message ?: it.javaClass.simpleName)
            _state.update { s -> s.copy(errorMessage = "Failed to apply effects: ${it.message}") }
        }
    }

    /**
     * The panel's native mode, not the window: Android TV often renders UI (and
     * the SurfaceView) at 1080p on a 4K panel while video is shown at 4K.
     */
    private fun physicalDisplaySize(): DisplaySize? {
        val display = appContext.getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY) ?: return null
        val mode = display.mode
        return DisplaySize(mode.physicalWidth, mode.physicalHeight)
    }

    private fun buildStats(): DebugStats {
        val videoFormat = player.videoFormat
        val audioFormat = player.audioFormat
        val sourceHeight = _state.value.videoHeight
        val sourceWidth = _state.value.videoWidth
        val applied = effectChainTelemetry.snapshot(sourceWidth, sourceHeight)
        return DebugStats(
            engine = "Media3/ExoPlayer",
            videoCodec = videoFormat?.codecs ?: videoFormat?.sampleMimeType,
            videoDecoder = videoDecoderName,
            sourceResolution = videoFormat?.let { "${it.width}x${it.height}" },
            outputResolution = applied.outputWidth?.let { width ->
                applied.outputHeight?.let { height -> "${width}x$height" }
            },
            videoFps = videoFormat?.frameRate?.takeIf { it > 0 },
            droppedFrames = droppedFrames,
            renderedFrames = player.videoDecoderCounters?.renderedOutputBufferCount,
            renderFps = sampleRenderFps(),
            bitrateKbps = videoFormat?.bitrateKbps(),
            colorInfo = videoFormat?.colorInfo?.toLogString(),
            audioCodec = audioFormat?.codecs ?: audioFormat?.sampleMimeType,
            audioChannels = audioFormat?.channelCount?.takeIf { it != Format.NO_VALUE },
            audioSampleRateHz = audioFormat?.sampleRate?.takeIf { it != Format.NO_VALUE },
            upscaleProfile = currentProfile.name,
            upscalePasses = applied.effects,
            upscaleRuntimeState = applied.runtimeState,
            upscaleRuntimeReason = applied.degradedReasons.takeIf { it.isNotEmpty() }?.joinToString("; "),
            upscaleDisplayLimit = displayLimitStatus,
            extras = buildMap {
                if (player.isCurrentMediaItemLive) {
                    val latency = player.currentLiveOffset.takeIf { it != C.TIME_UNSET }
                    put(
                        "live latency",
                        listOfNotNull(
                            latency?.let { "${it}ms" },
                            liveTargetOffsetMs()?.let { "target ${it}ms" },
                            "speed ${player.playbackParameters.speed}",
                        ).joinToString(" · "),
                    )
                }
                audioDecoderName?.let { put("audio decoder", it) }
                DeviceGpu.capabilities?.let { put("gpu", it.summary()) }
                displayLimitStatus?.let { put("screen limit", it.technicalText()) }
                if (applied.pending) put("shader graph", "pending")
                if (applied.degradedReasons.isNotEmpty()) {
                    put("shader graph", "degraded: ${applied.degradedReasons.joinToString("; ")}")
                }
            },
        )
    }

    /**
     * Output FPS from the rendered-buffer counter between two stats polls;
     * null on the first poll and whenever the counter did not move forward
     * (paused, or the decoder was reset).
     */
    private fun sampleRenderFps(): Float? {
        val rendered = player.videoDecoderCounters?.renderedOutputBufferCount ?: return null
        val now = SystemClock.elapsedRealtime()
        val previous = lastRenderSample
        lastRenderSample = rendered to now
        if (previous == null) return null
        val frames = rendered - previous.first
        val elapsedMs = now - previous.second
        if (frames < 0 || elapsedMs <= 0) return null
        return frames * MS_IN_SECOND_F / elapsedMs
    }

    private fun DisplayLimitStatus.technicalText(): String = when (kind) {
        DisplayLimitStatus.Kind.LIMITED -> "upscale ×$requestedFactor → ×$effectiveFactor (screen ${screenHeight}p)"
        DisplayLimitStatus.Kind.SKIPPED -> "upscale skipped (screen ${screenHeight}p)"
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

    private fun TrackSelection.toMedia3TrackType(): Int = when (type) {
        TrackType.VIDEO -> C.TRACK_TYPE_VIDEO
        TrackType.AUDIO -> C.TRACK_TYPE_AUDIO
        TrackType.SUBTITLE -> C.TRACK_TYPE_TEXT
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

    private fun MediaSource.toMediaItem(): MediaItem =
        MediaItem.Builder()
            .setUri(uri)
            .setMimeType(streamMimeType(uri))
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
            .setSubtitleConfigurations(externalSubtitles.map { it.toSubtitleConfiguration() })
            .build()

    private fun ExternalSubtitle.toSubtitleConfiguration(): MediaItem.SubtitleConfiguration =
        MediaItem.SubtitleConfiguration.Builder(Uri.parse(uri))
            .setMimeType(subtitleMimeType(title, uri))
            .setLabel(title)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()

    private fun subtitleMimeType(title: String, uri: String): String =
        when ((title.substringAfterLast('.', "").ifBlank { uri.substringAfterLast('.', "") }).lowercase()) {
            "srt" -> MimeTypes.APPLICATION_SUBRIP
            "vtt", "webvtt" -> MimeTypes.TEXT_VTT
            "ass", "ssa" -> MimeTypes.TEXT_SSA
            "ttml", "xml", "dfxp" -> MimeTypes.APPLICATION_TTML
            else -> MimeTypes.APPLICATION_SUBRIP
        }

    /** Extensionless HLS/DASH links are not recognized by Media3 on their own. */
    private fun streamMimeType(uri: String): String? {
        if (!StreamUrl.isRemote(uri)) return null
        return when (StreamUrl.detectFormat(uri)) {
            StreamFormat.HLS -> MimeTypes.APPLICATION_M3U8
            StreamFormat.DASH -> MimeTypes.APPLICATION_MPD
            StreamFormat.PROGRESSIVE -> null
        }
    }

    private companion object {
        const val DRM_PROTECTED_CODE = "DRM_PROTECTED_CONTENT"
        const val DVR_BEHIND_THRESHOLD_MS = 3_000L
        const val VIDEO_UNSUPPORTED_CODE = "VIDEO_TRACKS_UNSUPPORTED"
        const val POSITION_POLL_MS = 250L
        const val MS_IN_SECOND_F = 1000f
    }
}

@OptIn(UnstableApi::class)
class Media3EngineFactory(
    private val context: Context,
    private val assSubtitles: () -> Boolean = { false },
) : PlayerEngineFactory {
    override val type = EngineType.MEDIA3
    override fun create(): PlayerEngine = Media3PlayerEngine(context.applicationContext, assSubtitles())
}


/**
 * libass ships for arm64-v8a, armeabi-v7a and x86_64 only. On any other ABI (32-bit x86) the
 * load fails and the engine keeps Media3's built-in ASS rendering instead of crashing later.
 */
private object LibassNative {
    val available: Boolean by lazy { runCatching { System.loadLibrary("asskt") }.isSuccess }
}
