package com.rinwave.sakuro.bench

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rinwave.sakuro.core.player.EngineRegistry
import com.rinwave.sakuro.core.player.EngineType
import com.rinwave.sakuro.core.player.MediaSource
import com.rinwave.sakuro.core.upscale.UpscaleProfile
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/** One benchmarked configuration: an engine rendering a preset or a single shader. */
data class BenchConfigItem(
    val engine: EngineType,
    val profile: UpscaleProfile,
) {
    /**
     * Mode name used for capture files and report rows, e.g. `media3_anime-sd`.
     * It becomes a path inside the bundle, so anything unusual in a preset id
     * (user presets and synthetic shader profiles) is folded to `_`.
     */
    val mode: String get() = "${engine.name.lowercase()}_${profile.id}".replace(UNSAFE_IN_PATH, "_")
}

private val UNSAFE_IN_PATH = Regex("[^A-Za-z0-9._-]+")

/** Stage of the currently benchmarked configuration. */
enum class BenchStage { STARTING, PLAYING, CAPTURING, PACKING }

sealed interface BenchState {
    data object Idle : BenchState

    data class Running(
        val configIndex: Int,
        val configCount: Int,
        val mode: String,
        val stage: BenchStage,
    ) : BenchState

    data class Done(
        val bundlePath: String,
        val bundleName: String,
        val configCount: Int,
        val captureCount: Int,
    ) : BenchState

    data class Failed(val message: String) : BenchState
}

/**
 * Runs the on-device benchmark: renders the chosen video through every
 * configuration on an offscreen surface, measures startup/playback/perf
 * metrics, captures frames at fixed timestamps and packs everything into a
 * single zip bundle for `tools/sakuro-bench` (`device-bundle` command).
 */
interface BenchRunner {
    val state: StateFlow<BenchState>

    fun start(video: MediaSource, configs: List<BenchConfigItem>)

    fun cancel()

    /** Opens the system share sheet for a finished bundle. */
    fun share(bundlePath: String)
}

/** Platform benchmark runner; null — benchmarking is not supported here (desktop). */
@Composable
expect fun rememberBenchRunner(engineRegistry: EngineRegistry): BenchRunner?

/**
 * The surface the benchmark renders into. Compose it while the bench screen is
 * open: captures are read from this surface, so it has to stay on screen for
 * the whole run.
 */
@Composable
expect fun BenchSurface(runner: BenchRunner, modifier: Modifier)

// --- Bundle manifest (schema shared with tools/sakuro-bench) ---

@Serializable
data class BenchManifest(
    val schema: Int = 1,
    val createdAtMs: Long,
    val appVersion: String,
    val device: BenchDevice,
    val video: BenchVideo,
    val outputWidth: Int,
    val outputHeight: Int,
    val captureTimestampsMs: List<Long>,
    val modes: List<BenchModeResult>,
)

@Serializable
data class BenchDevice(
    val manufacturer: String,
    val model: String,
    val sdk: Int,
    val abi: String,
)

@Serializable
data class BenchVideo(
    val title: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
)

@Serializable
data class BenchModeResult(
    val mode: String,
    val engine: String,
    val presetId: String,
    val presetName: String,
    val metrics: BenchMetrics,
    /** Bundle-relative capture paths in timestamp order. */
    val captures: List<String>,
    /** Non-fatal problems (capture timeout, metric unavailable). */
    val warnings: List<String> = emptyList(),
)

@Serializable
data class BenchMetrics(
    /** load() → READY. */
    val startupMs: Long? = null,
    /** load() → first frame on the output surface. */
    val firstFrameMs: Long? = null,
    val playSeconds: Float? = null,
    val droppedFrames: Int? = null,
    /** Frames delivered to the output surface per second of the play segment. */
    val renderFps: Float? = null,
    /** CPU load of the app process, percent of one core (can exceed 100). */
    val cpuAvgPercent: Float? = null,
    val cpuPeakPercent: Float? = null,
    val rssAvgMb: Float? = null,
    val rssPeakMb: Float? = null,
    /** Android thermal status at segment end (0 = none … 6 = shutdown). */
    val thermalStatus: Int? = null,
    /** PowerManager.getThermalHeadroom(0); < 1.0 means margin left. */
    val thermalHeadroom: Float? = null,
    val batteryTempC: Float? = null,
)
