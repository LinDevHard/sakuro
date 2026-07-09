package com.rinwave.sakuro.engine.media3.usershader

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
        val passes = MpvUserShaderParser.parse(upscaleS).passes
        assertEquals(3, passes.size)

        val first = passes[0]
        assertEquals(listOf("MAIN"), first.hooks)
        assertEquals(listOf("MAIN"), first.binds)
        assertEquals("conv2d_tf", first.save)
        assertEquals(4, first.components)
        assertTrue(first.body.contains("vec4 hook()"))
        assertTrue(first.condition != null)
    }

    @Test
    fun `the license header before the first pass is discarded`() {
        val passes = MpvUserShaderParser.parse(upscaleS).passes
        assertTrue(passes.none { it.body.contains("MIT License") })
    }

    @Test
    fun `a depth-to-space multi-bind keeps both inputs in order`() {
        val last = MpvUserShaderParser.parse(upscaleS).passes.last()
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
        val pass = MpvUserShaderParser.parse(src).passes.single()
        assertEquals(MpvUserShaderParser.HOOKED, pass.save)
        assertEquals(listOf("HOOKED"), pass.binds)
    }

    @Test
    fun `FSRCNNX directive order (DESC after BIND) stays one pass`() {
        val src = """
            //!HOOK LUMA
            //!BIND HOOKED
            //!SAVE FEATURE
            //!DESC feature map (first layer)
            vec4 hook() { return HOOKED_tex(HOOKED_pos); }

            //!HOOK LUMA
            //!BIND FEATURE
            //!DESC mapping
            vec4 hook() { return FEATURE_tex(FEATURE_pos); }
        """.trimIndent()
        val passes = MpvUserShaderParser.parse(src).passes
        assertEquals(2, passes.size)
        assertEquals("feature map (first layer)", passes[0].desc)
        assertEquals("FEATURE", passes[0].save)
        assertEquals(listOf("LUMA"), passes[1].hooks)
    }

    @Test
    fun `several HOOK directives collect into one pass`() {
        val src = """
            //!HOOK LUMA
            //!HOOK RGB
            //!BIND HOOKED
            vec4 hook() { return HOOKED_tex(HOOKED_pos); }
        """.trimIndent()
        val pass = MpvUserShaderParser.parse(src).passes.single()
        assertEquals(listOf("LUMA", "RGB"), pass.hooks)
    }

    @Test
    fun `OFFSET parses fixed shifts and ALIGN`() {
        val src = """
            //!HOOK MAIN
            //!OFFSET -0.5 1.25
            vec4 hook() { return vec4(0.0); }

            //!HOOK MAIN
            //!OFFSET ALIGN
            vec4 hook() { return vec4(0.0); }
        """.trimIndent()
        val passes = MpvUserShaderParser.parse(src).passes
        assertEquals(PassOffset(-0.5f, 1.25f, align = false), passes[0].offset)
        assertEquals(PassOffset(0f, 0f, align = true), passes[1].offset)
        assertFalse(passes[0].offset!!.isZero)
    }

    @Test
    fun `COMPUTE parses block and optional workgroup sizes`() {
        val src = """
            //!HOOK MAIN
            //!COMPUTE 32 8
            void hook() { }

            //!HOOK MAIN
            //!COMPUTE 32 8 16 4
            void hook() { }
        """.trimIndent()
        val passes = MpvUserShaderParser.parse(src).passes
        assertEquals(ComputeLayout(32, 8, 32, 8), passes[0].compute)
        assertEquals(ComputeLayout(32, 8, 16, 4), passes[1].compute)
    }

    @Test
    fun `a TEXTURE block decodes its hex body`() {
        val src = """
            //!TEXTURE WEIGHTS
            //!SIZE 2 1
            //!FORMAT rgba16f
            //!FILTER NEAREST
            //!BORDER CLAMP
            00ff10A0

            //!HOOK MAIN
            //!BIND WEIGHTS
            vec4 hook() { return WEIGHTS_tex(vec2(0.5)); }
        """.trimIndent()
        val document = MpvUserShaderParser.parse(src)
        val texture = document.textures.single()
        assertEquals("WEIGHTS", texture.name)
        assertEquals(2, texture.width)
        assertEquals(1, texture.height)
        assertNull(texture.depth)
        assertEquals("rgba16f", texture.format)
        assertFalse(texture.filterLinear)
        assertFalse(texture.storage)
        assertContentEquals(byteArrayOf(0x00, 0xff.toByte(), 0x10, 0xa0.toByte()), texture.data)
        assertEquals(1, document.passes.size)
    }

    @Test
    fun `a bodyless STORAGE texture does not swallow the next pass`() {
        val src = """
            //!TEXTURE POOLED
            //!SIZE 1 1
            //!FORMAT rgba16f
            //!STORAGE
            //!DESC uses the pooled stats
            //!HOOK MAIN
            vec4 hook() { return vec4(0.0); }
        """.trimIndent()
        val document = MpvUserShaderParser.parse(src)
        val texture = document.textures.single()
        assertTrue(texture.storage)
        assertNull(texture.data)
        assertEquals("uses the pooled stats", document.passes.single().desc)
    }

    @Test
    fun `a PARAM block keeps its DESC, bounds and default`() {
        val src = """
            //!PARAM intensity
            //!DESC Sharpening intensity
            //!TYPE float
            //!MINIMUM 0
            //!MAXIMUM 10
            0.25

            //!HOOK MAIN
            //!DESC sharpen
            vec4 hook() { return vec4(intensity); }
        """.trimIndent()
        val document = MpvUserShaderParser.parse(src)
        val param = document.params.single()
        assertEquals("intensity", param.name)
        assertEquals("Sharpening intensity", param.desc)
        assertEquals("float", param.type)
        assertEquals(0f, param.minimum)
        assertEquals(10f, param.maximum)
        assertEquals(0.25f, param.defaultValue)
        assertFalse(param.define)
        // The pass after the PARAM block is parsed independently.
        assertEquals("sharpen", document.passes.single().desc)
    }

    @Test
    fun `PARAM TYPE modifiers are recognized`() {
        val src = """
            //!PARAM mode
            //!TYPE ENUM DEFINE
            first

            //!PARAM taps
            //!TYPE CONSTANT int
            3
        """.trimIndent()
        val params = MpvUserShaderParser.parse(src).params
        assertTrue(params[0].enum)
        assertTrue(params[0].define)
        assertEquals("first", params[0].default)
        assertTrue(params[1].constant)
        assertEquals("int", params[1].type)
        assertEquals(3f, params[1].defaultValue)
    }

    @Test
    fun `a BUFFER block collects VAR declarations`() {
        val src = """
            //!BUFFER stats
            //!VAR uint num_wg
            //!VAR vec4 sums[32]
            //!STORAGE
            //!HOOK MAIN
            vec4 hook() { return vec4(0.0); }
        """.trimIndent()
        val document = MpvUserShaderParser.parse(src)
        val buffer = document.buffers.single()
        assertEquals("stats", buffer.name)
        assertTrue(buffer.storage)
        assertEquals(listOf(BufferVar("uint", "num_wg"), BufferVar("vec4", "sums[32]")), buffer.vars)
        assertEquals(1, document.passes.size)
    }

    @Test
    fun `merged documents concatenate in order`() {
        val one = MpvUserShaderParser.parse("//!HOOK MAIN\nvec4 hook() { return vec4(1.0); }")
        val two = MpvUserShaderParser.parse("//!HOOK MAIN\nvec4 hook() { return vec4(2.0); }")
        val merged = ShaderDocument.merge(listOf(one, two))
        assertEquals(2, merged.passes.size)
        assertTrue(merged.passes[0].body.contains("1.0"))
        assertTrue(merged.passes[1].body.contains("2.0"))
    }
}
