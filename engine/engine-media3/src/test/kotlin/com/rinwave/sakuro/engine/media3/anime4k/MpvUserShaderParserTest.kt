package com.rinwave.sakuro.engine.media3.anime4k

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MpvUserShaderParserTest {

    // A trimmed but structurally accurate Upscale_CNN_x2_S: two conv passes and
    // a final depth-to-space with a multi-bind of MAIN + conv2d_last_tf.
    private val upscaleS = """
        // MIT License (the license header is discarded)

        //!DESC Anime4K-Upscale-Conv-4x3x3x3
        //!HOOK MAIN
        //!BIND MAIN
        //!SAVE conv2d_tf
        //!WIDTH MAIN.w
        //!HEIGHT MAIN.h
        //!COMPONENTS 4
        //!WHEN OUTPUT.w MAIN.w / 1.200 > OUTPUT.h MAIN.h / 1.200 > *
        #define go_0(x, y) (MAIN_texOff(vec2(x, y)))
        vec4 hook() { return go_0(0.0, 0.0); }

        //!DESC Anime4K-Upscale-Conv-4x3x3x8
        //!HOOK MAIN
        //!BIND conv2d_tf
        //!SAVE conv2d_last_tf
        //!WIDTH conv2d_tf.w
        //!HEIGHT conv2d_tf.h
        //!COMPONENTS 4
        //!WHEN OUTPUT.w MAIN.w / 1.200 > OUTPUT.h MAIN.h / 1.200 > *
        vec4 hook() { return conv2d_tf_tex(conv2d_tf_pos); }

        //!DESC Anime4K-Upscale-Depth-to-Space
        //!HOOK MAIN
        //!BIND MAIN
        //!BIND conv2d_last_tf
        //!SAVE MAIN
        //!WIDTH conv2d_last_tf.w 2 *
        //!HEIGHT conv2d_last_tf.h 2 *
        //!WHEN OUTPUT.w MAIN.w / 1.200 > OUTPUT.h MAIN.h / 1.200 > *
        vec4 hook() { return vec4(0.0) + MAIN_tex(MAIN_pos); }
    """.trimIndent()

    @Test
    fun `three passes are recognized with their directives`() {
        val passes = MpvUserShaderParser.parse(upscaleS)
        assertEquals(3, passes.size)

        val first = passes[0]
        assertEquals("MAIN", first.hook)
        assertEquals(listOf("MAIN"), first.binds)
        assertEquals("conv2d_tf", first.save)
        assertEquals(4, first.components)
        assertTrue(first.body.contains("vec4 hook()"))
        assertTrue(first.condition != null)
    }

    @Test
    fun `the license header before the first pass is discarded`() {
        val passes = MpvUserShaderParser.parse(upscaleS)
        assertTrue(passes.none { it.body.contains("MIT License") })
    }

    @Test
    fun `a depth-to-space multi-bind keeps both inputs in order`() {
        val last = MpvUserShaderParser.parse(upscaleS).last()
        assertEquals(listOf("MAIN", "conv2d_last_tf"), last.binds)
        assertEquals("MAIN", last.save)
    }

    @Test
    fun `SAVE defaults to the hooked stage`() {
        val src = """
            //!DESC Clamp
            //!HOOK MAIN
            //!BIND HOOKED
            vec4 hook() { return HOOKED_tex(HOOKED_pos); }
        """.trimIndent()
        val pass = MpvUserShaderParser.parse(src).single()
        assertEquals(MpvUserShaderParser.HOOKED, pass.save)
        assertEquals(listOf("HOOKED"), pass.binds)
    }
}
