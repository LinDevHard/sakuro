package com.rinwave.sakuro.playback

import com.rinwave.sakuro.AppInfo
import com.rinwave.sakuro.core.player.DebugStats
import com.rinwave.sakuro.core.upscale.BuiltInPresets

/** Allowlisted technical snapshot. [platform] must come from rememberDiagnosticPlatform. */
fun buildDiagnosticReport(stats: DebugStats, errorCode: String?, presetId: String, platform: String): String {
    val engine = stats.engine.takeIf { it in diagnosticEngines } ?: "unknown"
    val preset = when {
        presetId == "auto" -> "auto"
        BuiltInPresets.byId(presetId) != null -> presetId
        else -> "custom"
    }
    val error = if (errorCode == null) "none" else errorCode.takeIf { diagnosticError.matches(it) } ?: "unknown"
    return buildString {
        appendLine("Sakuro ${AppInfo.VERSION} diagnostic report")
        appendLine("Platform: $platform")
        appendLine("Engine: $engine")
        appendLine("Video codec: ${diagnosticCodec(stats.videoCodec)}")
        appendLine("Source resolution: ${diagnosticResolution(stats.sourceResolution)}")
        appendLine("Output resolution: ${diagnosticResolution(stats.outputResolution)}")
        appendLine("Video FPS: ${diagnosticFps(stats.videoFps)}")
        appendLine("Render FPS: ${diagnosticFps(stats.renderFps)}")
        appendLine("Dropped frames: ${stats.droppedFrames.coerceAtLeast(0)}")
        appendLine("Rendered frames: ${stats.renderedFrames?.coerceAtLeast(0) ?: "unknown"}")
        appendLine("Bitrate kbps: ${stats.bitrateKbps?.coerceAtLeast(0) ?: "unknown"}")
        appendLine("Audio codec: ${diagnosticCodec(stats.audioCodec)}")
        appendLine("Audio channels: ${stats.audioChannels?.coerceAtLeast(0) ?: "unknown"}")
        appendLine("Audio sample rate Hz: ${stats.audioSampleRateHz?.coerceAtLeast(0) ?: "unknown"}")
        appendLine("Preset: $preset")
        appendLine("Upscale runtime: ${stats.upscaleRuntimeState.name}")
        appendLine("Error: $error")
    }
}

private fun diagnosticResolution(value: String?): String =
    value?.takeIf { resolution.matches(it) } ?: "unknown"

private fun diagnosticFps(value: Float?): String =
    value?.takeIf { it.isFinite() && it >= 0f }?.toString() ?: "unknown"

/** Never returns the original codec string, which may originate in media metadata. */
private fun diagnosticCodec(value: String?): String {
    val codec = value?.lowercase() ?: return "unknown"
    return codecAliases[codec]
        ?: codecPrefixes.entries.firstOrNull { codec.startsWith(it.key) }?.value
        ?: "unknown"
}

private val codecAliases = mapOf(
    "h264" to "AVC", "avc" to "AVC", "video/avc" to "AVC",
    "hevc" to "HEVC", "h265" to "HEVC", "video/hevc" to "HEVC",
    "av1" to "AV1", "video/av01" to "AV1", "fake/av1" to "AV1",
    "vp9" to "VP9", "video/x-vnd.on2.vp9" to "VP9",
    "vp8" to "VP8", "video/x-vnd.on2.vp8" to "VP8",
    "mpeg4" to "MPEG-4", "video/mp4v-es" to "MPEG-4",
    "mpeg2video" to "MPEG-2", "video/mpeg2" to "MPEG-2",
    "aac" to "AAC", "audio/mp4a-latm" to "AAC",
    "opus" to "Opus", "audio/opus" to "Opus", "fake/opus" to "Opus",
    "vorbis" to "Vorbis", "audio/vorbis" to "Vorbis",
    "mp3" to "MP3", "audio/mpeg" to "MP3", "flac" to "FLAC", "audio/flac" to "FLAC",
)
private val codecPrefixes = mapOf(
    "avc1." to "AVC", "hvc1." to "HEVC", "hev1." to "HEVC",
    "av01." to "AV1", "vp09." to "VP9", "mp4a." to "AAC",
)

private val diagnosticEngines = setOf("Media3/ExoPlayer", "libmpv", "FakePlayerEngine")
private val diagnosticError = Regex("ERROR_CODE_[A-Z0-9_]+")
private val resolution = Regex("[0-9]{1,5}x[0-9]{1,5}")
