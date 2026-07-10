package com.rinwave.sakuro.engine.media3.usershader

/**
 * Byte-size calculation of a `//!BUFFER` block for GL allocation.
 * `//!STORAGE` buffers are shader-storage blocks laid out as std430,
 * plain buffers are uniform blocks laid out as std140 — matching the layout
 * qualifiers [ShaderPreamble] emits.
 */
internal object BufferLayout {

    private data class Scalar(val size: Int, val align: Int)

    /** Base GLSL type → (size, alignment) before layout-specific array rules. */
    private val TYPES = mapOf(
        "float" to Scalar(4, 4),
        "int" to Scalar(4, 4),
        "uint" to Scalar(4, 4),
        "bool" to Scalar(4, 4),
        "vec2" to Scalar(8, 8),
        "ivec2" to Scalar(8, 8),
        "uvec2" to Scalar(8, 8),
        "vec3" to Scalar(12, 16),
        "ivec3" to Scalar(12, 16),
        "uvec3" to Scalar(12, 16),
        "vec4" to Scalar(16, 16),
        "ivec4" to Scalar(16, 16),
        "uvec4" to Scalar(16, 16),
        // Matrices are column arrays of vec4-aligned columns in both layouts.
        "mat2" to Scalar(32, 16),
        "mat3" to Scalar(48, 16),
        "mat4" to Scalar(64, 16),
    )

    private val ARRAY = Regex("""^(\w+)\s*\[\s*(\d+)\s*]$""")

    fun sizeOf(buffer: ShaderBuffer): Int {
        val std140 = !buffer.storage
        var offset = 0
        var maxAlign = 0
        for (variable in buffer.vars) {
            val match = ARRAY.find(variable.name)
            val count = match?.groupValues?.get(2)?.toInt() ?: 1
            val scalar = TYPES[variable.type]
                ?: throw UserShaderException("//!BUFFER ${buffer.name}: unsupported //!VAR type '${variable.type}'")
            // std140 rounds array strides (and their alignment) up to 16 bytes.
            val stride = if (count > 1 || std140 && match != null) {
                val raw = align(scalar.size, scalar.align)
                if (std140) align(raw, VEC4_ALIGN) else raw
            } else {
                scalar.size
            }
            val alignment = when {
                match != null && std140 -> maxOf(scalar.align, VEC4_ALIGN)
                else -> scalar.align
            }
            offset = align(offset, alignment)
            offset += if (match != null) stride * count else scalar.size
            maxAlign = maxOf(maxAlign, alignment)
        }
        return align(offset, maxOf(maxAlign, VEC4_ALIGN))
    }

    private fun align(value: Int, alignment: Int): Int =
        (value + alignment - 1) / alignment * alignment

    private const val VEC4_ALIGN = 16
}
