package com.rinwave.sakuro.engine.media3.usershader

import kotlin.test.Test
import kotlin.test.assertTrue

class ShaderPreambleTest {

    private fun pass(body: String, binds: List<String> = listOf("HOOKED"), hooks: List<String> = listOf("MAIN")) =
        UserShaderPass(
            desc = "test",
            hooks = hooks,
            binds = binds,
            save = "MAIN",
            width = null,
            height = null,
            components = 4,
            condition = null,
            offset = null,
            compute = null,
            body = body,
        )

    @Test
    fun `the full per-bind symbol set of the spec is declared`() {
        val fragment = ShaderPreamble.fragmentShader(pass("vec4 hook() { return HOOKED_tex(HOOKED_pos); }"))
        for (symbol in listOf("_pos", "_raw", "_off", "_mul", "_rot")) {
            assertTrue(fragment.contains("#define HOOKED$symbol"), "missing HOOKED$symbol")
        }
        assertTrue(fragment.contains("vec4 HOOKED_tex("))
        assertTrue(fragment.contains("#define HOOKED_texOff(off)"))
        assertTrue(fragment.contains("vec2 HOOKED_map("))
        assertTrue(fragment.contains("uniform vec2 HOOKED_size;"))
        assertTrue(fragment.contains("uniform vec2 HOOKED_pt;"))
    }

    @Test
    fun `spec globals are declared in every pass`() {
        val fragment = ShaderPreamble.fragmentShader(pass("vec4 hook() { return vec4(float(frame)); }"))
        assertTrue(fragment.contains("uniform int frame;"))
        assertTrue(fragment.contains("uniform float random;"))
        assertTrue(fragment.contains("uniform vec2 input_size;"))
        assertTrue(fragment.contains("uniform vec2 target_size;"))
        assertTrue(fragment.contains("uniform vec2 tex_offset;"))
        assertTrue(fragment.contains("vec4 linearize("))
        assertTrue(fragment.contains("vec4 delinearize("))
    }

    @Test
    fun `the stage alias mirrors the extended symbols too`() {
        // BIND HOOKED while the body references MAIN_raw → the alias must cover it.
        val fragment = ShaderPreamble.fragmentShader(
            pass("vec4 hook() { return texelFetch(MAIN_raw, ivec2(0), 0); }"),
        )
        assertTrue(fragment.contains("#define MAIN_raw HOOKED"))
        assertTrue(fragment.contains("#define MAIN_rot"))
        assertTrue(fragment.contains("vec2 MAIN_map("))
    }

    @Test
    fun `params are injected as constants or defines with defaults`() {
        val params = listOf(
            ShaderParam(
                name = "intensity", desc = "", type = "float", define = false, dynamic = false,
                constant = false, enum = false, minimum = 0f, maximum = 10f, default = "0.25",
            ),
            ShaderParam(
                name = "taps", desc = "", type = "int", define = false, dynamic = false,
                constant = true, enum = false, minimum = null, maximum = null, default = "3",
            ),
            ShaderParam(
                name = "MODE", desc = "", type = "", define = true, dynamic = false,
                constant = false, enum = false, minimum = null, maximum = null, default = "fast",
            ),
        )
        val fragment = ShaderPreamble.fragmentShader(
            pass("vec4 hook() { return vec4(intensity); }"),
            params = params,
        )
        assertTrue(fragment.contains("const float intensity = 0.25;"))
        assertTrue(fragment.contains("const int taps = 3;"))
        assertTrue(fragment.contains("#define MODE fast"))
    }

    @Test
    fun `the final pass forces alpha to one`() {
        val fragment = ShaderPreamble.fragmentShader(
            pass("vec4 hook() { return vec4(0.5); }"),
            isFinal = true,
        )
        assertTrue(fragment.contains("frag_out = vec4(hook().rgb, 1.0);"))
    }
}
