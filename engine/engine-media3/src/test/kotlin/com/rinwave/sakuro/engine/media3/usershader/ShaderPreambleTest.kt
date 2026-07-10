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
            document = ShaderDocument.EMPTY.copy(params = params),
        )
        assertTrue(fragment.contains("const float intensity = 0.25;"))
        assertTrue(fragment.contains("const int taps = 3;"))
        assertTrue(fragment.contains("#define MODE fast"))
    }

    @Test
    fun `gather symbols and version 310 appear only with the capability`() {
        val body = "vec4 hook() { return HOOKED_gather(HOOKED_pos, 0); }"
        val baseline = ShaderPreamble.fragmentShader(pass(body))
        assertTrue(baseline.startsWith("#version 300 es"))
        assertTrue(!baseline.contains("#define HOOKED_gather"))

        val es31 = ShaderPreamble.fragmentShader(pass(body), caps = RuntimeCapabilities.ES31)
        assertTrue(es31.startsWith("#version 310 es"))
        assertTrue(es31.contains("#define HOOKED_gather(pos, c) textureGather(HOOKED, pos, c)"))
    }

    @Test
    fun `a compute pass gets the workgroup layout and out_image instead of frag_out`() {
        val compute = UserShaderPass(
            desc = "cs",
            hooks = listOf("MAIN"),
            binds = listOf("HOOKED"),
            save = "MAIN",
            width = null,
            height = null,
            components = 4,
            condition = null,
            offset = null,
            compute = ComputeLayout(64, 16, 32, 8),
            body = "void hook() { imageStore(out_image, ivec2(gl_GlobalInvocationID.xy), vec4(0.0)); }",
        )
        val source = ShaderPreamble.computeShader(compute)
        assertTrue(source.startsWith("#version 310 es"))
        assertTrue(source.contains("layout(local_size_x = 32, local_size_y = 8, local_size_z = 1) in;"))
        assertTrue(source.contains("writeonly highp image2D out_image;"))
        assertTrue(source.contains("#define HOOKED_pos (HOOKED_map(ivec2(gl_GlobalInvocationID.xy)))"))
        assertTrue(!source.contains("frag_out"))
        assertTrue(!source.contains("v_texcoord"))
        assertTrue(source.contains("void main() {\n  hook();\n}"))
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
