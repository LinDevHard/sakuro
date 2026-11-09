package com.rinwave.media3.usershader

/** A shader chain failed to parse/plan/build; the runtime degrades to passthrough. */
public class UserShaderException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Everything parsed out of one (or several merged) mpv user-shader `.glsl` files:
 * the hook passes plus the auxiliary `//!TEXTURE`, `//!BUFFER` and `//!PARAM` blocks.
 */
@ConsistentCopyVisibility
public data class ShaderDocument internal constructor(
    internal val passes: List<UserShaderPass>,
    internal val textures: List<ShaderTexture>,
    internal val buffers: List<ShaderBuffer>,
    /** Tunable `//!PARAM` blocks of every merged file, in order. */
    public val params: List<ShaderParam>,
) {
    /** Number of hook passes; an empty document makes [UserShaderGlEffect] a no-op. */
    public val passCount: Int get() = passes.size

    public val isEmpty: Boolean get() = passes.isEmpty()

    public companion object {
        public val EMPTY: ShaderDocument = ShaderDocument(emptyList(), emptyList(), emptyList(), emptyList())

        internal fun of(passes: List<UserShaderPass>): ShaderDocument =
            ShaderDocument(passes, emptyList(), emptyList(), emptyList())

        /**
         * Concatenates several files of a chain into one document, preserving order.
         * [parameterValues] holds per-file `//!PARAM` overrides, index-aligned with [documents].
         */
        public fun merge(
            documents: List<ShaderDocument>,
            parameterValues: List<Map<String, Float>> = emptyList(),
        ): ShaderDocument = ShaderDocument(
            passes = documents.flatMapIndexed { index, document ->
                document.passes.map { pass ->
                    pass.copy(
                        parameters = pass.parameters ?: document.params,
                        parameterValues = parameterValues.getOrElse(index) { emptyMap() } + pass.parameterValues,
                    )
                }
            },
            textures = documents.flatMap { it.textures },
            buffers = documents.flatMap { it.buffers },
            params = documents.flatMap { it.params },
        )
    }
}

/**
 * A `//!TEXTURE <name>` block — a custom texture with optional embedded contents
 * (`//!SIZE/FORMAT/FILTER/BORDER/STORAGE` directives; the block body is the texel
 * data as one hex string, absent for `STORAGE` images).
 */
internal data class ShaderTexture(
    val name: String,
    val width: Int,
    val height: Int?,
    val depth: Int?,
    /** Raw format token from the shader (`rgba16f`, `rg16hf`, `r32f`, …). */
    val format: String,
    /** `//!FILTER LINEAR` (default is `NEAREST`). */
    val filterLinear: Boolean,
    /** Raw `//!BORDER` token: `CLAMP` (default), `REPEAT` or `MIRROR`. */
    val border: String,
    /** `//!STORAGE` — bound as a read-write storage image instead of a sampler. */
    val storage: Boolean,
    /** Texel data decoded from the hex body; null for storage images. */
    val data: ByteArray?,
) {
    override fun equals(other: Any?): Boolean = other is ShaderTexture && other.name == name
    override fun hashCode(): Int = name.hashCode()
}

/** One `//!VAR <type> <name>` declaration inside a `//!BUFFER` block. */
internal data class BufferVar(val type: String, val name: String)

/** A `//!BUFFER <name>` block — a uniform (or, with `//!STORAGE`, shader-storage) buffer. */
internal data class ShaderBuffer(
    val name: String,
    val vars: List<BufferVar>,
    val storage: Boolean,
)

/**
 * A `//!PARAM <name>` block — a tunable parameter usable in GLSL bodies and in RPN
 * expressions. The block body holds the default value.
 */
public data class ShaderParam(
    public val name: String,
    public val desc: String,
    /** Scalar GLSL type token: `float`, `int` or `uint`; empty for pure `DEFINE` params. */
    public val type: String,
    /** `TYPE DEFINE` — injected as a preprocessor define instead of a variable. */
    public val define: Boolean,
    public val dynamic: Boolean,
    public val constant: Boolean,
    public val enum: Boolean,
    public val minimum: Float?,
    public val maximum: Float?,
    /** The raw default value (block body). */
    public val default: String,
) {
    /** Numeric default for RPN evaluation (0 when the default is not a number). */
    public val defaultValue: Float get() = default.toFloatOrNull() ?: 0f

    /** The value the runtime uses: the override clamped to `//!MINIMUM`/`//!MAXIMUM`, or the default. */
    public fun effective(override: Float?): Float = override
        ?.coerceIn(minimum ?: Float.NEGATIVE_INFINITY, maximum ?: Float.POSITIVE_INFINITY)
        ?: defaultValue
}

/** `//!OFFSET <x y | ALIGN>` — output position shift relative to the hooked stage. */
internal data class PassOffset(val x: Float, val y: Float, val align: Boolean) {
    val isZero: Boolean get() = !align && x == 0f && y == 0f
}

/** `//!COMPUTE <bw> <bh> [<tw> <th>]` — compute-shader block and workgroup sizes. */
internal data class ComputeLayout(
    val blockWidth: Int,
    val blockHeight: Int,
    val threadsWidth: Int,
    val threadsHeight: Int,
)
