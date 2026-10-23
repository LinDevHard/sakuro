package com.rinwave.sakuro.engine.media3

import androidx.media3.effect.Presentation
import com.rinwave.sakuro.core.upscale.ContentClass
import com.rinwave.sakuro.core.upscale.UpscalePass
import com.rinwave.sakuro.core.upscale.UpscaleProfile
import com.rinwave.sakuro.engine.media3.usershader.MpvUserShaderParser
import com.rinwave.sakuro.engine.media3.usershader.ShaderDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UpscaleSizingTest {
    private val shader = MpvUserShaderParser.parse(
        "//!HOOK MAIN\n//!BIND HOOKED\nvec4 hook() { return HOOKED_tex(HOOKED_pos); }",
    )

    @Test
    fun `4K input still gets a capped presentation after a doubling shader`() {
        assertEquals(2160, presentationHeight(chain(2160, 2f)))
    }

    @Test
    fun `anime fractional upscale normalizes to the requested height`() {
        assertEquals(1620, presentationHeight(chain(1080, 1.5f, anime = true)))
    }

    @Test
    fun `anime 4K output is capped`() {
        assertEquals(2160, presentationHeight(chain(2160, 2f, anime = true)))
    }

    @Test
    fun `double upscale retains presentation in case shader fails at runtime`() {
        assertEquals(2160, presentationHeight(chain(1080, 2f)))
    }

    @Test
    fun `missing shader still scales to requested size`() {
        assertEquals(1620, presentationHeight(chain(1080, 1.5f, loaded = false)))
    }

    @Test
    fun `unknown source does not create an absolute presentation`() {
        assertFalse(chain(0, 1.5f).effects.any { it is Presentation })
    }

    @Test
    fun `explicit chains retain shader defined dimensions`() {
        val profile = UpscaleProfile(
            "test", "test", passes = listOf(UpscalePass.Upscale(1.5f)), shaderChain = listOf("custom.glsl"),
        )
        val built = UpscaleEffectChain.build(profile, 1080, shader, ShaderDocument.EMPTY) { null }
        assertFalse(built.effects.any { it is Presentation })
        assertFalse(built.sourceDependent)
    }

    @Test
    fun `failed custom chain with anime fallback rebuilds once source becomes known`() {
        val profile = UpscaleProfile(
            "test", "test", contentClass = ContentClass.ANIME,
            passes = listOf(UpscalePass.Upscale(1.5f)), shaderChain = listOf("missing.glsl"),
        )
        val unknown = UpscaleEffectChain.build(profile, 0, null, shader) { null }
        val tracker = EffectSourceSize()
        tracker.onEffectsBuilt(if (unknown.sourceDependent) 0 else -1)
        assertTrue(tracker.needsRebuild(1080))
        assertEquals(1620, presentationHeight(UpscaleEffectChain.build(profile, 1080, null, shader) { null }))
    }

    private fun chain(height: Int, factor: Float, anime: Boolean = false, loaded: Boolean = true) =
        UpscaleEffectChain.build(
            UpscaleProfile(
                "test", "test", contentClass = if (anime) ContentClass.ANIME else ContentClass.UNKNOWN,
                passes = listOf(UpscalePass.Upscale(factor)),
            ),
            height, null, if (anime) shader else ShaderDocument.EMPTY,
        ) { if (loaded) shader else null }

    private fun presentationHeight(chain: UpscaleEffectChain.BuiltChain): Int {
        val presentation = assertIs<Presentation>(chain.effects.last())
        // Inspect the emitted Media3 configuration without invoking Android Matrix on the JVM.
        val height = Presentation::class.java.getDeclaredField("requestedHeightPixels").apply { isAccessible = true }
        return height.getInt(presentation)
    }
}
