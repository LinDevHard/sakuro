package com.rinwave.sakuro.engine.media3.usershader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Phase-2 acceptance: real third-party mpv shaders that hook the post-scale
 * stages must parse and plan unmodified (test fixtures in
 * `src/test/resources/usershader/`, original license headers retained):
 *
 * - `adaptive-sharpen.glsl` (bacondither/igv) — `HOOK OUTPUT`, `DESC` after `BIND`;
 * - `SSimSuperRes.glsl` (Shiandow) — four `POSTKERNEL` passes with named
 *   intermediates, `BIND PREKERNEL` and the `NATIVE_CROPPED` RPN pseudo-texture.
 */
class ThirdPartyShaderTest {

    private fun document(resource: String): ShaderDocument {
        val stream = javaClass.getResourceAsStream("/usershader/$resource")
            ?: fail("test resource /usershader/$resource not found")
        return MpvUserShaderParser.parse(stream.bufferedReader().use { it.readText() })
    }

    @Test
    fun `adaptive-sharpen hooks OUTPUT and runs on the post slot at the frame size`() {
        val document = document("adaptive-sharpen.glsl")
        val pass = document.passes.single()
        assertEquals(listOf("OUTPUT"), pass.hooks)
        assertEquals("adaptive-sharpen", pass.desc)

        val plan = ShaderGraphPlanner.plan(document, 1280, 720, 1280 * 4, 720 * 4)
        assertEquals(1, plan.passes.size)
        assertEquals("POSTKERNEL", plan.passes.single().stage)
        assertEquals("POSTKERNEL", plan.presentSlot)
        assertEquals(1280 to 720, plan.outputWidth to plan.outputHeight)
        assertTrue(plan.skipped.isEmpty())

        val fragment = ShaderPreamble.fragmentShader(pass, hook = "OUTPUT", params = document.params)
        assertTrue(fragment.contains("vec4 hook()"))
        assertTrue(fragment.contains("vec4 HOOKED_tex("))
    }

    @Test
    fun `SSimSuperRes plans all four POSTKERNEL passes with NATIVE_CROPPED sizes`() {
        val document = document("SSimSuperRes.glsl")
        assertEquals(4, document.passes.size)

        val plan = ShaderGraphPlanner.plan(document, 640, 360, 1920, 1080)
        assertEquals(4, plan.passes.size, "skipped: ${plan.skipped}")
        assertTrue(plan.passes.all { it.stage == "POSTKERNEL" })
        // Document order is kept within one hook point.
        assertEquals(
            listOf("SSSR Downscaling I", "SSSR Downscaling II", "SSSR var", "SSSR final pass"),
            plan.passes.map { it.pass.desc },
        )
        // LOWRES intermediates are sized by the NATIVE_CROPPED pseudo-texture (the input).
        assertEquals(640 to 360, plan.passes[1].outWidth to plan.passes[1].outHeight)
        // The final in-place pass keeps the post-slot size; the frame presents from POSTKERNEL.
        assertEquals("POSTKERNEL", plan.presentSlot)
        assertEquals(640 to 360, plan.outputWidth to plan.outputHeight)

        for (planned in plan.passes) {
            val fragment = ShaderPreamble.fragmentShader(planned.pass, planned.hook, document.params)
            assertTrue(fragment.contains("void main()"), "'${planned.pass.desc}': no main()")
        }
    }

    @Test
    fun `SSimSuperRes is cut entirely when the output is not larger than the source`() {
        val plan = ShaderGraphPlanner.plan(document("SSimSuperRes.glsl"), 1920, 1080, 1920, 1080)
        assertTrue(plan.passes.isEmpty())
        assertEquals("MAIN", plan.presentSlot)
    }

    @Test
    fun `FSRCNNX doubles the frame through the virtual LUMA plane`() {
        val document = document("FSRCNNX_x2_8-0-4-1.glsl")
        assertTrue(document.passes.isNotEmpty())
        assertTrue(document.passes.all { it.hooks == listOf("LUMA") })

        val plan = ShaderGraphPlanner.plan(document, 640, 360, 640 * 4, 360 * 4)
        assertTrue(plan.skipped.isEmpty(), "skipped: ${plan.skipped}")
        // Extraction opens the planes, the merge closes them.
        assertEquals(listOf("<extract-luma>", "<extract-chroma>"), plan.passes.take(2).map { it.pass.desc })
        assertEquals("<merge-planes>", plan.passes.last().pass.desc)
        assertEquals(document.passes.size + 3, plan.passes.size)
        // The aggregation pass doubled LUMA in place → the merged MAIN is ×2.
        assertEquals(1280 to 720, plan.outputWidth to plan.outputHeight)
        assertEquals("MAIN", plan.presentSlot)

        for (planned in plan.passes) {
            val fragment = ShaderPreamble.fragmentShader(planned.pass, planned.hook, document.params)
            assertTrue(fragment.contains("void main()"), "'${planned.pass.desc}': no main()")
        }
    }

