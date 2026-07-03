package com.rinwave.sakuro.engine.mpv

import com.rinwave.sakuro.core.upscale.BuiltInPresets
import com.rinwave.sakuro.core.upscale.ContentClass
import com.rinwave.sakuro.core.upscale.UpscalePass
import com.rinwave.sakuro.core.upscale.UpscaleProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MpvUpscalePropertiesTest {

    private fun props(profile: UpscaleProfile): Map<String, String> =
        buildMpvUpscaleProperties(profile).toMap()

    @Test
    fun `пресет Выкл сбрасывает всё в нейтральные значения`() {
        val props = props(BuiltInPresets.OFF)

        assertEquals("bilinear", props["scale"])
        assertEquals("bilinear", props["cscale"])
        assertEquals("0.0", props["sharpen"])
    }

    @Test
    fun `Upscale-проход включает качественный скейлер, фактор не участвует`() {
        val hd = props(BuiltInPresets.ANIME_HD)

        assertEquals("ewa_lanczossharp", hd["scale"])
        assertEquals("ewa_lanczossharp", hd["cscale"])
        assertEquals("0.5", hd["sharpen"])
    }

    @Test
    fun `Denoise-проход деградирует — vf не задаётся`() {
        val sd = props(BuiltInPresets.ANIME_SD)

        assertFalse("vf" in sd)
        assertEquals("0.8", sd["sharpen"])
        assertEquals("ewa_lanczossharp", sd["scale"])
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
