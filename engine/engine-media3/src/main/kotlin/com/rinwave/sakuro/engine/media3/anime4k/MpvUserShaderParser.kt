package com.rinwave.sakuro.engine.media3.anime4k

/**
 * Parser of the mpv user-shader format (libplacebo hook-language) into a list of
 * [UserShaderPass]. Splits a `.glsl` file into passes by `//!` directives;
 * each pass body is the GLSL between the directive block and the next `//!DESC`
 * (or the end of file).
 *
 * Supported directives: `DESC HOOK BIND SAVE WIDTH HEIGHT COMPONENTS WHEN`.
 * Unknown directives (`OFFSET`, `COMPUTE`, etc.) are deliberately
 * ignored — Anime4K v4.0.1 does not use them. The leading license
 * comment and any lines before the first `//!HOOK` are discarded.
 *
 * Pass bodies are almost ready-to-use GLSL; the parser only has to lay out
 * directives and boundaries.
 */
internal object MpvUserShaderParser {

    private const val PREFIX = "//!"

    fun parse(source: String): List<UserShaderPass> {
        val passes = mutableListOf<UserShaderPass>()
        var current: DirectiveBlock? = null
        val body = StringBuilder()

        fun flush() {
            current?.let { passes += it.toPass(body.toString()) }
            body.setLength(0)
        }

        for (rawLine in source.lineSequence()) {
            val line = rawLine.trim()
            // A new pass starts at `//!DESC`, or at the first `//!HOOK` if there is no DESC.
            val startsPass = line.startsWith(PREFIX + "DESC") ||
                (line.startsWith(PREFIX + "HOOK") && current == null)
            when {
                startsPass -> {
                    if (line.startsWith(PREFIX + "DESC")) flush()
                    current = DirectiveBlock().apply { applyDirective(line) }
                }
                line.startsWith(PREFIX) -> current?.applyDirective(line) // ignored before the first pass
                current != null -> body.appendLine(rawLine)
                // lines before the first pass (the license) are dropped
            }
        }
        flush()
        return passes
    }

    /** Accumulator for one pass's directives until its body appears. */
    private class DirectiveBlock {
        private var desc = ""
        private var hook = "MAIN"
        private val binds = mutableListOf<String>()
        private var save: String? = null
        private var width: RpnExpression? = null
        private var height: RpnExpression? = null
        private var components = DEFAULT_COMPONENTS
        private var condition: RpnExpression? = null

        fun applyDirective(line: String) {
            val content = line.removePrefix(PREFIX).trim()
            val keyword = content.substringBefore(' ')
            val value = content.substringAfter(' ', "").trim()
            when (keyword) {
                "DESC" -> desc = value
                "HOOK" -> hook = value
                "BIND" -> if (value.isNotEmpty()) binds += value
                "SAVE" -> save = value
                "WIDTH" -> width = RpnExpression.parse(value)
                "HEIGHT" -> height = RpnExpression.parse(value)
                "COMPONENTS" -> components = value.toIntOrNull() ?: DEFAULT_COMPONENTS
                "WHEN" -> condition = RpnExpression.parse(value)
                else -> Unit // OFFSET/COMPUTE/… — not used by Anime4K v4.0.1
            }
        }

        fun toPass(body: String): UserShaderPass = UserShaderPass(
            desc = desc,
            hook = hook,
            // Without an explicit BIND, the pass still reads the hooked stage.
            binds = if (binds.isEmpty()) listOf(HOOKED) else binds.toList(),
            save = save ?: HOOKED,
            width = width,
            height = height,
            components = components,
            condition = condition,
            body = body.trimEnd(),
        )
    }

    /** Pseudo-name of the current hooked stage in mpv (`HOOKED_tex`, `SAVE HOOKED`). */
    const val HOOKED = "HOOKED"
    private const val DEFAULT_COMPONENTS = 4
}
