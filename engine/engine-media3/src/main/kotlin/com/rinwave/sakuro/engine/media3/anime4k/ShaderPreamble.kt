package com.rinwave.sakuro.engine.media3.anime4k

/**
 * Generates the GLSL ES 3.00 around an mpv user-shader pass's `hook()` body so that
 * it compiles **unchanged** (docs/anime4k-media3-port-plan.md §3.3).
 *
 * For each `//!BIND <n>` the mpv body expects a set of symbols:
 * `<n>_tex(vec2)`, `<n>_texOff(vec2)`, `<n>_pos`, `<n>_pt`, `<n>_size`.
 * Here they are synthesized on top of a plain `sampler2D`; the coordinate comes from
 * the varying `v_texcoord` (shared by all inputs — we draw a fullscreen quad).
 *
 * Intermediate textures are `GL_RGBA16F` (needed because CNN feature maps go beyond
 * [0,1] and go negative), so all the math is in `highp`.
 */
internal object ShaderPreamble {

    const val VERTEX_SHADER = """#version 300 es
in vec4 a_position;
out vec2 v_texcoord;
void main() {
  gl_Position = a_position;
  v_texcoord = a_position.xy * 0.5 + 0.5;
}
"""

    /** Uniform name of the sampler for input [bind]. */
    fun samplerUniform(bind: String): String = "${bind}_sampler"

    /** Uniform name of the size (in texels) for input [bind]. */
    fun sizeUniform(bind: String): String = "${bind}_size"

    /** Uniform name of the texel step (1/size) for input [bind]. */
    fun pointUniform(bind: String): String = "${bind}_pt"

    /**
     * Assembles the full fragment shader for pass [pass].
     * [isFinal] — the last pass of the graph: we write into the Media3 output texture
     * with alpha=1 (intermediate passes keep all 4 channels as-is).
     */
    fun fragmentShader(pass: UserShaderPass, isFinal: Boolean): String = buildString {
        appendLine("#version 300 es")
        appendLine("precision highp float;")
        appendLine("precision highp sampler2D;")
        appendLine("in vec2 v_texcoord;")
        appendLine("out vec4 frag_out;")
        val binds = pass.binds.distinct()
        for (bind in binds) {
            appendLine("uniform sampler2D ${samplerUniform(bind)};")
            appendLine("uniform vec2 ${sizeUniform(bind)};")
            appendLine("uniform vec2 ${pointUniform(bind)};")
            // Coordinate and texel step — as in mpv (a single v_texcoord for all inputs).
            appendLine("#define ${bind}_pos v_texcoord")
            appendLine("#define ${bind}_mul 1.0")
            appendLine("vec4 ${bind}_tex(vec2 p) { return texture(${samplerUniform(bind)}, p); }")
            appendLine(
                "vec4 ${bind}_texOff(vec2 o) { " +
                    "return texture(${samplerUniform(bind)}, v_texcoord + o * ${pointUniform(bind)}); }",
            )
        }
        // In mpv, `HOOKED` and the hooked-stage name (`MAIN`/`PREKERNEL`/`NATIVE`) are
        // aliases of ONE texture: both symbol sets are available. A pass may
        // bind one name and reference another in its body (Denoise: BIND HOOKED,
        // the body calls MAIN_texOff). We add the missing alias over the same sampler.
        appendStageAlias(binds, MpvUserShaderParser.HOOKED, pass.hook)
        appendLine()
        appendLine(pass.body)
        appendLine()
        appendLine("void main() {")
        if (isFinal) {
            appendLine("  frag_out = vec4(hook().rgb, 1.0);")
        } else {
            appendLine("  frag_out = hook();")
        }
        appendLine("}")
    }

    /**
     * Adds an alias between `HOOKED` and the hooked-stage name over the sampler
     * actually bound from them: symbols `<target>_tex/_texOff/_pos/_pt/_size`
     * reference the already-declared `<source>_*`.
     */
    private fun StringBuilder.appendStageAlias(binds: List<String>, hooked: String, hookName: String) {
        val (target, source) = when {
            hooked in binds && hookName !in binds -> hookName to hooked
            hookName in binds && hooked !in binds -> hooked to hookName
            else -> return
        }
        appendLine("#define ${target}_pos v_texcoord")
        appendLine("#define ${target}_pt ${pointUniform(source)}")
        appendLine("#define ${target}_size ${sizeUniform(source)}")
        appendLine("#define ${target}_mul 1.0")
        appendLine("vec4 ${target}_tex(vec2 p) { return ${source}_tex(p); }")
        appendLine("vec4 ${target}_texOff(vec2 o) { return ${source}_texOff(o); }")
    }
}
