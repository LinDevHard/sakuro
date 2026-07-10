package com.rinwave.sakuro.engine.media3.usershader

/**
 * Generates the GLSL ES around an mpv user-shader pass's `hook()` body so that
 * it compiles unchanged — as a fragment shader for ordinary passes and as a
 * compute shader for `//!COMPUTE` passes.
 *
 * For each `//!BIND <n>` the mpv body may reference a set of symbols:
 * `<n>_tex(vec2)`, `<n>_texOff(off)`, `<n>_raw`, `<n>_pos`, `<n>_size`,
 * `<n>_pt`, `<n>_off`, `<n>_mul`, `<n>_rot`, `<n>_map(ivec2)` and (on ES 3.1)
 * `<n>_gather(pos, c)`. The sampler uniform is the bare bind name, as in mpv —
 * custom `//!TEXTURE` LUTs are sampled directly by name (`texture(ravu_lut3, …)`)
 * and `<n>_raw` aliases it. `texOff` is a macro with a `vec2()` argument
 * conversion because shaders call it with `ivec2` and scalar arguments.
 *
 * The spec's global symbols (`frame`, `random`, `input_size`, `target_size`,
 * `tex_offset`, `linearize()`, `delinearize()`) are declared for every pass; the
 * GLSL compiler drops the unused ones, and [UserShaderProgram] sets only the
 * active uniforms.
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

    /**
     * Uniform name of the sampler for input [bind] — the bare bind name, as in
     * mpv: custom `//!TEXTURE` LUTs are sampled directly by name
     * (ravu: `texture(ravu_lut3, …)`), and `<bind>_raw` aliases it.
     */
    fun samplerUniform(bind: String): String = bind

    /** Uniform name of the size (in texels) for input [bind]. */
    fun sizeUniform(bind: String): String = "${bind}_size"

    /** Uniform name of the texel step (1/size) for input [bind]. */
    fun pointUniform(bind: String): String = "${bind}_pt"

    /** Names of the global uniforms every pass may reference (spec globals). */
    const val UNIFORM_FRAME = "frame"
    const val UNIFORM_RANDOM = "random"
    const val UNIFORM_INPUT_SIZE = "input_size"
    const val UNIFORM_TARGET_SIZE = "target_size"
    const val UNIFORM_TEX_OFFSET = "tex_offset"

    /**
     * Assembles the full fragment shader for pass [pass].
     * [hook] — the hook point the pass actually fired on (its `HOOKED`); a pass
     * with several `//!HOOK`s builds a separate program per firing.
     * [params] — the document's `//!PARAM` blocks, injected as compile-time
     * constants/defines with their default values (runtime tunability is phase 6).
     * [caps] — gather symbols and `#version 310 es` are emitted only when available.
     * [isFinal] — the last pass of the graph: we write into the Media3 output texture
     * with alpha=1 (intermediate passes keep all 4 channels as-is).
     */
    fun fragmentShader(
        pass: UserShaderPass,
        hook: String = pass.hooks.first(),
        document: ShaderDocument = ShaderDocument.EMPTY,
        caps: RuntimeCapabilities = RuntimeCapabilities.BASELINE,
        isFinal: Boolean = false,
    ): String = buildString {
        appendLine(if (caps.gather) "#version 310 es" else "#version 300 es")
        appendLine("precision highp float;")
        appendLine("precision highp sampler2D;")
        appendLine("in vec2 v_texcoord;")
        appendLine("out vec4 frag_out;")
        appendGlobals()
        appendParams(document.params)
        val binds = pass.binds.distinct()
        for (bind in binds) {
            appendBind(bind, document, pass.body, posExpression = "v_texcoord", gather = caps.gather)
        }
        appendStageAlias(binds, MpvUserShaderParser.HOOKED, hook, gather = caps.gather)
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
     * Assembles the compute shader for a `//!COMPUTE` pass. The body defines
     * `void hook()` and writes through `out_image`; `<bind>_pos` maps the
     * invocation id into texel coordinates, as in mpv.
     */
    fun computeShader(
        pass: UserShaderPass,
        hook: String = pass.hooks.first(),
        document: ShaderDocument = ShaderDocument.EMPTY,
    ): String = buildString {
        val layout = requireNotNull(pass.compute) { "not a compute pass: '${pass.desc}'" }
        appendLine("#version 310 es")
        appendLine("precision highp float;")
        appendLine("precision highp sampler2D;")
        appendLine("precision highp image2D;")
        appendLine(
            "layout(local_size_x = ${layout.threadsWidth}, " +
                "local_size_y = ${layout.threadsHeight}, local_size_z = 1) in;",
        )
        appendLine("layout(rgba16f, binding = 0) uniform writeonly highp image2D out_image;")
        appendGlobals()
        appendParams(document.params)
        val binds = pass.binds.distinct()
        for (bind in binds) {
            appendBind(
                bind,
                document,
                pass.body,
                posExpression = "${bind}_map(ivec2(gl_GlobalInvocationID.xy))",
                gather = true,
            )
        }
        appendStageAlias(binds, MpvUserShaderParser.HOOKED, hook, gather = true)
        appendLine()
        appendLine(pass.body)
        appendLine()
        appendLine("void main() {")
        appendLine("  hook();")
        appendLine("}")
    }

    /** Spec globals available to every pass (unused ones are dropped by the compiler). */
    private fun StringBuilder.appendGlobals() {
        appendLine("uniform int $UNIFORM_FRAME;")
        appendLine("uniform float $UNIFORM_RANDOM;")
        appendLine("uniform vec2 $UNIFORM_INPUT_SIZE;")
        appendLine("uniform vec2 $UNIFORM_TARGET_SIZE;")
        appendLine("uniform vec2 $UNIFORM_TEX_OFFSET;")
        // SDR working space of the Media3 pipeline is electrical RGB; a pure-power
        // gamma 2.2 approximation stands in for the source transfer until the
        // LINEAR/SIGMOID stage emulation lands (plan phase 2).
        appendLine("vec4 linearize(vec4 color) { return vec4(pow(max(color.rgb, vec3(0.0)), vec3(2.2)), color.a); }")
        appendLine(
            "vec4 delinearize(vec4 color) " +
                "{ return vec4(pow(max(color.rgb, vec3(0.0)), vec3(1.0 / 2.2)), color.a); }",
        )
    }

    /** `//!PARAM` blocks as compile-time constants (defaults) — phase 6 makes them live. */
    private fun StringBuilder.appendParams(params: List<ShaderParam>) {
        for (param in params) {
            if (param.define) {
                appendLine("#define ${param.name} ${param.default}")
            } else {
                val type = param.type.ifEmpty { "float" }
                appendLine("const $type ${param.name} = ${param.type.glslLiteral(param.default)};")
            }
        }
    }

    /** Formats a `//!PARAM` default as a literal of its GLSL type. */
    private fun String.glslLiteral(default: String): String = when (this) {
        "int" -> default.toFloatOrNull()?.toInt()?.toString() ?: default
        "uint" -> (default.toFloatOrNull()?.toInt()?.toString() ?: default) + "u"
        else -> (default.toFloatOrNull() ?: 0f).toString()
    }

    /** A bind is a buffer block, a storage image, or an ordinary sampled texture. */
    private fun StringBuilder.appendBind(
        bind: String,
        document: ShaderDocument,
        body: String,
        posExpression: String,
        gather: Boolean,
    ) {
        val buffer = document.buffers.firstOrNull { it.name == bind }
        if (buffer != null) {
            appendBufferBlock(buffer, document)
            return
        }
        val texture = document.textures.firstOrNull { it.name == bind }
        if (texture?.storage == true) {
            appendStorageImage(texture, document, body)
            return
        }
        appendBindSymbols(bind, posExpression, gather)
    }

    /** A `//!BUFFER` block: std430 SSBO when `STORAGE`, std140 UBO otherwise. */
    private fun StringBuilder.appendBufferBlock(buffer: ShaderBuffer, document: ShaderDocument) {
        val declaration = if (buffer.storage) {
            "layout(std430, binding = ${ShaderBindings.ssboBinding(document, buffer.name)}) coherent buffer"
        } else {
            "layout(std140, binding = ${ShaderBindings.uboBinding(document, buffer.name)}) uniform"
        }
        appendLine("$declaration ${buffer.name} {")
        buffer.vars.forEach { appendLine("  ${it.type} ${it.name};") }
        appendLine("};")
    }

    /**
     * A `//!TEXTURE … STORAGE` image with the size symbols. ES 3.1 requires a
     * `readonly`/`writeonly` qualifier for formats other than `r32f`, so the
     * access mode is derived from how the pass body uses the image.
     */
    private fun StringBuilder.appendStorageImage(
        texture: ShaderTexture,
        document: ShaderDocument,
        body: String,
    ) {
        val qualifier = checkNotNull(ShaderTextureFormats.imageFormatQualifier(texture.format))
        val unit = ShaderBindings.imageUnit(document, texture.name)
        val access = when (ShaderBindings.imageAccess(body, texture.name)) {
            ShaderBindings.ImageAccess.READ -> "readonly "
            ShaderBindings.ImageAccess.WRITE -> "writeonly "
            ShaderBindings.ImageAccess.READ_WRITE -> ""
        }
        appendLine(
            "layout($qualifier, binding = $unit) uniform coherent ${access}highp image2D ${texture.name};",
        )
        appendLine("uniform vec2 ${sizeUniform(texture.name)};")
        appendLine("uniform vec2 ${pointUniform(texture.name)};")
    }

    /** The full per-bind symbol set of the spec on top of one `sampler2D`. */
    private fun StringBuilder.appendBindSymbols(bind: String, posExpression: String, gather: Boolean) {
        appendLine("uniform sampler2D ${samplerUniform(bind)};")
        appendLine("uniform vec2 ${sizeUniform(bind)};")
        appendLine("uniform vec2 ${pointUniform(bind)};")
        appendLine("vec2 ${bind}_map(ivec2 id) { return (vec2(id) + vec2(0.5)) * ${pointUniform(bind)}; }")
        appendLine("#define ${bind}_pos ($posExpression)")
        appendLine("#define ${bind}_raw ${samplerUniform(bind)}")
        appendLine("#define ${bind}_off vec2(0.0)")
        appendLine("#define ${bind}_mul 1.0")
        appendLine("#define ${bind}_rot mat2(1.0)")
        appendLine("vec4 ${bind}_tex(vec2 p) { return texture(${samplerUniform(bind)}, p); }")
        // A macro, as in mpv: shaders call texOff with ivec2/float arguments too,
        // and GLSL ES has no implicit int→float conversion for a function call.
        appendLine("#define ${bind}_texOff(off) ${bind}_tex(${bind}_pos + vec2(off) * ${pointUniform(bind)})")
        if (gather) {
            appendLine("#define ${bind}_gather(pos, c) textureGather(${samplerUniform(bind)}, pos, c)")
        }
    }

    /**
     * Adds an alias between `HOOKED` and the hooked-stage name over the sampler
     * actually bound from them: symbols `<target>_tex/_texOff/_pos/_pt/_size/…`
     * reference the already-declared `<source>_*`.
     */
    private fun StringBuilder.appendStageAlias(
        binds: List<String>,
        hooked: String,
        hookName: String,
        gather: Boolean,
    ) {
        val (target, source) = when {
            hooked in binds && hookName !in binds -> hookName to hooked
            hookName in binds && hooked !in binds -> hooked to hookName
            else -> return
        }
        appendLine("#define ${target}_pos ${source}_pos")
        appendLine("#define ${target}_pt ${pointUniform(source)}")
        appendLine("#define ${target}_size ${sizeUniform(source)}")
        appendLine("#define ${target}_raw ${samplerUniform(source)}")
        appendLine("#define ${target}_off vec2(0.0)")
        appendLine("#define ${target}_mul 1.0")
        appendLine("#define ${target}_rot mat2(1.0)")
        appendLine("vec4 ${target}_tex(vec2 p) { return ${source}_tex(p); }")
        appendLine("#define ${target}_texOff(off) ${source}_texOff(off)")
        appendLine("vec2 ${target}_map(ivec2 id) { return ${source}_map(id); }")
        if (gather) {
            appendLine("#define ${target}_gather(pos, c) ${source}_gather(pos, c)")
        }
    }
}
