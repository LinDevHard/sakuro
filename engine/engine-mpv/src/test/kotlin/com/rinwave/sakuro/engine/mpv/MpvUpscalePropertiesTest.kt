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
    fun `пресет Выкл сбрасывает всё в нейтральные значения без шейдеров`() {
        val props = props(BuiltInPresets.OFF)

        assertEquals("bilinear", props["scale"])
        assertEquals("bilinear", props["cscale"])
        assertEquals("0.0", props["sharpen"])
        assertTrue(shaders(BuiltInPresets.OFF).isEmpty())
    }

    @Test
    fun `аниме-пресет строит цепочку Anime4K в каноническом порядке`() {
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
    fun `слабые проходы берут малые CNN-модели`() {
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
    fun `при цепочке шейдеров свойство sharpen обнуляется — резкость делает Restore`() {
        val sd = props(BuiltInPresets.ANIME_SD)

        assertEquals("0.0", sd["sharpen"])
        assertEquals("ewa_lanczossharp", sd["scale"])
    }

    @Test
    fun `live-action не трогает Anime4K и работает свойствами`() {
        val light = BuiltInPresets.LIVE_ACTION_LIGHT

        assertTrue(shaders(light).isEmpty())
        assertEquals("0.25", props(light)["sharpen"])
        assertEquals("bilinear", props(light)["scale"])
    }

    @Test
    fun `Denoise вне аниме деградирует — vf не задаётся`() {
        val profile = UpscaleProfile(
            id = "t",
            name = "t",
            contentClass = ContentClass.LIVE_ACTION,
            passes = listOf(UpscalePass.Denoise(0.5f)),
        )
        val config = buildMpvRenderConfig(profile)

        assertTrue(config.shaders.isEmpty())
        assertFalse("vf" in config.properties.toMap())
    }

    @Test
    fun `аниме-пресет только с Denoise получает кламп и денойз-шейдер`() {
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
    fun `значения формируются с точкой независимо от локали`() {
        val profile = UpscaleProfile(
            id = "t",
            name = "t",
            contentClass = ContentClass.UNKNOWN,
            passes = listOf(UpscalePass.Sharpen(0.25f)),
        )

        assertEquals("0.25", props(profile)["sharpen"])
    }
}
