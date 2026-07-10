package com.rinwave.sakuro.engine.media3.usershader

import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserShaderValidatorTest {

    @Test
    fun `a valid shader passes validation`() {
        val source = """
            //!HOOK MAIN
            //!BIND HOOKED
            vec4 hook() { return HOOKED_tex(HOOKED_pos); }
        """.trimIndent()
        assertNull(UserShaderValidator.validate(source))
    }

    @Test
    fun `a compute shader passes validation (capabilities are checked at playback)`() {
        val source = """
            //!HOOK LUMA
            //!BIND HOOKED
            //!COMPUTE 64 16 32 8
            void hook() { }
        """.trimIndent()
        assertNull(UserShaderValidator.validate(source))
    }

    @Test
    fun `plain GLSL without hook passes is rejected`() {
        val problem = UserShaderValidator.validate("void main() { }")
        assertTrue(problem!!.contains("no hook passes"))
    }

    @Test
    fun `an undefined bind is reported with the pass name`() {
        val source = """
            //!DESC broken
            //!HOOK MAIN
            //!BIND NOPE
            vec4 hook() { return vec4(0.0); }
        """.trimIndent()
        val problem = UserShaderValidator.validate(source)
        assertTrue(problem!!.contains("broken"), problem)
    }

    @Test
    fun `a shader whose hooks never fire is reported`() {
        val source = """
            //!DESC xyz-only
            //!HOOK XYZ
            vec4 hook() { return vec4(0.0); }
        """.trimIndent()
        val problem = UserShaderValidator.validate(source)
        assertTrue(problem!!.contains("never fire"), problem)
    }

    @Test
    fun `real fixtures validate`() {
        for (fixture in listOf("FSRCNNX_x2_8-0-4-1.glsl", "ravu-lite-r3.hook", "KrigBilateral.glsl")) {
            val source = javaClass.getResourceAsStream("/usershader/$fixture")!!
                .bufferedReader().use { it.readText() }
            assertNull(UserShaderValidator.validate(source), fixture)
        }
    }
}
