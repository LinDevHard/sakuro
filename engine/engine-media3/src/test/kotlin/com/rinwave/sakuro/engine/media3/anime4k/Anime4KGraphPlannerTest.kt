package com.rinwave.sakuro.engine.media3.anime4k

import kotlin.test.Test
import kotlin.test.assertEquals

class Anime4KGraphPlannerTest {

    @Suppress("LongParameterList")
    private fun pass(
        desc: String,
        hook: String = "MAIN",
        binds: List<String>,
        save: String,
        width: String? = null,
        height: String? = null,
        whenExpr: String? = null,
    ) = UserShaderPass(
        desc = desc,
        hook = hook,
        binds = binds,
        save = save,
        width = width?.let { RpnExpression.parse(it) },
        height = height?.let { RpnExpression.parse(it) },
        components = 4,
        condition = whenExpr?.let { RpnExpression.parse(it) },
        body = "vec4 hook() { return vec4(0.0); }",
    )

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
        val plan = Anime4KGraphPlanner.plan(chain, 640, 360, 1920, 1080)
        assertEquals(3, plan.passes.size)
        assertEquals(1280, plan.outputWidth)
        assertEquals(720, plan.outputHeight)
    }

    @Test
    fun `intermediate passes inherit the input size`() {
        val plan = Anime4KGraphPlanner.plan(chain, 640, 360, 1920, 1080)
        assertEquals(640 to 360, plan.passes[0].outWidth to plan.passes[0].outHeight)
        assertEquals(1280 to 720, plan.passes[2].outWidth to plan.passes[2].outHeight)
    }

    @Test
    fun `a false WHEN cuts all passes and MAIN stays the source`() {
        // OUTPUT == input → upscale is not applied.
        val plan = Anime4KGraphPlanner.plan(chain, 640, 360, 640, 360)
        assertEquals(0, plan.passes.size)
        assertEquals(640, plan.outputWidth)
        assertEquals(360, plan.outputHeight)
    }

    @Test
    fun `writing to the PREKERNEL stage is canonicalized to MAIN (Clamp affects later MAIN passes)`() {
        val clampThenUpscaleReads = listOf(
            // Clamp: HOOK PREKERNEL, SAVE HOOKED(=PREKERNEL→MAIN), doubles the size.
            pass(
                "clamp", hook = "PREKERNEL", binds = listOf("HOOKED"),
                save = MpvUserShaderParser.HOOKED, width = "HOOKED.w 2 *", height = "HOOKED.h",
            ),
            // The next pass reads MAIN and must see the width doubled by Clamp.
            pass(
                "read-main", hook = "MAIN", binds = listOf("MAIN"),
                save = "MAIN", width = "MAIN.w", height = "MAIN.h",
            ),
        )
        val plan = Anime4KGraphPlanner.plan(clampThenUpscaleReads, 640, 360, 1920, 1080)
        assertEquals(2, plan.passes.size)
        // read-main inherited the width written by Clamp via PREKERNEL → MAIN.
        assertEquals(1280 to 360, plan.passes[1].outWidth to plan.passes[1].outHeight)
    }

    @Test
    fun `HOOKED in SAVE and WIDTH resolves to the hooked stage`() {
        val clamp = listOf(
            pass(
                "clamp", hook = "MAIN", binds = listOf("HOOKED"),
                save = MpvUserShaderParser.HOOKED, width = "HOOKED.w", height = "HOOKED.h",
            ),
        )
        val plan = Anime4KGraphPlanner.plan(clamp, 800, 450, 1920, 1080)
        assertEquals(1, plan.passes.size)
        assertEquals(800 to 450, plan.passes[0].outWidth to plan.passes[0].outHeight)
    }
}
