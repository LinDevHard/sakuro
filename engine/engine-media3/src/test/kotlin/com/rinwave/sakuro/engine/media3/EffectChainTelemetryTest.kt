package com.rinwave.sakuro.engine.media3

import com.rinwave.sakuro.core.player.UpscaleRuntimeState
import com.rinwave.sakuro.engine.media3.usershader.RuntimeShaderPass
import com.rinwave.sakuro.engine.media3.usershader.ShaderGraphRuntimeStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EffectChainTelemetryTest {

    @Test
    fun `an unconfigured shader is pending instead of reporting profile guesses`() {
        val telemetry = EffectChainTelemetry()
        telemetry.addShader("custom.glsl")

        val stats = telemetry.snapshot(640, 360)

        assertTrue(stats.pending)
        assertEquals(UpscaleRuntimeState.PENDING, stats.runtimeState)
        assertNull(stats.outputWidth)
        assertNull(stats.outputHeight)
        assertTrue(stats.effects.isEmpty())
    }

    @Test
    fun `runtime graph drives output resolution and effect descriptions`() {
        val telemetry = EffectChainTelemetry()
        val configured = telemetry.addShader("custom.glsl")
        configured(
            ShaderGraphRuntimeStats(
                outputWidth = 1280,
                outputHeight = 720,
                passes = listOf(
                    RuntimeShaderPass("restore", 640, 360),
                    RuntimeShaderPass("upscale", 1280, 720),
                ),
            ),
        )

        val stats = telemetry.snapshot(640, 360)

        assertFalse(stats.pending)
        assertEquals(UpscaleRuntimeState.APPLIED, stats.runtimeState)
        assertEquals(1280, stats.outputWidth)
        assertEquals(720, stats.outputHeight)
        assertEquals(
            listOf("custom.glsl: restore 640x360", "custom.glsl: upscale 1280x720"),
            stats.effects,
        )
    }

    @Test
    fun `presentation is applied after the actual shader output`() {
        val telemetry = EffectChainTelemetry()
        val configured = telemetry.addShader("RAVU")
        telemetry.addPresentation(900)
        configured(
            ShaderGraphRuntimeStats(
                outputWidth = 1280,
                outputHeight = 720,
                passes = listOf(RuntimeShaderPass("ravu", 1280, 720)),
            ),
        )

        val stats = telemetry.snapshot(640, 360)

        assertEquals(1600, stats.outputWidth)
        assertEquals(900, stats.outputHeight)
        assertEquals("Presentation: 1600x900", stats.effects.last())
    }

    @Test
    fun `degraded graph reports passthrough and its reason`() {
        val telemetry = EffectChainTelemetry()
        val configured = telemetry.addShader("custom.glsl")
        configured(
            ShaderGraphRuntimeStats(
                outputWidth = 640,
                outputHeight = 360,
                passes = emptyList(),
                degradedReason = "FP16 unavailable",
            ),
        )

        val stats = telemetry.snapshot(640, 360)

        assertEquals(640, stats.outputWidth)
        assertEquals(360, stats.outputHeight)
        assertEquals(listOf("custom.glsl: passthrough"), stats.effects)
        assertEquals(listOf("custom.glsl: FP16 unavailable"), stats.degradedReasons)
        assertEquals(UpscaleRuntimeState.PASSTHROUGH, stats.runtimeState)
    }

    @Test
    fun `an empty effect chain reports inactive processing`() {
        val stats = EffectChainTelemetry().snapshot(640, 360)

        assertEquals(UpscaleRuntimeState.INACTIVE, stats.runtimeState)
    }
}