    @Test
    fun `FSRCNNX is cut entirely without upscaling and costs no plane round-trip`() {
        val plan = ShaderGraphPlanner.plan(document("FSRCNNX_x2_8-0-4-1.glsl"), 1920, 1080, 1920, 1080)
        assertTrue(plan.passes.isEmpty())
    }

    @Test
    fun `ravu-r3 plans with its hex weight LUT and doubles the frame`() {
        val document = document("ravu-r3.hook")
        assertEquals(4, document.passes.size)
        val lut = document.textures.single()
        assertEquals("ravu_lut3", lut.name)
        assertEquals(5 to 648, lut.width to lut.height)
        assertEquals("rgba16f", lut.format)
        // Classic-mpv rgba16f = float32 payload: 16 bytes per texel.
        assertEquals(5 * 648 * 16, lut.data!!.size)

        val plan = ShaderGraphPlanner.plan(document, 640, 360, 640 * 4, 360 * 4)
        assertTrue(plan.skipped.isEmpty(), "skipped: ${plan.skipped}")
        assertEquals(listOf("<extract-luma>", "<extract-chroma>"), plan.passes.take(2).map { it.pass.desc })
        assertEquals("<merge-planes>", plan.passes.last().pass.desc)
        // step4 doubles LUMA in place (WIDTH 2 HOOKED.w *) → the merged MAIN is ×2.
        assertEquals(1280 to 720, plan.outputWidth to plan.outputHeight)
        // Its //!OFFSET -0.5 -0.5 accumulates for present compensation.
        assertEquals(-0.5f to -0.5f, plan.offsetX to plan.offsetY)

        for (planned in plan.passes) {
            val fragment = ShaderPreamble.fragmentShader(planned.pass, planned.hook, document.params)
            assertTrue(fragment.contains("void main()"), "'${planned.pass.desc}': no main()")
        }
    }

    @Test
    fun `ravu-lite-r3 plans its compute pass on an ES 3-1 context and degrades without one`() {
        val document = document("ravu-lite-r3.hook")
        val pass = document.passes.single()
        assertEquals(ComputeLayout(64, 16, 32, 8), pass.compute)
        assertEquals(13 to 288, document.textures.single().width to document.textures.single().height)

        val plan = ShaderGraphPlanner.plan(document, 640, 360, 640 * 4, 360 * 4, RuntimeCapabilities.ES31)
        assertTrue(plan.skipped.isEmpty(), "skipped: ${plan.skipped}")
        assertEquals(
            listOf("<extract-luma>", "<extract-chroma>", "RAVU-Lite (r3, compute)", "<merge-planes>"),
            plan.passes.map { it.pass.desc },
        )
        // The compute pass doubles LUMA in place → the merged MAIN is ×2.
        assertEquals(1280 to 720, plan.outputWidth to plan.outputHeight)

        val source = ShaderPreamble.computeShader(pass, "LUMA", document.params)
        assertTrue(source.contains("layout(local_size_x = 32, local_size_y = 8"))
        assertTrue(source.contains("shared float inp[432];"))

        // Without ES 3.1 the whole chain honestly degrades.
        assertFailsWith<UserShaderException> {
            ShaderGraphPlanner.plan(document, 640, 360, 640 * 4, 360 * 4)
        }
    }

    @Test
    fun `KrigBilateral upscales the virtual chroma to the luma size`() {
        val document = document("KrigBilateral.glsl")
        assertEquals(3, document.passes.size)
        assertTrue(document.passes.all { it.hooks == listOf("CHROMA") })

        val plan = ShaderGraphPlanner.plan(document, 640, 360, 1920, 1080)
        assertTrue(plan.skipped.isEmpty(), "skipped: ${plan.skipped}")
        assertEquals(
            listOf(
                "<extract-luma>", "<extract-chroma>",
                "KrigBilateral Downscaling Y pass 1", "KrigBilateral Downscaling Y pass 2",
                "KrigBilateral Upscaling UV", "<merge-planes>",
            ),
            plan.passes.map { it.pass.desc },
        )
        // The UV upscale runs at the luma size; the frame itself does not grow.
        assertEquals(640 to 360, plan.passes[4].outWidth to plan.passes[4].outHeight)
        assertEquals(640 to 360, plan.outputWidth to plan.outputHeight)
        // Its OFFSET ALIGN resets the frame offset.
        assertEquals(0f to 0f, plan.offsetX to plan.offsetY)

        for (planned in plan.passes) {
            val fragment = ShaderPreamble.fragmentShader(planned.pass, planned.hook, document.params)
            assertTrue(fragment.contains("void main()"), "'${planned.pass.desc}': no main()")
        }
    }
}
