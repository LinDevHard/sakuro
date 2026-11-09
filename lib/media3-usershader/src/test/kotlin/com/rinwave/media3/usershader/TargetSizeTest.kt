package com.rinwave.media3.usershader

import androidx.media3.common.util.Size
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * `targetSize` is what `OUTPUT` means to the planner: real upscalers gate
 * themselves on "output larger than the source", so a known display size must
 * be able to switch them off.
 */
class TargetSizeTest {

    private val upscale = MpvUserShaderParser.parse(engineAsset("anime4k/Anime4K_Upscale_CNN_x2_S.glsl").readText())

    private fun plan(inputWidth: Int, inputHeight: Int, target: Size?): GraphPlan {
        val resolved = resolveTargetSize(inputWidth, inputHeight, target)
        return ShaderGraphPlanner.plan(upscale, inputWidth, inputHeight, resolved.width, resolved.height)
    }

    @Test
    fun `without a target the 4x fallback keeps the upscale gate open`() {
        val resolved = resolveTargetSize(1280, 720, null)
        assertEquals(5120, resolved.width)
        assertEquals(2880, resolved.height)

        val plan = plan(1280, 720, null)
        assertEquals(2560, plan.outputWidth)
        assertEquals(1440, plan.outputHeight)
    }

    @Test
    fun `a 4K source on a 1080p screen skips the upscaler`() {
        val plan = plan(3840, 2160, Size(1920, 1080))

        assertTrue(plan.passes.isEmpty(), "upscale passes ran: ${plan.passes.map { it.pass.desc }}")
        assertEquals(3840, plan.outputWidth)
    }

    @Test
    fun `a 720p source on a 1440p screen upscales x2`() {
        val plan = plan(1280, 720, Size(2560, 1440))

        assertTrue(plan.passes.isNotEmpty())
        assertEquals(2560, plan.outputWidth)
        assertEquals(1440, plan.outputHeight)
    }

    @Test
    fun `the effect rejects a non-positive target`() {
        assertFailsWith<IllegalArgumentException> {
            UserShaderGlEffect(ShaderDocument.EMPTY, targetSize = Size(0, 1080))
        }
    }
}
