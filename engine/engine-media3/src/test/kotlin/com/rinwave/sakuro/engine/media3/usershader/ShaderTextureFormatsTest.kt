package com.rinwave.sakuro.engine.media3.usershader

import android.opengl.GLES30
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ShaderTextureFormatsTest {

    @Test
    fun `float formats map to GL upload parameters`() {
        // Classic-mpv `16f`: fp16 storage fed with float32 texel data (ravu LUTs).
        val rgba16f = ShaderTextureFormats.parse("rgba16f")!!
        assertEquals(GLES30.GL_RGBA16F, rgba16f.internalFormat)
        assertEquals(GLES30.GL_FLOAT, rgba16f.type)
        assertEquals(16, rgba16f.bytesPerTexel)

        // libplacebo `16hf`: true half-float texel data.
        val rgba16hf = ShaderTextureFormats.parse("rgba16hf")!!
        assertEquals(GLES30.GL_RGBA16F, rgba16hf.internalFormat)
        assertEquals(GLES30.GL_HALF_FLOAT, rgba16hf.type)
        assertEquals(8, rgba16hf.bytesPerTexel)

        assertEquals(4, ShaderTextureFormats.parse("r32f")!!.bytesPerTexel)
        assertEquals(4, ShaderTextureFormats.parse("rgba8")!!.bytesPerTexel)
    }

    @Test
    fun `unknown formats are rejected`() {
        assertNull(ShaderTextureFormats.parse("rgba8ui"))
        assertNull(ShaderTextureFormats.parse(""))
    }

    @Test
    fun `border tokens map to wrap modes`() {
        assertEquals(GLES30.GL_CLAMP_TO_EDGE, ShaderTextureFormats.wrapMode("CLAMP"))
        assertEquals(GLES30.GL_REPEAT, ShaderTextureFormats.wrapMode("REPEAT"))
        assertEquals(GLES30.GL_MIRRORED_REPEAT, ShaderTextureFormats.wrapMode("MIRROR"))
    }

    @Test
    fun `validate checks data size against the format`() {
        val texture = ShaderTexture(
            name = "LUT", width = 5, height = 648, depth = null, format = "rgba16f",
            filterLinear = false, border = "CLAMP", storage = false,
            data = ByteArray(5 * 648 * 16),
        )
        assertEquals(16, ShaderTextureFormats.validate(texture).bytesPerTexel)
        assertFailsWith<UserShaderException> {
            ShaderTextureFormats.validate(texture.copy(data = ByteArray(3)))
        }
        assertFailsWith<UserShaderException> {
            ShaderTextureFormats.validate(texture.copy(format = "bc7"))
        }
    }
}
