package com.rinwave.sakuro.core.upscale

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShaderTunableTest {

    private fun tunable(
        name: String = "intensity",
        default: Float = 0.35f,
        minimum: Float? = 0f,
        maximum: Float? = 1f,
        integral: Boolean = false,
    ) = ShaderTunable(name, "", default, minimum, maximum, integral)

    @Test
    fun `the range follows the shader bounds`() {
        assertEquals(0f..1f, tunable().range)
    }

    @Test
    fun `an unbounded param still gets a usable range around its default`() {
        val open = tunable(default = 4f, minimum = null, maximum = null)
        assertTrue(open.range.start <= 0f)
        assertTrue(open.range.endInclusive >= 4f, "the default must be reachable: ${open.range}")
    }

    @Test
    fun `clamping keeps values in range and integral params whole`() {
        assertEquals(1f, tunable().clamp(5f))
        assertEquals(0f, tunable().clamp(-2f))
        assertEquals(3f, tunable(default = 3f, minimum = 1f, maximum = 5f, integral = true).clamp(3.7f))
    }

    @Test
    fun `integral params get one slider stop per whole value`() {
        // 1..5 → stops at 2,3,4 between the ends.
        assertEquals(3, tunable(default = 3f, minimum = 1f, maximum = 5f, integral = true).steps)
        assertEquals(0, tunable().steps)
    }
}

class ShaderParamSanitizeTest {

    private val inspector = object : ShaderInspector {
        override fun tunables(fileName: String): List<ShaderTunable> = when (fileName) {
            "CAS.glsl" -> listOf(ShaderTunable("SHARPENING", "", 0f, 0f, 1f, integral = false))
            else -> emptyList()
        }
    }

    @Test
    fun `overrides are clamped and defaults are dropped`() {
        val sanitized = mapOf("CAS.glsl" to mapOf("SHARPENING" to 5f))
            .sanitizeAgainst(inspector, listOf("CAS.glsl"))
        assertEquals(mapOf("CAS.glsl" to mapOf("SHARPENING" to 1f)), sanitized)

        val atDefault = mapOf("CAS.glsl" to mapOf("SHARPENING" to 0f))
            .sanitizeAgainst(inspector, listOf("CAS.glsl"))
        assertTrue(atDefault.isEmpty(), "a value equal to the default is not an override")
    }

    @Test
    fun `values for shaders outside the chain or unknown params are dropped`() {
        val stale = mapOf(
            "CAS.glsl" to mapOf("SHARPENING" to 0.5f, "NOPE" to 1f),
            "ravu-r3.hook" to mapOf("X" to 1f),
        ).sanitizeAgainst(inspector, listOf("CAS.glsl"))
        assertEquals(mapOf("CAS.glsl" to mapOf("SHARPENING" to 0.5f)), stale)
    }
}
