package com.rinwave.sakuro.engine.mpv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MpvShaderMaterializerTest {

    private val source = """
        // header comment

        //!PARAM intensity
        //!DESC Denoise intensity
        //!TYPE float
        //!MINIMUM 0.0
        //!MAXIMUM 1.0
        0.35

        //!HOOK MAIN
        //!BIND HOOKED
        //!DESC First pass

        vec4 hook() { return vec4(intensity); }

        //!TEXTURE lut
        //!SIZE 2
        //!FORMAT rgba16f
        deadbeef

        //!HOOK MAIN
        //!BIND HOOKED
        //!BIND lut
        //!DESC Second pass

        vec4 hook() { return vec4(0.0); }
    """.trimIndent()

    @Test
    fun `PARAM blocks fold into defines after every hook pass header`() {
        val result = MpvShaderMaterializer.materialize(source)!!
        assertFalse(result.contains("//!PARAM"))
        assertFalse(result.contains("//!MINIMUM"))
        // One define per pass, none for the TEXTURE block.
        assertEquals(2, Regex("#define intensity 0\\.35").findAll(result).count())
        assertTrue(result.indexOf("#define intensity") > result.indexOf("//!DESC First pass"))
        // The TEXTURE block and its hex payload survive untouched.
        assertTrue(result.contains("//!TEXTURE lut"))
        assertTrue(result.contains("deadbeef"))
    }

    @Test
    fun `overrides replace the default and clamp to the bounds`() {
        val tuned = MpvShaderMaterializer.materialize(source, mapOf("intensity" to 0.6f))!!
        assertTrue(tuned.contains("#define intensity 0.6"))
        val clamped = MpvShaderMaterializer.materialize(source, mapOf("intensity" to 5f))!!
        assertTrue(clamped.contains("#define intensity 1.0"))
    }

    @Test
    fun `int params format as int literals`() {
        val src = """
            //!PARAM taps
            //!TYPE int
            //!MINIMUM 1
            //!MAXIMUM 5
            3

            //!HOOK MAIN
            //!BIND HOOKED
            vec4 hook() { return vec4(float(taps)); }
        """.trimIndent()
        val result = MpvShaderMaterializer.materialize(src, mapOf("taps" to 4f))!!
        assertTrue(result.contains("#define taps 4"))
        assertFalse(result.contains("#define taps 4.0"))
    }

    @Test
    fun `a file without params is left alone`() {
        val plain = """
            //!HOOK MAIN
            //!BIND HOOKED
            vec4 hook() { return vec4(0.0); }
        """.trimIndent()
        assertNull(MpvShaderMaterializer.materialize(plain))
    }
}
