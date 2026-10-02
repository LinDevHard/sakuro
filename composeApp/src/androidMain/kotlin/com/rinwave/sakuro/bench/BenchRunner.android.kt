package com.rinwave.sakuro.bench

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import android.view.PixelCopy
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.media3.common.util.UnstableApi
import com.rinwave.sakuro.AppInfo
import com.rinwave.sakuro.attachReferenceEngineSurface
import com.rinwave.sakuro.core.player.EngineRegistry
import com.rinwave.sakuro.core.player.MediaSource
import com.rinwave.sakuro.core.player.PlaybackStatus
import com.rinwave.sakuro.core.player.PlayerEngine
import com.rinwave.sakuro.detachReferenceEngineSurface
import com.rinwave.sakuro.engine.media3.Media3PlayerEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume

@Composable
actual fun rememberBenchRunner(engineRegistry: EngineRegistry): BenchRunner? {
    val context = LocalContext.current
    return remember(engineRegistry) { AndroidBenchRunner(context, engineRegistry) }
}

/**
 * The surface the benchmark renders into. It must stay composed and on screen
 * for the whole run: captures come from its buffer via [PixelCopy], which only
 * works for a live surface. The buffer is pinned to 1920×1080 with
 * [SurfaceHolder.setFixedSize], so captures are identical on every device
 * regardless of the view's on-screen size, display density or cutouts.
 */
@Composable
actual fun BenchSurface(runner: BenchRunner, modifier: Modifier) {
    val android = runner as? AndroidBenchRunner ?: return
    AndroidView(
        factory = { context ->
            SurfaceView(context).apply {
                holder.setFixedSize(AndroidBenchRunner.OUT_WIDTH, AndroidBenchRunner.OUT_HEIGHT)
                holder.addCallback(
                    object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) = android.attachView(this@apply)

                        override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hh: Int) = Unit

                        override fun surfaceDestroyed(holder: SurfaceHolder) = android.detachView()
                    },
                )
            }
        },
        modifier = modifier,
    )
}

/**
 * On-device benchmark: plays the chosen video through every engine × preset
 * configuration on a shared [SurfaceView], measuring startup and first-frame
 * latency, output FPS, dropped frames, CPU, memory and thermal state, then
 * capturing the same frames from every configuration. Everything lands in one
 * zip bundle consumed by `tools/sakuro-bench device-bundle`.
 */
