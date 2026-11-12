package com.rinwave.sakuro.core.upscale

import com.rinwave.sakuro.core.model.DisplayLimitStatus
import com.rinwave.sakuro.core.model.DisplaySize
import com.rinwave.sakuro.core.model.UpscalePass
import com.rinwave.sakuro.core.model.UpscaleProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class DisplayUpscaleLimitTest {

    private val phone1080 = DisplaySize(1080, 2400)
    private val tv4k = DisplaySize(3840, 2160)

    private fun profile(factor: Float, vararg extra: UpscalePass) = UpscaleProfile(
        id = "p",
        name = "p",
        passes = listOf(UpscalePass.Denoise(0.3f), UpscalePass.Upscale(factor)) + extra,
    )

    @Test
    fun `no display means no limit`() {
        assertNull(DisplayUpscaleLimit.apply(profile(2f), 1920, 1080, null))
    }

    @Test
    fun `unknown source size means no decision yet`() {
        assertNull(DisplayUpscaleLimit.apply(profile(2f), 0, 0, phone1080))
    }

    @Test
    fun `fit uses the orientation that shows the video bigger`() {
        assertEquals(1f, DisplayUpscaleLimit.fitScale(1920, 1080, phone1080))
        assertEquals(1f, DisplayUpscaleLimit.fitScale(1920, 1080, DisplaySize(2400, 1080)))
        assertEquals(2f, DisplayUpscaleLimit.fitScale(1920, 1080, tv4k))
    }

    @Test
    fun `1080p on a 1080p phone drops only the upscale step`() {
        val result = DisplayUpscaleLimit.apply(profile(2f, UpscalePass.Sharpen(0.5f)), 1920, 1080, phone1080)!!

        assertEquals(listOf(UpscalePass.Denoise(0.3f), UpscalePass.Sharpen(0.5f)), result.profile.passes)
        assertEquals(DisplayLimitStatus(DisplayLimitStatus.Kind.SKIPPED, 2f, 1f, 1080), result.status)
    }

    @Test
    fun `720p on a 1080p phone is limited to 1_5x`() {
        val result = DisplayUpscaleLimit.apply(profile(2f), 1280, 720, phone1080)!!

        assertEquals(UpscalePass.Upscale(1.5f), result.profile.passes.last())
        assertEquals(DisplayLimitStatus.Kind.LIMITED, result.status?.kind)
        assertEquals(1.5f, result.status?.effectiveFactor)
    }

    @Test
    fun `1080p on a 4K TV keeps the full 2x`() {
        val input = profile(2f)
        val result = DisplayUpscaleLimit.apply(input, 1920, 1080, tv4k)!!

        assertSame(input, result.profile)
        assertNull(result.status)
        assertEquals(2160, result.screenHeight)
    }

    @Test
    fun `a preset without upscale is never touched`() {
        val input = UpscaleProfile(id = "p", name = "p", passes = listOf(UpscalePass.Sharpen(0.5f)))
        val result = DisplayUpscaleLimit.apply(input, 1920, 1080, phone1080)!!

        assertSame(input, result.profile)
        assertNull(result.status)
    }

    @Test
    fun `a shader chain keeps its files and reports the screen size`() {
        val chain = UpscaleProfile(id = "c", name = "c", shaderChain = listOf("FSRCNNX.glsl"))

        val small = DisplayUpscaleLimit.apply(chain, 1920, 1080, phone1080)!!
        assertSame(chain, small.profile)
        assertEquals(DisplayLimitStatus.Kind.SKIPPED, small.status?.kind)

        val big = DisplayUpscaleLimit.apply(chain, 1280, 720, tv4k)!!
        assertNull(big.status)
        assertEquals(3840 to 2160, big.screenWidth to big.screenHeight)
    }

    @Test
    fun `the limited factor never overshoots the panel`() {
        // 1440 / 1080 = 1.333… → 1.33, not 1.34.
        val result = DisplayUpscaleLimit.apply(profile(2f), 1920, 1080, DisplaySize(2560, 1440))!!
        assertEquals(1.33f, result.status?.effectiveFactor)
    }
}
