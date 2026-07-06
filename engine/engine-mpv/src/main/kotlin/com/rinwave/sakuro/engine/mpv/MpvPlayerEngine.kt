package com.rinwave.sakuro.engine.mpv

import android.content.Context
import android.net.Uri
import android.view.Surface
import com.rinwave.sakuro.core.player.DebugStats
import com.rinwave.sakuro.core.player.EngineType
import com.rinwave.sakuro.core.player.MediaSource
import com.rinwave.sakuro.core.player.PlaybackStatus
import com.rinwave.sakuro.core.player.PlayerEngine
import com.rinwave.sakuro.core.player.PlayerEngineFactory
import com.rinwave.sakuro.core.player.PlayerState
import com.rinwave.sakuro.core.player.TrackSelection
import com.rinwave.sakuro.core.player.TrackType
import com.rinwave.sakuro.core.upscale.BuiltInPresets
import com.rinwave.sakuro.core.upscale.ContentClass
import com.rinwave.sakuro.core.upscale.UpscaleProfile
import com.rinwave.sakuro.core.upscale.describe
import dev.jdtech.mpv.MPVLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import java.util.concurrent.atomic.AtomicBoolean

/** Frame scaling mode for mpv; the UI maps its ScaleMode here (pinch gesture, FEATURES.md §3.1). */
enum class MpvScaleMode { FIT, FILL, ZOOM }

/**
 * Engine #2 (ARCHITECTURE.md §2): libmpv via a prebuilt JNI wrapper
 * `dev.jdtech.mpv:libmpv` (Findroid's libmpv-android fork).
 *
 * Differences from Media3: rendering goes to a plain [Surface] (not PlayerView), upscale
 * presets are applied on the fly via mpv properties without re-prepare, `content://` URIs
 * are opened through a file descriptor (`fdclose://`) — libmpv has no access
 * to the ContentResolver.
 */
class MpvPlayerEngine(private val context: Context) : PlayerEngine {

    private val mpv: MPVLib = checkNotNull(MPVLib.create(context)) { "MPVLib.create() returned null" }
    private val shaderStore = MpvShaderStore(context)
    private val released = AtomicBoolean(false)

    private val _state = MutableStateFlow(PlayerState())
    override val state = _state.asStateFlow()

    private var currentProfile: UpscaleProfile = BuiltInPresets.OFF

    // The Surface appears after load(): a deferred loadfile waits for attachSurface().
    private var surfaceAttached = false
    private var pendingLoad: String? = null

    // A snapshot of mpv event flags from which PlaybackStatus is derived.
    private var fileLoaded = false
    private var paused = true
    private var pausedForCache = false
    private var eofReached = false

    private val observer = object : MPVLib.EventObserver {
        override fun event(eventId: Int) {
            when (eventId) {
                MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED -> {
                    fileLoaded = true
                    publishStatus()
                }

                MPVLib.MpvEvent.MPV_EVENT_END_FILE -> onEndFile()
            }
        }

        // track-list is observed without a value (MPV_FORMAT_NONE) and re-read
        // as a string: mpv returns it as JSON.
        override fun eventProperty(property: String) {
            if (property == "track-list" && !released.get()) {
                val json = mpv.getPropertyString("track-list") ?: return
                _state.update { it.copy(tracks = parseMpvTrackList(json)) }
            }
        }

        override fun eventProperty(property: String, value: Long) {
            when (property) {
                "video-params/w" -> _state.update { it.copy(videoWidth = value.toInt()) }
                "video-params/h" -> _state.update { it.copy(videoHeight = value.toInt()) }
            }
        }

        override fun eventProperty(property: String, value: Double) {
            when (property) {
                "time-pos" -> _state.update { it.copy(positionMs = value.toMillis()) }
                "duration" -> _state.update { it.copy(durationMs = value.toMillis()) }
                "demuxer-cache-time" -> _state.update { it.copy(bufferedMs = value.toMillis()) }
                "speed" -> _state.update { it.copy(speed = value.toFloat()) }
            }
        }

        override fun eventProperty(property: String, value: Boolean) {
            when (property) {
                "pause" -> paused = value
                "paused-for-cache" -> pausedForCache = value
                "eof-reached" -> eofReached = value
                else -> return
            }
            publishStatus()
        }

        override fun eventProperty(property: String, value: String) = Unit
    }