@UnstableApi
internal class AndroidBenchRunner(
    context: Context,
    private val registry: EngineRegistry,
) : BenchRunner {

    private val appContext = context.applicationContext
    private val activity: Activity? = context.findActivity()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow<BenchState>(BenchState.Idle)
    override val state: StateFlow<BenchState> = _state
    private var job: Job? = null

    @Volatile
    private var surfaceView: SurfaceView? = null

    fun attachView(view: SurfaceView) {
        surfaceView = view
    }

    fun detachView() {
        surfaceView = null
    }

    override fun start(video: MediaSource, configs: List<BenchConfigItem>) {
        if (job?.isActive == true || configs.isEmpty()) return
        job = scope.launch {
            keepScreenOn(true)
            try {
                runBench(video, configs)
            } catch (e: CancellationException) {
                _state.value = BenchState.Idle
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // The run touches decoders, GL and the file system; whatever
                // fails, the bench must surface it instead of killing the app.
                _state.value = BenchState.Failed(e.message ?: e.javaClass.simpleName)
            } finally {
                keepScreenOn(false)
            }
        }
    }

    override fun cancel() {
        job?.cancel()
    }

    override fun share(bundlePath: String) {
        val file = File(bundlePath)
        if (!file.isFile) return
        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, file.name).apply {
            if (activity == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        (activity ?: appContext).startActivity(chooser)
    }

    private suspend fun runBench(video: MediaSource, configs: List<BenchConfigItem>) {
        val workDir = File(appContext.cacheDir, "bench/work").apply {
            deleteRecursively()
            mkdirs()
        }
        var videoInfo: BenchVideo? = null
        var timestamps: List<Long> = emptyList()
        val results = mutableListOf<BenchModeResult>()

        configs.forEachIndexed { index, config ->
            _state.value = BenchState.Running(index + 1, configs.size, config.mode, BenchStage.STARTING)
            results += runConfig(
                config = config,
                video = video,
                workDir = workDir,
                knownTimestamps = timestamps,
                onVideoInfo = { duration, width, height ->
                    // The first successful load defines the video info and capture points.
                    if (videoInfo == null) {
                        videoInfo = BenchVideo(video.title, duration, width, height)
                        timestamps = captureTimestamps(duration)
                    }
                    timestamps
                },
                onStage = { stage ->
                    _state.value = BenchState.Running(index + 1, configs.size, config.mode, stage)
                },
            )
        }

        _state.value = BenchState.Running(configs.size, configs.size, "bundle", BenchStage.PACKING)
        val manifest = BenchManifest(
            createdAtMs = System.currentTimeMillis(),
            appVersion = AppInfo.VERSION,
            device = BenchDevice(
                manufacturer = Build.MANUFACTURER,
                model = Build.MODEL,
                sdk = Build.VERSION.SDK_INT,
                abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
            ),
            video = videoInfo ?: BenchVideo(video.title, 0, 0, 0),
            outputWidth = OUT_WIDTH,
            outputHeight = OUT_HEIGHT,
            captureTimestampsMs = timestamps,
            modes = results,
        )
        val bundle = withContext(Dispatchers.IO) { packBundle(workDir, manifest) }
        workDir.deleteRecursively()
        _state.value = BenchState.Done(
            bundlePath = bundle.absolutePath,
            bundleName = bundle.name,
            configCount = results.size,
            captureCount = results.sumOf { it.captures.size },
        )
    }

    @Suppress("LongMethod", "LongParameterList")
    private suspend fun runConfig(
        config: BenchConfigItem,
        video: MediaSource,
        workDir: File,
        knownTimestamps: List<Long>,
        onVideoInfo: (Long, Int, Int) -> List<Long>,
        onStage: (BenchStage) -> Unit,
    ): BenchModeResult {
        val warnings = mutableListOf<String>()
        val captures = mutableListOf<String>()
        var metrics = BenchMetrics()
        val view = surfaceView
            ?: return failed(config, listOf("the benchmark surface is not on screen"))
        val engine = registry.create(config.engine)
        try {
            attachSurface(engine, view)
            engine.applyUpscale(config.profile)

            val loadStart = SystemClock.elapsedRealtime()
            engine.load(video)
            val ready = withTimeoutOrNull(LOAD_TIMEOUT_MS) {
                engine.state.first { it.status == PlaybackStatus.READY || it.status == PlaybackStatus.ERROR }
            }
            if (ready == null || ready.status == PlaybackStatus.ERROR) {
                return failed(config, listOf("load failed: ${ready?.errorMessage ?: "timeout"}"))
            }
            val startupMs = SystemClock.elapsedRealtime() - loadStart
            // READY fires before the duration and video size are polled in, and
            // the capture points are derived from them — wait for the real values.
            val info = withTimeoutOrNull(INFO_TIMEOUT_MS) {
                engine.state.first { it.durationMs > 0 && it.videoWidth > 0 }
            } ?: ready
            if (info.durationMs <= 0) warnings += "duration unknown, captured a single frame"
            val timestamps = onVideoInfo(info.durationMs, info.videoWidth, info.videoHeight)
                .ifEmpty { knownTimestamps }

            onStage(BenchStage.PLAYING)
            engine.play()
            // First frame = the first non-black content the surface actually shows;
            // engine-agnostic, and it is what the user perceives as "it started".
            val firstFrameMs = withTimeoutOrNull(FIRST_FRAME_TIMEOUT_MS) {
                while (capture(view)?.use { it.looksBlank() } != false) delay(FIRST_FRAME_POLL_MS)
                SystemClock.elapsedRealtime() - loadStart
            }
            if (firstFrameMs == null) warnings += "no picture reached the surface"

            val perf = PerfSampler()
            val fpsSamples = mutableListOf<Float>()
            var dropped = 0
            val statsJob = scope.launch {
                engine.debugStats.collect { stats ->
                    dropped = stats.droppedFrames
                    stats.renderFps?.let { fpsSamples += it }
                }
            }
            val playStart = SystemClock.elapsedRealtime()
            repeat(PLAY_SECONDS) {
                delay(1000)
                perf.sample()
            }
            val playedSec = (SystemClock.elapsedRealtime() - playStart) / MS_IN_SECOND
            engine.pause()
            statsJob.cancel()

            onStage(BenchStage.CAPTURING)
            captures += captureFrames(engine, view, timestamps, workDir, config.mode, warnings)

            metrics = BenchMetrics(
                startupMs = startupMs,
                firstFrameMs = firstFrameMs,
                playSeconds = playedSec,
                droppedFrames = dropped,
                renderFps = fpsSamples.takeIf { it.isNotEmpty() }?.let { it.sum() / it.size },
                cpuAvgPercent = perf.cpuAvg(),
                cpuPeakPercent = perf.cpuPeak(),
                rssAvgMb = perf.rssAvgMb(),
                rssPeakMb = perf.rssPeakMb(),
                thermalStatus = thermalStatus(),
                thermalHeadroom = thermalHeadroom(),
                batteryTempC = batteryTempC(),
            )
        } finally {
            // A reference engine must let go before the next engine takes the shared surface.
            runCatching { detachReferenceEngineSurface(engine) }
            runCatching { engine.release() }
        }
        return BenchModeResult(
            mode = config.mode,
            engine = config.engine.name,
            presetId = config.profile.id,
            presetName = config.profile.name,
            metrics = metrics,
            captures = captures,
            warnings = warnings,
        )
    }

    /** Seeks to each capture point and stores the settled frame; returns bundle-relative paths. */
    @Suppress("LongParameterList")
    private suspend fun captureFrames(
        engine: PlayerEngine,
        view: SurfaceView,
        timestamps: List<Long>,
        workDir: File,
        mode: String,
        warnings: MutableList<String>,
    ): List<String> {
        val modeDir = File(workDir, "captures/$mode").apply { mkdirs() }
        val stored = mutableListOf<String>()
        timestamps.forEachIndexed { index, timestamp ->
            engine.seekTo(timestamp)
            val bitmap = captureSettled(view)
            if (bitmap == null) {
                warnings += "capture t$index (@${timestamp}ms) timed out"
                return@forEachIndexed
            }
            val file = File(modeDir, "t$index.png")
            withContext(Dispatchers.IO) {
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
            }
            bitmap.recycle()
            stored += "captures/$mode/t$index.png"
        }
        return stored
    }

    private fun failed(config: BenchConfigItem, warnings: List<String>) = BenchModeResult(
        mode = config.mode,
        engine = config.engine.name,
        presetId = config.profile.id,
        presetName = config.profile.name,
        metrics = BenchMetrics(),
        captures = emptyList(),
        warnings = warnings,
    )

    private fun attachSurface(engine: PlayerEngine, view: SurfaceView) {
        when (engine) {
            is Media3PlayerEngine -> engine.player.setVideoSurfaceHolder(view.holder)
            else -> if (!attachReferenceEngineSurface(engine, view)) {
                error("engine ${engine.javaClass.simpleName} has no surface output")
            }
        }
    }

    /** One [PixelCopy] of the surface buffer at its fixed 1920×1080 size. */
    private suspend fun capture(view: SurfaceView): Bitmap? = suspendCancellableCoroutine { cont ->
        val bitmap = Bitmap.createBitmap(OUT_WIDTH, OUT_HEIGHT, Bitmap.Config.ARGB_8888)
        runCatching {
            PixelCopy.request(view, bitmap, { result ->
                if (result == PixelCopy.SUCCESS) {
                    cont.resume(bitmap)
                } else {
                    bitmap.recycle()
                    cont.resume(null)
                }
            }, captureHandler)
        }.onFailure {
            bitmap.recycle()
            cont.resume(null)
        }
    }

    /**
     * Captures once the picture stops changing: an exact seek renders
     * intermediate frames while decoding toward the target, so the first copy
     * after a seek is often the wrong frame.
     */
    private suspend fun captureSettled(view: SurfaceView): Bitmap? {
        var previous: Bitmap? = null
        var previousSignature = 0L
        val deadline = SystemClock.elapsedRealtime() + CAPTURE_TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            delay(CAPTURE_POLL_MS)
            val current = capture(view) ?: continue
            val signature = current.signature()
            if (previous != null && signature == previousSignature && !current.looksBlank()) {
                previous.recycle()
                return current
            }
            previous?.recycle()
            previous = current
            previousSignature = signature
        }
        previous?.recycle()
        return null
    }

    /** CPU (jiffies of this process) and RSS sampling via /proc/self. */
    private class PerfSampler {
        private val clkTck = Os.sysconf(OsConstants._SC_CLK_TCK).toFloat()
        private val pageSize = Os.sysconf(OsConstants._SC_PAGESIZE)
        private var lastJiffies = readJiffies()
        private var lastAtMs = SystemClock.elapsedRealtime()
        private val cpu = mutableListOf<Float>()
        private val rssMb = mutableListOf<Float>()

        fun sample() {
            val jiffies = readJiffies()
            val now = SystemClock.elapsedRealtime()
            val elapsedSec = (now - lastAtMs) / MS_IN_SECOND
            if (elapsedSec > 0 && jiffies >= lastJiffies) {
                cpu += (jiffies - lastJiffies) / clkTck / elapsedSec * PERCENT
            }
            lastJiffies = jiffies
            lastAtMs = now
            readRssPages()?.let { rssMb += it * pageSize / MB.toFloat() }
        }

        fun cpuAvg(): Float? = cpu.takeIf { it.isNotEmpty() }?.let { it.sum() / it.size }
        fun cpuPeak(): Float? = cpu.maxOrNull()
        fun rssAvgMb(): Float? = rssMb.takeIf { it.isNotEmpty() }?.let { it.sum() / it.size }
        fun rssPeakMb(): Float? = rssMb.maxOrNull()

        /** utime+stime of the process: fields 14/15 of /proc/self/stat after the comm field. */
        private fun readJiffies(): Long = runCatching {
            val stat = File("/proc/self/stat").readText()
            val fields = stat.substringAfterLast(')').trim().split(' ')
            fields[UTIME_INDEX].toLong() + fields[STIME_INDEX].toLong()
        }.getOrDefault(0L)

        private fun readRssPages(): Long? = runCatching {
            File("/proc/self/statm").readText().trim().split(' ')[1].toLong()
        }.getOrNull()

        private companion object {
            // Field indexes after the ')' of the comm field, 0-based: utime=11, stime=12.
            const val UTIME_INDEX = 11
            const val STIME_INDEX = 12
            const val MB = 1024 * 1024
        }
    }

    private fun thermalStatus(): Int? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) powerManager?.currentThermalStatus else null

    private fun thermalHeadroom(): Float? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            powerManager?.getThermalHeadroom(0)?.takeUnless { it.isNaN() }
        } else {
            null
        }

    private fun batteryTempC(): Float? {
        val intent = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val tenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
        return if (tenths > 0) tenths / TENTHS else null
    }

    private val powerManager: PowerManager?
        get() = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager

    private fun packBundle(workDir: File, manifest: BenchManifest): File {
        File(workDir, "manifest.json").writeText(json.encodeToString(BenchManifest.serializer(), manifest))
        val outDir = File(appContext.filesDir, "bench").apply { mkdirs() }
        val stamp = android.text.format.DateFormat.format("yyyyMMdd-HHmmss", manifest.createdAtMs)
        val model = manifest.device.model.replace(Regex("[^A-Za-z0-9._-]+"), "_")
        val bundle = File(outDir, "sakuro-bench_${model}_$stamp.zip")
        ZipOutputStream(bundle.outputStream().buffered()).use { zip ->
            workDir.walkTopDown().filter { it.isFile }.forEach { file ->
                zip.putNextEntry(ZipEntry(file.relativeTo(workDir).invariantSeparatorsPath))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return bundle
    }

    /** Capture points: 20/50/80% of the duration, floored to whole seconds (mpv seeks in seconds). */
    private fun captureTimestamps(durationMs: Long): List<Long> {
        if (durationMs < MIN_DURATION_FOR_SPREAD_MS) {
            return listOf((durationMs / 2 / MS_IN_SECOND_L) * MS_IN_SECOND_L)
        }
        return listOf(0.2, 0.5, 0.8).map { fraction ->
            (durationMs * fraction).toLong() / MS_IN_SECOND_L * MS_IN_SECOND_L
        }
    }

    private fun keepScreenOn(on: Boolean) {
        val window = activity?.window ?: return
        if (on) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    companion object {
        const val OUT_WIDTH = 1920
        const val OUT_HEIGHT = 1080

        private const val PLAY_SECONDS = 8
        private const val LOAD_TIMEOUT_MS = 30_000L
        private const val INFO_TIMEOUT_MS = 3_000L
        private const val PNG_QUALITY = 100
        private const val FIRST_FRAME_TIMEOUT_MS = 10_000L
        private const val FIRST_FRAME_POLL_MS = 100L
        private const val CAPTURE_TIMEOUT_MS = 10_000L
        private const val CAPTURE_POLL_MS = 250L
        private const val MIN_DURATION_FOR_SPREAD_MS = 5_000L
        private const val MS_IN_SECOND = 1000f
        private const val MS_IN_SECOND_L = 1000L
        private const val PERCENT = 100f
        private const val TENTHS = 10f

        private val json = Json {
            prettyPrint = true
            // The manifest is a wire format for sakuro-bench: keep empty lists
            // and unmeasured metrics explicit instead of omitting them.
            encodeDefaults = true
        }

        private val captureThread = HandlerThread("sakuro-bench-capture").apply { start() }
        private val captureHandler = Handler(captureThread.looper)
    }
}

/** Runs [block] on the bitmap and recycles it afterwards. */
private inline fun <T> Bitmap.use(block: (Bitmap) -> T): T = try {
    block(this)
} finally {
    recycle()
}

/** A cheap content signature over a sparse pixel grid — for "has the picture settled?". */
private fun Bitmap.signature(): Long {
    var hash = 1125899906842597L // FNV-ish seed
    var y = 0
    while (y < height) {
        var x = 0
        while (x < width) {
            hash = hash * 31 + getPixel(x, y)
            x += SIGNATURE_STEP
        }
        y += SIGNATURE_STEP
    }
    return hash
}

/** True when the frame carries no picture yet (the surface is still cleared to black). */
private fun looksBlankImpl(bitmap: Bitmap): Boolean {
    var y = 0
    while (y < bitmap.height) {
        var x = 0
        while (x < bitmap.width) {
            val pixel = bitmap.getPixel(x, y)
            val luma = ((pixel shr 16 and 0xFF) + (pixel shr 8 and 0xFF) + (pixel and 0xFF)) / 3
            if (luma > BLANK_LUMA) return false
            x += SIGNATURE_STEP
        }
        y += SIGNATURE_STEP
    }
    return true
}

private fun Bitmap.looksBlank(): Boolean = looksBlankImpl(this)

private const val SIGNATURE_STEP = 64
private const val BLANK_LUMA = 12

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
