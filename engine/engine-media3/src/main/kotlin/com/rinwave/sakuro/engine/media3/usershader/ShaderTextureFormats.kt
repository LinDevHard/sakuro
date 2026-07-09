package com.rinwave.sakuro.engine.media3.usershader

import android.opengl.GLES30

/** GL upload parameters for one `//!FORMAT` token. */
internal data class TextureFormat(
    val internalFormat: Int,
    val format: Int,
    val type: Int,
    val bytesPerTexel: Int,
)

/**
 * Maps `//!TEXTURE` `//!FORMAT` tokens (libplacebo naming: `rgba16f`, `rg16hf`,
 * `r32f`, `rgba8`, …) to OpenGL ES 3.0 upload parameters. `hf` is an alias for
 * the half-float `f` formats. Unsupported tokens return null — the planner
 * fails the chain with a clear message instead of uploading garbage.
 */
internal object ShaderTextureFormats {

    @Suppress("CyclomaticComplexMethod")
    fun parse(token: String): TextureFormat? = when (token.lowercase()) {
        "r8" -> TextureFormat(GLES30.GL_R8, GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE, 1)
        "rg8" -> TextureFormat(GLES30.GL_RG8, GLES30.GL_RG, GLES30.GL_UNSIGNED_BYTE, 2)
        "rgba8" -> TextureFormat(GLES30.GL_RGBA8, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, 4)
        // Classic-mpv `16f` names carry float32 texel data uploaded into fp16
        // storage (ES 3.0 allows GL_FLOAT client data for 16F internal formats);
        // ravu LUTs ship exactly that. libplacebo's `16hf` names carry true halves.
        "r16f" -> TextureFormat(GLES30.GL_R16F, GLES30.GL_RED, GLES30.GL_FLOAT, 4)
        "rg16f" -> TextureFormat(GLES30.GL_RG16F, GLES30.GL_RG, GLES30.GL_FLOAT, 8)
        "rgba16f" -> TextureFormat(GLES30.GL_RGBA16F, GLES30.GL_RGBA, GLES30.GL_FLOAT, 16)
        "r16hf" -> TextureFormat(GLES30.GL_R16F, GLES30.GL_RED, GLES30.GL_HALF_FLOAT, 2)
        "rg16hf" -> TextureFormat(GLES30.GL_RG16F, GLES30.GL_RG, GLES30.GL_HALF_FLOAT, 4)
        "rgba16hf" -> TextureFormat(GLES30.GL_RGBA16F, GLES30.GL_RGBA, GLES30.GL_HALF_FLOAT, 8)
        "r32f" -> TextureFormat(GLES30.GL_R32F, GLES30.GL_RED, GLES30.GL_FLOAT, 4)
        "rg32f" -> TextureFormat(GLES30.GL_RG32F, GLES30.GL_RG, GLES30.GL_FLOAT, 8)
        "rgba32f" -> TextureFormat(GLES30.GL_RGBA32F, GLES30.GL_RGBA, GLES30.GL_FLOAT, 16)
        else -> null
    }

    /** GL wrap mode for a `//!BORDER` token (`CLAMP` is the default). */
    fun wrapMode(border: String): Int = when (border.uppercase()) {
        "REPEAT" -> GLES30.GL_REPEAT
        "MIRROR" -> GLES30.GL_MIRRORED_REPEAT
        else -> GLES30.GL_CLAMP_TO_EDGE
    }

    /**
     * Checks that a texture is one the executor can upload and sample:
     * a 1D/2D non-storage texture in a known format with correctly sized data.
     * Throws [UserShaderException] otherwise.
     */
    fun validate(texture: ShaderTexture): TextureFormat {
        val format = parse(texture.format)
        val data = texture.data
        val expected = format?.let { texture.width * (texture.height ?: 1) * it.bytesPerTexel }
        val problem = when {
            texture.storage -> "STORAGE images are not supported yet"
            (texture.depth ?: 1) > 1 -> "3D textures are not supported yet"
            format == null -> "unsupported format '${texture.format}'"
            data == null -> "missing texel data"
            data.size != expected ->
                "data is ${data.size} bytes, expected $expected " +
                    "(${texture.width}x${texture.height ?: 1}, '${texture.format}')"
            else -> null
        }
        if (problem != null) throw UserShaderException("//!TEXTURE ${texture.name}: $problem")
        return checkNotNull(format)
    }
}
