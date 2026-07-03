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

/** Режим кадра для mpv; UI мапит сюда свой ScaleMode (пинч-жест, FEATURES.md §3.1). */
enum class MpvScaleMode { FIT, FILL, ZOOM }

/**
 * Движок №2 (ARCHITECTURE.md §2): libmpv через prebuilt JNI-обёртку
 * `dev.jdtech.mpv:libmpv` (форк libmpv-android от Findroid).
 *
 * Отличия от Media3: рендер идёт в обычный [Surface] (не PlayerView), пресеты
 * апскейла применяются на лету свойствами mpv без re-prepare, `content://`-URI
 * открываются через file descriptor (`fdclose://`) — у libmpv нет доступа
 * к ContentResolver.
 */
class MpvPlayerEngine(private val context: Context) : PlayerEngine {

    private val mpv: MPVLib = checkNotNull(MPVLib.create(context)) { "MPVLib.create() вернул null" }
    private val shaderStore = MpvShaderStore(context)
    private val released = AtomicBoolean(false)

    private val _state = MutableStateFlow(PlayerState())
    override val state = _state.asStateFlow()

    private var currentProfile: UpscaleProfile = BuiltInPresets.OFF

    // Surface появляется позже load(): отложенный loadfile ждёт attachSurface().
    private var surfaceAttached = false
    private var pendingLoad: String? = null

    // Слепок событийных флагов mpv, из которого выводится PlaybackStatus.
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

        // track-list наблюдается без значения (MPV_FORMAT_NONE) и перечитывается
        // строкой: mpv отдаёт его как JSON.
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
        // Опции до init(): GL-рендер на Android-поверхность + аппаратный декод.
        mpv.setOptionString("vo", "gpu")
        mpv.setOptionString("gpu-context", "android")
        mpv.setOptionString("opengl-es", "yes")
        // -copy: кадры копируются из декодера и рисуются GL-конвейером mpv
        // (прямой hwdec=mediacodec рендерит мимо GL — шейдеры/скейлеры не работают).
        // На эмуляторе goldfish-декодер не отдаёт кадры ffmpeg-мосту и вешает core
        // (зависает даже чтение свойств) — там декодируем программно.
        mpv.setOptionString("hwdec", if (isEmulator()) "no" else "mediacodec-copy")
        mpv.setOptionString("hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1")
        mpv.setOptionString("ao", "audiotrack")
        // На EOF файл не выгружается — конец ловим по eof-reached (статус ENDED).
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

    // Опрос свойств — не на main: getProperty ждёт core-лок mpv, который
    // во время (ре)инициализации VO бывает занят надолго.
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
            _state.update { it.copy(status = PlaybackStatus.ERROR, errorMessage = "Не удалось открыть файл") }
            return
        }
        // Паттерн mpv-android: пока нет Surface, loadfile откладывается —
        // старт с vo=gpu без поверхности фатально роняет VO, а переключение
        // vo на лету при активном видео блокирует core (ANR).
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
        val id = track.trackId.toIntOrNull() ?: return
        val property = when (track.type) {
            TrackType.VIDEO -> "vid"
            TrackType.AUDIO -> "aid"
            TrackType.SUBTITLE -> "sid"
        }
        mpv.setPropertyInt(property, id)
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

    // --- Привязка Surface (зовёт VideoSurface из composeApp) ---

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
            // Поверхность вернулась к уже загруженному файлу (например, после смены окна).
            mpv.setPropertyString("vo", "gpu")
            redrawIfPaused()
        }
    }

    fun resizeSurface(width: Int, height: Int) {
        if (released.get()) return
        mpv.setPropertyString("android-surface-size", "${width}x$height")
        // Вход/выход PiP не пересоздаёт surface, а только ресайзит его;
        // на паузе VO после ресайза остаётся чёрным, пока кадр не перерисован.
        redrawIfPaused()
    }

    /** Refresh-seek: точный seek на 0 относительно перерисовывает текущий кадр на паузе. */
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

    // --- Внутреннее ---

    /** Пересчёт статуса из флагов mpv; до FILE_LOADED статусом управляют load()/onEndFile(). */
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
     * END_FILE с keep-open=always приходит только при stop, замене файла или ошибке.
     * Если файл так и не загрузился — это ошибка открытия (деталей событие не несёт).
     */
    private fun onEndFile() {
        if (!fileLoaded) {
            _state.update { it.copy(status = PlaybackStatus.ERROR, errorMessage = "Не удалось открыть файл") }
        } else {
            fileLoaded = false
            _state.update { it.copy(status = PlaybackStatus.IDLE, isPlaying = false) }
        }
    }

    /** libmpv не умеет content:// — открываем через fd; mpv закроет его сам (fdclose://). */
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
            // Шейдеры не развернулись — деградация до пути свойствами
            // (как для не-аниме контента), чтобы Sharpen не потерялся.
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
                // Фактическая цепочка user-shaders глазами mpv (короткие имена).
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
