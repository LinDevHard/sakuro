package com.rinwave.sakuro.engine.media3.usershader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShaderParameterScopeTest {
    private fun document(value: Float) = MpvUserShaderParser.parse(
        """
        //!PARAM scale
        //!TYPE float
        //!MINIMUM 1
        //!MAXIMUM 3
        $value
        //!HOOK MAIN
        //!BIND HOOKED
        //!WIDTH HOOKED.w scale *
        //!HEIGHT HOOKED.h scale *
        //!WHEN scale 1 >
        vec4 hook() { return HOOKED_tex(HOOKED_pos) * scale; }
        """.trimIndent(),
    )

    @Test
    fun `merged files declare only their own parameter defaults`() {
        val merged = ShaderDocument.merge(listOf(document(2f), document(3f)))
        val sources = merged.passes.map { ShaderPreamble.fragmentShader(it, document = merged) }
        assertEquals(1, Regex("const float scale =").findAll(sources[0]).count())
        assertTrue(sources[0].contains("const float scale = 2.0;"))
        assertTrue(sources[1].contains("const float scale = 3.0;"))
    }

    @Test
    fun `RPN uses the default of the file that owns each pass`() {
        val merged = ShaderDocument.merge(listOf(document(2f), document(3f)))
        val plan = ShaderGraphPlanner.plan(merged, 100, 50, 600, 300)
        assertEquals(listOf(200, 600), plan.passes.map { it.outWidth })
        assertEquals(300, plan.outputHeight)
    }

    @Test
    fun `per-file overrides stay independent in GLSL and RPN including nested merges`() {
        val merged = ShaderDocument.merge(
            listOf(document(2f), document(3f)),
            listOf(mapOf("scale" to 1f), mapOf("scale" to 2f)),
        )
        val nested = ShaderDocument.merge(listOf(merged))
        val plan = ShaderGraphPlanner.plan(nested, 100, 50, 600, 300)
        // First pass is disabled by its own WHEN; second uses its own override.
        assertEquals(listOf(200), plan.passes.map { it.outWidth })
        val sources = nested.passes.map { ShaderPreamble.fragmentShader(it, document = nested) }
        assertTrue(sources[0].contains("const float scale = 1.0;"))
        assertTrue(sources[1].contains("const float scale = 2.0;"))
    }

    @Test
    fun `compute and DEFINE parameters also stay local`() {
        fun compute(default: Int) = MpvUserShaderParser.parse(
            """
            //!PARAM MODE
            //!TYPE DEFINE
            $default
            //!HOOK MAIN
            //!BIND HOOKED
            //!COMPUTE 8 8
            void hook() { imageStore(out_image, ivec2(gl_GlobalInvocationID.xy), vec4(float(MODE))); }
            """.trimIndent(),
        )
        val merged = ShaderDocument.merge(listOf(compute(1), compute(2)))
        val sources = merged.passes.map { ShaderPreamble.computeShader(it, document = merged) }
        assertEquals(1, Regex("#define MODE ").findAll(sources[0]).count())
        assertTrue(sources[0].contains("#define MODE 1"))
        assertTrue(sources[1].contains("#define MODE 2"))
    }

    @Test
    fun `file without parameters does not inherit declarations from another file`() {
        val plain = MpvUserShaderParser.parse(
            "//!HOOK MAIN\n//!BIND HOOKED\nvec4 hook() { float scale = 0.5; return vec4(scale); }",
        )
        val merged = ShaderDocument.merge(listOf(document(2f), plain))
        val source = ShaderPreamble.fragmentShader(merged.passes.last(), document = merged)
        assertEquals(0, Regex("const float scale =").findAll(source).count())
    }
}
