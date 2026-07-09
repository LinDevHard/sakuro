package com.rinwave.sakuro.engine.media3.usershader

/**
 * Parser of the mpv user-shader format (libplacebo hook-language) into a
 * [ShaderDocument]. A file is a sequence of blocks; each block is a run of
 * contiguous `//!` directive lines followed by a body (the lines until the next
 * directive). Block kinds by their first directive:
 *
 * - `//!TEXTURE` — a custom texture; the body is texel data as a hex string;
 * - `//!BUFFER` — a uniform/storage buffer with `//!VAR` members (no body);
 * - `//!PARAM` — a tunable parameter; the body is its default value;
 * - anything else — a hook pass; the body is GLSL with `hook()`.
 *
 * Directive order inside a block is free (Anime4K puts `DESC` first, FSRCNNX
 * puts it after `BIND` — both are one block as long as the directives are
 * contiguous). The leading license comment and any lines before the first
 * directive are discarded.
 */
internal object MpvUserShaderParser {

    private const val PREFIX = "//!"

    /** Directive keywords that always open a new block. */
    private val BLOCK_KEYWORDS = setOf("TEXTURE", "BUFFER", "PARAM")

    /**
     * Pass-only keywords that close a bodyless TEXTURE/BUFFER block. `DESC` is
     * excluded for PARAM blocks — they legitimately carry their own `//!DESC`.
     */
    private val PASS_KEYWORDS = setOf("DESC", "HOOK")
    private val PARAM_CLOSING_KEYWORDS = setOf("HOOK")

    fun parse(source: String): ShaderDocument {
        val builder = DocumentBuilder()
        var block: Block? = null
        val body = StringBuilder()

        fun flush() {
            block?.let { builder.add(it, body.toString()) }
            block = null
            body.setLength(0)
        }

        for (rawLine in source.lineSequence()) {
            val line = rawLine.trim()
            if (!line.startsWith(PREFIX)) {
                if (block != null) body.appendLine(rawLine)
                continue // lines before the first directive (the license) are dropped
            }
            val keyword = line.removePrefix(PREFIX).substringBefore(' ').trim()
            val value = line.removePrefix(PREFIX).substringAfter(' ', "").trim()
            val closing = if (block?.kind == "PARAM") PARAM_CLOSING_KEYWORDS else PASS_KEYWORDS
            val startsBlock = body.isNotBlank() ||
                keyword in BLOCK_KEYWORDS ||
                (block?.isAuxiliary == true && keyword in closing)
            if (startsBlock) flush()
            (block ?: Block(keyword).also { block = it }).directives += keyword to value
        }
        flush()
        return builder.build()
    }

    /** A raw block: its first keyword (defines the kind) and all directives in order. */
    private class Block(firstKeyword: String) {
        val directives = mutableListOf<Pair<String, String>>()
        val kind: String = if (firstKeyword in BLOCK_KEYWORDS) firstKeyword else "PASS"
        val isAuxiliary: Boolean get() = kind != "PASS"

        fun single(keyword: String): String? = directives.lastOrNull { it.first == keyword }?.second
        fun all(keyword: String): List<String> = directives.filter { it.first == keyword }.map { it.second }
        fun flag(keyword: String): Boolean = directives.any { it.first == keyword }
    }

    private class DocumentBuilder {
        private val passes = mutableListOf<UserShaderPass>()
        private val textures = mutableListOf<ShaderTexture>()
        private val buffers = mutableListOf<ShaderBuffer>()
        private val params = mutableListOf<ShaderParam>()

        fun add(block: Block, body: String) {
            when (block.kind) {
                "TEXTURE" -> textures += texture(block, body)
                "BUFFER" -> buffers += buffer(block)
                "PARAM" -> params += param(block, body)
                else -> passes += pass(block, body)
            }
        }

        fun build() = ShaderDocument(passes, textures, buffers, params)
    }

    private fun pass(block: Block, body: String): UserShaderPass {
        val hooks = block.all("HOOK").filter { it.isNotEmpty() }
        return UserShaderPass(
            desc = block.single("DESC").orEmpty(),
            hooks = hooks.ifEmpty { listOf("MAIN") },
            // Without an explicit BIND, the pass still reads the hooked stage.
            binds = block.all("BIND").filter { it.isNotEmpty() }.ifEmpty { listOf(HOOKED) },
            save = block.single("SAVE") ?: HOOKED,
            width = block.single("WIDTH")?.let { RpnExpression.parse(it) },
            height = block.single("HEIGHT")?.let { RpnExpression.parse(it) },
            components = block.single("COMPONENTS")?.toIntOrNull() ?: DEFAULT_COMPONENTS,
            condition = block.single("WHEN")?.let { RpnExpression.parse(it) },
            offset = block.single("OFFSET")?.let { parseOffset(it) },
            compute = block.single("COMPUTE")?.let { parseCompute(it) },
            body = body.trimEnd(),
        )
    }

