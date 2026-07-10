package com.rinwave.sakuro.engine.media3.usershader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BufferLayoutTest {

    private fun buffer(storage: Boolean, vararg vars: Pair<String, String>) = ShaderBuffer(
        name = "b",
        vars = vars.map { BufferVar(it.first, it.second) },
        storage = storage,
    )

    @Test
    fun `std430 packs scalars and arrays tightly`() {
        // uint[64] (256) + uint total (4) → aligned up to 16.
        assertEquals(272, BufferLayout.sizeOf(buffer(true, "uint" to "histogram[64]", "uint" to "total")))
        // float + vec2: float at 0, vec2 aligned to 8 → 16.
        assertEquals(16, BufferLayout.sizeOf(buffer(true, "float" to "a", "vec2" to "b")))
        // vec3 is 16-aligned but 12 bytes — the float packs into its tail.
        assertEquals(16, BufferLayout.sizeOf(buffer(true, "vec3" to "n", "float" to "w")))
    }

    @Test
    fun `std140 rounds array strides to 16 bytes`() {
        // float[4] in std140 = 4 × 16.
        assertEquals(64, BufferLayout.sizeOf(buffer(false, "float" to "weights[4]")))
        // The same array in std430 stays tight.
        assertEquals(16, BufferLayout.sizeOf(buffer(true, "float" to "weights[4]")))
    }

    @Test
    fun `matrices take vec4 columns`() {
        assertEquals(64, BufferLayout.sizeOf(buffer(true, "mat4" to "m")))
    }

    @Test
    fun `an unknown VAR type is rejected`() {
        assertFailsWith<UserShaderException> {
            BufferLayout.sizeOf(buffer(true, "double" to "d"))
        }
    }
}
