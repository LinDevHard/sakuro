package com.rinwave.media3.usershader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
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

    private val es30 = GpuCapabilities(3, 0, "test", "test", "OpenGL ES 3.0", fp16Renderable = true)
    private val es32 = GpuCapabilities(3, 2, "test", "test", "OpenGL ES 3.2", fp16Renderable = true)

    private fun fixture(name: String) = javaClass.getResourceAsStream("/usershader/$name")!!
        .bufferedReader().use { it.readText() }

    @Test
    fun `a compute shader is unsupported on an ES 3_0 device and fine on ES 3_2`() {
        val ravuLite = fixture("ravu-lite-r3.hook")

        val onEs30 = UserShaderValidator.check(ravuLite, es30)
        assertIs<ShaderCheck.Unsupported>(onEs30)
        assertTrue(onEs30.reason.contains("ES 3.1"), onEs30.reason)
        assertEquals(ShaderCheck.Ok, UserShaderValidator.check(ravuLite, es32))
    }

    @Test
    fun `a fragment-only shader runs on ES 3_0`() {
        assertEquals(ShaderCheck.Ok, UserShaderValidator.check(fixture("ravu-r3.hook"), es30))
    }

    @Test
    fun `without FP16 render targets nothing runs`() {
        val check = UserShaderValidator.check(fixture("ravu-r3.hook"), es32.copy(fp16Renderable = false))
        assertIs<ShaderCheck.Unsupported>(check)
        assertTrue(check.reason.contains("FP16"))
    }

    @Test
    fun `a broken shader is invalid regardless of the device`() {
        assertIs<ShaderCheck.Invalid>(UserShaderValidator.check("void main() { }", es32))
    }
}