    private fun parseOffset(value: String): PassOffset {
        if (value.equals("ALIGN", ignoreCase = true)) return PassOffset(0f, 0f, align = true)
        val parts = value.split(WHITESPACE)
        val x = parts.getOrNull(0)?.toFloatOrNull()
            ?: throw UserShaderException("//!OFFSET: cannot parse '$value'")
        val y = parts.getOrNull(1)?.toFloatOrNull()
            ?: throw UserShaderException("//!OFFSET: cannot parse '$value'")
        return PassOffset(x, y, align = false)
    }

    private fun parseCompute(value: String): ComputeLayout {
        val parts = value.split(WHITESPACE).mapNotNull { it.toIntOrNull() }
        if (parts.size < 2) throw UserShaderException("//!COMPUTE: cannot parse '$value'")
        return ComputeLayout(
            blockWidth = parts[0],
            blockHeight = parts[1],
            threadsWidth = parts.getOrElse(2) { parts[0] },
            threadsHeight = parts.getOrElse(3) { parts[1] },
        )
    }

    private fun texture(block: Block, body: String): ShaderTexture {
        val name = block.single("TEXTURE").takeUnless { it.isNullOrEmpty() }
            ?: throw UserShaderException("//!TEXTURE: missing name")
        val size = block.single("SIZE").orEmpty().split(WHITESPACE).mapNotNull { it.toIntOrNull() }
        if (size.isEmpty()) throw UserShaderException("//!TEXTURE $name: missing //!SIZE")
        val storage = block.flag("STORAGE")
        val hex = body.filterNot { it.isWhitespace() }
        return ShaderTexture(
            name = name,
            width = size[0],
            height = size.getOrNull(1),
            depth = size.getOrNull(2),
            format = block.single("FORMAT").orEmpty(),
            filterLinear = block.single("FILTER").equals("LINEAR", ignoreCase = true),
            border = block.single("BORDER") ?: "CLAMP",
            storage = storage,
            data = if (hex.isEmpty()) null else decodeHex(name, hex),
        )
    }

    private fun decodeHex(name: String, hex: String): ByteArray {
        if (hex.length % 2 != 0) throw UserShaderException("//!TEXTURE $name: odd hex data length")
        return ByteArray(hex.length / 2) { i ->
            val byte = hex.substring(i * 2, i * 2 + 2).toIntOrNull(HEX_RADIX)
                ?: throw UserShaderException("//!TEXTURE $name: invalid hex at byte $i")
            byte.toByte()
        }
    }

    private fun buffer(block: Block): ShaderBuffer {
        val name = block.single("BUFFER").takeUnless { it.isNullOrEmpty() }
            ?: throw UserShaderException("//!BUFFER: missing name")
        val vars = block.all("VAR").map { declaration ->
            val type = declaration.substringBefore(' ')
            val varName = declaration.substringAfter(' ', "").trim()
            if (varName.isEmpty()) throw UserShaderException("//!BUFFER $name: bad //!VAR '$declaration'")
            BufferVar(type, varName)
        }
        return ShaderBuffer(name, vars, storage = block.flag("STORAGE"))
    }

    private fun param(block: Block, body: String): ShaderParam {
        val name = block.single("PARAM").takeUnless { it.isNullOrEmpty() }
            ?: throw UserShaderException("//!PARAM: missing name")
        val typeTokens = block.single("TYPE").orEmpty().split(WHITESPACE).filter { it.isNotEmpty() }
        val modifiers = setOf("ENUM", "DEFINE", "DYNAMIC", "CONSTANT")
        return ShaderParam(
            name = name,
            desc = block.single("DESC").orEmpty(),
            type = typeTokens.lastOrNull { it !in modifiers }.orEmpty(),
            define = "DEFINE" in typeTokens,
            dynamic = "DYNAMIC" in typeTokens,
            constant = "CONSTANT" in typeTokens,
            enum = "ENUM" in typeTokens,
            minimum = block.single("MINIMUM")?.toFloatOrNull(),
            maximum = block.single("MAXIMUM")?.toFloatOrNull(),
            default = body.trim(),
        )
    }

    /** Pseudo-name of the current hooked stage in mpv (`HOOKED_tex`, `SAVE HOOKED`). */
    const val HOOKED = "HOOKED"
    private const val DEFAULT_COMPONENTS = 4
    private const val HEX_RADIX = 16
    private val WHITESPACE = Regex("\\s+")
}
