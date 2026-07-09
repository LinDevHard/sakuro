package com.rinwave.sakuro.engine.media3.usershader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ShaderGraphPlannerTest {

    @Suppress("LongParameterList")
    private fun pass(
        desc: String,
        hooks: List<String> = listOf("MAIN"),
        binds: List<String>,
        save: String,
        width: String? = null,
        height: String? = null,
        whenExpr: String? = null,
        offset: PassOffset? = null,
        compute: ComputeLayout? = null,
    ) = UserShaderPass(
        desc = desc,
        hooks = hooks,
        binds = binds,
        save = save,
        width = width?.let { RpnExpression.parse(it) },
        height = height?.let { RpnExpression.parse(it) },
        components = 4,
        condition = whenExpr?.let { RpnExpression.parse(it) },
        offset = offset,
        compute = compute,
        body = "vec4 hook() { return vec4(0.0); }",
    )

    private fun plan(
        passes: List<UserShaderPass>,
        inputWidth: Int,
        inputHeight: Int,
        outputWidth: Int,
        outputHeight: Int,
    ) = ShaderGraphPlanner.plan(ShaderDocument.of(passes), inputWidth, inputHeight, outputWidth, outputHeight)

    private val upscaleWhen = "OUTPUT.w MAIN.w / 1.200 > OUTPUT.h MAIN.h / 1.200 > *"

    private val chain = listOf(
        pass(
            "conv0", binds = listOf("MAIN"), save = "conv2d_tf",
            width = "MAIN.w", height = "MAIN.h", whenExpr = upscaleWhen,
        ),
        pass(
            "conv1", binds = listOf("conv2d_tf"), save = "conv2d_last_tf",
            width = "conv2d_tf.w", height = "conv2d_tf.h", whenExpr = upscaleWhen,
        ),
        pass(
            "d2s", binds = listOf("MAIN", "conv2d_last_tf"), save = "MAIN",
            width = "conv2d_last_tf.w 2 *", height = "conv2d_last_tf.h 2 *", whenExpr = upscaleWhen,
        ),
    )

    @Test
    fun `depth-to-space doubles the final MAIN`() {
        val plan = plan(chain, 640, 360, 1920, 1080)
        assertEquals(3, plan.passes.size)
        assertEquals(1280, plan.outputWidth)
        assertEquals(720, plan.outputHeight)
    }

    @Test
    fun `intermediate passes inherit the input size`() {
        val plan = plan(chain, 640, 360, 1920, 1080)
        assertEquals(640 to 360, plan.passes[0].outWidth to plan.passes[0].outHeight)
        assertEquals(1280 to 720, plan.passes[2].outWidth to plan.passes[2].outHeight)
    }

    @Test
    fun `a false WHEN cuts all passes and MAIN stays the source`() {
        // OUTPUT == input → upscale is not applied.
        val plan = plan(chain, 640, 360, 640, 360)
        assertEquals(0, plan.passes.size)
        assertEquals(640, plan.outputWidth)
        assertEquals(360, plan.outputHeight)
    }

    @Test
    fun `PREKERNEL fires after MAIN hooks regardless of document order (mpv pipeline order)`() {
        val clampFileFirst = listOf(
            // Clamp: HOOK PREKERNEL, first in the document — but it must run last,
            // and its in-place resize lands on the shared MAIN slot.
            pass(
                "clamp", hooks = listOf("PREKERNEL"), binds = listOf("HOOKED"),
                save = MpvUserShaderParser.HOOKED, width = "HOOKED.w 2 *", height = "HOOKED.h",
            ),
            pass(
                "read-main", hooks = listOf("MAIN"), binds = listOf("MAIN"),
                save = "MAIN", width = "MAIN.w", height = "MAIN.h",
            ),
        )
        val plan = plan(clampFileFirst, 640, 360, 1920, 1080)
        assertEquals(listOf("read-main", "clamp"), plan.passes.map { it.pass.desc })
        // read-main ran before the clamp and saw the source size.
        assertEquals(640 to 360, plan.passes[0].outWidth to plan.passes[0].outHeight)
        // The clamp's PREKERNEL write is the frame slot — the final MAIN is doubled.
        assertEquals(1280 to 360, plan.outputWidth to plan.outputHeight)
    }

    @Test
    fun `HOOKED in SAVE and WIDTH resolves to the hooked stage`() {
        val clamp = listOf(
            pass(
                "clamp", hooks = listOf("MAIN"), binds = listOf("HOOKED"),
                save = MpvUserShaderParser.HOOKED, width = "HOOKED.w", height = "HOOKED.h",
            ),
        )
        val plan = plan(clamp, 800, 450, 1920, 1080)
        assertEquals(1, plan.passes.size)
        assertEquals(800 to 450, plan.passes[0].outWidth to plan.passes[0].outHeight)
    }

    @Test
    fun `a pass hooking a stage absent from the pipeline is skipped like in mpv`() {
        val passes = listOf(
            pass("luma-only", hooks = listOf("LUMA"), binds = listOf("HOOKED"), save = "HOOKED"),
            pass("main", hooks = listOf("MAIN"), binds = listOf("MAIN"), save = "MAIN"),
        )
        val plan = plan(passes, 640, 360, 1920, 1080)
        assertEquals(1, plan.passes.size)
        assertEquals("main", plan.passes.single().pass.desc)
        assertEquals(listOf("luma-only"), plan.skipped)
    }

    @Test
    fun `of several hooks only the ones existing in the pipeline fire`() {
        val passes = listOf(
            pass("multi", hooks = listOf("LUMA", "MAIN"), binds = listOf("HOOKED"), save = "HOOKED"),
        )
        val plan = plan(passes, 640, 360, 1920, 1080)
        assertEquals("MAIN", plan.passes.single().stage)
        assertTrue(plan.skipped.isEmpty())
    }

    @Test
    fun `a pass hooking two live stages fires once per hook point`() {
        val passes = listOf(
            pass("both", hooks = listOf("MAIN", "OUTPUT"), binds = listOf("HOOKED"), save = "HOOKED"),
        )
        val plan = plan(passes, 640, 360, 1920, 1080)
        assertEquals(2, plan.passes.size)
        assertEquals(listOf("MAIN", "POSTKERNEL"), plan.passes.map { it.stage })
    }

    @Test
    fun `a compute pass fails the plan until phase 5`() {
        val passes = listOf(
            pass(
                "cas", binds = listOf("MAIN"), save = "MAIN",
                compute = ComputeLayout(32, 8, 32, 8),
            ),
        )
        assertFailsWith<UserShaderException> { plan(passes, 640, 360, 1920, 1080) }
    }

    @Test
    fun `an OUTPUT hook runs on the post slot after MAIN passes at the final size`() {
        val passes = listOf(
            // Document order: sharpen (OUTPUT) first, upscale (MAIN) second.
            pass("sharpen", hooks = listOf("OUTPUT"), binds = listOf("HOOKED"), save = "HOOKED"),
            pass(
                "upscale", hooks = listOf("MAIN"), binds = listOf("MAIN"), save = "MAIN",
                width = "MAIN.w 2 *", height = "MAIN.h 2 *",
            ),
        )
        val plan = plan(passes, 640, 360, 1920, 1080)
        assertEquals(listOf("upscale", "sharpen"), plan.passes.map { it.pass.desc })
        // The post slot starts as the upscaled MAIN.
        assertEquals(1280 to 720, plan.passes[1].outWidth to plan.passes[1].outHeight)
        assertEquals("POSTKERNEL", plan.presentSlot)
        assertEquals(1280 to 720, plan.outputWidth to plan.outputHeight)
    }

    @Test
    fun `resizing a post stage in place fails the plan (spec - only the MAIN family resizes)`() {
        val passes = listOf(
            pass(
                "bad", hooks = listOf("OUTPUT"), binds = listOf("HOOKED"), save = "HOOKED",
                width = "HOOKED.w 2 *", height = "HOOKED.h",
            ),
        )
        assertFailsWith<UserShaderException> { plan(passes, 640, 360, 1920, 1080) }
    }

    @Test
    fun `a LINEAR hook is bracketed by synthetic linearize and delinearize`() {
        val passes = listOf(
            pass("soften", hooks = listOf("LINEAR"), binds = listOf("HOOKED"), save = "HOOKED"),
        )
        val plan = plan(passes, 640, 360, 1920, 1080)
        assertEquals(listOf("<linearize>", "soften", "<delinearize>"), plan.passes.map { it.pass.desc })
        assertEquals("MAIN", plan.presentSlot)
        assertEquals(640 to 360, plan.outputWidth to plan.outputHeight)
    }

    @Test
    fun `a SIGMOID hook gets the full linearize-sigmoidize bracket`() {
        val passes = listOf(
            pass("ring-free", hooks = listOf("SIGMOID"), binds = listOf("HOOKED"), save = "HOOKED"),
            pass("post", hooks = listOf("POSTKERNEL"), binds = listOf("HOOKED"), save = "HOOKED"),
        )
        val plan = plan(passes, 640, 360, 1920, 1080)
        assertEquals(
            listOf("<linearize>", "<sigmoidize>", "ring-free", "<desigmoidize>", "<delinearize>", "post"),
            plan.passes.map { it.pass.desc },
        )
    }

    @Test
    fun `a WHEN-cut LINEAR pass leaves no synthetic conversions behind`() {
        val passes = listOf(
            pass(
                "never", hooks = listOf("LINEAR"), binds = listOf("HOOKED"), save = "HOOKED",
                whenExpr = "0",
            ),
        )
        val plan = plan(passes, 640, 360, 1920, 1080)
        assertTrue(plan.passes.isEmpty())
    }

    @Test
    fun `OFFSET of in-place frame writes accumulates and ALIGN resets it`() {
        val shifted = plan(
            listOf(
                pass(
                    "shift1", binds = listOf("MAIN"), save = "MAIN",
                    offset = PassOffset(0.5f, -0.5f, align = false),
                ),
                pass(
                    "shift2", binds = listOf("MAIN"), save = "MAIN",
                    offset = PassOffset(1f, 0f, align = false),
                ),
                // An offset on a named SAVE (an intermediate) does not move the frame.
                pass(
                    "aux", binds = listOf("MAIN"), save = "scratch",
                    offset = PassOffset(100f, 100f, align = false),
                ),
            ),
            640, 360, 1920, 1080,
        )
        assertEquals(1.5f to -0.5f, shifted.offsetX to shifted.offsetY)

        val aligned = plan(
            listOf(
                pass(
                    "shift", binds = listOf("MAIN"), save = "MAIN",
                    offset = PassOffset(2f, 2f, align = false),
                ),
                pass(
                    "align", binds = listOf("MAIN"), save = "MAIN",
                    offset = PassOffset(0f, 0f, align = true),
                ),
            ),
            640, 360, 1920, 1080,
        )
        assertEquals(0f to 0f, aligned.offsetX to aligned.offsetY)
    }

    @Test
    fun `binding a declared TEXTURE fails the plan until phase 4`() {
        val document = ShaderDocument(
            passes = listOf(pass("lut-read", binds = listOf("MAIN", "LUT"), save = "MAIN")),
            textures = listOf(
                ShaderTexture(
                    name = "LUT", width = 2, height = 2, depth = null, format = "rgba16f",
                    filterLinear = false, border = "CLAMP", storage = false, data = ByteArray(0),
                ),
            ),
            buffers = emptyList(),
            params = emptyList(),
        )
        assertFailsWith<UserShaderException> { ShaderGraphPlanner.plan(document, 640, 360, 1920, 1080) }
    }

    @Test
    fun `bare PARAM names and long size fields resolve in WHEN`() {
        val document = ShaderDocument(
            passes = listOf(
                pass(
                    "gated", binds = listOf("MAIN"), save = "MAIN",
                    whenExpr = "OUTPUT.width MAIN.width / factor >",
                ),
            ),
            textures = emptyList(),
            buffers = emptyList(),
            params = listOf(
                ShaderParam(
                    name = "factor", desc = "", type = "float", define = false, dynamic = false,
                    constant = false, enum = false, minimum = null, maximum = null, default = "2.0",
                ),
            ),
        )
        // OUTPUT/MAIN = 3 > 2 → the pass stays.
        val plan = ShaderGraphPlanner.plan(document, 640, 360, 1920, 1080)
        assertEquals(1, plan.passes.size)
        // OUTPUT/MAIN = 1 < 2 → the pass is cut.
        val cut = ShaderGraphPlanner.plan(document, 640, 360, 640, 360)
        assertTrue(cut.passes.isEmpty())
    }

    @Test
    fun `an undefined bind fails the plan`() {
        val passes = listOf(pass("broken", binds = listOf("NOPE"), save = "MAIN"))
        assertFailsWith<UserShaderException> { plan(passes, 640, 360, 1920, 1080) }
    }
}
