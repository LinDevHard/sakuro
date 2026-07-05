package com.rinwave.sakuro.engine.media3.anime4k

/**
 * Генерация GLSL ES 3.00 вокруг тела `hook()` прохода mpv user-shader, чтобы
 * оно компилировалось **без изменений** (docs/anime4k-media3-port-plan.md §3.3).
 *
 * На каждый `//!BIND <n>` mpv-тело ожидает набор символов:
 * `<n>_tex(vec2)`, `<n>_texOff(vec2)`, `<n>_pos`, `<n>_pt`, `<n>_size`.
 * Здесь они синтезируются поверх обычного `sampler2D`; координата берётся из
 * varying `v_texcoord` (единая для всех входов — рисуем фулскрин-квад).
 *
 * Промежуточные текстуры — `GL_RGBA16F` (нужно, т.к. фичемапы CNN выходят за
 * [0,1] и уходят в минус), поэтому вся математика в `highp`.
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

    /** Uniform-имя сэмплера для входа [bind]. */
    fun samplerUniform(bind: String): String = "${bind}_sampler"

    /** Uniform-имя размера (в текселях) для входа [bind]. */
    fun sizeUniform(bind: String): String = "${bind}_size"

    /** Uniform-имя шага текселя (1/size) для входа [bind]. */
    fun pointUniform(bind: String): String = "${bind}_pt"

    /**
     * Собирает полный фрагментный шейдер прохода [pass].
     * [isFinal] — последний проход графа: пишем в выходную текстуру Media3
     * с alpha=1 (промежуточные проходы сохраняют все 4 канала как есть).
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
            // Координата и шаг текселя — как в mpv (единый v_texcoord на все входы).
            appendLine("#define ${bind}_pos v_texcoord")
            appendLine("#define ${bind}_mul 1.0")
            appendLine("vec4 ${bind}_tex(vec2 p) { return texture(${samplerUniform(bind)}, p); }")
            appendLine(
                "vec4 ${bind}_texOff(vec2 o) { " +
                    "return texture(${samplerUniform(bind)}, v_texcoord + o * ${pointUniform(bind)}); }",
            )
        }
        // В mpv `HOOKED` и имя хукнутой стадии (`MAIN`/`PREKERNEL`/`NATIVE`) —
        // псевдонимы ОДНОЙ текстуры: доступны оба набора символов. Проход может
        // забиндить одно имя, а в теле обращаться к другому (Denoise: BIND HOOKED,
        // тело зовёт MAIN_texOff). Достраиваем недостающий алиас поверх того же сэмплера.
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
     * Достраивает алиас между `HOOKED` и именем хукнутой стадии поверх сэмплера
     * реально забинженного из них: символы `<target>_tex/_texOff/_pos/_pt/_size`
     * ссылаются на уже объявленный `<source>_*`.
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
