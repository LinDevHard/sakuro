package com.rinwave.sakuro.engine.mpv

import com.rinwave.sakuro.core.upscale.BuiltInPresets
import com.rinwave.sakuro.core.upscale.ContentClass
import com.rinwave.sakuro.core.upscale.UpscalePass
import com.rinwave.sakuro.core.upscale.UpscaleProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MpvUpscalePropertiesTest {

    private fun props(profile: UpscaleProfile): Map<String, String> =
        buildMpvRenderConfig(profile).properties.toMap()

    private fun shaders(profile: UpscaleProfile): List<String> =
        buildMpvRenderConfig(profile).shaders

    @Test
    fun `the Off preset resets everything to neutral values with no shaders`() {
        val props = props(BuiltInPresets.OFF)

        assertEquals("bilinear", props["scale"])
        assertEquals("bilinear", props["cscale"])
        assertEquals("0.0", props["sharpen"])
        assertTrue(shaders(BuiltInPresets.OFF).isEmpty())
    }

    @Test
    fun `an anime preset builds the Anime4K chain in canonical order`() {
        val sd = shaders(BuiltInPresets.ANIME_SD)

        assertEquals(
            listOf(
                "Anime4K_Clamp_Highlights.glsl",
                "Anime4K_Denoise_Bilateral_Mode.glsl",
                "Anime4K_Restore_CNN_M.glsl",
                "Anime4K_Upscale_CNN_x2_M.glsl",
            ),
            sd,
        )
    }

    @Test
    fun `weak passes use the small CNN models`() {
        val hd = shaders(BuiltInPresets.ANIME_HD)

        assertEquals(
            listOf(
                "Anime4K_Clamp_Highlights.glsl",
                "Anime4K_Restore_CNN_S.glsl",
                "Anime4K_Upscale_CNN_x2_S.glsl",
            ),
            hd,
        )
    }

    @Test
    fun `with a shader chain the sharpen property is zeroed — Restore does the sharpening`() {
        val sd = props(BuiltInPresets.ANIME_SD)

        assertEquals("0.0", sd["sharpen"])
        assertEquals("ewa_lanczossharp", sd["scale"])
    }

    @Test
    fun `live-action maps Sharpen onto the bundled CAS and mutes the property`() {
        val light = BuiltInPresets.LIVE_ACTION_LIGHT
        val config = buildMpvRenderConfig(light)

        assertTrue(config.shaders.isEmpty())
        assertEquals(listOf("CAS.glsl"), config.userShaders)
        assertEquals(mapOf("CAS.glsl" to mapOf("SHARPENING" to 0.25f)), config.userShaderParams)
        assertEquals("0.0", config.properties.toMap()["sharpen"])
        assertEquals("bilinear", config.properties.toMap()["scale"])
    }

    @Test
    fun `a non-anime parametric chain follows the canonical order with baked params`() {
        val profile = UpscaleProfile(
            id = "t",
            name = "t",
            contentClass = ContentClass.LIVE_ACTION,
            passes = listOf(
                UpscalePass.Sharpen(0.5f),
                UpscalePass.Denoise(0.4f),
                UpscalePass.Upscale(2f),
            ),
        )
        val config = buildMpvRenderConfig(profile)

        // Denoise → Upscale → Sharpen regardless of the preset's pass order.
        assertEquals(
            listOf("Sakuro_Denoise_Bilateral.glsl", "ravu-r3.hook", "CAS.glsl"),
            config.userShaders,
        )
        assertEquals(mapOf("intensity" to 0.4f), config.userShaderParams["Sakuro_Denoise_Bilateral.glsl"])
        assertEquals(mapOf("SHARPENING" to 0.5f), config.userShaderParams["CAS.glsl"])
        assertFalse("ravu-r3.hook" in config.userShaderParams)
        assertEquals("ewa_lanczossharp", config.properties.toMap()["scale"])
    }

    @Test
    fun `without deployable shaders the config degrades to properties only`() {
        val profile = UpscaleProfile(
            id = "t",
            name = "t",
            contentClass = ContentClass.LIVE_ACTION,
            passes = listOf(UpscalePass.Sharpen(0.25f), UpscalePass.Denoise(0.5f)),
        )
        val config = buildMpvRenderConfig(profile, shadersAvailable = false)

        assertTrue(config.shaders.isEmpty())
        assertTrue(config.userShaders.isEmpty())
        // Sharpen survives as the property; Denoise degrades (the contract).
        assertEquals("0.25", config.properties.toMap()["sharpen"])
    }

    @Test
    fun `an anime preset with Denoise only gets the clamp and denoise shaders`() {
        val profile = UpscaleProfile(
            id = "t",
            name = "t",
            contentClass = ContentClass.CARTOON,
            passes = listOf(UpscalePass.Denoise(0.5f)),
        )

        assertEquals(
            listOf("Anime4K_Clamp_Highlights.glsl", "Anime4K_Denoise_Bilateral_Mode.glsl"),
            shaders(profile),
        )
    }

    @Test
    fun `values are formatted with a dot regardless of locale`() {
        val profile = UpscaleProfile(
            id = "t",
            name = "t",
            contentClass = ContentClass.UNKNOWN,
            passes = listOf(UpscalePass.Sharpen(0.25f)),
        )

        // The properties-only path — the parametric chain otherwise mutes `sharpen`.
        val properties = buildMpvRenderConfig(profile, shadersAvailable = false).properties.toMap()
        assertEquals("0.25", properties["sharpen"])
    }

    @Test
    fun `an explicit chain carries the profile's own PARAM values`() {
        val profile = UpscaleProfile(
            id = "user-2",
            name = "Sharp",
            passes = emptyList(),
            shaderChain = listOf("CAS.glsl"),
            shaderParams = mapOf(
                "CAS.glsl" to mapOf("SHARPENING" to 0.8f),
                // A shader that is not in the chain must not leak into the config.
                "ravu-r3.hook" to mapOf("X" to 1f),
            ),
        )
        val config = buildMpvRenderConfig(profile)

        assertEquals(listOf("CAS.glsl"), config.userShaders)
        assertEquals(mapOf("CAS.glsl" to mapOf("SHARPENING" to 0.8f)), config.userShaderParams)
    }

    @Test
    fun `an explicit shader chain replaces the anime4k selection and mutes sharpen`() {
        val profile = UpscaleProfile(
            id = "user-1",
            name = "FSRCNNX",
            contentClass = ContentClass.ANIME,
            passes = listOf(UpscalePass.Upscale(2f), UpscalePass.Sharpen(0.8f)),
            shaderChain = listOf("FSRCNNX_x2_8-0-4-1.glsl", "KrigBilateral.glsl"),
        )
        val config = buildMpvRenderConfig(profile)
        assertEquals(listOf("FSRCNNX_x2_8-0-4-1.glsl", "KrigBilateral.glsl"), config.userShaders)
        assertEquals(emptyList(), config.shaders)
        // The chain owns the look; upscale still selects the good scaler.
        val properties = config.properties.toMap()
        assertEquals("ewa_lanczossharp", properties["scale"])
        assertEquals("0.0", properties["sharpen"])
    }
}
