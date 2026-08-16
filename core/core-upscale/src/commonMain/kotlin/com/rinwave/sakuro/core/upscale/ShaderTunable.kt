package com.rinwave.sakuro.core.upscale

/**
 * A `//!PARAM` block a shader exposes — what the UI needs to draw a control
 * for it. Read from the shader file by a [ShaderInspector]; the engines bake
 * the chosen values into the shader when they build the chain.
 */
data class ShaderTunable(
    val name: String,
    /** `//!DESC` of the block; empty when the shader does not document it. */
    val description: String,
    val default: Float,
    val minimum: Float?,
    val maximum: Float?,
    /** `int`/`uint` (and `ENUM`) params step in whole numbers. */
    val integral: Boolean,
) {
    /** The slider range: the shader's bounds, or a sane span around the default. */
    val range: ClosedFloatingPointRange<Float>
        get() {
            val low = minimum ?: minOf(0f, default)
            val high = maximum ?: maxOf(low + 1f, default * FALLBACK_SPAN)
            return low..maxOf(high, low + FALLBACK_EPSILON)
        }

    /** Discrete steps between slider stops for integral params (0 — continuous). */
    val steps: Int
        get() {
            if (!integral) return 0
            val span = (range.endInclusive - range.start).toInt()
            return (span - 1).coerceIn(0, MAX_STEPS)
        }

    fun clamp(value: Float): Float {
        val bounded = value.coerceIn(range)
        return if (integral) bounded.toInt().toFloat() else bounded
    }

    private companion object {
        const val FALLBACK_SPAN = 2f
        const val FALLBACK_EPSILON = 0.001f
        const val MAX_STEPS = 64
    }
}

/** The tunables of one shader file, in declaration order. */
data class ShaderTunables(
    val fileName: String,
    val params: List<ShaderTunable>,
)

/**
 * Reads `//!PARAM` blocks out of shader files (imported or bundled). Implemented
 * by the Media3 engine, which owns the mpv user-shader parser; platforms
 * without it get [NoopShaderInspector].
 */
interface ShaderInspector {
    /** Tunables of [fileName]; empty when the shader has none or cannot be read. */
    fun tunables(fileName: String): List<ShaderTunable>
}

class NoopShaderInspector : ShaderInspector {
    override fun tunables(fileName: String): List<ShaderTunable> = emptyList()
}

/**
 * Per-file `//!PARAM` overrides of a profile, clamped to each param's bounds
 * and stripped of values equal to the default — a profile stores only what the
 * user actually changed.
 */
fun Map<String, Map<String, Float>>.sanitizeAgainst(
    inspector: ShaderInspector,
    chain: List<String>,
): Map<String, Map<String, Float>> = chain
    .mapNotNull { file ->
        val overrides = this[file] ?: return@mapNotNull null
        val declared = inspector.tunables(file).associateBy { it.name }
        val kept = overrides.mapNotNull { (name, value) ->
            val tunable = declared[name] ?: return@mapNotNull null
            val clamped = tunable.clamp(value)
            if (clamped == tunable.default) null else name to clamped
        }.toMap()
        if (kept.isEmpty()) null else file to kept
    }
    .toMap()
