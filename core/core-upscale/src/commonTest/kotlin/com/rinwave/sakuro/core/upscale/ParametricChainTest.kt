package com.rinwave.sakuro.core.upscale

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParametricChainTest {

    private fun profile(vararg passes: UpscalePass, shaderChain: List<String> = emptyList()) =
        UpscaleProfile(
            id = "t",
            name = "t",
            contentClass = ContentClass.LIVE_ACTION,
            passes = passes.toList(),
            shaderChain = shaderChain,
        )

    @Test
    fun `passes map onto bundled shaders in canonical order with strengths as params`() {
        val links = ParametricChain.forProfile(
            profile(UpscalePass.Sharpen(0.7f), UpscalePass.Upscale(2f), UpscalePass.Denoise(0.3f)),
        )

        assertEquals(listOf("sakuro-denoise", "ravu-r3", "cas"), links.map { it.shader.id })
        assertEquals(mapOf("intensity" to 0.3f), links[0].params)
        assertTrue(links[1].params.isEmpty())
        assertEquals(mapOf("SHARPENING" to 0.7f), links[2].params)
    }

    @Test
    fun `strengths clamp to the params' 0-1 range`() {
        val links = ParametricChain.forProfile(profile(UpscalePass.Sharpen(1.8f)))
        assertEquals(mapOf("SHARPENING" to 1f), links.single().params)
    }

    @Test
    fun `an explicit shader chain suppresses the parametric mapping`() {
        val links = ParametricChain.forProfile(
            profile(UpscalePass.Sharpen(0.5f), shaderChain = listOf("FSRCNNX_x2_8-0-4-1.glsl")),
        )
        assertTrue(links.isEmpty())
    }

    @Test
    fun `an empty pass list maps to nothing`() {
        assertTrue(ParametricChain.forProfile(profile()).isEmpty())
    }
}
