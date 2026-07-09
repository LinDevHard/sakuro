package com.rinwave.sakuro.engine.media3.usershader

import kotlin.test.Test
import kotlin.test.assertEquals
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
}
