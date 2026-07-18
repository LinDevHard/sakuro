package com.rinwave.sakuro.engine.mpv

/**
 * Classic `vo=gpu` (what this engine runs) predates the libplacebo `//!PARAM`
 * extension and rejects files carrying it. This materializer keeps one
 * canonical `.glsl` with `//!PARAM` blocks as the source of truth: the blocks
 * are stripped and every hook pass gets the parameters baked into its body as
 * `#define`s — an override clamped to the param's bounds, or the default.
 *
 * The result matches the Media3 runtime, which bakes effective `//!PARAM`
 * values as compile-time constants too.
 */
internal object MpvShaderMaterializer {

    private data class Param(
        val name: String,
        val type: String,
        val minimum: Float?,
        val maximum: Float?,
        val default: String,
    ) {
        fun defineValue(override: Float?): String {
            val value = override
                ?.coerceIn(minimum ?: Float.NEGATIVE_INFINITY, maximum ?: Float.POSITIVE_INFINITY)
                ?: return default
            return when (type) {
                "int", "uint" -> value.toInt().toString()
                else -> value.toString()
            }
        }
    }

    /**
     * Returns the source with `//!PARAM` blocks folded into per-pass defines,
     * or null when the file has no params (use the original file as-is).
     */
    fun materialize(source: String, overrides: Map<String, Float> = emptyMap()): String? {
        val lines = source.lines()
        val params = mutableListOf<Param>()
        val kept = mutableListOf<String>()

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (!line.startsWith("//!PARAM ")) {
                kept += line
                i++
                continue
            }
            val name = line.removePrefix("//!PARAM ").trim()
            var type = ""
            var minimum: Float? = null
            var maximum: Float? = null
            i++
            while (i < lines.size && lines[i].startsWith("//!")) {
                val directive = lines[i]
                when {
                    directive.startsWith("//!TYPE ") -> {
                        val tokens = directive.removePrefix("//!TYPE ").trim().split(WHITESPACE)
                        type = tokens.lastOrNull { it !in MODIFIERS }.orEmpty()
                    }
                    directive.startsWith("//!MINIMUM ") ->
                        minimum = directive.removePrefix("//!MINIMUM ").trim().toFloatOrNull()
                    directive.startsWith("//!MAXIMUM ") ->
                        maximum = directive.removePrefix("//!MAXIMUM ").trim().toFloatOrNull()
                }
                i++
            }
            // The block body up to the next block: the first non-blank line is the default.
            var default = ""
            while (i < lines.size && !lines[i].startsWith("//!")) {
                if (default.isEmpty()) default = lines[i].trim()
                i++
            }
            params += Param(name, type, minimum, maximum, default)
        }
        if (params.isEmpty()) return null

        val defines = params.map { "#define ${it.name} ${it.defineValue(overrides[it.name])}" }
        return injectDefines(kept, defines).joinToString("\n")
    }

    /** Inserts the defines right after every directive run that declares a `//!HOOK`. */
    private fun injectDefines(lines: List<String>, defines: List<String>): List<String> {
        val out = mutableListOf<String>()
        var runHasHook = false
        var inRun = false
        for (line in lines) {
            val directive = line.startsWith("//!")
            if (directive) {
                if (!inRun) {
                    inRun = true
                    runHasHook = false
                }
                if (line.startsWith("//!HOOK ")) runHasHook = true
            } else if (inRun) {
                inRun = false
                if (runHasHook) out += defines
            }
            out += line
        }
        return out
    }

    private val WHITESPACE = Regex("\\s+")
    private val MODIFIERS = setOf("ENUM", "DEFINE", "DYNAMIC", "CONSTANT")
}