    init {
        // Options before init(): GL rendering to an Android surface + hardware decode.
        mpv.setOptionString("vo", "gpu")
        mpv.setOptionString("gpu-context", "android")
        mpv.setOptionString("opengl-es", "yes")
        // -copy: frames are copied out of the decoder and drawn by mpv's GL pipeline
        // (plain hwdec=mediacodec renders around GL — shaders/scalers do not work).
        // On the emulator the goldfish decoder does not hand frames to the ffmpeg bridge and hangs the core
        // (even property reads freeze) — there we decode in software.
        mpv.setOptionString("hwdec", if (isEmulator()) "no" else "mediacodec-copy")
        mpv.setOptionString("hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1")
        mpv.setOptionString("ao", "audiotrack")
        // On EOF the file is not unloaded — we catch the end via eof-reached (status ENDED).
        mpv.setOptionString("keep-open", "always")
        mpv.setOptionString("force-window", "no")
        mpv.setOptionString("idle", "yes")
        mpv.setOptionString("cache", "yes")
        mpv.setOptionString("demuxer-max-bytes", "32MiB")
        mpv.setOptionString("demuxer-max-back-bytes", "32MiB")
        mpv.setOptionString("save-position-on-quit", "no")
        mpv.init()
        mpv.addObserver(observer)
        mpv.observeProperty("track-list", MPVLib.MpvFormat.MPV_FORMAT_NONE)
        mpv.observeProperty("video-params/w", MPVLib.MpvFormat.MPV_FORMAT_INT64)
        mpv.observeProperty("video-params/h", MPVLib.MpvFormat.MPV_FORMAT_INT64)
        mpv.observeProperty("time-pos", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
        mpv.observeProperty("duration", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
        mpv.observeProperty("demuxer-cache-time", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
        mpv.observeProperty("speed", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
        mpv.observeProperty("pause", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
        mpv.observeProperty("paused-for-cache", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
        mpv.observeProperty("eof-reached", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
    }

    // Property polling — off the main thread: getProperty waits on mpv's core lock, which
    // can be held for a long time during (re)initialization of the VO.
    override val debugStats: Flow<DebugStats> = flow {
        while (currentCoroutineContext().isActive) {
            emit(buildStats())
            delay(STATS_POLL_MS)
        }
    }.flowOn(Dispatchers.Default)

    override fun load(media: MediaSource) {
        if (released.get()) return
        fileLoaded = false
        eofReached = false
        _state.update {
            it.copy(
                status = PlaybackStatus.BUFFERING,
                errorMessage = null,
                positionMs = 0,
                durationMs = 0,
                bufferedMs = 0,
                tracks = emptyList(),
            )
        }
        applyUpscaleProperties()
        val target = resolvePlayableUri(media.uri)
        if (target == null) {
            _state.update { it.copy(status = PlaybackStatus.ERROR, errorMessage = "Failed to open the file") }
            return
        }
        // The mpv-android pattern: while there is no Surface, loadfile is deferred —
        // starting vo=gpu without a surface fatally crashes the VO, and switching
        // the vo on the fly with active video blocks the core (ANR).
        if (surfaceAttached) {
            mpv.command(arrayOf("loadfile", target))
        } else {
            pendingLoad = target
        }
        mpv.setPropertyBoolean("pause", false)
    }

    override fun play() {
        if (released.get()) return
        if (_state.value.status == PlaybackStatus.ENDED) {
            seekTo(0)
        }
        mpv.setPropertyBoolean("pause", false)
    }

    override fun pause() {
        if (released.get()) return
        mpv.setPropertyBoolean("pause", true)
    }

    override fun seekTo(positionMs: Long) {
        if (released.get()) return
        _state.update { it.copy(positionMs = positionMs) }
        mpv.command(arrayOf("seek", (positionMs / MS_IN_SECOND).toString(), "absolute"))
    }

    override fun setSpeed(speed: Float) {
        if (released.get()) return
        mpv.setPropertyDouble("speed", speed.toDouble())
    }

    override fun selectTrack(track: TrackSelection) {
        if (released.get()) return
        val property = when (track.type) {
            TrackType.VIDEO -> "vid"
            TrackType.AUDIO -> "aid"
            TrackType.SUBTITLE -> "sid"
        }
        val id = track.trackId?.toIntOrNull()
        if (id == null) {
            mpv.setPropertyString(property, "no")
        } else {
            mpv.setPropertyInt(property, id)
        }
    }

    override fun applyUpscale(profile: UpscaleProfile) {
        currentProfile = profile
        _state.update { it.copy(activeUpscaleProfileId = profile.id) }
        applyUpscaleProperties()
    }

    override fun release() {
        if (!released.compareAndSet(false, true)) return
        mpv.removeObserver(observer)
        mpv.destroy()
    }

    // --- Surface binding (called by VideoSurface from composeApp) ---

    fun attachSurface(surface: Surface) {
        if (released.get()) return
        mpv.attachSurface(surface)
        mpv.setOptionString("force-window", "yes")
        surfaceAttached = true
        val pending = pendingLoad
        pendingLoad = null
        if (pending != null) {
            mpv.command(arrayOf("loadfile", pending))
        } else {
            // The surface returned to an already-loaded file (for example, after a window change).
            mpv.setPropertyString("vo", "gpu")
            redrawIfPaused()
        }
    }

    fun resizeSurface(width: Int, height: Int) {
        if (released.get()) return
        mpv.setPropertyString("android-surface-size", "${width}x$height")
        // Entering/leaving PiP does not recreate the surface, only resizes it;
        // while paused, the VO stays black after a resize until the frame is redrawn.
        redrawIfPaused()
    }

    /** Refresh-seek: an exact relative seek by 0 redraws the current frame while paused. */
    private fun redrawIfPaused() {
        if (fileLoaded && !_state.value.isPlaying) {
            mpv.command(arrayOf("seek", "0", "exact"))
        }
    }

    fun detachSurface() {
        if (released.get()) return
        surfaceAttached = false
        mpv.setPropertyString("vo", "null")
        mpv.setOptionString("force-window", "no")
        mpv.detachSurface()
    }

    fun setScaleMode(mode: MpvScaleMode) {
        if (released.get()) return
        when (mode) {
            MpvScaleMode.FIT -> {
                mpv.setPropertyString("keepaspect", "yes")
                mpv.setPropertyDouble("panscan", 0.0)
            }

            MpvScaleMode.FILL -> {
                mpv.setPropertyString("keepaspect", "no")
                mpv.setPropertyDouble("panscan", 0.0)
            }

            MpvScaleMode.ZOOM -> {
                mpv.setPropertyString("keepaspect", "yes")
                mpv.setPropertyDouble("panscan", 1.0)
            }
        }
    }

    // --- Internal ---

    /** Recomputes status from mpv flags; before FILE_LOADED, load()/onEndFile() drive the status. */
    private fun publishStatus() {
        if (!fileLoaded) return
        val status = when {
            eofReached -> PlaybackStatus.ENDED
            pausedForCache -> PlaybackStatus.BUFFERING
            else -> PlaybackStatus.READY
        }
        _state.update {
            it.copy(status = status, isPlaying = !paused && !eofReached && !pausedForCache)
        }
    }

    /**
     * END_FILE with keep-open=always only arrives on stop, file replacement or an error.
     * If the file never loaded, it is an open error (the event carries no details).
     */
    private fun onEndFile() {
        if (!fileLoaded) {
            _state.update { it.copy(status = PlaybackStatus.ERROR, errorMessage = "Failed to open the file") }
        } else {
            fileLoaded = false
            _state.update { it.copy(status = PlaybackStatus.IDLE, isPlaying = false) }
        }
    }

    /** libmpv cannot handle content:// — we open via fd; mpv closes it itself (fdclose://). */
    private fun resolvePlayableUri(uri: String): String? {
        if (!uri.startsWith("content://")) return uri
        return runCatching {
            val fd = requireNotNull(context.contentResolver.openFileDescriptor(Uri.parse(uri), "r"))
            "fdclose://${fd.detachFd()}"
        }.getOrNull()
    }

    private fun applyUpscaleProperties() {
        if (released.get()) return
        var config = buildMpvRenderConfig(currentProfile)
        val shaderPaths = shaderStore.resolve(config.shaders)
        if (shaderPaths == null) {
            // Shaders did not deploy — degrade to the properties-only path
            // (as for non-anime content) so that Sharpen is not lost.
            config = buildMpvRenderConfig(currentProfile.copy(contentClass = ContentClass.UNKNOWN))
        }
        mpv.setPropertyString("glsl-shaders", shaderPaths.orEmpty().joinToString(":"))
        config.properties.forEach { (name, value) ->
            mpv.setPropertyString(name, value)
        }
    }

    private fun buildStats(): DebugStats {
        if (released.get()) return DebugStats(engine = ENGINE_NAME)
        val snapshot = _state.value
        val hwdec = mpv.getPropertyString("hwdec-current")
        return DebugStats(
            engine = ENGINE_NAME,
            videoCodec = mpv.getPropertyString("video-codec"),
            videoDecoder = hwdec?.let { if (it == "no") "software" else it },
            sourceResolution = snapshot.takeIf { it.videoWidth > 0 }
                ?.let { "${it.videoWidth}x${it.videoHeight}" },
            outputResolution = mpv.getPropertyString("android-surface-size"),
            videoFps = mpv.getPropertyDouble("container-fps")?.toFloat(),
            droppedFrames = mpv.getPropertyInt("frame-drop-count") ?: 0,
            bitrateKbps = mpv.getPropertyInt("video-bitrate")?.div(BITS_IN_KBIT),
            colorInfo = listOfNotNull(
                mpv.getPropertyString("video-params/pixelformat"),
                mpv.getPropertyString("video-params/colormatrix"),
            ).joinToString(" ").ifBlank { null },
            audioCodec = mpv.getPropertyString("audio-codec"),
            audioChannels = mpv.getPropertyInt("audio-params/channel-count"),
            audioSampleRateHz = mpv.getPropertyInt("audio-params/samplerate"),
            upscaleProfile = currentProfile.name,
            upscalePasses = currentProfile.passes.map { it.describe() },
            extras = buildMap {
                mpv.getPropertyString("current-vo")?.let { put("vo", it) }
                mpv.getPropertyString("mpv-version")?.let { put("mpv", it) }
                // The actual user-shader chain as mpv sees it (short names).
                mpv.getPropertyString("glsl-shaders")?.takeIf { it.isNotBlank() }?.let { value ->
                    put("shaders", value.split(",", ":").joinToString(",") { it.substringAfterLast('/') })
                }
            },
        )
    }

    private fun Double.toMillis(): Long = (this * MS_IN_SECOND).toLong().coerceAtLeast(0)

    private fun isEmulator(): Boolean = android.os.Build.HARDWARE in setOf("goldfish", "ranchu", "cutf_cvm")

    private companion object {
        const val ENGINE_NAME = "libmpv"
        const val STATS_POLL_MS = 500L
        const val MS_IN_SECOND = 1000.0
        const val BITS_IN_KBIT = 1000
    }
}

class MpvEngineFactory(private val context: Context) : PlayerEngineFactory {
    override val type = EngineType.MPV
    override fun create(): PlayerEngine = MpvPlayerEngine(context.applicationContext)
}
