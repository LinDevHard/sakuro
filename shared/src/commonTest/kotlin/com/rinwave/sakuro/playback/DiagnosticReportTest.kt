package com.rinwave.sakuro.playback

import com.rinwave.sakuro.AppInfo
import com.rinwave.sakuro.core.player.DebugStats
import com.rinwave.sakuro.core.player.UpscaleRuntimeState
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class DiagnosticReportTest {
    @Test
    fun retainsUsefulAllowlistedDiagnostics() {
        val report = buildDiagnosticReport(
            DebugStats(
                engine = "Media3/ExoPlayer", videoCodec = "avc1.640028", sourceResolution = "1920x1080",
                droppedFrames = 12, renderedFrames = 450, upscaleRuntimeState = UpscaleRuntimeState.PASSTHROUGH,
            ),
            "ERROR_CODE_DECODING_FAILED", "anime-hd", "Android 16; device: Example",
        )
        listOf(
            AppInfo.VERSION, "Media3/ExoPlayer", "AVC", "1920x1080", "12", "450", "PASSTHROUGH",
            "ERROR_CODE_DECODING_FAILED", "anime-hd", "Android 16",
        ).forEach { assertContains(report, it) }
    }

    @Test
    fun neverExportsPrivateFreeFormPlayerFields() {
        val secret = "content://provider/private/My holiday.mp4"
        val report = buildDiagnosticReport(
            DebugStats(
                engine = secret, videoCodec = secret, videoDecoder = secret, sourceResolution = secret,
                outputResolution = secret, colorInfo = secret, audioCodec = secret, upscaleProfile = secret,
                upscalePasses = listOf("/storage/private/shader.glsl"), upscaleRuntimeReason = secret,
                extras = mapOf("title" to secret),
            ),
            "ERROR_CODE_FAILED $secret", secret, "Android 16",
        )
        listOf("content://", "private", "holiday", "shader.glsl").forEach { assertFalse(report.contains(it)) }
        assertContains(report, "Preset: custom")
        assertContains(report, "Error: unknown")
    }
}
